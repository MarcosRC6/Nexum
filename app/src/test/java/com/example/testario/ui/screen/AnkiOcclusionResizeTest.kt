package com.example.testario.ui.screen

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.*
import org.junit.Test

class AnkiOcclusionResizeTest {
    @Test fun bottomRightDragOnlyExtendsRightAndBottom() {
        val start = ImageOcclusionMask(x = .25, y = .2, width = .2, height = .25)
        val moved = resizeOcclusionMask(start, 3, .1f, .05f)
        assertEquals(.25, moved.x, .00001)
        assertEquals(.2, moved.y, .00001)
        assertEquals(.3, moved.width, .00001)
        assertEquals(.3, moved.height, .00001)
    }

    @Test fun topLeftMoveKeepsOppositeCornerFixed() {
        val start = ImageOcclusionMask(x = .25, y = .2, width = .2, height = .25)
        val moved = resizeOcclusionMask(start, 0, .1f, .08f)
        assertEquals(.45, moved.x + moved.width, .00001)
        assertEquals(.45, moved.y + moved.height, .00001)
    }

    @Test fun resizingNeverLeavesImageOrMakesCoverNegative() {
        val start = ImageOcclusionMask(x = .9, y = .9, width = .08, height = .08)
        val moved = resizeOcclusionMask(start, 3, 10f, 10f)
        assertTrue(moved.width >= .035)
        assertTrue(moved.height >= .035)
        assertTrue(moved.x + moved.width <= 1.0 + 1e-8)
        assertTrue(moved.y + moved.height <= 1.0 + 1e-8)
    }
}
