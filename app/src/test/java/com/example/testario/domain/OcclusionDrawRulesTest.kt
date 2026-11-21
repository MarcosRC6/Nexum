package com.example.testario.domain

import org.junit.Assert.*
import org.junit.Test

class OcclusionDrawRulesTest {
    @Test fun draggingInAnyDirectionMakesSameRectangle() {
        val forward = requireNotNull(OcclusionDrawRules.rectangle(.1,.2,.5,.6))
        val reverse = requireNotNull(OcclusionDrawRules.rectangle(.5,.6,.1,.2))
        assertEquals(forward.x, reverse.x, 1e-9)
        assertEquals(forward.y, reverse.y, 1e-9)
        assertEquals(forward.width, reverse.width, 1e-9)
        assertEquals(forward.height, reverse.height, 1e-9)
    }
    @Test fun clippingToImageBounds() {
        val mask = requireNotNull(OcclusionDrawRules.rectangle(-1.0,-2.0,3.0,5.0))
        assertEquals(0.0, mask.x, 1e-9)
        assertEquals(0.0, mask.y, 1e-9)
        assertEquals(1.0, mask.width, 1e-9)
        assertEquals(1.0, mask.height, 1e-9)
    }
    @Test fun tinyTapAndBadNumbersAreNotSaved() {
        assertNull(OcclusionDrawRules.rectangle(.3,.3,.301,.301))
        assertNull(OcclusionDrawRules.rectangle(Double.NaN,.1,.5,.6))
    }
    @Test fun smallMapLabelsAreSupported() {
        val mask = requireNotNull(OcclusionDrawRules.rectangle(.311,.412,.329,.427))
        assertTrue(mask.width < 0.04)
        assertTrue(mask.height < 0.04)
    }
}
