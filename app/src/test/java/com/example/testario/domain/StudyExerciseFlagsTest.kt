package com.example.testario.domain

import com.example.testario.data.model.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StudyExerciseFlagsTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun previousSchemaSevenWrittenExercisesDefaultToUnflagged() {
        val older = """{"id":"written-1","kind":"WRITTEN_MULTI","prompt":"Enumera las calles"}"""
        val loaded = json.decodeFromString<StudyExercise>(older)
        assertFalse(loaded.flagged)
        assertNull(loaded.writtenAnswerMode) // legacy JSON: infer from numbered photo markers
        assertEquals("written-1", loaded.id)
        assertEquals(StudyExerciseKind.WRITTEN_MULTI, loaded.kind)
    }

    @Test fun previousSchemaSevenAnkiDefaultsToUnflaggedWithoutLosingCard() {
        val older = """{"id":"anki-1","kind":"ANKI_CARD","prompt":"Foto",
            "flashcard":{"kind":"BASIC","front":"Foto","back":"Respuesta"}}"""
        val loaded = json.decodeFromString<StudyExercise>(older)
        assertFalse(loaded.flagged)
        assertEquals("Respuesta", loaded.flashcard?.back)
    }

    @Test fun newWrittenModeRoundTripsWithoutChangingLegacyQuestionIds() {
        for (mode in WrittenAnswerMode.entries) {
            val saved = StudyExercise(id = "original-id",
                kind = StudyExerciseKind.WRITTEN_MULTI, prompt = "Enumera",
                solutions = listOf(CanonicalSolution(id = "sol-id", text = "Respuesta")),
                writtenAnswerMode = mode)
            val restored = json.decodeFromString<StudyExercise>(json.encodeToString(saved))
            assertEquals(saved, restored)
            assertEquals("sol-id", restored.solutions.single().id)
        }
    }

    @Test fun flaggedWrittenAndAnkiRoundTripWithoutChangingIds() {
        for (kind in StudyExerciseKind.entries) {
            val card = if (kind == StudyExerciseKind.ANKI_CARD)
                FlashcardContent(front = "Pregunta", back = "Respuesta") else null
            val value = StudyExercise(id = "stable", kind = kind,
                prompt = "Pregunta", flashcard = card, flagged = true)
            val restored = json.decodeFromString<StudyExercise>(json.encodeToString(value))
            assertEquals(value, restored)
            assertTrue(restored.flagged)
            assertEquals("stable", restored.id)
        }
    }

    @Test fun duplicationKeepsSourceFlagWithoutCollidingWithSourceIdentity() {
        val original = StudyExercise(id = "known",
            kind = StudyExerciseKind.ANKI_CARD, prompt = "Pregunta",
            flashcard = FlashcardContent(front = "Pregunta", back = "Respuesta"),
            flagged = true)
        val duplicated = StudyExerciseMerge.duplicate(original)
        assertTrue(duplicated.flagged)
        assertNotEquals(original.id, duplicated.id)
        assertEquals(StudyExerciseMerge.signature(original),
            StudyExerciseMerge.signature(duplicated))
    }
}
