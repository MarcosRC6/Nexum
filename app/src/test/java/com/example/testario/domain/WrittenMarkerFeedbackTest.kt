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

    @Test fun omissionIsRedAndMarkerIdsNeverChange() {
        assertEquals(mapOf("p1" to true, "p2" to false, "p3" to false),
            WrittenMarkerFeedback.statusByMarker(exercise, listOf("a")))
    }
}
