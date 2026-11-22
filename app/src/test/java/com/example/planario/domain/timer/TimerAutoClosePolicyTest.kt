package com.example.planario.domain.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerAutoClosePolicyTest {
    @Test fun noCollectorMustPreserveCompletedSessionForRecovery() {
        assertTrue(TimerAutoClosePolicy.preserveForRecovery(minutes = 61, subscriberCount = 0))
        assertTrue(TimerAutoClosePolicy.preserveForRecovery(minutes = 1, subscriberCount = 0))
    }

    @Test fun collectorAllowsNormalAutoCloseAndZeroMinutesNeedNoHandoff() {
        assertFalse(TimerAutoClosePolicy.preserveForRecovery(minutes = 61, subscriberCount = 1))
        assertFalse(TimerAutoClosePolicy.preserveForRecovery(minutes = 0, subscriberCount = 0))
        assertFalse(TimerAutoClosePolicy.preserveForRecovery(minutes = 0, subscriberCount = 1))
    }
}
