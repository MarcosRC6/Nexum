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

    @Test fun highlighterCreatedImageIsAcceptedByWrittenExerciseValidation() {
        val ink = ImageAnnotations(listOf(
            ImageAnnotationStroke(
                id = "highlighted", tool = ImageAnnotationTool.HIGHLIGHTER,
                colorArgb = 0xFFFFD54FL, opacity = .6, widthFraction = .06,
                points = listOf(ImageAnnotationPoint(.1,.2), ImageAnnotationPoint(.4,.5))
            )
        ))
        val exercise = StudyExercise(
            kind = StudyExerciseKind.WRITTEN_MULTI,
            prompt = "Indica la respuesta",
            solutions = listOf(CanonicalSolution(text = "Respuesta")),
            sketch = SketchAnnotationCodec.fromAnnotations(ink)
        )
        assertTrue(StudyExerciseRules.errors(exercise).isEmpty())
        val changedInk = ink.copy(strokes = ink.strokes.map {
            it.copy(opacity = .25)
        })
        val modified = exercise.copy(sketch = SketchAnnotationCodec.fromAnnotations(changedInk))
        assertNotEquals(StudyExerciseMerge.signature(exercise), StudyExerciseMerge.signature(modified))
    }

    @Test fun rejectInvalidPointsBeforeCreatingSketch() {
        val bad = ImageAnnotations(listOf(ImageAnnotationStroke(
            points = listOf(ImageAnnotationPoint(-.2, .4))
        )))
        assertTrue(runCatching { SketchAnnotationCodec.fromAnnotations(bad) }.isFailure)
    }
}
