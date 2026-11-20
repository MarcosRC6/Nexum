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

    @Test fun imageLocationsArePositionAwareButPlainEnumerationStaysUnordered() {
        val a = CanonicalSolution(
            id = "a", text = "Primera",
            points = listOf(ImagePoint(x = .2, y = .2, order = 1))
        )
        val b = CanonicalSolution(
            id = "b", text = "Segunda",
            points = listOf(ImagePoint(x = .7, y = .6, order = 2))
        )
        val pictured = written(a, b).copy(baseImageUri = "private/photo.jpg")
        val swapped = StudyExerciseRules.gradeWrittenForQuestion(
            pictured, listOf("Segunda", "Primera")
        )
        assertEquals(0, swapped.correct)
        assertEquals(2, swapped.incorrect)
        assertTrue(swapped.correctIds.isEmpty())
        val ordered = StudyExerciseRules.gradeWrittenForQuestion(
            pictured, listOf("Primera", "Segunda")
        )
        assertEquals(2, ordered.correct)
        assertEquals(10.0, ordered.scoreOutOfTen, 0.0)

        val plain = written(a.copy(points = emptyList()), b.copy(points = emptyList()))
        assertEquals(2, StudyExerciseRules.gradeWrittenForQuestion(
            plain, listOf("Segunda", "Primera")).correct)
    }

    @Test fun blankFirstNumberedFieldDoesNotShiftSecondAnswer() {
        val pictured = written(
            CanonicalSolution(id = "a", text = "Primera",
                points = listOf(ImagePoint(x = .2, y = .2, order = 1))),
            CanonicalSolution(id = "b", text = "Segunda",
                points = listOf(ImagePoint(x = .8, y = .8, order = 2)))
        ).copy(sketch = SketchDrawing())
        val result = StudyExerciseRules.gradeWrittenForQuestion(
            pictured, listOf("", "Segunda")
        )
        assertEquals(1, result.correct)
        assertEquals(0, result.incorrect)
        assertEquals(setOf("b"), result.correctIds)
    }
}
