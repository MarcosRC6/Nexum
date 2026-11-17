package com.example.planario.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerRecoveryPolicyTest {
    @Test fun sameBootCarriesOnRunningTime() {
        assertEquals(173L, TimerRecoveryPolicy.safeRunningSeconds(
            savedSeconds = 120, lastWallMillis = 100000L, lastUptimeMillis = 20000L,
            nowWallMillis = 153000L, nowUptimeMillis = 73000L))
    }
    @Test fun rebootOrClockJumpNeverInventsStudyTime() {
        assertNull(TimerRecoveryPolicy.safeRunningSeconds(
            120, 100000L, 20000L, 155000L, 1000L))
        assertNull(TimerRecoveryPolicy.safeRunningSeconds(
            120, 100000L, 20000L, 1000000L, 30000L))
    }
    @Test fun midnightCapsOldDateAtBoundary() {
        assertEquals(150L, TimerRecoveryPolicy.safeRunningSeconds(
            120, 100000L, 20000L, 200000L, 120000L,
            endOfDayWallMillis = 130000L))
    }
}
