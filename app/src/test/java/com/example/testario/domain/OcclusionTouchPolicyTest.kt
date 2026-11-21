package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.*
import org.junit.Test

class OcclusionTouchPolicyTest {
    private val first = ImageOcclusionMask(
        id = "first", x = .15, y = .20, width = .30, height = .20
    )
    private val second = ImageOcclusionMask(
        id = "second", x = .60, y = .45, width = .20, height = .10
    )

    @Test fun touchingFormerRectangleSelectsItWithoutCreatingNewOne() {
        assertEquals(OcclusionTouchPolicy.Hit.Body("first"),
            OcclusionTouchPolicy.hitTest(listOf(first, second), "second",
                .30, .30, .005, .005))
    }

    @Test fun touchingClearPhotoReturnsBackgroundEvenWithSelection() {
        assertEquals(OcclusionTouchPolicy.Hit.Background,
            OcclusionTouchPolicy.hitTest(listOf(first, second), "first",
                .92, .90, .005, .005))
    }

    @Test fun draggingSelectedCornerResizesInsteadOfMoving() {
        assertEquals(
            OcclusionTouchPolicy.Hit.Corner("first", OcclusionHandle.BOTTOM_RIGHT),
            OcclusionTouchPolicy.hitTest(listOf(first, second), "first",
                .45, .40, .01, .01))
    }

    @Test fun tinyRectangleCornersStayIndividuallySelectable() {
        val tiny = first.copy(width = .012, height = .012)
        assertEquals(
            OcclusionTouchPolicy.Hit.Corner("first", OcclusionHandle.TOP_LEFT),
            OcclusionTouchPolicy.hitTest(listOf(tiny), "first",
                .15, .20, .06, .06))
        assertEquals(
            OcclusionTouchPolicy.Hit.Corner("first", OcclusionHandle.BOTTOM_RIGHT),
            OcclusionTouchPolicy.hitTest(listOf(tiny), "first",
                .162, .212, .06, .06))
    }

    @Test fun oneTapNewMaskClipsAtPhotoBordersWithoutLosingValidGeometry() {
        val atTopLeft = requireNotNull(OcclusionTouchPolicy.createAt(.005, .005))
        assertTrue(atTopLeft.x >= 0.0 && atTopLeft.y >= 0.0)
        assertTrue(atTopLeft.width > .006 && atTopLeft.height > .006)
        val atBottomRight = requireNotNull(OcclusionTouchPolicy.createAt(.995, .995))
        assertTrue(atBottomRight.x + atBottomRight.width <= 1.0)
        assertTrue(atBottomRight.y + atBottomRight.height <= 1.0)
    }

    @Test fun invalidCoordinateNeverCreatesUnboundedMask() {
        assertNull(OcclusionTouchPolicy.createAt(Double.NaN, .2))
    }
}
