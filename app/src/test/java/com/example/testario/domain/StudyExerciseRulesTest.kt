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

    @Test fun onlyDefinedAliasesMatchNotFuzzySubstrings() {
        val ex = written(CanonicalSolution(text = "Thomas Edison"))
        val result = StudyExerciseRules.gradeWritten(ex, listOf("Edison", "Thomas Edisno"))
        assertEquals(0, result.correct)
        assertEquals(2, result.incorrect)
    }

    @Test fun markersMustBeOnBaseWithFiniteCoordinates() {
        val ex = written(CanonicalSolution(text = "Uno", points = listOf(
            ImagePoint(x = Double.NaN, y = 0.3)
        )))
        val errors = StudyExerciseRules.errors(ex)
        assertTrue(errors.any { it.contains("fuera de la imagen") })
        assertTrue(errors.any { it.contains("requiere imagen") })
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
