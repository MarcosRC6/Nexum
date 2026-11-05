package com.example.planario.features.home

import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyTopic
import com.example.planario.domain.timer.StudyTimerState

data class HomeUiState(
    val isLoading: Boolean = true,

    val dateText: String = "",

    val activeTopic: StudyTopic? = null,
    val fixedObjectives: List<FixedObjective> = emptyList(),
    val completedFixedObjectiveIds: Set<String> = emptySet(),

    val studiedMinutes: Int = 0,
    val dailyGoalMinutes: Int = 360,

    val studyElapsedSeconds: Long = 0L,
    val studyTimerState: StudyTimerState = StudyTimerState.STOPPED,

    val dayStatus: DayStatus = DayStatus.EMPTY,
    val isRestDay: Boolean = false,
    val isFreeDay: Boolean = false,
    val isFinishedManually: Boolean = false
) {
    val currentStudyMinutes: Int
        get() = (studyElapsedSeconds / 60L).toInt()

    val totalTodayMinutesIncludingCurrent: Int
        get() = studiedMinutes + currentStudyMinutes

    val remainingMinutes: Int
        get() = (dailyGoalMinutes - totalTodayMinutesIncludingCurrent).coerceAtLeast(0)

    val progress: Float
        get() {
            if (dailyGoalMinutes <= 0) return 0f
            return (totalTodayMinutesIncludingCurrent.toFloat() / dailyGoalMinutes.toFloat())
                .coerceIn(0f, 1f)
        }

    val isStudyTimerActive: Boolean
        get() = studyTimerState != StudyTimerState.STOPPED

    val effectiveDayStatus: DayStatus
        get() {
            if (isFreeDay) return DayStatus.REST
            if (isFinishedManually) return DayStatus.COMPLETED
            if (dailyGoalMinutes > 0 && totalTodayMinutesIncludingCurrent >= dailyGoalMinutes) {
                return DayStatus.COMPLETED
            }
            if (isStudyTimerActive || totalTodayMinutesIncludingCurrent > 0) {
                return DayStatus.IN_PROGRESS
            }

            return dayStatus
        }

    val statusText: String
        get() {
            if (isFinishedManually) return "Finalizado"

            return when (effectiveDayStatus) {
                DayStatus.EMPTY -> "Sin comenzar"
                DayStatus.IN_PROGRESS -> "En curso"
                DayStatus.COMPLETED -> "Completado"
                DayStatus.NOT_COMPLETED -> "No completado"
                DayStatus.REST -> "Descanso"
            }
        }

    val canFinishToday: Boolean
        get() = !isFreeDay && !isFinishedManually

    val canResetToday: Boolean
        get() {
            if (isFreeDay) return false

            return isStudyTimerActive ||
                studiedMinutes > 0 ||
                completedFixedObjectiveIds.isNotEmpty() ||
                isFinishedManually
        }

    val studiedTimeText: String
        get() = totalTodayMinutesIncludingCurrent.toHourMinuteText()

    val currentTimerText: String
        get() = studyElapsedSeconds.toTimerText()

    val remainingTimeText: String
        get() {
            if (isFinishedManually) {
                val difference = dailyGoalMinutes - totalTodayMinutesIncludingCurrent

                return when {
                    difference > 0 -> "Faltaron ${difference.toHourMinuteText()}"
                    difference < 0 -> "Extra ${(-difference).toHourMinuteText()}"
                    else -> "Objetivo justo"
                }
            }

            return remainingMinutes.toHourMinuteText()
        }

    val dailyGoalText: String
        get() = dailyGoalMinutes.toHourMinuteText()

    fun isFixedObjectiveCompleted(objectiveId: String): Boolean {
        return completedFixedObjectiveIds.contains(objectiveId)
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
