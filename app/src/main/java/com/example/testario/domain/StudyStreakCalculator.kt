package com.example.testario.domain

import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.StudyDay
import java.time.LocalDate

data class StudyStreakSummary(
    val current: Int = 0,
    val best: Int = 0,
    val studiedDays: Int = 0
)

object StudyStreakCalculator {
    fun calculate(
        days: Map<LocalDate, StudyDay>,
        defaultFreeWeekDays: Set<Int> = emptySet(),
        today: LocalDate = LocalDate.now()
    ): StudyStreakSummary {
        if (days.isEmpty()) return StudyStreakSummary()

        fun isRest(date: LocalDate, day: StudyDay?): Boolean {
            if (day?.forceStudyDay == true) return false
            return day?.isRestDay == true ||
                day?.status == DayStatus.REST ||
                date.dayOfWeek.value in defaultFreeWeekDays
        }

        fun isCompleted(day: StudyDay?): Boolean =
            day?.status == DayStatus.COMPLETED || day?.isFinishedManually == true

        val studiedDays = days.values.count { isCompleted(it) }

        var current = 0
        var cursor = today
        var firstDay = true
        var safety = 0
        while (safety < 5000) {
            val day = days[cursor]
            when {
                firstDay && !isRest(cursor, day) && !isCompleted(day) && day?.status != DayStatus.NOT_COMPLETED -> {
                    // El día de hoy aún puede estar vacío o en curso; conserva la racha de ayer.
                    cursor = cursor.minusDays(1)
                    firstDay = false
                }
                isRest(cursor, day) -> {
                    cursor = cursor.minusDays(1)
                    firstDay = false
                }
                isCompleted(day) -> {
                    current++
                    cursor = cursor.minusDays(1)
                    firstDay = false
                }
                else -> break
            }
            safety++
        }

        val minDate = days.keys.minOrNull() ?: return StudyStreakSummary(current, 0, studiedDays)
        val maxDate = minOf(today, days.keys.maxOrNull() ?: today)
        var best = 0
        var run = 0
        var date = minDate
        while (!date.isAfter(maxDate)) {
            val day = days[date]
            when {
                isRest(date, day) -> Unit
                isCompleted(day) -> {
                    run++
                    if (run > best) best = run
                }
                else -> run = 0
            }
            date = date.plusDays(1)
        }

        return StudyStreakSummary(current = current, best = best, studiedDays = studiedDays)
    }
}
