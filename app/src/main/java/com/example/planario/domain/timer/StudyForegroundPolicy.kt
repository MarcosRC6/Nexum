package com.example.planario.domain.timer

/** El servicio compartido no puede desaparecer mientras alguno de los dos relojes
 * necesita continuar o preservar controles de pausa/reanudación.
 */
internal object StudyForegroundPolicy {
    fun shouldKeepRunning(study: StudyTimerState, pomodoro: PomodoroTimerState): Boolean =
        study != StudyTimerState.STOPPED ||
            pomodoro == PomodoroTimerState.RUNNING ||
            pomodoro == PomodoroTimerState.PAUSED
}
