package com.example.testario.domain

import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.StudyDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StudyStreakCalculatorTest {

    @Test
    fun restDayDoesNotBreakCurrentStreak() {
        val today = LocalDate.of(2026, 11, 5)
        val days = mapOf(
            today.minusDays(1) to StudyDay(
                date = today.minusDays(1),
                status = DayStatus.REST,
                isRestDay = true
            ),
            today.minusDays(2) to StudyDay(
                date = today.minusDays(2),
                status = DayStatus.COMPLETED
            ),
            today.minusDays(3) to StudyDay(
                date = today.minusDays(3),
                status = DayStatus.COMPLETED
            )
        )

        val result = StudyStreakCalculator.calculate(days, today = today)

        assertEquals(2, result.current)
        assertEquals(2, result.best)
        assertEquals(2, result.studiedDays)
    }

    @Test
    fun unfinishedTodayDoesNotDestroyYesterdayStreak() {
        val today = LocalDate.of(2026, 11, 5)
        val days = mapOf(
            today to StudyDay(date = today, status = DayStatus.IN_PROGRESS),
            today.minusDays(1) to StudyDay(
                date = today.minusDays(1),
                status = DayStatus.COMPLETED
            )
        )

        assertEquals(
            1,
            StudyStreakCalculator.calculate(days, today = today).current
        )
    }
}
