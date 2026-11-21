package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.DuplicateTopicAction
import com.example.testario.data.model.Question
import com.example.testario.data.model.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportBatchPlannerTest {
    private fun question(correct: String = "a") = Question(
        id = "q-$correct",
        text = "¿Qué dato se debe recordar?",
        answers = listOf(Answer("a", "Primero"), Answer("b", "Segundo"), Answer("c", "Tercero")),
        correctAnswerId = correct
    )

    private fun topic(name: String, correct: String = "a") =
        Topic(id = "incoming-$name", name = name, questions = listOf(question(correct)))

    private fun plan(
        original: List<Topic>,
        vararg choices: ImportBatchDecision
    ): ImportBatchPlan = ImportBatchPlanner.prepare(original, choices.toList()) {
        it.copy(id = "fresh-${it.name}")
    }

    @Test fun importsTwentyTopicsAsOneCompletePlanWithoutChangingSource() {
        val source = (1..20).map { topic("Tema $it") }
        val result = plan(emptyList(), *source.map {
            ImportBatchDecision(it, DuplicateTopicAction.RENAME_AUTO)
        }.toTypedArray())
        assertEquals(20, result.created)
        assertEquals(20, result.topics.size)
        assertEquals(20, result.outcomes.size)
        assertEquals(20, result.addedQuestions)
        assertEquals(20, source.size)
    }

    @Test fun identicalMergeIsReportedAndNeverDuplicatesQuestions() {
        val existing = listOf(topic("Tema 1"))
        val result = plan(existing, ImportBatchDecision(topic("Tema 1"), DuplicateTopicAction.MERGE))
        assertEquals(0, result.merged)
        assertEquals(1, result.identical)
        assertEquals(1, result.duplicatedQuestions)
        assertEquals(1, result.topics.single().questions.size)
        assertEquals(1, existing.single().questions.size)
    }

    @Test fun sameQuestionInDifferentTopicsIsKeptByDesign() {
        val existing = listOf(topic("Zona 1"))
        val result = plan(existing, ImportBatchDecision(topic("Callejero General"), DuplicateTopicAction.RENAME_AUTO))
        assertEquals(2, result.topics.size)
        assertEquals(1, result.created)
        assertEquals(1, result.topics.last().questions.size)
    }

    @Test fun sameWordingWithDifferentCorrectAnswerIsNotDeduplicated() {
        val existing = listOf(topic("Tema 1", "a"))
        val result = plan(existing, ImportBatchDecision(topic("Tema 1", "b"), DuplicateTopicAction.MERGE))
        assertEquals(1, result.merged)
        assertEquals(2, result.topics.single().questions.size)
        assertEquals(0, result.duplicatedQuestions)
    }

    @Test fun repeatedNameWithinSingleBatchIsAutoRenamed() {
        val result = plan(
            emptyList(),
            ImportBatchDecision(topic("Tema"), DuplicateTopicAction.RENAME_AUTO),
            ImportBatchDecision(topic("Tema"), DuplicateTopicAction.RENAME_AUTO)
        )
        assertEquals(listOf("Tema", "Tema (2)"), result.topics.map { it.name })
        assertEquals(2, result.created)
    }

    @Test fun conflictingManualNameRejectsEntirePlanWithoutChangingOriginal() {
        val existing = listOf(topic("Tema 1"))
        val outcome = runCatching {
            plan(existing, ImportBatchDecision(topic("Tema 2"), DuplicateTopicAction.RENAME, "Tema 1"))
        }
        assertTrue(outcome.isFailure)
        assertEquals(1, existing.size)
        assertEquals("Tema 1", existing.single().name)
    }

    @Test fun lateFailureDoesNotReturnPartiallyMutatedOriginal() {
        val existing = listOf(topic("Original"))
        val result = runCatching {
            plan(existing,
                ImportBatchDecision(topic("Nuevo"), DuplicateTopicAction.RENAME_AUTO),
                ImportBatchDecision(topic("Otro"), DuplicateTopicAction.RENAME, "Original")
            )
        }
        assertTrue(result.isFailure)
        assertEquals(listOf("Original"), existing.map { it.name })
    }
}
