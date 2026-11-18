package com.example.planario.domain.timer

import android.content.Context
import android.os.SystemClock

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
        val recovered = PomodoroRecoveryPolicy.recover(
            storedState = storedState,
            storedRemainingSeconds = remaining,
            beforeElapsedRealtimeMillis = prefs.getLong("uptime", -1L),
            beforeWallMillis = prefs.getLong("wall", -1L),
            nowElapsedRealtimeMillis = SystemClock.elapsedRealtime(),
            nowWallMillis = System.currentTimeMillis()
        )
        return Snapshot(minutes, recovered.remainingSeconds, recovered.state)
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
