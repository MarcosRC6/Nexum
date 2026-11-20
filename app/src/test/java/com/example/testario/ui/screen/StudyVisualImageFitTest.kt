package com.example.testario.ui.screen

import org.junit.Assert.*
import androidx.compose.ui.geometry.Offset
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

    @Test fun pinchZoomInversePreservesExactImageCoordinates() {
        val original = Offset(250f, 120f)
        val center = Offset(200f, 150f)
        val pan = Offset(30f, -25f)
        val zoom = 2f
        val visible = center + (original - center) * zoom + pan
        val mapped = inverseImageViewport(visible, zoom, pan, 400f, 300f)
        assertEquals(original.x, mapped.x, .0001f)
        assertEquals(original.y, mapped.y, .0001f)
    }

    @Test fun zoomedLetterboxedPhotoStillRejectsMargins() {
        val fit = requireNotNull(fitImageToBox(1200, 600, 400f, 300f))
        val pan = Offset(35f, -15f)
        val zoom = 3f
        val imageCenter = fit.locate(.70, .25)
        val center = Offset(200f, 150f)
        val visible = center + (imageCenter - center) * zoom + pan
        val logical = inverseImageViewport(visible, zoom, pan, 400f, 300f)
        val normalized = requireNotNull(fit.toImage(logical.x, logical.y))
        assertEquals(.70, normalized.first, .00001)
        assertEquals(.25, normalized.second, .00001)
    }

    @Test fun panClampingNeverExposesUnboundedEmptyViewport() {
        assertEquals(Offset.Zero, clampImagePan(Offset(500f, -500f), 1f, 400f, 300f))
        assertEquals(Offset(400f, -300f),
            clampImagePan(Offset(1000f, -1000f), 3f, 400f, 300f))
    }
}
