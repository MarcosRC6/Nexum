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

class NexumQuickTestWidgetModelTest {
    private val saved = TestTemplate(id = "template-1", name = "Mi plantilla")

    @Test
    fun defaultModeDoesNotRequireExistingTemplates() {
        assertEquals("10 preguntas aleatorias", NexumQuickTestWidgetModel.label(emptyList(), null))
        assertTrue(NexumQuickTestWidgetModel.isSelectionValid(emptyList(), null))
    }

    @Test
    fun savedTemplateUsesStableIdAndCurrentName() {
        assertEquals("Mi plantilla", NexumQuickTestWidgetModel.label(listOf(saved), "template-1"))
        val renamed = saved.copy(name = "Nuevo nombre")
        assertEquals("Nuevo nombre", NexumQuickTestWidgetModel.label(listOf(renamed), "template-1"))
    }

    @Test
    fun removedTemplateNeverSilentlyStartsUnrelatedExam() {
        assertFalse(NexumQuickTestWidgetModel.isSelectionValid(emptyList(), "template-1"))
        assertEquals(
            "Plantilla eliminada: elige otra",
            NexumQuickTestWidgetModel.label(emptyList(), "template-1")
        )
        val result = QuickTestLaunchResolver.resolve(TestarioDatabase(), emptyList(), 10, "template-1")
        assertTrue(result is QuickTestLaunchResult.Invalid)
    }

    @Test
    fun defaultQuickTestExcludesEmptyTopicsAndUsesTenQuestions() {
        val question = Question(
            id = "q1",
            text = "¿Cuál es correcta?",
            answers = listOf(Answer(id = "a", text = "Sí"), Answer(id = "b", text = "No")),
            correctAnswerId = "a"
        )
        val db = TestarioDatabase(
            topics = listOf(
                Topic(id = "has-questions", name = "Tema 1", questions = listOf(question)),
                Topic(id = "empty", name = "Tema 2")
            )
        )
        val ids = NexumQuickTestWidgetModel.defaultTopicIds(db)
        assertEquals(listOf("has-questions"), ids)
        val result = QuickTestLaunchResolver.resolve(
            db, ids, NexumQuickTestWidgetModel.DEFAULT_QUESTION_COUNT, null
        )
        assertTrue(result is QuickTestLaunchResult.Ready)
        assertEquals(
            10,
            (result as QuickTestLaunchResult.Ready).config.questionCount
        )
    }
}
