package com.example.testario.domain

import com.example.testario.data.local.MigrationManager
import com.example.testario.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StudyExerciseV5MigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun v4CardAndQuestionUpgradeWithoutLosingIdsOrContent() {
        val oldQuestion = Question(
            id = "old-question",
            text = "Pregunta de prueba",
            answers = listOf(Answer("a","Sí"),Answer("b","No"),Answer("c","Tal vez")),
            correctAnswerId = "a"
        )
        val oldCard = StudyExercise(
            id = "old-anki",kind = StudyExerciseKind.ANKI_CARD,
            prompt = "La ciudad Zaragoza",
            flashcard = FlashcardContent(
                kind = FlashcardKind.CLOZE,
                front = "La ciudad Zaragoza",back = "",
                gaps = listOf(ClozeGap(id="gap1",start=10,end=18))
            )
        )
        val old = TestarioDatabase(version=4,
            topics = listOf(Topic(id="tema",name="Tema",questions=listOf(oldQuestion),
                exercises=listOf(oldCard))),
            ankiReviewEvents = listOf(AnkiReviewEvent(
                id="event",cardId="old-anki:gap1",timestampMillis=1000,
                rating=ReviewRating.GOOD,nextDueAtMillis=2000,intervalMinutes=10
            ))
        )
        val migrated=MigrationManager.migrate(json.encodeToString(old))
        val restored=json.decodeFromString<TestarioDatabase>(migrated)
        assertEquals(5,restored.version)
        assertEquals("old-question",restored.topics[0].questions[0].id)
        assertEquals("old-anki",restored.topics[0].exercises[0].id)
        assertEquals("gap1",restored.topics[0].exercises[0].flashcard?.gaps?.first()?.id)
        assertNull(restored.topics[0].exercises[0].flashcard?.gaps?.first()?.groupNumber)
        assertEquals("event",restored.ankiReviewEvents[0].id)
        assertTrue(restored.ankiClozeCursors.isEmpty())
    }

    @Test fun v5PersistsFreehandVectorAndCursorRoundtrip() {
        val next = TestarioDatabase(
            topics = listOf(Topic(name="Tema",exercises=listOf(
                StudyExercise(kind=StudyExerciseKind.WRITTEN_MULTI,prompt="Enumera",
                    solutions=listOf(CanonicalSolution(text="Respuesta")),
                    sketch=SketchDrawing(strokes=listOf(SketchStroke(
                        points=listOf(SketchPoint(.1,.2),SketchPoint(.3,.4))
                    ))))
            ))),
            ankiClozeCursors=mapOf("card" to 1)
        )
        val reparsed=json.decodeFromString<TestarioDatabase>(json.encodeToString(next))
        assertEquals(next,reparsed)
    }

    @Test fun neverMigratesFutureVersionIntoEmptyData() {
        assertThrows(IllegalArgumentException::class.java) {
            MigrationManager.migrate("""{"version":6,"topics":[]}""")
        }
    }
}
