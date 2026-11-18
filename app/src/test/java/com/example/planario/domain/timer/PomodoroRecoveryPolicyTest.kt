package com.example.planario.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroRecoveryPolicyTest {
    @Test fun sameBootCountsOnlyElapsedSeconds() {
        val actual = PomodoroRecoveryPolicy.recover(
            PomodoroTimerState.RUNNING, 1500L,
            beforeElapsedRealtimeMillis = 100_000L,
            beforeWallMillis = 10_000_000L,
            nowElapsedRealtimeMillis = 130_000L,
            nowWallMillis = 10_030_000L
        )
        assertEquals(1470L, actual.remainingSeconds)
        assertEquals(PomodoroTimerState.RUNNING, actual.state)
    }

    @Test fun bootChangePausesWithoutInventingTime() {
        val actual = PomodoroRecoveryPolicy.recover(
            PomodoroTimerState.RUNNING, 1200L,
            beforeElapsedRealtimeMillis = 150_000L,
            beforeWallMillis = 10_000_000L,
            nowElapsedRealtimeMillis = 2_000L,
            nowWallMillis = 10_060_000L
        )
        assertEquals(1200L, actual.remainingSeconds)
        assertEquals(PomodoroTimerState.PAUSED, actual.state)
    }

    @Test fun wallClockJumpPausesInsteadOfAdvancing() {
        val actual = PomodoroRecoveryPolicy.recover(
            PomodoroTimerState.RUNNING, 1200L,
            100_000L, 10_000_000L, 110_000L, 10_300_000L
        )
        assertEquals(1200L, actual.remainingSeconds)
        assertEquals(PomodoroTimerState.PAUSED, actual.state)
    }

    @Test fun expiredPomodoroFinishes() {
        val actual = PomodoroRecoveryPolicy.recover(
            PomodoroTimerState.RUNNING, 3L,
            100_000L, 10_000_000L, 105_000L, 10_005_000L
        )
        assertEquals(0L, actual.remainingSeconds)
        assertEquals(PomodoroTimerState.FINISHED, actual.state)
    }

    @Test fun pausedPomodoroDoesNotAdvance() {
        val actual = PomodoroRecoveryPolicy.recover(
            PomodoroTimerState.PAUSED, 322L,
            100_000L, 10_000_000L, 200_000L, 10_100_000L
        )
        assertEquals(322L, actual.remainingSeconds)
        assertEquals(PomodoroTimerState.PAUSED, actual.state)
    }
}
