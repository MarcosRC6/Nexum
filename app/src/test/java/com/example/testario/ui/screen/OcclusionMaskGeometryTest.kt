package com.example.testario.ui.screen

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.*
import org.junit.Test

class OcclusionMaskGeometryTest {
    private val initial = ImageOcclusionMask(
        id = "mask", x = .25, y = .25, width = .3, height = .25
    )

    @Test fun movingMaskPreservesSizeAndKeepsItInsidePhoto() {
        val moved = moveOrResizeMask(initial, "MOVE", .7, -.5)
        assertEquals(.7, moved.x, 0.000001)
        assertEquals(0.0, moved.y, 0.000001)
        assertEquals(initial.width, moved.width, 0.0)
        assertEquals(initial.height, moved.height, 0.0)
    }

    @Test fun eachCornerAndSideHandleCanBeResizedWithinImage() {
        val handles = listOf("L", "R", "T", "B", "LT", "RT", "LB", "RB")
        handles.forEach { handle ->
            val m = moveOrResizeMask(initial, handle, .1, .1)
            assertTrue(handle, m.x >= 0.0 && m.y >= 0.0)
            assertTrue(handle, m.width >= .04 && m.height >= .04)
            assertTrue(handle, m.x + m.width <= 1.0 + 1e-8)
            assertTrue(handle, m.y + m.height <= 1.0 + 1e-8)
            assertEquals(initial.id, m.id)
        }
    }

    @Test fun resizingPastEdgesIsSafelyClamped() {
        listOf("L", "R", "T", "B", "LT", "RT", "LB", "RB").forEach { handle ->
            val m = moveOrResizeMask(initial, handle, 10.0, -10.0)
            assertTrue(m.width >= .04 && m.height >= .04)
            assertTrue(m.x + m.width <= 1.0 + 1e-8)
            assertTrue(m.y + m.height <= 1.0 + 1e-8)
        }
    }

    @Test fun leftHandleChangesXButDoesNotMoveOppositeEdge() {
        val mask = moveOrResizeMask(initial, "L", .08, 0.0)
        assertEquals(initial.x + initial.width, mask.x + mask.width, 1e-9)
        assertEquals(initial.y, mask.y, 0.0)
    }

    @Test fun nonFiniteMoveIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException::class.java) {
            moveOrResizeMask(initial, "MOVE", Double.NaN, .1)
        }
    }
}
