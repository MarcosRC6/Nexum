package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.SketchDrawing
import com.example.testario.data.model.SketchPoint
import com.example.testario.data.model.SketchStroke
import com.example.testario.data.model.StudyExercise
import com.example.testario.data.model.StudyExerciseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StudyExerciseMergeSketchTest {
    private fun exercise(strokeId: String, y: Double = 0.8): StudyExercise =
        StudyExercise(
            id = strokeId,
            kind = StudyExerciseKind.WRITTEN_MULTI,
            prompt = "Enumera los puntos",
            solutions = listOf(CanonicalSolution(id = "sol-$strokeId", text = "Centro")),
            sketch = SketchDrawing(
                strokes = listOf(
                    SketchStroke(
                        id = strokeId,
                        points = listOf(SketchPoint(0.1, 0.2), SketchPoint(0.9, y)),
                        brush = "purple"
                    )
                )
            )
        )

    @Test fun sameDrawingWithDifferentIdsIsConsideredDuplicate() {
        val first = exercise("id-a")
        val second = exercise("id-b")
        assertEquals(StudyExerciseMerge.signature(first), StudyExerciseMerge.signature(second))
        assertEquals(1, StudyExerciseMerge.unique(listOf(first, second), emptyList()).size)
    }

    @Test fun visuallyDifferentDrawingIsNotDiscarded() {
        val first = exercise("id-a", 0.8)
        val second = exercise("id-b", 0.7)
        assertNotEquals(StudyExerciseMerge.signature(first), StudyExerciseMerge.signature(second))
        assertEquals(2, StudyExerciseMerge.unique(listOf(first, second), emptyList()).size)
    }

    @Test fun sameDrawingAlreadyInDestinationIsNotReimported() {
        val existing = exercise("local")
        val incoming = exercise("source")
        assertEquals(0, StudyExerciseMerge.unique(listOf(incoming), listOf(existing)).size)
    }
}
