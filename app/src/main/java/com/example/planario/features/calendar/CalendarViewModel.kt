package com.example.planario.features.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.planario.app.PlanarioDependencies
import com.example.planario.data.repository.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val calendarBaseData = combine(
        selectedMonth,
        selectedDate,
        repository.studyDays,
        repository.settings,
        repository.timeEntries
    ) { month, date, _, _, _ ->
        CalendarBaseData(
            selectedMonth = month,
            selectedDate = date
        )
    }

    val uiState = combine(
        calendarBaseData,
        repository.activeTopic,
        repository.fixedObjectives
    ) { baseData, _, _ ->

        val selectedDay = repository.getEffectiveStudyDay(baseData.selectedDate)
        val isDefaultFreeDay = repository.isDefaultFreeDay(baseData.selectedDate)

        CalendarUiState(
            isLoading = false,
            selectedMonthTitle = baseData.selectedMonth.toSpanishMonthTitle(),
            monthTitle = baseData.selectedMonth.toSpanishMonthTitle(),
            selectedDateText = baseData.selectedDate.toSpanishFullDateText(),
            selectedDate = baseData.selectedDate,
            weeks = buildMonthWeeks(
                month = baseData.selectedMonth,
                selectedDate = baseData.selectedDate
            ),
            selectedDay = SelectedCalendarDayUi(
                date = selectedDay.date,
                status = selectedDay.status,
                isRestDay = selectedDay.isRestDay,
                isDefaultFreeDay = isDefaultFreeDay,
                isForcedStudyDay = selectedDay.forceStudyDay,
                studiedMinutes = selectedDay.studiedMinutes,
                dailyGoalMinutes = selectedDay.dailyGoalMinutes,
                activeTopicNameSnapshot = selectedDay.activeTopicNameSnapshot,
                completedFixedObjectiveIds = selectedDay.completedFixedObjectiveIds
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState()
    )

    fun goToPreviousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun goToNextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun goToCurrentMonth() {
        val today = LocalDate.now()

        selectedMonth.value = YearMonth.from(today)
        selectedDate.value = today
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        selectedMonth.value = YearMonth.from(date)
    }

    fun toggleSelectedDateRestDay() {
        val date = selectedDate.value
        val selectedDay = repository.getEffectiveStudyDay(date)

        if (selectedDay.status == com.example.planario.core.model.DayStatus.REST) {
            repository.cancelFreeDay(date)
        } else {
            repository.setRestDay(
                date = date,
                isRestDay = true
            )
        }
    }

    private fun buildMonthWeeks(
        month: YearMonth,
        selectedDate: LocalDate
    ): List<List<CalendarDayUi?>> {
        val firstDay = month.atDay(1)
        val daysInMonth = month.lengthOfMonth()

        val leadingEmptyDays = firstDay.dayOfWeek.value - 1
        val totalCells = leadingEmptyDays + daysInMonth
        val trailingEmptyDays = (7 - (totalCells % 7)).takeIf { it < 7 } ?: 0

        val cells = mutableListOf<CalendarDayUi?>()

        repeat(leadingEmptyDays) {
            cells.add(null)
        }

        for (dayNumber in 1..daysInMonth) {
            val date = month.atDay(dayNumber)
            val day = repository.getEffectiveStudyDay(date)
            val isDefaultFreeDay = repository.isDefaultFreeDay(date)

            cells.add(
                CalendarDayUi(
                    date = date,
                    dayNumber = dayNumber,
                    status = day.status,
                    isToday = date == LocalDate.now(),
                    isSelected = date == selectedDate,
                    isDefaultFreeDay = isDefaultFreeDay,
                    isForcedStudyDay = day.forceStudyDay
                )
            )
        }

        repeat(trailingEmptyDays) {
            cells.add(null)
        }

        return cells.chunked(7)
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CalendarViewModel(
                        repository = PlanarioDependencies.studyRepository
                    ) as T
                }
            }
        }
    }
}

private data class CalendarBaseData(
    val selectedMonth: YearMonth,
    val selectedDate: LocalDate
)

private fun YearMonth.toSpanishMonthTitle(): String {
    val formatter = DateTimeFormatter.ofPattern(
        "MMMM yyyy",
        Locale("es", "ES")
    )

    return atDay(1).format(formatter).replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase(Locale("es", "ES"))
        } else {
            firstChar.toString()
        }
    }
}

private fun LocalDate.toSpanishFullDateText(): String {
    val formatter = DateTimeFormatter.ofPattern(
        "EEEE, d 'de' MMMM 'de' yyyy",
        Locale("es", "ES")
    )

    return format(formatter).replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase(Locale("es", "ES"))
        } else {
            firstChar.toString()
        }
    }
}