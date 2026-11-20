package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class StudyExerciseRulesTest {
    private fun written(vararg solutions: CanonicalSolution): StudyExercise =
        StudyExercise(
            kind = StudyExerciseKind.WRITTEN_MULTI,
            prompt = "Enumera las conexiones",
            solutions = solutions.toList()
        )

    @Test fun aliasesAndRepeatedCorrectAnswersCountOnlyOnce() {
        val ex = written(
            CanonicalSolution(id = "one", text = "Thomas Edison", aliases = listOf("Edison")),
            CanonicalSolution(id = "two", text = "Av. Cataluña", aliases = listOf("Avenida Cataluña"))
        )
        val result = StudyExerciseRules.gradeWritten(
            ex, listOf("THOMAS EDÍSON", "Edison", "  avenida cataluña ", "inexistente", "INEXISTENTE", "  ")
        )
        assertEquals(2, result.correct)
        assertEquals(1, result.incorrect)
        assertEquals(0, result.omitted)
        assertEquals(8.75, result.scoreOutOfTen, 0.000001)
        assertEquals(setOf("one", "two"), result.correctIds)
    }

    @Test fun negativeGradesClampToZeroWithManyWrongAnswers() {
        val ex = written(CanonicalSolution(text = "Solución"))
        val result = StudyExerciseRules.gradeWritten(
            ex, (1..100).map { "error-$it" }, penaltyWrong = -1.0
        )
        assertEquals(100, result.incorrect)
        assertEquals(0.0, result.scoreOutOfTen, 0.0)
    }

    @Test fun configurablePenaltyChangesScoreNotRecall() {
        val ex = written(
            CanonicalSolution(text = "Uno"),
            CanonicalSolution(text = "Dos"),
            CanonicalSolution(text = "Tres")
        )
        val inputs = listOf("Uno", "Dos", "Fallida")
        val zero = StudyExerciseRules.gradeWritten(ex, inputs, penaltyWrong = 0.0)
        val half = StudyExerciseRules.gradeWritten(ex, inputs, penaltyWrong = -0.5)
        assertEquals(10.0 * 2.0 / 3.0, zero.scoreOutOfTen, 0.0001)
        assertEquals(5.0, half.scoreOutOfTen, 0.0001)
        assertEquals(zero.recallPercent, half.recallPercent, 0.0)
    }

    @Test fun ambiguousAliasesCannotBePublished() {
        val ex = written(
            CanonicalSolution(text = "Calle A", aliases = listOf("A")),
            CanonicalSolution(text = "A", aliases = listOf("Calle segunda"))
        )
        assertTrue(StudyExerciseRules.errors(ex).any { it.contains("Alias ambiguo") })
    }

    @Test fun distinctiveSurnameIsAutomaticButTyposStillNeverMatch() {
        val ex = written(CanonicalSolution(text = "Thomas Edison"))
        val result = StudyExerciseRules.gradeWritten(ex, listOf("Edison", "Thomas Edisno"))
        assertEquals(1, result.correct)
        assertEquals(1, result.incorrect)
    }

    @Test fun markersMustBeOnBaseWithFiniteCoordinates() {
        val ex = written(CanonicalSolution(text = "Uno", points = listOf(
            ImagePoint(x = Double.NaN, y = 0.3)
        )))
        val errors = StudyExerciseRules.errors(ex)
        assertTrue(errors.any { it.contains("fuera de la imagen") })
        assertTrue(errors.any { it.contains("requiere imagen") })
    }

    @Test fun writtenPictureMarkersRequireTheCorrectAnswerInTheMatchingNumberedField() {
        val ex = written(
            CanonicalSolution(id = "s1", text = "Uno", points = listOf(
                ImagePoint(id = "p1", x = .15, y = .25, order = 1)
            )),
            CanonicalSolution(id = "s2", text = "Dos", points = listOf(
                ImagePoint(id = "p2", x = .35, y = .5, order = 2)
            )),
            CanonicalSolution(id = "s3", text = "Tres", points = listOf(
                ImagePoint(id = "p3", x = .75, y = .75, order = 3)
            ))
        ).copy(baseImageUri = "/private/photo.png")
        assertTrue(StudyExerciseRules.errors(ex).isEmpty())
        val swapped = StudyExerciseRules.gradeWritten(ex, listOf("Dos", "Uno", "Tres"))
        assertEquals(1, swapped.correct)
        assertEquals(2, swapped.incorrect)
        assertEquals(setOf("s3"), swapped.correctIds)
        val blankMiddle = StudyExerciseRules.gradeWritten(ex, listOf("Uno", "", "Tres"))
        assertEquals(2, blankMiddle.correct)
        assertEquals(1, blankMiddle.omitted)
        assertEquals(0, blankMiddle.incorrect)
        val full = StudyExerciseRules.gradeWritten(ex, listOf("Uno", "Dos", "Tres"))
        assertEquals(3, full.correct)
        assertEquals(10.0, full.scoreOutOfTen, 0.0)
    }

    @Test fun plainWrittenSolutionsAreStillAcceptedInAnyOrder() {
        val ex = written(
            CanonicalSolution(id = "one", text = "Uno"),
            CanonicalSolution(id = "two", text = "Dos")
        )
        assertEquals(2, StudyExerciseRules.gradeWritten(ex, listOf("Dos", "Uno")).correct)
    }

    @Test fun removingMiddleWrittenSolutionAlsoRemovesItsMarkerAndRenumbersThird() {
        val before = listOf(
            CanonicalSolution(id = "one", text = "Uno",
                points = listOf(ImagePoint(id = "m1", x = .1, y = .2, order = 1))),
            CanonicalSolution(id = "two", text = "Dos",
                points = listOf(ImagePoint(id = "m2", x = .2, y = .3, order = 2))),
            CanonicalSolution(id = "three", text = "Tres",
                points = listOf(ImagePoint(id = "m3", x = .3, y = .4, order = 3)))
        )
        val remaining = StudyExerciseRules.removeSolutionAndRenumber(before, "two")
        assertEquals(listOf("one", "three"), remaining.map { it.id })
        assertEquals(listOf(1, 2), remaining.flatMap { it.points }.map { it.order })
        assertFalse(remaining.flatMap { it.points }.any { it.id == "m2" })
        assertEquals("m3", remaining.last().points.single().id)
    }

    @Test fun clozeGapsCannotOverlap() {
        val ex = StudyExercise(
            kind = StudyExerciseKind.ANKI_CARD, prompt = "",
            flashcard = FlashcardContent(
                kind = FlashcardKind.CLOZE, front = "La capital es Zaragoza", back = "",
                gaps = listOf(ClozeGap(start = 3, end = 10), ClozeGap(start = 5, end = 12))
            )
        )
        assertTrue(StudyExerciseRules.errors(ex).any { it.contains("superpuestos") })
    }

    @Test fun imageMasksMustFitInsideImage() {
        val ex = StudyExercise(
            kind = StudyExerciseKind.ANKI_CARD, prompt = "",
            flashcard = FlashcardContent(
                kind = FlashcardKind.IMAGE_OCCLUSION,
                front = "¿Qué es esto?", back = "Elemento",
                frontImageUri = "foo.png",
                masks = listOf(ImageOcclusionMask(x = 0.8, y = 0.2, width = 0.3, height = 0.3))
            )
        )
        assertTrue(StudyExerciseRules.errors(ex).any { it.contains("fuera de la imagen") })
    }

    @Test fun rejectInvalidBaremo() {
        val ex = written(CanonicalSolution(text = "Una"))
        assertThrows(IllegalArgumentException::class.java) {
            StudyExerciseRules.gradeWritten(ex, listOf("Una"), pointsCorrect = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudyExerciseRules.gradeWritten(ex, listOf("Una"), penaltyWrong = 1.0)
        }
    }

    @Test fun duplicateChangesIdsWithoutChangingContent() {
        val original = written(CanonicalSolution(
            id = "sol", text = "Calle A",
            points = listOf(ImagePoint(id = "point", x = 0.5, y = 0.5))
        )).copy(id = "exercise", diagram = LineDiagram())
        val copy = StudyExerciseMerge.duplicate(original)
        assertNotEquals(original.id, copy.id)
        assertNotEquals(original.solutions.first().id, copy.solutions.first().id)
        assertNotEquals(original.solutions.first().points.first().id, copy.solutions.first().points.first().id)
        assertEquals(StudyExerciseMerge.signature(original), StudyExerciseMerge.signature(copy))
        assertTrue(StudyExerciseMerge.unique(listOf(copy), listOf(original)).isEmpty())
    }
}
