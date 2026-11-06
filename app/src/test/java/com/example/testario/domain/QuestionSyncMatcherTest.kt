package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
