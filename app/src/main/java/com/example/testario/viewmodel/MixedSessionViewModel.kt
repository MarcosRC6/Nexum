package com.example.testario.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.local.SavedMixedSessionStore
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.repository.StudyExerciseRepository
import com.example.testario.data.model.*
import com.example.testario.domain.MixedSessionEngine
import com.example.testario.domain.MixedSessionClock
import com.example.testario.domain.MixedSessionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Separate state for mixed sessions; never overwrites old suspended Testario
 * sessions, legacy test ordering or historical exam snapshots.
 */
class MixedSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SavedMixedSessionStore(application)
    private val repo = MixedSessionRepository(
        TestarioLocalStore.get(application), PreferencesManager(application)
    )
    private val _snapshot = MutableStateFlow<MixedSessionSnapshot?>(null)
    val snapshot: StateFlow<MixedSessionSnapshot?> = _snapshot
    private val _pending = MutableStateFlow(store.load())
    val pending: StateFlow<MixedSessionSnapshot?> = _pending
    private val _result = MutableStateFlow<MixedSessionResult?>(null)
    val result: StateFlow<MixedSessionResult?> = _result
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private var ticker: Job? = null
    private var lastCheckpointSeconds = 0L

    fun start(
        topics: List<Topic>, config: TestConfig,
        cursors: Map<String, Int> = emptyMap()
    ): Boolean {
        if (_pending.value != null) {
            _error.value = "Ya hay un examen mixto guardado. Retómalo o descártalo primero."
            return false
        }
        return runCatching {
            val created = MixedSessionEngine.create(topics, config, config.contentFilter, cursors)
            check(store.save(created)) { "No se pudo guardar la nueva sesión en el dispositivo." }
            ticker?.cancel()
            _result.value = null
            _snapshot.value = created
            _pending.value = created
            _error.value = null
            startTicker(created)
            true
        }.getOrElse {
            _error.value = it.message ?: "No se pudo iniciar la sesión mixta."
            false
        }
    }

    fun resume(): Boolean {
        val saved = store.load() ?: return false
        return runCatching {
            MixedSessionEngine.validate(saved)
            ticker?.cancel()
            _snapshot.value = saved
            _pending.value = saved
            _result.value = null
            _error.value = null
            if (saved.completed) {
                // A process may have died between durable session completion
                // and durable stats. Reconcile idempotently before showing success.
                viewModelScope.launch { finish() }
            } else startTicker(saved)
            true
        }.getOrElse {
            _error.value = it.message ?: "No se pudo reanudar la sesión."
            false
        }
    }

    private fun startTicker(initial: MixedSessionSnapshot) {
        lastCheckpointSeconds = initial.elapsedSeconds
        val began = SystemClock.elapsedRealtime()
        val startElapsed = initial.elapsedSeconds
        val startRemaining = initial.remainingSeconds
        ticker = viewModelScope.launch {
            while (true) {
                delay(1000)
                val active = _snapshot.value ?: break
                if (active.id != initial.id || active.completed) break
                val delta = ((SystemClock.elapsedRealtime() - began) / 1000L).coerceAtLeast(0)
                val newElapsed = startElapsed + delta
                val remaining = startRemaining?.let { (it.toLong() - delta).coerceAtLeast(0).toInt() }
                _snapshot.value = MixedSessionClock.advance(active, newElapsed, remaining)
                if (newElapsed - lastCheckpointSeconds >= 5L) {
                    checkpoint()
                    lastCheckpointSeconds = newElapsed
                }
                if (remaining == 0) {
                    // Do not cancel the running ticker coroutine from inside
                    // its own suspend finish() transaction.
                    viewModelScope.launch { finish() }
                    break
                }
            }
        }
    }

    private fun checkpoint(): Boolean {
        val value = _snapshot.value ?: return false
        val saved = store.save(value)
        if (saved) _pending.value = value
        else _error.value = "No se pudo guardar el progreso. No cierres NEXUM sin exportar tus datos."
        return saved
    }

    private fun edit(update: (MixedSessionSnapshot) -> MixedSessionSnapshot): Boolean {
        val current = _snapshot.value ?: return false
        if (current.completed) return false
        val changed = update(current)
        if (!store.save(changed)) {
            _error.value = "No se pudieron guardar los cambios de la sesión."
            return false
        }
        _snapshot.value = changed
        _pending.value = changed
        _error.value = null
        return true
    }

    /** Persist Testario flags/difficulty in the bank and refresh this session snapshot. */
    fun toggleTestFlag(): Boolean {
        val state = _snapshot.value ?: return false
        val item = state.items.getOrNull(state.currentIndex) ?: return false
        val question = item.question ?: return false
        if (item.type != MixedItemType.TEST) return false
        val db = TestarioLocalStore.get(getApplication<Application>()).database.value
        val current = db.topics.asSequence().flatMap { it.questions.asSequence() }
            .firstOrNull { it.id == question.id } ?: return false
        val newValue = !current.flagged
        val saved = runCatching {
            TestarioLocalStore.get(getApplication<Application>()).saveDatabase(
                db.copy(topics = db.topics.map { topic ->
                    topic.copy(questions = topic.questions.map {
                        if (it.id == question.id) it.copy(flagged = newValue) else it
                    })
                })
            )
        }.isSuccess
        if (!saved) return false
        return edit { original ->
            original.copy(items = original.items.map {
                if (it.type == MixedItemType.TEST && it.question?.id == question.id)
                    it.copy(question = it.question?.copy(flagged = newValue))
                else it
            })
        }
    }

    /** Flags for both written exercises and Anki cards, saved to the original
     * topic and mirrored into any in-progress snapshots of the same exercise.
     * This never marks an answer correct or advances Anki scheduling. */
    fun toggleExerciseFlag(): Boolean {
        val state = _snapshot.value ?: return false
        val item = state.items.getOrNull(state.currentIndex) ?: return false
        if (item.type == MixedItemType.TEST) return false
        val exercise = item.exercise ?: return false
        val dbStore = TestarioLocalStore.get(getApplication<Application>())
        val current = dbStore.database.value.topics.firstOrNull { it.id == item.topicId }
            ?.exercises?.firstOrNull { it.id == exercise.id } ?: return false
        val next = !current.flagged
        if (!runCatching {
            StudyExerciseRepository(dbStore).toggleFlag(item.topicId, exercise.id)
        }.getOrDefault(false)) return false
        return edit { original ->
            original.copy(items = original.items.map { inSession ->
                if (inSession.exercise?.id == exercise.id &&
                    inSession.topicId == item.topicId)
                    inSession.copy(exercise = inSession.exercise?.copy(flagged = next))
                else inSession
            })
        }
    }

    fun updateTestDifficulty(newValue: Int?): Boolean {
        if (newValue != null && newValue !in 1..3) return false
        val state = _snapshot.value ?: return false
        val item = state.items.getOrNull(state.currentIndex) ?: return false
        val question = item.question ?: return false
        if (item.type != MixedItemType.TEST) return false
        val store = TestarioLocalStore.get(getApplication<Application>())
        val db = store.database.value
        if (db.topics.none { t -> t.questions.any { it.id == question.id } }) return false
        if (runCatching {
            store.saveDatabase(db.copy(topics = db.topics.map { t ->
                t.copy(questions = t.questions.map {
                    if (it.id == question.id) it.copy(difficulty = newValue) else it
                })
            }))
        }.isFailure) return false
        return edit { old -> old.copy(items = old.items.map {
            if (it.type == MixedItemType.TEST && it.question?.id == question.id)
                it.copy(question = it.question?.copy(difficulty = newValue))
            else it
        }) }
    }

    fun selectTestAnswer(answerId: String?) = edit { state ->
        val item = state.items[state.currentIndex]
        require(item.type == MixedItemType.TEST)
        require(answerId == null || item.question!!.answers.any { it.id == answerId })
        state.copy(responses = state.responses.mapIndexed { idx, response ->
            if (idx == state.currentIndex) response.copy(
                selectedAnswerId = answerId,
                revealed = state.mode != TestMode.EXAM
            ) else response
        })
    }

    fun submitWritten(text: String, reveal: Boolean = true) = edit { state ->
        require(state.items[state.currentIndex].type == MixedItemType.WRITTEN)
        // Preserve the indices of numbered fields across save/resume.
        // Removing interior blanks would incorrectly shift response 3 into response 2.
        val expected = state.items[state.currentIndex].exercise?.solutions?.size ?: 0
        val parsed = text.lines().take(expected).map(String::trim)
        val answers = if (parsed.all(String::isBlank)) emptyList() else parsed
        state.copy(responses = state.responses.mapIndexed { idx, response ->
            if (idx == state.currentIndex) response.copy(
                writtenAnswers = answers,
                revealed = response.revealed || (reveal && state.mode != TestMode.EXAM)
            ) else response
        })
    }

    fun revealAnki() = edit { state ->
        require(state.items[state.currentIndex].type == MixedItemType.ANKI)
        state.copy(responses = state.responses.mapIndexed { idx, response ->
            if (idx == state.currentIndex) response.copy(revealed = true) else response
        })
    }

    fun goTo(index: Int) = edit { state ->
        require(index in state.items.indices)
        state.copy(currentIndex = index)
    }

    fun saveAndLeave(): Boolean {
        // If storage is full or unavailable, keep the live timer/session running.
        if (!checkpoint()) return false
        ticker?.cancel()
        ticker = null
        return true
    }

    suspend fun finish(): Boolean {
        val current = _snapshot.value ?: return false
        return runCatching {
            val finished = current.copy(completed = true)
            check(store.save(finished)) { "No se pudo proteger el examen antes de finalizarlo." }
            ticker?.cancel()
            ticker = null
            _snapshot.value = finished
            _pending.value = finished
            repo.registerCompletion(finished)
            val calculatedResult = MixedSessionEngine.finish(finished)
            check(store.clear()) { "No se pudo eliminar la sesión pendiente tras guardar el resultado." }
            _result.value = calculatedResult
            _pending.value = null
            _error.value = null
            true
        }.getOrElse {
            _error.value = it.message ?: "No se pudo guardar el resultado."
            false
        }
    }

    fun discardSaved(): Boolean {
        // Never tell the UI that an exam was discarded while its durable slot survives.
        if (!store.clear()) {
            _error.value = "No se pudo eliminar la sesión guardada; inténtalo de nuevo."
            return false
        }
        ticker?.cancel()
        ticker = null
        _pending.value = null
        _snapshot.value = null
        _result.value = null
        _error.value = null
        return true
    }

    override fun onCleared() {
        ticker?.cancel()
        _snapshot.value?.takeUnless { it.completed }?.let { store.save(it) }
        super.onCleared()
    }
}
