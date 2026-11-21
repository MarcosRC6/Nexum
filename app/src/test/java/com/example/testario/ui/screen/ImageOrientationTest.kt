package com.example.testario.ui.screen

import org.junit.Assert.*
import org.junit.Test

class ImageOrientationTest {
    @Test fun quarterTurnOrientationsSwapWidthAndHeight() {
        listOf(5,6,7,8).forEach { assertTrue(exifSwapsAxes(it)) }
    }
    @Test fun unrotatedAndMirroredOrientationsKeepAxes() {
        listOf(0,1,2,3,4,9).forEach { assertFalse(exifSwapsAxes(it)) }
    }
    @Test fun fitImageTransformExcludesLetterboxingAndNormalizesPrecisely() {
        val fit = fitImageToBox(900, 1600, 400f, 300f)!!
        assertNull(fit.toImage(0f, 150f))
        val point = fit.locate(.25, .75)
        val restored = fit.toImage(point.x, point.y)!!
        assertEquals(.25, restored.first, 0.00001)
        assertEquals(.75, restored.second, 0.00001)
    }
}
