package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class StudyExerciseAutoAliasTest {
    private fun exercise(vararg names: String): StudyExercise = StudyExercise(
        kind = StudyExerciseKind.WRITTEN_MULTI,
        prompt = "Enumera los nombres",
        solutions = names.map { CanonicalSolution(text = it) }
    )

    @Test fun streetPrefixesAndSurnamesRecognizedWhenUnambiguous() {
        val ex = exercise("Calle de Thomas Edison", "Avenida Cataluña", "Plaza del Pilar")
        assertEquals("Calle de Thomas Edison",
            StudyExerciseRules.findAcceptedSolution(ex,"Edison")?.text)
        assertEquals("Calle de Thomas Edison",
            StudyExerciseRules.findAcceptedSolution(ex,"C/ Thomas Edison")?.text)
        assertEquals("Avenida Cataluña",
            StudyExerciseRules.findAcceptedSolution(ex,"Av. Cataluña")?.text)
        assertEquals("Plaza del Pilar",
            StudyExerciseRules.findAcceptedSolution(ex,"Pza. del Pilar")?.text)
        assertEquals("Avenida Cataluña",
            StudyExerciseRules.findAcceptedSolution(ex,"Cataluña")?.text)
    }

    @Test fun ambiguousShortNameIsNeverCountedAsCorrect() {
        val ex = exercise("Calle Thomas Edison","Plaza Antonio Edison")
        assertNull(StudyExerciseRules.findAcceptedSolution(ex,"Edison"))
        assertEquals("Calle Thomas Edison",
            StudyExerciseRules.findAcceptedSolution(ex,"Thomas Edison")?.text)
        val grade = StudyExerciseRules.gradeWritten(ex,listOf("Edison","Thomas Edison"))
        assertEquals(1,grade.correct)
        assertEquals(1,grade.incorrect)
    }

    @Test fun doesNotInventMiddleWordsOrGuessTypos() {
        val ex = exercise("Calle de Thomas Edison","Avenida de Aragón")
        assertNull(StudyExerciseRules.findAcceptedSolution(ex,"Thomas"))
        assertNull(StudyExerciseRules.findAcceptedSolution(ex,"Edisno"))
        assertNull(StudyExerciseRules.findAcceptedSolution(ex,"A"))
        assertEquals(0,StudyExerciseRules.gradeWritten(ex,listOf("ediso")).correct)
    }

    @Test fun fullAndShortAliasesOnlyScoreOnce() {
        val ex = exercise("Calle Thomas Edison","Avenida Cataluña")
        val grade = StudyExerciseRules.gradeWritten(ex,
            listOf("Calle Thomas Edison","Edison","Av. Cataluña","Cataluña"))
        assertEquals(2,grade.correct)
        assertEquals(0,grade.incorrect)
        assertEquals(10.0,grade.scoreOutOfTen,0.001)
    }
}
