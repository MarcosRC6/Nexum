package com.example.testario.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.local.SavedMixedSessionStore
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.*
import com.example.testario.domain.MixedSessionEngine
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
                _snapshot.value = active.copy(
                    elapsedSeconds = newElapsed, remainingSeconds = remaining,
                    responses = active.responses.mapIndexed { idx, response ->
                        if (idx == active.currentIndex)
                            response.copy(timeSpentSeconds = response.timeSpentSeconds + 1)
                        else response
                    }
                )
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

    /** Edits displayed metadata only; never alters answer IDs or grading. */
    fun updateTestQuestion(questionId: String, flagged: Boolean? = null,
        difficulty: Int? = null, updateDifficulty: Boolean = false): Boolean = edit { state ->
        state.copy(items = state.items.map { item ->
            if (item.type == MixedItemType.TEST && item.question?.id == questionId) {
                item.copy(question = item.question.copy(
                    flagged = flagged ?: item.question.flagged,
                    difficulty = if (updateDifficulty) difficulty else item.question.difficulty
                ))
            } else item
        })
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
        // Keep blank slots: Solution 3 must remain solution 3 after suspend/resume.
        val answers = text.lines().map(String::trim)
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
        ticker?.cancel()
        return checkpoint()
    }

    suspend fun finish(): Boolean {
        ticker?.cancel()
        val current = _snapshot.value ?: return false
        return runCatching {
            val finished = current.copy(completed = true)
            check(store.save(finished)) { "No se pudo proteger el examen antes de finalizarlo." }
            _snapshot.value = finished
            _pending.value = finished
            repo.registerCompletion(finished)
            _result.value = MixedSessionEngine.finish(finished)
            store.clear()
            _pending.value = null
            _error.value = null
            true
        }.getOrElse {
            _error.value = it.message ?: "No se pudo guardar el resultado."
            false
        }
    }

    /** Leave an unfinished mixed test without creating resumable progress or stats. */
    fun discardActive(): Boolean {
        ticker?.cancel()
        val erased = runCatching { store.clear(); store.load() == null }.getOrDefault(false)
        if (!erased) {
            _error.value = "No se pudo descartar la sesión; no se ha cerrado."
            return false
        }
        _pending.value = null
        _snapshot.value = null
        _result.value = null
        _error.value = null
        return true
    }

    fun discardSaved(): Boolean {
        ticker?.cancel()
        store.clear()
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
