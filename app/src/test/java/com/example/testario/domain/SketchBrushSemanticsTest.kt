package com.example.testario.domain

import com.example.testario.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SketchBrushSemanticsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun exercise(color: Long = 0xFFA855F7L, alpha: Double = 1.0): StudyExercise =
        StudyExercise(
            kind = StudyExerciseKind.WRITTEN_MULTI,
            prompt = "Dibuja una línea",
            solutions = listOf(CanonicalSolution(text = "Línea")),
            sketch = SketchDrawing(strokes = listOf(SketchStroke(
                points = listOf(SketchPoint(.2, .4), SketchPoint(.8, .6)),
                colorArgb = color, widthFraction = .006, opacity = alpha
            )))
        )

    @Test fun sameGeometryDifferentColorsDoNotDeduplicate() {
        val purple = exercise()
        val green = exercise(color = 0xFF4CAF50L)
        assertNotEquals(StudyExerciseMerge.signature(purple), StudyExerciseMerge.signature(green))
        assertEquals(2, StudyExerciseMerge.unique(listOf(purple, green), emptyList()).size)
    }

    @Test fun alphaDifferenceIsNotLostOnImport() {
        val transparent = exercise(alpha = .35)
        val opaque = exercise(alpha = .9)
        assertNotEquals(StudyExerciseMerge.signature(transparent), StudyExerciseMerge.signature(opaque))
        assertTrue(StudyExerciseRules.errors(transparent).isEmpty())
    }

    @Test fun colorAndOpacityRoundTripThroughJson() {
        val original = exercise(alpha = .42)
        val restored = json.decodeFromString<StudyExercise>(json.encodeToString(original))
        val stroke = restored.sketch!!.strokes.single()
        assertEquals(.42, stroke.opacity!!, 0.00001)
        assertEquals(0xFFA855F7L, stroke.colorArgb)
        assertEquals(.006, stroke.widthFraction!!, 0.00001)
    }

    @Test fun historicalSketchWithoutBrushMetadataRemainsValid() {
        val old = """{"kind":"WRITTEN_MULTI","prompt":"Historia","solutions":[{"text":"Trazo"}],"sketch":{"strokes":[{"points":[{"x":0.1,"y":0.1},{"x":0.9,"y":0.9}],"brush":"purple"}]}}"""
        val restored = json.decodeFromString<StudyExercise>(old)
        val stroke = restored.sketch!!.strokes.single()
        assertNull(stroke.colorArgb)
        assertNull(stroke.opacity)
        assertNull(stroke.widthFraction)
        assertTrue(StudyExerciseRules.errors(restored).isEmpty())
    }

    @Test fun invalidBrushAttributesAreRejected() {
        assertTrue(StudyExerciseRules.errors(exercise(alpha = 1.2)).isNotEmpty())
        val invalid = exercise().copy(sketch = SketchDrawing(strokes = listOf(
            SketchStroke(points = listOf(SketchPoint(.1,.1),SketchPoint(.2,.2)), widthFraction = -.01)
        )))
        assertTrue(StudyExerciseRules.errors(invalid).isNotEmpty())
    }
}
