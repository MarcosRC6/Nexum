package com.example.planario.domain.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyForegroundPolicyTest {
    @Test fun runningStudyRemainsProtectedWhenPomodoroResets() {
        assertTrue(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.RUNNING, PomodoroTimerState.STOPPED))
        assertTrue(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.PAUSED, PomodoroTimerState.FINISHED))
    }

    @Test fun standalonePomodoroKeepsServiceAlive() {
        assertTrue(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.STOPPED, PomodoroTimerState.RUNNING))
        assertTrue(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.STOPPED, PomodoroTimerState.PAUSED))
    }

    @Test fun neitherTimerActiveMeansNoForegroundNeeded() {
        assertFalse(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.STOPPED, PomodoroTimerState.STOPPED))
        assertFalse(StudyForegroundPolicy.shouldKeepRunning(
            StudyTimerState.STOPPED, PomodoroTimerState.FINISHED))
    }
}
