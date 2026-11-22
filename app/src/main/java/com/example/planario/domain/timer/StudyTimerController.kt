package com.example.planario.domain.timer

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

enum class StudyTimerState {
    STOPPED,
    RUNNING,
    PAUSED
}

enum class PomodoroTimerState {
    STOPPED,
    RUNNING,
    PAUSED,
    FINISHED
}

data class StudyTimerControllerState(
    val studyElapsedSeconds: Long = 0L,
    val studyTimerState: StudyTimerState = StudyTimerState.STOPPED,

    val pomodoroRemainingSeconds: Long = 25L * 60L,
    val pomodoroTotalSeconds: Long = 25L * 60L,
    val pomodoroTimerState: PomodoroTimerState = PomodoroTimerState.STOPPED,

    val lastPomodoroMinutes: Int = 25
)

data class FinishedStudyTimerSession(
    val date: LocalDate,
    val minutes: Int,
    /** Stable across retries after a crash; also used as TimeEntry.id. */
    val id: String
)

class StudyTimerController {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(StudyTimerControllerState())
    val state: StateFlow<StudyTimerControllerState> = _state.asStateFlow()

    private val _autoClosedStudyTimerSessions = MutableSharedFlow<FinishedStudyTimerSession>(
        replay = 0,
        extraBufferCapacity = 1
    )
    val autoClosedStudyTimerSessions: SharedFlow<FinishedStudyTimerSession> =
        _autoClosedStudyTimerSessions.asSharedFlow()

    private var appContext: Context? = null

    private var studyTickerJob: Job? = null
    private var studyStartedAtElapsedRealtimeMillis: Long? = null
    private var studyStartedAtWallMillis: Long? = null
    private var studyAccumulatedSecondsBeforeRun: Long = 0L
    private var studyDate: LocalDate? = null
    // SharedPreferences.commit can return false after updating its in-memory
    // map. Retain the journal in memory until a successful acknowledgement.
    private var pendingCompletionInMemory: FinishedStudyTimerSession? = null

    private var lastStudyHeartbeatWallMillis: Long = 0L

    private var pomodoroTickerJob: Job? = null
    private var pomodoroStartedAtMillis: Long? = null
    private var pomodoroRemainingSecondsBeforeRun: Long = 25L * 60L
    private var pomodoroRestored = false
    private var lastPomodoroPersistWallMillis = 0L

    fun attachContext(context: Context) {
        appContext = context.applicationContext
        if (!pomodoroRestored) {
            pomodoroRestored = true
            val saved = NexumPomodoroStore.load(context.applicationContext)
            pomodoroRemainingSecondsBeforeRun = saved.remainingSeconds
            pomodoroStartedAtMillis = if (saved.state == PomodoroTimerState.RUNNING)
                SystemClock.elapsedRealtime() else null
            _state.value = _state.value.copy(
                pomodoroRemainingSeconds = saved.remainingSeconds,
                pomodoroTotalSeconds = saved.minutes * 60L,
                pomodoroTimerState = saved.state,
                lastPomodoroMinutes = saved.minutes
            )
            savePomodoroState()
            if (saved.state == PomodoroTimerState.RUNNING) startPomodoroTicker()
        }
    }

    private fun savePomodoroState() {
        appContext?.let { NexumPomodoroStore.save(it, _state.value) }
        lastPomodoroPersistWallMillis = System.currentTimeMillis()
    }

    fun persistCurrentPomodoroNow() {
        if (_state.value.pomodoroTimerState == PomodoroTimerState.RUNNING) {
            _state.value = _state.value.copy(
                pomodoroRemainingSeconds = calculateCurrentPomodoroRemainingSeconds()
            )
        }
        savePomodoroState()
    }

    fun recoverPendingStudyTimerIfNeeded(
        today: LocalDate = LocalDate.now()
    ): FinishedStudyTimerSession? {
        // A previous process may have saved the journal, saved Planario and
        // died before acknowledging. Replaying the SAME ID is safe.
        readPendingStudyTimerCompletion()?.let { return it }
        if (_state.value.studyTimerState != StudyTimerState.STOPPED) return null

        val stored = readStoredStudyTimer() ?: return null
        if (stored.date.isAfter(today)) {
            clearStoredStudyTimer()
            return null
        }
        if (stored.date.isBefore(today)) {
            val safeSeconds = recoverAccurateRunningSeconds(stored, endedYesterday = true)
                ?: stored.elapsedSeconds
            val minutes = (safeSeconds / 60L).toInt()
            // Stage durably; DON'T delete the timer until Planario commits.
            return stageStudyTimerCompletion(stored.date, minutes)
        }

        studyDate = stored.date
        val recoveredRunningSeconds = recoverAccurateRunningSeconds(stored)
        val canResume = stored.state == StudyTimerState.RUNNING &&
            recoveredRunningSeconds != null
        studyAccumulatedSecondsBeforeRun = recoveredRunningSeconds ?: stored.elapsedSeconds
        studyStartedAtElapsedRealtimeMillis = if (canResume) SystemClock.elapsedRealtime() else null
        studyStartedAtWallMillis = if (canResume) System.currentTimeMillis() else null

        _state.value = _state.value.copy(
            studyElapsedSeconds = studyAccumulatedSecondsBeforeRun,
            studyTimerState = if (canResume) StudyTimerState.RUNNING else StudyTimerState.PAUSED
        )
        persistStudyTimerNow(
            state = _state.value.studyTimerState,
            elapsedSeconds = studyAccumulatedSecondsBeforeRun
        )
        if (canResume) startStudyTicker()
        return null
    }

    /**
     * Recover time ONLY when wall time and monotonic uptime agree: same boot,
     * no clock jump, and a bounded interval. Otherwise preserve saved seconds
     * and pause rather than inventing a study session.
     */
    private fun recoverAccurateRunningSeconds(
        stored: StoredStudyTimer, endedYesterday: Boolean = false
    ): Long? {
        if (stored.state != StudyTimerState.RUNNING) return null
        val endOfDayMillis = if (endedYesterday) stored.date.plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() else null
        return TimerRecoveryPolicy.safeRunningSeconds(
            savedSeconds = stored.elapsedSeconds,
            lastWallMillis = stored.lastHeartbeatWallMillis,
            lastUptimeMillis = stored.lastHeartbeatUptimeMillis,
            nowWallMillis = System.currentTimeMillis(),
            nowUptimeMillis = SystemClock.elapsedRealtime(),
            endOfDayWallMillis = endOfDayMillis
        )
    }

    fun restorePausedStudyTimer(
        date: LocalDate,
        elapsedSeconds: Long
    ) {
        studyTickerJob?.cancel()
        studyTickerJob = null

        val safeElapsedSeconds = elapsedSeconds.coerceAtLeast(0L)
        studyDate = date
        studyAccumulatedSecondsBeforeRun = safeElapsedSeconds
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null

        _state.value = _state.value.copy(
            studyElapsedSeconds = safeElapsedSeconds,
            studyTimerState = StudyTimerState.PAUSED
        )

        persistStudyTimerNow(
            state = StudyTimerState.PAUSED,
            elapsedSeconds = safeElapsedSeconds
        )
    }

    fun persistCurrentStudyTimerNow() {
        val currentState = _state.value.studyTimerState

        if (currentState == StudyTimerState.STOPPED) {
            clearStoredStudyTimer()
            return
        }

        val elapsedSeconds = when (currentState) {
            StudyTimerState.RUNNING -> calculateCurrentStudyElapsedSeconds()
            StudyTimerState.PAUSED -> _state.value.studyElapsedSeconds
            StudyTimerState.STOPPED -> 0L
        }

        persistStudyTimerNow(
            state = currentState,
            elapsedSeconds = elapsedSeconds
        )
    }

    fun startStudyTimer(
        date: LocalDate = LocalDate.now()
    ) {
        // An unacknowledged completed session must never be overwritten by a
        // new one. Normal starts are unchanged once the journal is cleared.
        if (readPendingStudyTimerCompletion() != null) return
        val current = _state.value

        if (current.studyTimerState == StudyTimerState.RUNNING) return

        if (current.studyTimerState == StudyTimerState.STOPPED) {
            studyDate = date
            studyAccumulatedSecondsBeforeRun = 0L
            updateStudyElapsedSeconds(0L)
        } else {
            studyDate = studyDate ?: date
            studyAccumulatedSecondsBeforeRun = current.studyElapsedSeconds
        }

        studyStartedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        studyStartedAtWallMillis = System.currentTimeMillis()

        _state.value = _state.value.copy(
            studyTimerState = StudyTimerState.RUNNING
        )

        persistStudyTimerNow(
            state = StudyTimerState.RUNNING,
            elapsedSeconds = _state.value.studyElapsedSeconds
        )

        startStudyTicker()
    }

    fun pauseStudyTimer() {
        if (_state.value.studyTimerState != StudyTimerState.RUNNING) return

        val elapsedSeconds = calculateCurrentStudyElapsedSeconds()

        studyTickerJob?.cancel()
        studyTickerJob = null
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null
        studyAccumulatedSecondsBeforeRun = elapsedSeconds

        _state.value = _state.value.copy(
            studyElapsedSeconds = elapsedSeconds,
            studyTimerState = StudyTimerState.PAUSED
        )

        persistStudyTimerNow(
            state = StudyTimerState.PAUSED,
            elapsedSeconds = elapsedSeconds
        )
    }

    fun resumeStudyTimer() {
        // A stop with pending durable completion cannot resume or alter its
        // frozen minutes while a write is being retried.
        if (readPendingStudyTimerCompletion() != null) return
        if (_state.value.studyTimerState != StudyTimerState.PAUSED) return

        studyAccumulatedSecondsBeforeRun = _state.value.studyElapsedSeconds
        studyStartedAtElapsedRealtimeMillis = SystemClock.elapsedRealtime()
        studyStartedAtWallMillis = System.currentTimeMillis()
        studyDate = studyDate ?: LocalDate.now()

        _state.value = _state.value.copy(
            studyTimerState = StudyTimerState.RUNNING
        )

        persistStudyTimerNow(
            state = StudyTimerState.RUNNING,
            elapsedSeconds = _state.value.studyElapsedSeconds
        )

        startStudyTicker()
    }

    /**
     * Stage the stop before touching the active timer. A journal in the same
     * SharedPreferences survives process death between the Planario commit
     * and the final removal of the stopwatch snapshot.
     *
     * This method does NOT stop/reset the timer. Call acknowledge only after
     * StudyRepository.recordTimerSessionOnce() returns true.
     */
    fun prepareStudyTimerCompletion(): FinishedStudyTimerSession? {
        readPendingStudyTimerCompletion()?.let { return it }
        if (_state.value.studyTimerState == StudyTimerState.STOPPED) return null
        val date = studyDate ?: LocalDate.now()
        val seconds = when (_state.value.studyTimerState) {
            StudyTimerState.RUNNING -> if (date.isBefore(LocalDate.now())) {
                calculateStudyElapsedSecondsCappedAtEndOfDay(date)
            } else calculateCurrentStudyElapsedSeconds()
            StudyTimerState.PAUSED -> _state.value.studyElapsedSeconds
            StudyTimerState.STOPPED -> 0L
        }
        val staged = stageStudyTimerCompletion(date, (seconds / 60L).toInt())
            ?: return null
        // Pressing "stop/save" freezes the clock at that instant. On a disk
        // failure it stays PAUSED and the user can retry without losing time.
        studyTickerJob?.cancel()
        studyTickerJob = null
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null
        studyAccumulatedSecondsBeforeRun = seconds
        _state.value = _state.value.copy(
            studyElapsedSeconds = seconds,
            studyTimerState = StudyTimerState.PAUSED
        )
        persistStudyTimerNow(StudyTimerState.PAUSED, seconds)
        return staged
    }

    /**
     * Final stage of the commit protocol. Removal of the journal and active
     * timer is ONE durable preference transaction. If it fails, a later
     * restart replays the same ID and Planario deduplicates it.
     */
    fun acknowledgeSavedStudyTimerSession(session: FinishedStudyTimerSession): Boolean {
        val pending = readPendingStudyTimerCompletion() ?: return false
        if (pending != session) return false
        val preferences = prefs() ?: return false
        val cleared = preferences.edit()
            .remove(KEY_PENDING_SESSION_ID)
            .remove(KEY_PENDING_SESSION_DATE)
            .remove(KEY_PENDING_SESSION_MINUTES)
            .remove(KEY_ACTIVE).remove(KEY_DATE).remove(KEY_STATE)
            .remove(KEY_ELAPSED_SECONDS).remove(KEY_LAST_HEARTBEAT_WALL_MILLIS)
            .remove(KEY_LAST_HEARTBEAT_UPTIME_MILLIS).remove(KEY_STARTED_WALL_MILLIS)
            .commit()
        if (cleared) {
            pendingCompletionInMemory = null
            resetStudyTimer(clearPersistedState = false)
        }
        return cleared
    }

    private fun stageStudyTimerCompletion(date: LocalDate, minutes: Int):
        FinishedStudyTimerSession? {
        readPendingStudyTimerCompletion()?.let { return it }
        val preferences = prefs() ?: return null
        val session = FinishedStudyTimerSession(
            date = date, minutes = minutes.coerceAtLeast(0),
            id = UUID.randomUUID().toString()
        )
        val written = preferences.edit()
            .putString(KEY_PENDING_SESSION_ID, session.id)
            .putString(KEY_PENDING_SESSION_DATE, session.date.toString())
            .putInt(KEY_PENDING_SESSION_MINUTES, session.minutes)
            .commit()
        if (written) pendingCompletionInMemory = session
        return if (written) session else null
    }

    private fun readPendingStudyTimerCompletion(): FinishedStudyTimerSession? {
        pendingCompletionInMemory?.let { return it }
        val preferences = prefs() ?: return null
        val id = preferences.getString(KEY_PENDING_SESSION_ID, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        val date = runCatching {
            LocalDate.parse(preferences.getString(KEY_PENDING_SESSION_DATE, null))
        }.getOrNull() ?: return null
        return FinishedStudyTimerSession(
            date = date,
            minutes = preferences.getInt(KEY_PENDING_SESSION_MINUTES, 0).coerceAtLeast(0),
            id = id
        ).also { pendingCompletionInMemory = it }
    }

    fun hasPendingStudyTimerCompletion(): Boolean =
        readPendingStudyTimerCompletion() != null

    fun discardStudyTimer() {
        // Once a stop has been staged it might ALREADY have been written to
        // Planario. Require recovery/ack first; don't create a phantom
        // "discarded" session with a journal that survives the reset.
        if (readPendingStudyTimerCompletion() != null) return
        resetStudyTimer(clearPersistedState = true)
    }

    fun setPomodoroDuration(minutes: Int) {
        if (minutes <= 0) return
        if (_state.value.pomodoroTimerState == PomodoroTimerState.RUNNING ||
            _state.value.pomodoroTimerState == PomodoroTimerState.PAUSED) return

        val totalSeconds = minutes.coerceIn(1, 1440) * 60L

        pomodoroTickerJob?.cancel()
        pomodoroTickerJob = null
        pomodoroStartedAtMillis = null
        pomodoroRemainingSecondsBeforeRun = totalSeconds

        _state.value = _state.value.copy(
            pomodoroRemainingSeconds = totalSeconds,
            pomodoroTotalSeconds = totalSeconds,
            pomodoroTimerState = PomodoroTimerState.STOPPED,
            lastPomodoroMinutes = minutes.coerceIn(1, 1440)
        )
        savePomodoroState()
    }

    fun startPomodoro() {
        val minutes = _state.value.lastPomodoroMinutes
        if (minutes <= 0) return

        val totalSeconds = minutes * 60L

        pomodoroTickerJob?.cancel()
        pomodoroTickerJob = null

        pomodoroRemainingSecondsBeforeRun = totalSeconds
        pomodoroStartedAtMillis = SystemClock.elapsedRealtime()

        _state.value = _state.value.copy(
            pomodoroRemainingSeconds = totalSeconds,
            pomodoroTotalSeconds = totalSeconds,
            pomodoroTimerState = PomodoroTimerState.RUNNING
        )

        savePomodoroState()
        startPomodoroTicker()
    }

    fun pausePomodoro() {
        if (_state.value.pomodoroTimerState != PomodoroTimerState.RUNNING) return

        val remainingSeconds = calculateCurrentPomodoroRemainingSeconds()

        pomodoroTickerJob?.cancel()
        pomodoroTickerJob = null
        pomodoroStartedAtMillis = null
        pomodoroRemainingSecondsBeforeRun = remainingSeconds

        _state.value = _state.value.copy(
            pomodoroRemainingSeconds = remainingSeconds,
            pomodoroTimerState = PomodoroTimerState.PAUSED
        )
        savePomodoroState()
    }

    fun resumePomodoro() {
        if (_state.value.pomodoroTimerState != PomodoroTimerState.PAUSED) return

        pomodoroRemainingSecondsBeforeRun = _state.value.pomodoroRemainingSeconds
        pomodoroStartedAtMillis = SystemClock.elapsedRealtime()

        _state.value = _state.value.copy(
            pomodoroTimerState = PomodoroTimerState.RUNNING
        )

        savePomodoroState()
        startPomodoroTicker()
    }

    fun resetPomodoro() {
        pomodoroTickerJob?.cancel()
        pomodoroTickerJob = null
        pomodoroStartedAtMillis = null

        val totalSeconds = _state.value.pomodoroTotalSeconds

        pomodoroRemainingSecondsBeforeRun = totalSeconds

        _state.value = _state.value.copy(
            pomodoroRemainingSeconds = totalSeconds,
            pomodoroTimerState = PomodoroTimerState.STOPPED
        )
        savePomodoroState()
    }

    private fun startStudyTicker() {
        studyTickerJob?.cancel()

        studyTickerJob = scope.launch {
            while (true) {
                if (autoCloseStudyTimerIfDateChanged()) {
                    break
                }

                val elapsedSeconds = calculateCurrentStudyElapsedSeconds()
                updateStudyElapsedSeconds(elapsedSeconds)
                persistHeartbeatIfNeeded(elapsedSeconds)

                delay(500L)
            }
        }
    }

    private fun startPomodoroTicker() {
        pomodoroTickerJob?.cancel()

        pomodoroTickerJob = scope.launch {
            while (true) {
                val remaining = calculateCurrentPomodoroRemainingSeconds()

                if (remaining <= 0L) {
                    pomodoroTickerJob?.cancel()
                    pomodoroTickerJob = null
                    pomodoroStartedAtMillis = null
                    pomodoroRemainingSecondsBeforeRun = 0L

                    _state.value = _state.value.copy(
                        pomodoroRemainingSeconds = 0L,
                        pomodoroTimerState = PomodoroTimerState.FINISHED
                    )

                    savePomodoroState()
                    break
                } else {
                    _state.value = _state.value.copy(
                        pomodoroRemainingSeconds = remaining
                    )
                if (System.currentTimeMillis() - lastPomodoroPersistWallMillis >= 10_000L) {
                        savePomodoroState()
                    }
                }

                delay(500L)
            }
        }
    }

    private fun autoCloseStudyTimerIfDateChanged(): Boolean {
        val date = studyDate ?: return false

        if (!date.isBefore(LocalDate.now())) {
            return false
        }

        val elapsedSeconds = when (_state.value.studyTimerState) {
            StudyTimerState.RUNNING -> calculateStudyElapsedSecondsCappedAtEndOfDay(date)
            StudyTimerState.PAUSED -> _state.value.studyElapsedSeconds
            StudyTimerState.STOPPED -> 0L
        }

        val minutes = (elapsedSeconds / 60L).toInt()

        // SharedFlow(replay=0) discards a close event if the foreground
        // service has no active subscriber. Keep the previous day's PAUSED
        // snapshot instead of deleting the only copy of the study time.
        if (TimerAutoClosePolicy.preserveForRecovery(
                minutes, _autoClosedStudyTimerSessions.subscriptionCount.value)) {
            studyAccumulatedSecondsBeforeRun = elapsedSeconds
            studyStartedAtElapsedRealtimeMillis = null
            studyStartedAtWallMillis = null
            _state.value = _state.value.copy(
                studyElapsedSeconds = elapsedSeconds,
                studyTimerState = StudyTimerState.PAUSED
            )
            persistStudyTimerNow(StudyTimerState.PAUSED, elapsedSeconds)
            return true
        }

        // The observer may be interrupted immediately after this event.
        // Stage the exact same stable ID before notifying it; neither the
        // event nor the controller may delete the original timer snapshot.
        val pending = stageStudyTimerCompletion(date, minutes) ?: return true
        studyTickerJob?.cancel()
        studyTickerJob = null
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null
        studyAccumulatedSecondsBeforeRun = elapsedSeconds
        _state.value = _state.value.copy(
            studyElapsedSeconds = elapsedSeconds,
            studyTimerState = StudyTimerState.PAUSED
        )
        persistStudyTimerNow(StudyTimerState.PAUSED, elapsedSeconds)
        if (pending.minutes > 0) _autoClosedStudyTimerSessions.tryEmit(pending)
        return true
    }

    private fun calculateCurrentStudyElapsedSeconds(): Long {
        val startedAt = studyStartedAtElapsedRealtimeMillis
            ?: return studyAccumulatedSecondsBeforeRun

        val runningSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000L

        return (studyAccumulatedSecondsBeforeRun + runningSeconds)
            .coerceAtLeast(0L)
    }

    private fun calculateStudyElapsedSecondsCappedAtEndOfDay(
        date: LocalDate
    ): Long {
        val startedWall = studyStartedAtWallMillis
            ?: return calculateCurrentStudyElapsedSeconds()

        val endOfDayWallMillis = date
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val runningSecondsUntilEndOfDay = ((endOfDayWallMillis - startedWall) / 1000L)
            .coerceAtLeast(0L)

        return (studyAccumulatedSecondsBeforeRun + runningSecondsUntilEndOfDay)
            .coerceAtLeast(0L)
    }

    private fun calculateCurrentPomodoroRemainingSeconds(): Long {
        val startedAt = pomodoroStartedAtMillis ?: return pomodoroRemainingSecondsBeforeRun
        val elapsedSeconds = (SystemClock.elapsedRealtime() - startedAt) / 1000L

        return (pomodoroRemainingSecondsBeforeRun - elapsedSeconds).coerceAtLeast(0L)
    }

    private fun updateStudyElapsedSeconds(seconds: Long) {
        _state.value = _state.value.copy(
            studyElapsedSeconds = seconds
        )
    }

    private fun persistHeartbeatIfNeeded(
        elapsedSeconds: Long
    ) {
        val now = System.currentTimeMillis()

        if (now - lastStudyHeartbeatWallMillis < STUDY_HEARTBEAT_INTERVAL_MILLIS) {
            return
        }

        persistStudyTimerNow(
            state = _state.value.studyTimerState,
            elapsedSeconds = elapsedSeconds,
            nowWallMillis = now
        )
    }

    private fun persistStudyTimerNow(
        state: StudyTimerState,
        elapsedSeconds: Long,
        nowWallMillis: Long = System.currentTimeMillis()
    ) {
        if (state == StudyTimerState.STOPPED) {
            clearStoredStudyTimer()
            return
        }

        val date = studyDate ?: LocalDate.now()

        prefs()
            ?.edit()
            ?.putBoolean(KEY_ACTIVE, true)
            ?.putString(KEY_DATE, date.toString())
            ?.putString(KEY_STATE, state.name)
            ?.putLong(KEY_ELAPSED_SECONDS, elapsedSeconds.coerceAtLeast(0L))
            ?.putLong(KEY_LAST_HEARTBEAT_WALL_MILLIS, nowWallMillis)
            ?.putLong(KEY_LAST_HEARTBEAT_UPTIME_MILLIS, SystemClock.elapsedRealtime())
            ?.putLong(KEY_STARTED_WALL_MILLIS, studyStartedAtWallMillis ?: 0L)
            ?.apply()

        lastStudyHeartbeatWallMillis = nowWallMillis
    }

    private fun readStoredStudyTimer(): StoredStudyTimer? {
        val prefs = prefs() ?: return null

        if (!prefs.getBoolean(KEY_ACTIVE, false)) {
            return null
        }

        val rawDate = prefs.getString(KEY_DATE, null) ?: return null
        val rawState = prefs.getString(KEY_STATE, null) ?: return null

        val date = runCatching {
            LocalDate.parse(rawDate)
        }.getOrNull() ?: return null

        val state = runCatching {
            StudyTimerState.valueOf(rawState)
        }.getOrNull() ?: StudyTimerState.PAUSED

        val elapsedSeconds = prefs.getLong(KEY_ELAPSED_SECONDS, 0L)
            .coerceAtLeast(0L)

        val lastHeartbeatWallMillis = prefs.getLong(KEY_LAST_HEARTBEAT_WALL_MILLIS, 0L)
        val lastHeartbeatUptimeMillis = prefs.getLong(KEY_LAST_HEARTBEAT_UPTIME_MILLIS, 0L)

        return StoredStudyTimer(
            date = date,
            state = state,
            elapsedSeconds = elapsedSeconds,
            lastHeartbeatWallMillis = lastHeartbeatWallMillis,
            lastHeartbeatUptimeMillis = lastHeartbeatUptimeMillis
        )
    }

    private fun clearStoredStudyTimer() {
        prefs()
            ?.edit()
            ?.remove(KEY_ACTIVE)
            ?.remove(KEY_DATE)
            ?.remove(KEY_STATE)
            ?.remove(KEY_ELAPSED_SECONDS)
            ?.remove(KEY_LAST_HEARTBEAT_WALL_MILLIS)
            ?.remove(KEY_LAST_HEARTBEAT_UPTIME_MILLIS)
            ?.remove(KEY_STARTED_WALL_MILLIS)
            ?.apply()

        lastStudyHeartbeatWallMillis = 0L
    }

    private fun prefs(): SharedPreferences? {
        return appContext?.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

    private fun resetStudyTimer(
        clearPersistedState: Boolean
    ) {
        studyTickerJob?.cancel()
        studyTickerJob = null
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null
        studyAccumulatedSecondsBeforeRun = 0L
        studyDate = null

        _state.value = _state.value.copy(
            studyElapsedSeconds = 0L,
            studyTimerState = StudyTimerState.STOPPED
        )

        if (clearPersistedState) {
            clearStoredStudyTimer()
        }
    }

    private data class StoredStudyTimer(
        val date: LocalDate,
        val state: StudyTimerState,
        val elapsedSeconds: Long,
        val lastHeartbeatWallMillis: Long,
        val lastHeartbeatUptimeMillis: Long
    )

    companion object {
        private const val PREFS_NAME = "planario_study_timer_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_DATE = "date"
        private const val KEY_STATE = "state"
        private const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
        private const val KEY_LAST_HEARTBEAT_WALL_MILLIS = "last_heartbeat_wall_millis"
        private const val KEY_LAST_HEARTBEAT_UPTIME_MILLIS = "last_heartbeat_uptime_millis"
        private const val KEY_STARTED_WALL_MILLIS = "started_wall_millis"
        private const val KEY_PENDING_SESSION_ID = "pending_session_id"
        private const val KEY_PENDING_SESSION_DATE = "pending_session_date"
        private const val KEY_PENDING_SESSION_MINUTES = "pending_session_minutes"

        private const val STUDY_HEARTBEAT_INTERVAL_MILLIS = 10_000L
    }
}
