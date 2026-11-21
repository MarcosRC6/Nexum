package com.example.testario.domain

import com.example.testario.data.model.SketchPoint
import com.example.testario.data.model.SketchStroke
import org.junit.Assert.*
import org.junit.Test

class SketchStrokeEraserTest {
    private val baseline = SketchStroke(
        id = "original",
        points = listOf(SketchPoint(.1, .5), SketchPoint(.9, .5)),
        colorArgb = 0xFFFF1010L, widthFraction = .006, opacity = .35
    )

    @Test fun eraseAcrossMiddleSplitsStrokeRatherThanDeletingEntireLine() {
        val pieces = SketchStrokeEraser.erase(
            listOf(baseline), listOf(SketchPoint(.5, .5)), 1000, 400, 20.0)
        assertEquals(2, pieces.size)
        assertEquals("original", pieces[0].id)
        assertTrue(pieces[0].points.last().x < .5)
        assertTrue(pieces[1].points.first().x > .5)
        assertEquals(0xFFFF1010L, pieces[1].colorArgb)
        assertEquals(.35, pieces[1].opacity!!, 0.00001)
        assertEquals(.006, pieces[0].widthFraction!!, 0.00001)
    }

    @Test fun erasingAwayFromInkKeepsOriginalUnmodified() {
        val restored = SketchStrokeEraser.erase(
            listOf(baseline), listOf(SketchPoint(.5, .1)), 1000, 400)
        assertEquals(listOf(baseline), restored)
    }

    @Test fun erasePathMayRemoveEntireLineAndNeverGenerateInvalidOnePointStrokes() {
        val small = baseline.copy(points = listOf(
            SketchPoint(.49,.5), SketchPoint(.5,.5), SketchPoint(.51,.5)))
        val cleaned = SketchStrokeEraser.erase(
            listOf(small), listOf(SketchPoint(.5, .5)), 400, 250, 25.0)
        assertTrue(cleaned.isEmpty())
        assertEquals(3, small.points.size)
    }

    @Test fun ignoresInvalidCanvasRatherThanDamagingDrawing() {
        assertEquals(listOf(baseline),
            SketchStrokeEraser.erase(listOf(baseline), listOf(SketchPoint(.5,.5)), 0, 0))
    }
}
