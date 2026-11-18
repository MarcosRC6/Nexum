package com.example.planario.app.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexumTodaySizePolicyTest {
    @Test fun compactWidgetUsesCompactLayout() {
        assertTrue(NexumTodaySizePolicy.compact("AUTO", 250, 140))
        assertTrue(NexumTodaySizePolicy.compact("DETAIL", 250, 140))
        assertFalse(NexumTodaySizePolicy.compact("COMPACT", 300, 500))
        assertTrue(NexumTodaySizePolicy.compact("AUTO", 170, 450))
        assertFalse(NexumTodaySizePolicy.compact("AUTO", 220, 190))
        assertFalse(NexumTodaySizePolicy.compact("DETAIL", 260, 180))
        assertTrue(NexumTodaySizePolicy.compact("DETAIL", 260, 120))
    }

    @Test fun largeWidgetsRevealInformationOnlyWhenRoomAllows() {
        assertFalse(NexumTodaySizePolicy.details("AUTO", 205))
        assertTrue(NexumTodaySizePolicy.details("DETAIL", 290))
        assertTrue(NexumTodaySizePolicy.details("AUTO", 320))
        assertFalse(NexumTodaySizePolicy.week(240, 480))
        assertFalse(NexumTodaySizePolicy.week(300, 330))
        assertTrue(NexumTodaySizePolicy.week(300, 460))
    }
}
