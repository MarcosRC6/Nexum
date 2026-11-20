package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class NumberedWrittenGradingTest {
    private fun exercise(numbered: Boolean): StudyExercise =
        StudyExercise(
            kind = StudyExerciseKind.WRITTEN_MULTI, prompt = "Identifica estas calles",
            baseImageUri = if (numbered) "/test/picture.jpg" else null,
            solutions = listOf(
                CanonicalSolution(id = "a", text = "Edison",
                    points = if (numbered) listOf(ImagePoint(x = .2, y = .3, order = 1)) else emptyList()),
                CanonicalSolution(id = "b", text = "Cajal",
                    points = if (numbered) listOf(ImagePoint(x = .7, y = .8, order = 2)) else emptyList())
            )
        )

    @Test fun numberedSolutionsHavePositionalMeaning() {
        val correct = StudyExerciseRules.gradeWrittenInSession(
            exercise(true), listOf("Edison", "Cajal"))
        assertEquals(10.0, correct.scoreOutOfTen, .001)
        val swapped = StudyExerciseRules.gradeWrittenInSession(
            exercise(true), listOf("Cajal", "Edison"))
        assertEquals(0.0, swapped.scoreOutOfTen, .001)
        assertEquals(2, swapped.incorrect)
        assertEquals(emptySet<String>(), swapped.correctIds)
    }

    @Test fun skippingFirstInputDoesNotMoveSecondResponseToFirstSolution() {
        val graded = StudyExerciseRules.gradeWrittenInSession(
            exercise(true), listOf("", "Cajal"))
        assertEquals(1, graded.correct)
        assertEquals(1, graded.omitted)
        assertEquals(setOf("b"), graded.correctIds)
    }

    @Test fun writtenWithoutPhotoRemainsOrderIndependent() {
        val graded = StudyExerciseRules.gradeWrittenInSession(
            exercise(false), listOf("Cajal", "Edison"))
        assertEquals(10.0, graded.scoreOutOfTen, .001)
    }

    @Test fun penaltyIsAppliedOnlyToWrongNonblankNumberedResponses() {
        val graded = StudyExerciseRules.gradeWrittenInSession(
            exercise(true), listOf("Edison", "otra calle"), 1.0, -0.25)
        assertEquals(3.75, graded.scoreOutOfTen, .001)
        assertEquals(1, graded.correct)
        assertEquals(1, graded.incorrect)
    }
}
