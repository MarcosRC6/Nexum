package com.example.testario.domain

import com.example.testario.data.local.MigrationManager
import com.example.testario.data.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ImageAnnotationRulesTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun point(x: Double, y: Double) = ImageAnnotationPoint(x, y)
    private fun layer(id: String, tool: ImageAnnotationTool = ImageAnnotationTool.PEN) =
        ImageAnnotations(listOf(ImageAnnotationStroke(
            id = id, tool = tool, colorArgb = 0xFFA855F7L,
            widthFraction = if (tool == ImageAnnotationTool.PEN) .004 else .03,
            opacity = if (tool == ImageAnnotationTool.PEN) 1.0 else .35,
            points = listOf(point(.2,.3),point(.7,.8))
        )))

    @Test fun importingIdenticalDrawingWithDifferentStrokeIdKeepsSameSignature() {
        assertEquals(ImageAnnotationRules.signature(layer("a")), ImageAnnotationRules.signature(layer("b")))
        assertNotEquals(ImageAnnotationRules.signature(layer("a")),
            ImageAnnotationRules.signature(layer("a", ImageAnnotationTool.HIGHLIGHTER)))
    }

    @Test fun rejectsOutOfBoundsAndInvalidOpacity() {
        val bad = ImageAnnotations(listOf(ImageAnnotationStroke(
            points = listOf(point(-.1,.5),point(1.4,.7)),
            opacity = Double.NaN
        )))
        assertTrue(ImageAnnotationRules.errors(bad).isNotEmpty())
        assertTrue(runCatching { ImageAnnotationRules.requireValid(bad) }.isFailure)
    }

    @Test fun v6UpgradesWithSameQuestionIdsAndNoMissingImage() {
        val old = TestarioDatabase(
            version = 6,
            topics = listOf(Topic(id = "tema-1", name = "Tema",
                questions = listOf(Question(
                    id = "old-question", text = "Enunciado",
                    answers = listOf(Answer("a","Verdadero"),Answer("b","Falso")),
                    correctAnswerId = "a", explanationImageUri = "/old/image.jpg"
                ))))
        )
        val restored = json.decodeFromString<TestarioDatabase>(
            MigrationManager.migrate(json.encodeToString(old))
        )
        assertEquals(7, restored.version)
        val question = restored.topics.single().questions.single()
        assertEquals("old-question", question.id)
        assertEquals("a", question.correctAnswerId)
        assertEquals("/old/image.jpg", question.explanationImageUri)
        assertTrue(question.statementAnnotations.isEmpty)
        assertTrue(question.correctionAnnotations.isEmpty)
    }

    @Test fun annotationsRoundtripSeparatelyOnEachPhotoUsage() {
        val sharedUri = "/private/question_images/pic.png"
        val q = Question(
            id = "q", text = "Pregunta",
            answers = listOf(Answer("a","Sí"),Answer("b","No")),
            correctAnswerId = "a",
            explanationImageUri = sharedUri,
            correctionImageUri = sharedUri,
            statementAnnotations = layer("pen"),
            correctionAnnotations = layer("highlight", ImageAnnotationTool.HIGHLIGHTER)
        )
        val card = FlashcardContent(front = "Pregunta", back = "Solución",
            frontImageUri = sharedUri, frontAnnotations = layer("card"))
        val db = TestarioDatabase(topics = listOf(Topic(name = "Tema",
            questions = listOf(q), exercises = listOf(
                StudyExercise(kind = StudyExerciseKind.ANKI_CARD, prompt = "Tarjeta", flashcard = card)
            ))))
        val restored = json.decodeFromString<TestarioDatabase>(json.encodeToString(db))
        val copy = restored.topics.single().questions.single()
        assertEquals(sharedUri, copy.explanationImageUri)
        assertEquals(sharedUri, copy.correctionImageUri)
        assertNotEquals(copy.statementAnnotations, copy.correctionAnnotations)
        assertEquals(layer("card"),
            restored.topics.single().exercises.single().flashcard!!.frontAnnotations)
    }

    @Test fun sourceImagesAreIndependentOfAnnotationMutation() {
        val original = layer("a")
        val appended = original.copy(strokes = original.strokes + ImageAnnotationStroke(
            points = listOf(point(.1,.1),point(.3,.3))
        ))
        assertEquals(1, original.strokes.size)
        assertEquals(2, appended.strokes.size)
        ImageAnnotationRules.requireValid(appended)
    }
}
