package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryMediaReferencesTest {
    @Test fun includesLegacyStatementAndCorrectionPhotosAlongsideAnkiMedia() {
        val testQuestion = Question(
            id = "q1", text = "Pregunta con imágenes",
            answers = listOf(Answer("a", "Sí"), Answer("b", "No")),
            correctAnswerId = "a",
            explanationImageUri = "/private/question_images/statement.png",
            correctionImageUri = "/private/question_images/correction.webp"
        )
        val anki = StudyExercise(
            kind = StudyExerciseKind.ANKI_CARD,
            prompt = "Foto",
            flashcard = FlashcardContent(
                kind = FlashcardKind.BASIC, front = "Foto", back = "Respuesta",
                frontImageUri = "/private/question_images/flash.jpg",
                backImageUri = "/private/question_images/flash-back.png"
            )
        )
        val topic = Topic(name = "Tema", questions = listOf(testQuestion), exercises = listOf(anki))
        assertEquals(
            listOf(
                "/private/question_images/statement.png",
                "/private/question_images/correction.webp",
                "/private/question_images/flash.jpg",
                "/private/question_images/flash-back.png"
            ), LibraryMediaReferences.paths(listOf(topic))
        )
    }

    @Test fun noImagesOrRepeatedUriNeverCreatesFalseReferences() {
        val plain = Topic(name = "Sin imágenes", questions = listOf(Question(
            text = "Texto",
            answers = listOf(Answer("a", "Sí"), Answer("b", "No")),
            correctAnswerId = "a"
        )))
        assertTrue(LibraryMediaReferences.paths(listOf(plain)).isEmpty())
        val reused = plain.copy(questions = listOf(
            plain.questions.single().copy(explanationImageUri = "/a.png",
                correctionImageUri = "/a.png")
        ))
        assertEquals(listOf("/a.png"), LibraryMediaReferences.paths(listOf(reused)))
    }
}
