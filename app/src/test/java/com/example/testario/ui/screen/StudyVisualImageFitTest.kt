package com.example.testario.ui.screen

import org.junit.Assert.*
import org.junit.Test

class StudyVisualImageFitTest {
    @Test fun widePhotoIsLetterboxedAndMarkerMapsToActualPixels() {
        val fit = requireNotNull(fitImageToBox(800, 400, 300f, 300f))
        assertEquals(0f, fit.left, 0.0001f)
        assertEquals(75f, fit.top, 0.0001f)
        assertEquals(300f, fit.width, 0.0001f)
        assertEquals(150f, fit.height, 0.0001f)
        assertEquals(150f, fit.locate(0.5, 0.5).x, 0.0001f)
        assertEquals(150f, fit.locate(0.5, 0.5).y, 0.0001f)
        assertEquals(0.5, fit.toImage(150f, 150f)!!.first, 0.00001)
        assertNull(fit.toImage(150f, 25f))
    }

    @Test fun tallPhotoIsLetterboxedHorizontally() {
        val fit = requireNotNull(fitImageToBox(400, 800, 300f, 300f))
        assertEquals(75f, fit.left, 0.0001f)
        assertEquals(0f, fit.top, 0.0001f)
        assertNull(fit.toImage(30f, 150f))
    }

    @Test fun refusesInvalidDimensionsInsteadOfCreatingFalseCoordinates() {
        assertNull(fitImageToBox(0, 400, 300f, 300f))
        assertNull(fitImageToBox(800, 0, 300f, 300f))
        assertNull(fitImageToBox(800, 400, 0f, 300f))
    }
}
