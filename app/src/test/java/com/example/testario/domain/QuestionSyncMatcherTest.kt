package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSyncMatcherTest {
    private fun question(id: String, vararg choices: String): Question {
        val answers = choices.mapIndexed { index, text -> Answer(id = "${id}-a${index}", text = text) }
        return Question(
            id = id,
            text = "Pregunta compartida",
            answers = answers,
            correctAnswerId = answers.first().id
        )
    }

    @Test fun sameIdAcceptsAnEditedAnswerSet() {
        val local = question("stable", "A", "B", "C")
        val updated = question("stable", "A", "D", "C")
        assertEquals(updated, QuestionSyncMatcher.findCompatible(local, listOf(updated)))
    }

    @Test fun differentIdAndDifferentAnswersAreNotConsideredSameQuestion() {
        val local = question("local", "A", "B", "C")
        val unrelated = question("external", "A", "B", "D")
        assertNull(QuestionSyncMatcher.findCompatible(local, listOf(unrelated)))
    }

    @Test fun sameAnswersWithDifferentOrderAreCompatible() {
        val local = question("local", "A", "B", "C")
        val external = question("external", "C", "A", "B")
        assertEquals(external, QuestionSyncMatcher.findCompatible(local, listOf(external)))
    }

    @Test fun rejectsForeignQuestionWithMissingCorrectAnswerId() {
        val local = question("local", "A", "B", "C")
        val invalid = question("local", "A", "B", "C")
            .copy(correctAnswerId = "nonexistent")
        assertNull(QuestionSyncMatcher.remapAnswers(local, invalid))
    }

    @Test fun correctAnswerFollowsReorderedOptionsByIdentityNotPosition() {
        val local = question("local", "A", "B", "C")
        val external = question("local", "C", "B", "A")
            .copy(correctAnswerId = "local-a2")
        val mapped = QuestionSyncMatcher.remapAnswers(local, external)
        assertEquals(local.answers[0].id, mapped?.correctAnswerId)
        assertEquals(listOf("C", "B", "A"), mapped?.answers?.map { it.text })
        assertEquals(3, mapped?.answers?.map { it.id }?.toSet()?.size)
    }

    @Test fun duplicateAnswerTextsStillReceiveDistinctIds() {
        val local = question("local", "Sí", "No", "Quizás")
        val external = question("local", "Sí", "Sí", "No")
            .copy(correctAnswerId = "local-a1")
        val mapped = QuestionSyncMatcher.remapAnswers(local, external)
        assertEquals(3, mapped?.answers?.map { it.id }?.toSet()?.size)
        assertEquals(mapped?.answers?.get(1)?.id, mapped?.correctAnswerId)
        assertNotEquals(mapped?.answers?.get(0)?.id, mapped?.correctAnswerId)
    }

    @Test fun keepsHistoryOnlyForAnswersStillPresent() {
        val original = question("local", "A", "B", "C")
            .copy(stats = com.example.testario.data.model.QuestionStats(lastAnswerId = "local-a1"))
        val incoming = question("foreign", "A", "D", "C")
        val mapped = QuestionSyncMatcher.remapAnswers(original, incoming)
        assertTrue(mapped != null)
        assertNull(mapped?.previousLastAnswerId)
        assertEquals(3, mapped?.answers?.map { it.id }?.toSet()?.size)
    }

    @Test fun twoPlausibleCandidatesAreAmbiguousWithoutStableId() {
        val local = question("local", "A", "B", "C")
        assertNull(
            QuestionSyncMatcher.findCompatible(
                local,
                listOf(question("one", "C", "B", "A"), question("two", "B", "C", "A"))
            )
        )
    }
}
