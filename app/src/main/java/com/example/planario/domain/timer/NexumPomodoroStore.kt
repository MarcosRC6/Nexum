package com.example.planario.domain.timer

import android.content.Context
import android.os.SystemClock
import kotlin.math.abs

/** Persistencia independiente del cronómetro de estudio, sin duplicar sus registros. */
internal object NexumPomodoroStore {
    private const val PREFS = "nexum_pomodoro_timer"

    data class Snapshot(
        val minutes: Int,
        val remainingSeconds: Long,
        val state: PomodoroTimerState
    )

    fun load(context: Context): Snapshot {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val minutes = prefs.getInt("minutes", 25).coerceIn(1, 1440)
        val total = minutes * 60L
        val remaining = prefs.getLong("remaining", total).coerceIn(0L, total)
        val storedState = runCatching {
            PomodoroTimerState.valueOf(prefs.getString("state", "STOPPED") ?: "STOPPED")
        }.getOrDefault(PomodoroTimerState.STOPPED)
        val nowUptime = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val beforeUptime = prefs.getLong("uptime", -1L)
        val beforeWall = prefs.getLong("wall", -1L)
        val delta = nowUptime - beforeUptime
        val wallDelta = nowWall - beforeWall
        val safe = beforeUptime >= 0L && beforeWall > 0L && delta >= 0L &&
            wallDelta >= 0L && abs(delta - wallDelta) <= 5_000L
        val finalRemaining = if (storedState == PomodoroTimerState.RUNNING && safe)
            (remaining - delta / 1000L).coerceAtLeast(0L) else remaining
        val finalState = when {
            storedState != PomodoroTimerState.RUNNING -> storedState
            !safe -> PomodoroTimerState.PAUSED
            finalRemaining == 0L -> PomodoroTimerState.FINISHED
            else -> PomodoroTimerState.RUNNING
        }
        return Snapshot(minutes, finalRemaining, finalState)
    }

    fun save(context: Context, state: StudyTimerControllerState) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("minutes", state.lastPomodoroMinutes.coerceIn(1, 1440))
            .putLong("remaining", state.pomodoroRemainingSeconds.coerceAtLeast(0L))
            .putString("state", state.pomodoroTimerState.name)
            .putLong("uptime", SystemClock.elapsedRealtime())
            .putLong("wall", System.currentTimeMillis())
            .apply()
    }
}
