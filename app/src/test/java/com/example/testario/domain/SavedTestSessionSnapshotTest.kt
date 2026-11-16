package com.example.testario.domain

import com.example.testario.data.local.SavedTestSession
import com.example.testario.data.local.StoredTestAnswer
import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SavedTestSessionSnapshotTest {
    @Test fun answersOrderAndRemainingTimeSurviveSerialization() {
        val question = Question(
            id = "q",
            text = "¿Pregunta?",
            answers = listOf(Answer(id = "c", text = "C"), Answer(id = "a", text = "A"), Answer(id = "b", text = "B")),
            correctAnswerId = "c"
        )
        val saved = SavedTestSession(
            sessionId = "test-session",
            savedAt = 4L,
            mode = TestMode.EXAM,
            questionIndex = 0,
            answers = listOf(StoredTestAnswer(question, selectedAnswerId = "b", timeSpentSeconds = 37)),
            elapsedSeconds = 99,
            remainingSeconds = 71,
            pointsCorrect = 1.0,
            penaltyWrong = -0.25
        )
        val after = Json.decodeFromString<SavedTestSession>(Json.encodeToString(saved))
        assertEquals("test-session", after.sessionId)
        assertEquals(listOf("c", "a", "b"), after.answers.single().question.answers.map { it.id })
        assertEquals("b", after.answers.single().selectedAnswerId)
        assertEquals(71, after.remainingSeconds)
        assertEquals(99L, after.elapsedSeconds)
        assertEquals(-0.25, after.penaltyWrong, 0.00001)
    }
}
