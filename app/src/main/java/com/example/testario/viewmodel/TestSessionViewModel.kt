package com.example.testario.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.local.SavedTestSessionStore
import com.example.testario.data.local.SavedTestSession
import com.example.testario.data.local.StoredTestAnswer
import java.util.UUID
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestMode
import com.example.testario.data.model.TestQuestionState
import com.example.testario.data.model.TestResult
import com.example.testario.data.model.Topic
import com.example.testario.domain.SmartSelectionEngine
import com.example.testario.domain.TestAnswerOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class TestSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val selectionEngine = SmartSelectionEngine()
    private val preferencesManager = PreferencesManager(application)
    private val savedStore = SavedTestSessionStore(application)
    private val _pendingSavedSession = MutableStateFlow(savedStore.load())
    val pendingSavedSession: StateFlow<SavedTestSession?> = _pendingSavedSession
    private var resumedFromSaved = false
    private var activeSessionId = UUID.randomUUID().toString()
    fun sessionId(): String = activeSessionId

    fun refreshSavedSession() { _pendingSavedSession.value = savedStore.load() }

    private val _questions = MutableStateFlow<List<TestQuestionState>>(emptyList())
    val questions: StateFlow<List<TestQuestionState>> = _questions

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _mode = MutableStateFlow(TestMode.TRAINING)
    val mode: StateFlow<TestMode> = _mode

    private val _remainingSeconds = MutableStateFlow<Int?>(null)
    val remainingSeconds: StateFlow<Int?> = _remainingSeconds

    private val _timeExpired = MutableStateFlow(false)
    val timeExpired: StateFlow<Boolean> = _timeExpired

    private val _totalElapsedSeconds = MutableStateFlow(0L)
    val totalElapsedSeconds: StateFlow<Long> = _totalElapsedSeconds

    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing

    private var timerJob: Job? = null
    private var generationJob: Job? = null
    private var questionTimerJob: Job? = null
    private var sessionActive: Boolean = false
    private var finalStatsClaimed: Boolean = false

    private var pointsCorrect: Double = 1.0
    private var penaltyWrong: Double = -0.25

    private fun startQuestionTicker() {
        questionTimerJob?.cancel()
        questionTimerJob = viewModelScope.launch {
            while (sessionActive) {
                delay(1000)
                if (sessionActive && !_timeExpired.value && _questions.value.isNotEmpty()) {
                    _totalElapsedSeconds.value += 1

                    val idx = _currentIndex.value
                    val list = _questions.value.toMutableList()
                    if (idx in list.indices && !list[idx].corrected) {
                        list[idx] = list[idx].copy(timeSpentSeconds = list[idx].timeSpentSeconds + 1)
                        _questions.value = list
                    }
                }
            }
        }
    }

    fun start(topics: List<Topic>, config: TestConfig) {
        timerJob?.cancel()
        generationJob?.cancel()
        resumedFromSaved = false
        activeSessionId = UUID.randomUUID().toString()

        sessionActive = true
        finalStatsClaimed = false
        _isPreparing.value = true
        _questions.value = emptyList()
        _currentIndex.value = 0
        _mode.value = config.mode
        pointsCorrect = config.pointsCorrect
        penaltyWrong = config.penaltyWrong
        _timeExpired.value = false
        _totalElapsedSeconds.value = 0L
        _remainingSeconds.value = null
        startQuestionTicker()

        val prioritizeFailuresInNormalTests = preferencesManager.getPrioritizeFailuresInNormalTests()

        generationJob = viewModelScope.launch(Dispatchers.Default) {
            val validQuestionsByTopic = topics.mapNotNull { topic ->
                var filtered = topic.questions

                if (config.mode == TestMode.FAILURES) {
                    filtered = filtered.filter { it.stats.inFailures }
                }

                if (config.difficulties.isNotEmpty()) {
                    filtered = filtered.filter { it.difficulty in config.difficulties }
                }

                if (filtered.isNotEmpty()) {
                    topic.id to filtered
                } else {
                    null
                }
            }.toMap()

            val finalQuestions = if (config.groupAllocations.isNotEmpty()) {
                selectionEngine.generateGroupedSmartSelection(
                    validQuestionsByTopic,
                    config,
                    prioritizeFailuresInNormalTests
                )
            } else {
                selectionEngine.generateSmartSelection(
                    validQuestionsByTopic,
                    config,
                    prioritizeFailuresInNormalTests
                )
            }

            val mappedQuestions = TestAnswerOrder.randomizeAndBalance(finalQuestions)
                .map { question -> TestQuestionState(question = question) }

            withContext(Dispatchers.Main) {
                if (!sessionActive) return@withContext

                _questions.value = mappedQuestions
                _currentIndex.value = 0
                _isPreparing.value = false

                if (config.timerEnabled && config.mode == TestMode.EXAM && config.timerMinutes > 0) {
                    startTimer(config.timerMinutes)
                } else {
                    _remainingSeconds.value = null
                }
            }
        }
    }

    private fun startTimer(mins: Int) = startTimerFromSeconds(mins * 60)

    private fun startTimerFromSeconds(seconds: Int) {
        val durationMillis = seconds * 1000L
        val endTime = System.currentTimeMillis() + durationMillis

        _remainingSeconds.value = seconds

        timerJob = viewModelScope.launch {
            while (System.currentTimeMillis() < endTime) {
                val remaining = ((endTime - System.currentTimeMillis()) / 1000).toInt()
                _remainingSeconds.value = remaining
                delay(500)
            }
            _remainingSeconds.value = 0
            _timeExpired.value = true
            sessionActive = false
            questionTimerJob?.cancel()
            questionTimerJob = null
        }
    }


    /** Explicit save-for-later: no results or per-question stats are registered. */
    fun saveForLater(): Boolean {
        if (!sessionActive || _isPreparing.value || _questions.value.isEmpty() || _timeExpired.value) return false
        val snapshot = SavedTestSession(
            sessionId = activeSessionId,
            savedAt = System.currentTimeMillis(),
            mode = _mode.value,
            questionIndex = _currentIndex.value,
            answers = _questions.value.map { s ->
                StoredTestAnswer(s.question, s.selectedAnswerId, s.corrected, s.answersRevealed, s.timeSpentSeconds)
            },
            elapsedSeconds = _totalElapsedSeconds.value,
            remainingSeconds = _remainingSeconds.value,
            pointsCorrect = pointsCorrect,
            penaltyWrong = penaltyWrong
        )
        if (!savedStore.save(snapshot)) return false
        _pendingSavedSession.value = snapshot
        sessionActive = false
        timerJob?.cancel()
        questionTimerJob?.cancel()
        questionTimerJob = null
        return true
    }

    /** Resume exactly the saved question/answer order, with the timed exam frozen while away. */
    fun resumeSavedSession(): Boolean {
        val snapshot = savedStore.load() ?: return false
        if (snapshot.answers.isEmpty()) return false
        timerJob?.cancel()
        generationJob?.cancel()
        questionTimerJob?.cancel()
        activeSessionId = snapshot.sessionId
        resumedFromSaved = true
        sessionActive = true
        finalStatsClaimed = false
        pointsCorrect = snapshot.pointsCorrect
        penaltyWrong = snapshot.penaltyWrong
        _mode.value = snapshot.mode
        _questions.value = snapshot.answers.map { a ->
            TestQuestionState(a.question, a.selectedAnswerId, a.corrected, a.answersRevealed, a.timeSpentSeconds)
        }
        _currentIndex.value = snapshot.questionIndex.coerceIn(0, _questions.value.lastIndex)
        _totalElapsedSeconds.value = snapshot.elapsedSeconds
        _remainingSeconds.value = snapshot.remainingSeconds
        _timeExpired.value = false
        _isPreparing.value = false
        startQuestionTicker()
        snapshot.remainingSeconds?.let { startTimerFromSeconds(it) }
        return true
    }

    fun discardSavedSession() {
        savedStore.clear()
        _pendingSavedSession.value = null
    }

    private fun clearResumedSessionIfNeeded() {
        if (resumedFromSaved) {
            discardSavedSession()
            resumedFromSaved = false
        }
    }

    fun selectAnswer(answerId: String) {
        if (_timeExpired.value) return
        val idx = _currentIndex.value

        // NUEVO: Bloqueo de seguridad en el ViewModel. Si ya está corregida, no se hace nada.
        if (_questions.value.getOrNull(idx)?.corrected == true) return

        _questions.value = _questions.value.mapIndexed { i, s ->
            if (i == idx) s.copy(selectedAnswerId = answerId, corrected = _mode.value == TestMode.TRAINING || _mode.value == TestMode.FAILURES) else s
        }
    }

    fun leaveBlank() {
        if (_timeExpired.value) return
        val idx = _currentIndex.value

        // Bloqueamos también la opción de dejarla en blanco si ya estaba corregida
        if (_questions.value.getOrNull(idx)?.corrected == true) return

        _questions.value = _questions.value.mapIndexed { i, s ->
            if (i == idx) s.copy(selectedAnswerId = null, corrected = false) else s
        }
    }

    fun next() { if (_currentIndex.value < _questions.value.lastIndex) _currentIndex.value++ }
    fun previous() { if (_currentIndex.value > 0) _currentIndex.value-- }
    fun goTo(index: Int) { if (index in _questions.value.indices) _currentIndex.value = index }

    fun toggleFlag(questionId: String) {
        _questions.value = _questions.value.map { s ->
            if (s.question.id == questionId) s.copy(question = s.question.copy(flagged = !s.question.flagged)) else s
        }
    }

    fun updateQuestionDifficulty(questionId: String, d: Int?) {
        _questions.value = _questions.value.map { s ->
            if (s.question.id == questionId) s.copy(question = s.question.copy(difficulty = d)) else s
        }
    }

    fun refreshQuestionsFromDatabase(topics: List<Topic>) {
        val dbQuestions = topics.flatMap { it.questions }.associateBy { it.id }

        val updatedList = _questions.value.map { state ->
            // A saved exam is an immutable snapshot: deleting a source question
            // must never silently erase a previously saved answer.
            val freshQuestion = dbQuestions[state.question.id] ?: return@map state
            if (freshQuestion == state.question) return@map state

            // La sesión baraja las respuestas al arrancar. Un cambio externo de la
            // pregunta (bandera, dificultad, edición, etc.) no debe recolocarlas
            // delante del usuario. Conservamos el orden de IDs ya mostrado y
            // añadimos al final cualquier respuesta nueva que haya aparecido.
            val freshAnswersById = freshQuestion.answers.associateBy { it.id }
            val existingIds = state.question.answers.mapTo(linkedSetOf()) { it.id }
            val mergedAnswers = buildList {
                state.question.answers.forEach { currentAnswer ->
                    freshAnswersById[currentAnswer.id]?.let(::add)
                }
                freshQuestion.answers.forEach { freshAnswer ->
                    if (freshAnswer.id !in existingIds) add(freshAnswer)
                }
            }

            val validAnswerIds = mergedAnswers.mapTo(hashSetOf()) { it.id }
            val selectedAnswerId = state.selectedAnswerId?.takeIf { it in validAnswerIds }

            state.copy(
                question = freshQuestion.copy(answers = mergedAnswers),
                selectedAnswerId = selectedAnswerId,
                corrected = if (state.selectedAnswerId != null && selectedAnswerId == null) false else state.corrected
            )
        }

        _questions.value = updatedList

        if (_currentIndex.value >= updatedList.size && updatedList.isNotEmpty()) {
            _currentIndex.value = updatedList.size - 1
        } else if (updatedList.isEmpty()) {
            _currentIndex.value = 0
        }
    }

    fun finish(): TestResult {
        sessionActive = false
        _isPreparing.value = false
        timerJob?.cancel()
        generationJob?.cancel()
        questionTimerJob?.cancel()
        questionTimerJob = null
        clearResumedSessionIfNeeded()
        val l = _questions.value
        val c = l.count { it.selectedAnswerId != null && it.selectedAnswerId == it.question.correctAnswerId }
        val b = l.count { it.selectedAnswerId == null }
        val w = l.size - c - b
        val raw = c * pointsCorrect + w * penaltyWrong
        val maxScore = l.size * pointsCorrect
        return TestResult(
            total = l.size, correct = c, wrong = w, blank = b, rawScore = raw, maxScore = maxScore,
            score = if (maxScore <= 0.0) 0.0 else ((raw / maxScore) * 10.0).coerceIn(0.0, 10.0),
            percent = if (l.isEmpty()) 0 else ((c.toDouble() / l.size) * 100).roundToInt(), timeExpired = _timeExpired.value
        )
    }

    fun claimFinalStatsRegistration(): Boolean {
        if (finalStatsClaimed) return false
        finalStatsClaimed = true
        return true
    }

    fun cancelSession() {
        clearResumedSessionIfNeeded()
        sessionActive = false
        finalStatsClaimed = false
        _isPreparing.value = false
        timerJob?.cancel()
        generationJob?.cancel()
        questionTimerJob?.cancel()
        questionTimerJob = null
        _remainingSeconds.value = null
        _timeExpired.value = false
        _questions.value = emptyList()
        _currentIndex.value = 0
    }

    fun isLastQuestion(): Boolean = _currentIndex.value == _questions.value.lastIndex

    override fun onCleared() {
        timerJob?.cancel()
        generationJob?.cancel()
        questionTimerJob?.cancel()
        super.onCleared()
    }
}