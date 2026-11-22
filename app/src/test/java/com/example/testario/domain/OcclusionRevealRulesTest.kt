package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.*
import org.junit.Test

class OcclusionRevealRulesTest {
    private val masks = listOf(
        ImageOcclusionMask(id = "a", x = 0.1, y = 0.1, width = 0.2, height = 0.2),
        ImageOcclusionMask(id = "b", x = 0.6, y = 0.5, width = 0.2, height = 0.2)
    )

    @Test fun beforeRevealAllAreasRemainHidden() {
        assertEquals(listOf("a", "b"),
            OcclusionRevealRules.covered(masks, "a", false).map { it.id })
    }

    @Test fun revealUncoversOnlyTheCurrentArea() {
        assertEquals(listOf("b"),
            OcclusionRevealRules.covered(masks, "a", true).map { it.id })
        assertEquals(listOf("a"),
            OcclusionRevealRules.covered(masks, "b", true).map { it.id })
        assertEquals(2, masks.size)
    }

    @Test fun unknownTargetNeverUncoversAnotherMask() {
        assertEquals(masks, OcclusionRevealRules.covered(masks, "missing", true))
    }
}
