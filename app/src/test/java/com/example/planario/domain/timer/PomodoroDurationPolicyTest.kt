package com.example.planario.domain.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroDurationPolicyTest {
    @Test fun configuredHourWinsOverDefaultTwentyFiveMinutes() {
        assertTrue(PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
            PomodoroTimerState.STOPPED, 25, 60))
        assertTrue(PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
            PomodoroTimerState.FINISHED, 25, 60))
        assertFalse(PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
            PomodoroTimerState.STOPPED, 60, 60))
    }

    @Test fun activePomodoroNeverGetsResetWhenWidgetOpens() {
        assertFalse(PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
            PomodoroTimerState.RUNNING, 25, 60))
        assertFalse(PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
            PomodoroTimerState.PAUSED, 25, 60))
    }
}
