package com.example.testario.widget

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic
import com.example.testario.domain.QuickTestLaunchResolver
import com.example.testario.domain.QuickTestLaunchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickWidgetSelectionTest {
    private val question = Question(
        id = "question",
        text = "¿Respuesta?",
        answers = listOf(Answer(id = "a", text = "A"), Answer(id = "b", text = "B")),
        correctAnswerId = "a"
    )
    private val db = TestarioDatabase(
        topics = listOf(
            Topic(id = "topic-1", name = "Tema 23 y 24", questions = listOf(question)),
            Topic(id = "topic-empty", name = "Vacío")
        ),
        testTemplates = listOf(TestTemplate(id = "template-1", name = "Mi examen"))
    )

    @Test fun randomModeUsesAllNonEmptyTopicsAndSelectedCount() {
        val s = QuickWidgetSelection(questionCount = 30)
        assertTrue(s.isValid(db))
        assertEquals(listOf("topic-1"), s.topicIds(db))
        assertEquals("30 preguntas · todos los temas", s.label(db))
        val result = QuickTestLaunchResolver.resolve(db, s.topicIds(db), s.questionCount, null)
        assertTrue(result is QuickTestLaunchResult.Ready)
        assertEquals(30, (result as QuickTestLaunchResult.Ready).config.questionCount)
    }

    @Test fun topicModeDoesNotLeakIntoOtherTopics() {
        val s = QuickWidgetSelection(
            mode = QuickWidgetMode.TOPIC, topicId = "topic-1", questionCount = 7
        )
        assertTrue(s.isValid(db))
        assertEquals(listOf("topic-1"), s.topicIds(db))
        assertEquals("Tema 23 y 24 · 7 preguntas", s.label(db))
        assertFalse(s.copy(topicId = "topic-empty").isValid(db))
        assertFalse(s.copy(topicId = "deleted").isValid(db))
    }

    @Test fun templateUsesExistingIdAndRefusesRemovedTemplate() {
        val s = QuickWidgetSelection(
            mode = QuickWidgetMode.TEMPLATE, templateId = "template-1"
        )
        assertTrue(s.isValid(db))
        assertEquals("Mi examen", s.label(db))
        assertFalse(s.copy(templateId = "deleted").isValid(db))
        assertEquals("Plantilla eliminada: configurar",
            s.copy(templateId = "deleted").label(db))
    }

    @Test fun countIsBoundedAndStableAcrossModes() {
        assertEquals(1, QuickWidgetSelection(questionCount = -3).normalized().questionCount)
        assertEquals(500, QuickWidgetSelection(questionCount = 10_000).normalized().questionCount)
    }
}
