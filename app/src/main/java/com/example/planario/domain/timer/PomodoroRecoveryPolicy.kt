package com.example.planario.domain.timer

import kotlin.math.abs

/** Sólo recupera un Pomodoro en marcha cuando el reloj monotónico y el civil
 * confirman que se trata del mismo arranque del dispositivo.
 */
internal object PomodoroRecoveryPolicy {
    data class Result(val remainingSeconds: Long, val state: PomodoroTimerState)

    fun recover(
        storedState: PomodoroTimerState,
        storedRemainingSeconds: Long,
        beforeElapsedRealtimeMillis: Long,
        beforeWallMillis: Long,
        nowElapsedRealtimeMillis: Long,
        nowWallMillis: Long
    ): Result {
        val remaining = storedRemainingSeconds.coerceAtLeast(0L)
        if (storedState != PomodoroTimerState.RUNNING) {
            return Result(remaining, storedState)
        }
        val monotonicDelta = nowElapsedRealtimeMillis - beforeElapsedRealtimeMillis
        val wallDelta = nowWallMillis - beforeWallMillis
        val trustworthy = beforeElapsedRealtimeMillis >= 0L && beforeWallMillis > 0L &&
            monotonicDelta >= 0L && wallDelta >= 0L &&
            abs(monotonicDelta - wallDelta) <= 5_000L
        if (!trustworthy) return Result(remaining, PomodoroTimerState.PAUSED)
        val remainingNow = (remaining - monotonicDelta / 1000L).coerceAtLeast(0L)
        return Result(remainingNow,
            if (remainingNow == 0L) PomodoroTimerState.FINISHED
            else PomodoroTimerState.RUNNING)
    }
}
