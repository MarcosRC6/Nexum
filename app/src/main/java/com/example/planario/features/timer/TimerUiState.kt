package com.example.planario.features.timer

import com.example.planario.domain.timer.PomodoroTimerState
import com.example.planario.domain.timer.StudyTimerState

data class TimerUiState(
    val isLoading: Boolean = true,

    val activeTopicName: String? = null,

    val savedTodayMinutes: Int = 0,
    val dailyGoalMinutes: Int = 360,

    val studyElapsedSeconds: Long = 0L,
    val studyTimerState: StudyTimerState = StudyTimerState.STOPPED,

    val pomodoroRemainingSeconds: Long = 25L * 60L,
    val pomodoroTotalSeconds: Long = 25L * 60L,
    val pomodoroTimerState: PomodoroTimerState = PomodoroTimerState.STOPPED,
    val lastPomodoroMinutes: Int = 25
) {
    val currentStudyMinutes: Int
        get() = (studyElapsedSeconds / 60L).toInt()

    val totalTodayMinutesIncludingCurrent: Int
        get() = savedTodayMinutes + currentStudyMinutes

    val remainingTodayMinutes: Int
        get() = (dailyGoalMinutes - totalTodayMinutesIncludingCurrent).coerceAtLeast(0)

    val todayProgress: Float
        get() {
            if (dailyGoalMinutes <= 0) return 0f
            return (totalTodayMinutesIncludingCurrent.toFloat() / dailyGoalMinutes.toFloat())
                .coerceIn(0f, 1f)
        }

    val hasActiveStudyTimer: Boolean
        get() = studyTimerState != StudyTimerState.STOPPED

    val studyElapsedText: String
        get() = studyElapsedSeconds.toTimerText()

    val savedTodayText: String
        get() = savedTodayMinutes.toHourMinuteText()

    val totalTodayIncludingCurrentText: String
        get() = totalTodayMinutesIncludingCurrent.toHourMinuteText()

    val dailyGoalText: String
        get() = dailyGoalMinutes.toHourMinuteText()

    val remainingTodayText: String
        get() = remainingTodayMinutes.toHourMinuteText()

    val pomodoroRemainingText: String
        get() = pomodoroRemainingSeconds.toTimerText()

    val selectedPomodoroText: String
        get() = lastPomodoroMinutes.toHourMinuteInputText()

    val canStartPomodoro: Boolean
        get() = lastPomodoroMinutes > 0

    val pomodoroProgress: Float
        get() {
            if (pomodoroTotalSeconds <= 0L) return 0f
            val done = pomodoroTotalSeconds - pomodoroRemainingSeconds
            return (done.toFloat() / pomodoroTotalSeconds.toFloat())
                .coerceIn(0f, 1f)
        }
}

private fun Long.toTimerText(): String {
    val hours = this / 3600L
    val minutes = (this % 3600L) / 60L
    val seconds = this % 60L

    return if (hours > 0L) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

private fun Int.toHourMinuteText(): String {
    val hours = this / 60
    val minutes = this % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        else -> "${minutes} min"
    }
}

private fun Int.toHourMinuteInputText(): String {
    val hours = this / 60
    val minutes = this % 60

    return "%02d:%02d".format(hours, minutes)
}
