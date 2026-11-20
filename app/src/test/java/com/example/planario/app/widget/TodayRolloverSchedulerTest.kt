package com.example.planario.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.Instant

class TodayRolloverSchedulerTest {
    @Test fun rolloverTargetsNextLocalDayNot24HoursLater() {
        val zone = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(2026, 11, 19, 23, 58, 0, 0, zone)
        val next = Instant.ofEpochMilli(TodayRolloverScheduler.nextRolloverMillis(now))
            .atZone(zone)
        assertEquals(20, next.dayOfMonth)
        assertEquals(0, next.hour)
        assertEquals(1, next.minute)
    }

    @Test fun summerClockChangeUsesCalendarDateInLocalZone() {
        val zone = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(2026, 3, 28, 20, 0, 0, 0, zone)
        val next = Instant.ofEpochMilli(TodayRolloverScheduler.nextRolloverMillis(now))
            .atZone(zone)
        assertEquals(29, next.dayOfMonth)
        assertEquals(0, next.hour)
        assertTrue(next.minute == 1)
    }
}
