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
    val minutes: Int
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

    private var lastStudyHeartbeatWallMillis: Long = 0L

    private var pomodoroTickerJob: Job? = null
    private var pomodoroStartedAtMillis: Long? = null
    private var pomodoroRemainingSecondsBeforeRun: Long = 25L * 60L

    fun attachContext(context: Context) {
        appContext = context.applicationContext
    }

    fun recoverPendingStudyTimerIfNeeded(
        today: LocalDate = LocalDate.now()
    ): FinishedStudyTimerSession? {
        if (_state.value.studyTimerState != StudyTimerState.STOPPED) {
            return null
        }

        val stored = readStoredStudyTimer() ?: return null

        if (stored.date.isAfter(today)) {
            clearStoredStudyTimer()
            return null
        }

        if (stored.date.isBefore(today)) {
            val minutes = (stored.elapsedSeconds / 60L).toInt()

            clearStoredStudyTimer()
            resetStudyTimer(clearPersistedState = true)

            return if (minutes > 0) {
                FinishedStudyTimerSession(
                    date = stored.date,
                    minutes = minutes
                )
            } else {
                null
            }
        }

        studyDate = stored.date
        studyAccumulatedSecondsBeforeRun = stored.elapsedSeconds
        studyStartedAtElapsedRealtimeMillis = null
        studyStartedAtWallMillis = null

        _state.value = _state.value.copy(
            studyElapsedSeconds = stored.elapsedSeconds,
            studyTimerState = StudyTimerState.PAUSED
        )

        persistStudyTimerNow(
            state = StudyTimerState.PAUSED,
            elapsedSeconds = stored.elapsedSeconds
        )

        return null
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

    fun finishStudyTimerAndReturnSession(): FinishedStudyTimerSession {
        val sessionDate = studyDate ?: LocalDate.now()

        val elapsedSeconds = when (_state.value.studyTimerState) {
            StudyTimerState.RUNNING -> {
                if (sessionDate.isBefore(LocalDate.now())) {
                    calculateStudyElapsedSecondsCappedAtEndOfDay(sessionDate)
                } else {
                    calculateCurrentStudyElapsedSeconds()
                }
            }

            StudyTimerState.PAUSED -> _state.value.studyElapsedSeconds
            StudyTimerState.STOPPED -> 0L
        }

        val minutes = (elapsedSeconds / 60L).toInt()

        resetStudyTimer(clearPersistedState = true)

        return FinishedStudyTimerSession(
            date = sessionDate,
            minutes = minutes
        )
    }

    fun finishStudyTimerAndReturnMinutes(): Int {
        return finishStudyTimerAndReturnSession().minutes
    }

    fun discardStudyTimer() {
        resetStudyTimer(clearPersistedState = true)
    }

    fun setPomodoroDuration(minutes: Int) {
        if (minutes <= 0) return
        if (_state.value.pomodoroTimerState == PomodoroTimerState.RUNNING) return

        val totalSeconds = minutes * 60L

        pomodoroTickerJob?.cancel()
        pomodoroTickerJob = null
        pomodoroStartedAtMillis = null
        pomodoroRemainingSecondsBeforeRun = totalSeconds

        _state.value = _state.value.copy(
            pomodoroRemainingSeconds = totalSeconds,
            pomodoroTotalSeconds = totalSeconds,
            pomodoroTimerState = PomodoroTimerState.STOPPED,
            lastPomodoroMinutes = minutes
        )
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
    }

    fun resumePomodoro() {
        if (_state.value.pomodoroTimerState != PomodoroTimerState.PAUSED) return

        pomodoroRemainingSecondsBeforeRun = _state.value.pomodoroRemainingSeconds
        pomodoroStartedAtMillis = SystemClock.elapsedRealtime()

        _state.value = _state.value.copy(
            pomodoroTimerState = PomodoroTimerState.RUNNING
        )

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

                    break
                } else {
                    _state.value = _state.value.copy(
                        pomodoroRemainingSeconds = remaining
                    )
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

        resetStudyTimer(clearPersistedState = true)

        if (minutes > 0) {
            _autoClosedStudyTimerSessions.tryEmit(
                FinishedStudyTimerSession(
                    date = date,
                    minutes = minutes
                )
            )
        }

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

        return StoredStudyTimer(
            date = date,
            state = state,
            elapsedSeconds = elapsedSeconds,
            lastHeartbeatWallMillis = lastHeartbeatWallMillis
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
        val lastHeartbeatWallMillis: Long
    )

    companion object {
        private const val PREFS_NAME = "planario_study_timer_state"
        private const val KEY_ACTIVE = "active"
        private const val KEY_DATE = "date"
        private const val KEY_STATE = "state"
        private const val KEY_ELAPSED_SECONDS = "elapsed_seconds"
        private const val KEY_LAST_HEARTBEAT_WALL_MILLIS = "last_heartbeat_wall_millis"
        private const val KEY_STARTED_WALL_MILLIS = "started_wall_millis"

        private const val STUDY_HEARTBEAT_INTERVAL_MILLIS = 10_000L
    }
}
