package com.example.planario.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexumClockSizePolicyTest {
    @Test fun twoByTwoBecomesCompact() {
        assertTrue(NexumClockSizePolicy.compact(165, 105))
        assertTrue(NexumClockSizePolicy.compact(300, 145))
        assertTrue(NexumClockSizePolicy.compact(195, 300))
    }

    @Test fun expandedSizeRetainsLargeClock() {
        assertFalse(NexumClockSizePolicy.compact(285, 200))
        assertFalse(NexumClockSizePolicy.compact(360, 300))
    }
}
