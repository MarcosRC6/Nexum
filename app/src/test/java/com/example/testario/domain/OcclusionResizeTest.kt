package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.*
import org.junit.Test

class OcclusionResizeTest {
    private val original = ImageOcclusionMask(
        id = "preserved", x = 0.2, y = 0.3, width = 0.4, height = 0.2,
        answer = "Mantener respuesta"
    )

    @Test fun allEightHandlesStayInsideImageBounds() {
        OcclusionHandle.values().forEach { handle ->
            listOf(-5.0, -0.05, 0.1, 5.0).forEach { dx ->
                listOf(-4.0, 0.0, 0.07, 4.0).forEach { dy ->
                    val changed = OcclusionResize.apply(original, handle, dx, dy)
                    assertTrue(changed.x >= 0.0 && changed.y >= 0.0)
                    assertTrue(changed.width > 0.0 && changed.height > 0.0)
                    assertTrue(changed.x + changed.width <= 1.000000001)
                    assertTrue(changed.y + changed.height <= 1.000000001)
                    assertEquals(original.id, changed.id)
                    assertEquals(original.answer, changed.answer)
                }
            }
        }
    }

    @Test fun leftHandleKeepsRightEdgeAndHeightFixed() {
        val changed = OcclusionResize.apply(original, OcclusionHandle.LEFT, 0.1, 0.4)
        assertEquals(0.3, changed.x, 1e-9)
        assertEquals(0.6, changed.x + changed.width, 1e-9)
        assertEquals(original.y, changed.y, 1e-9)
        assertEquals(original.height, changed.height, 1e-9)
    }

    @Test fun topRightKeepsOppositeCornerFixed() {
        val changed = OcclusionResize.apply(original, OcclusionHandle.TOP_RIGHT, 0.15, -0.2)
        assertEquals(original.x, changed.x, 1e-9)
        assertEquals(0.1, changed.y, 1e-9)
        assertEquals(0.75, changed.x + changed.width, 1e-9)
        assertEquals(0.5, changed.y + changed.height, 1e-9)
    }

    @Test fun tinyLegacyMasksRemainResizable() {
        val tiny = original.copy(x = 0.95, y = 0.96, width = 0.01, height = 0.01)
        val moved = OcclusionResize.apply(tiny, OcclusionHandle.BOTTOM_RIGHT, 5.0, 5.0)
        assertTrue(moved.width >= tiny.width)
        assertTrue(moved.height >= tiny.height)
        assertTrue(moved.x + moved.width <= 1.0)
        assertTrue(moved.y + moved.height <= 1.0)
    }

    @Test fun nonFiniteDeltaCannotCorruptStoredGeometry() {
        assertEquals(original, OcclusionResize.apply(original, OcclusionHandle.BOTTOM, 0.0, Double.NaN))
    }
    @Test fun precisionNudgesPreserveDimensionsAndNeverEscapeImage() {
        val moved = OcclusionResize.translate(original, 10.0, -10.0)
        assertEquals(0.6, moved.x, 1e-9)
        assertEquals(0.0, moved.y, 1e-9)
        assertEquals(original.width, moved.width, 0.0)
        assertEquals(original.height, moved.height, 0.0)
        assertEquals(original.id, moved.id)
        assertEquals(original.answer, moved.answer)
    }

    @Test fun precisionNudgesRejectNonFiniteValues() {
        assertEquals(original, OcclusionResize.translate(original, Double.NaN, 0.0))
    }

}
