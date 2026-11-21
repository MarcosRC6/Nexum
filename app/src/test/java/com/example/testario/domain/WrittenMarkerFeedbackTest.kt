package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class WrittenMarkerFeedbackTest {
    private val exercise = StudyExercise(kind = StudyExerciseKind.WRITTEN_MULTI,
        prompt = "Identifica los números",
        baseImageUri = "/tmp/photo.jpg",
        solutions = listOf(
            CanonicalSolution(id = "s1", text = "a", points = listOf(ImagePoint(id = "p1", x = .1, y = .1, order = 1))),
            CanonicalSolution(id = "s2", text = "b", points = listOf(ImagePoint(id = "p2", x = .2, y = .2, order = 2))),
            CanonicalSolution(id = "s3", text = "c", points = listOf(ImagePoint(id = "p3", x = .3, y = .3, order = 3)))
        ))

    @Test fun thirdWrongAnswerColorsThirdPinRedEvenIfFirstTwoCorrect() {
        assertEquals(mapOf("p1" to true, "p2" to true, "p3" to false),
            WrittenMarkerFeedback.statusByMarker(exercise, listOf("a", "b", "d")))
    }

    @Test fun misplacedCanonicalAnswerIsWrongForItsNumber() {
        assertEquals(mapOf("p1" to false, "p2" to false, "p3" to true),
            WrittenMarkerFeedback.statusByMarker(exercise, listOf("b", "a", "c")))
    }

    @Test fun numericalGradingMatchesMarkerColorsOnSwappedAnswers() {
        val result = StudyExerciseRules.gradeWrittenInSession(
            exercise, listOf("a", "c", "b"), 1.0, -0.25)
        assertEquals(1, result.correct)
        assertEquals(2, result.incorrect)
        assertEquals(setOf("s1"), result.correctIds)
    }

    @Test fun visibleMarkerOrderWinsOverHistoricalSolutionInsertionOrder() {
        val swapped = exercise.copy(solutions = listOf(
            exercise.solutions[2], exercise.solutions[0], exercise.solutions[1]
        ))
        assertEquals(listOf("s1", "s2", "s3"),
            StudyExerciseRules.positionalSolutions(swapped).map { it.id })
        val answers = listOf("a", "b", "f")
        assertEquals(mapOf("p1" to true, "p2" to true, "p3" to false),
            WrittenMarkerFeedback.statusByMarker(swapped, answers))
        val grade = StudyExerciseRules.gradeWrittenInSession(swapped, answers)
        assertEquals(setOf("s1", "s2"), grade.correctIds)
        assertEquals(1, grade.incorrect)
    }

    @Test fun ambiguousMultiPinLegacySolutionsKeepTheirInsertionOrder() {
        val legacy = exercise.copy(solutions = listOf(
            exercise.solutions[0].copy(points = exercise.solutions[0].points +
                ImagePoint(id = "extra", x = .4, y = .4, order = 4)),
            exercise.solutions[1], exercise.solutions[2]
        ))
        assertEquals(legacy.solutions,
            StudyExerciseRules.positionalSolutions(legacy))
    }

    @Test fun omissionIsRedAndMarkerIdsNeverChange() {
        assertEquals(mapOf("p1" to true, "p2" to false, "p3" to false),
            WrittenMarkerFeedback.statusByMarker(exercise, listOf("a")))
    }
}
