package com.example.testario.domain

import com.example.testario.data.model.SketchPoint
import com.example.testario.data.model.SketchStroke
import org.junit.Assert.*
import org.junit.Test

class SketchStrokeEraserTest {
    private val baseline = SketchStroke(
        points = listOf(SketchPoint(.1,.5), SketchPoint(.9,.5)),
        colorArgb = 0xFFA855F7L, widthFraction = .005, opacity = .75
    )

    @Test fun erasesMiddleOfLongLineWithoutDeletingBothEnds() {
        val result = SketchStrokeEraser.erasePath(listOf(baseline),
            listOf(SketchPoint(.5,.48), SketchPoint(.5,.52)), radius = .025)
        assertEquals(2, result.size)
        assertTrue(result[0].points.last().x < .5)
        assertTrue(result[1].points.first().x > .5)
        assertEquals(baseline.colorArgb, result[0].colorArgb)
        assertEquals(baseline.opacity, result[1].opacity)
    }

    @Test fun missesLeaveStrokesAndIdsUntouched() {
        val result = SketchStrokeEraser.erasePath(listOf(baseline),
            listOf(SketchPoint(.1,.1)), radius = .01)
        assertEquals(listOf(baseline), result)
    }

    @Test fun sweepingAlongEntireLineErasesIt() {
        val result = SketchStrokeEraser.erasePath(listOf(baseline),
            listOf(SketchPoint(.05,.5), SketchPoint(.95,.5)), radius = .04)
        assertTrue(result.isEmpty())
    }

    @Test fun invalidEraserParametersAreRejected() {
        assertTrue(runCatching {
            SketchStrokeEraser.erasePath(listOf(baseline), listOf(SketchPoint(.4,.5)),
                radius = Double.NaN)
        }.isFailure)
    }
}
