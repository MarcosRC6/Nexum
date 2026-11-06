package com.example.planario.features.calendar

import com.example.planario.core.model.DayStatus
import java.time.LocalDate

data class CalendarUiState(
    val isLoading: Boolean = true,

    val selectedMonthTitle: String = "",
    val monthTitle: String = "",
    val selectedDateText: String = "",

    val weekDays: List<String> = listOf("L", "M", "X", "J", "V", "S", "D"),
    val weeks: List<List<CalendarDayUi?>> = emptyList(),

    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDay: SelectedCalendarDayUi? = null
)

data class CalendarDayUi(
    val date: LocalDate,
    val dayNumber: Int,
    val status: DayStatus,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isDefaultFreeDay: Boolean,
    val isForcedStudyDay: Boolean
)

data class SelectedCalendarDayUi(
    val date: LocalDate,
    val status: DayStatus,
    val isRestDay: Boolean,
    val isDefaultFreeDay: Boolean,
    val isForcedStudyDay: Boolean,
    val studiedMinutes: Int,
    val dailyGoalMinutes: Int,
    val activeTopicNameSnapshot: String?,
    val completedFixedObjectiveIds: Set<String>
) {
    val isFreeDay: Boolean
        get() = status == DayStatus.REST

    val freeDayDescription: String
        get() {
            return when {
                isForcedStudyDay -> "Día normal de estudio"
                isRestDay -> "Descanso marcado manualmente"
                isDefaultFreeDay -> "Día libre por defecto"
                else -> "Día normal de estudio"
            }
        }
}

fun DayStatus.toReadableText(): String {
    return when (this) {
        DayStatus.EMPTY -> "Vacío"
        DayStatus.IN_PROGRESS -> "En curso"
        DayStatus.COMPLETED -> "Completado"
        DayStatus.NOT_COMPLETED -> "No completado"
        DayStatus.REST -> "Descanso"
    }
}

fun Int.toHourMinuteText(): String {
    val hours = this / 60
    val minutes = this % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        else -> "${minutes} min"
    }
}