package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class SketchAnnotationCodecTest {
    @Test fun oldPurpleSketchStillDisplaysWithoutNewFields() {
        val old = SketchDrawing(listOf(SketchStroke(
            id = "old", brush = "purple",
            points = listOf(SketchPoint(.1, .2), SketchPoint(.8, .9))
        )))
        val layer = SketchAnnotationCodec.toAnnotations(old)
        assertEquals(1, layer.strokes.size)
        assertEquals("old", layer.strokes.single().id)
        assertEquals(0xFFA855F7L, layer.strokes.single().colorArgb)
        assertEquals(.005, layer.strokes.single().widthFraction, .00001)
        ImageAnnotationRules.requireValid(layer)
    }

    @Test fun highlighterRoundTripKeepsColorOpacityWidthAndCoordinates() {
        val layer = ImageAnnotations(listOf(
            ImageAnnotationStroke(
                id = "stroke", tool = ImageAnnotationTool.HIGHLIGHTER,
                colorArgb = 0xFF42A5F5L, opacity = .45, widthFraction = .035,
                points = listOf(ImageAnnotationPoint(.2, .3), ImageAnnotationPoint(.6, .7))
            )
        ))
        val drawing = SketchAnnotationCodec.fromAnnotations(layer)
        val restored = SketchAnnotationCodec.toAnnotations(drawing)
        assertEquals(layer, restored)
        assertEquals("highlighter", drawing.strokes.single().brush)
    }

    @Test fun rejectInvalidPointsBeforeCreatingSketch() {
        val bad = ImageAnnotations(listOf(ImageAnnotationStroke(
            points = listOf(ImageAnnotationPoint(-.2, .4))
        )))
        assertTrue(runCatching { SketchAnnotationCodec.fromAnnotations(bad) }.isFailure)
    }
}
