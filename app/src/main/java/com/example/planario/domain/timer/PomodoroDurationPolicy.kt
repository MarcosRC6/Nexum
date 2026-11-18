package com.example.planario.domain.timer

/** La duración escogida en Plan es la autoridad, no el valor inicial de 25 minutos. */
internal object PomodoroDurationPolicy {
    fun shouldApplyConfiguredMinutes(
        state: PomodoroTimerState,
        currentMinutes: Int,
        configuredMinutes: Int
    ): Boolean = configuredMinutes > 0 && currentMinutes != configuredMinutes &&
        state != PomodoroTimerState.RUNNING && state != PomodoroTimerState.PAUSED
}
