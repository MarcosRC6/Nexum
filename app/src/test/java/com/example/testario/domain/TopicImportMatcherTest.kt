package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Test

class TopicImportMatcherTest {
    private fun question(correct: String = "a") = Question(
        id = "q1", text = "¿Qué elemento soporta la carga?",
        answers = listOf(Answer("a", "Pilar"), Answer("b", "Viga"), Answer("c", "Zapata")),
        correctAnswerId = correct
    )

    @Test fun duplicatesInTheSameTopicAreExcluded() {
        val existing = listOf(question())
        val incoming = listOf(question(), question())
        assertEquals(0, TopicImportMatcher.uniqueQuestions(incoming, existing).size)
    }

    @Test fun sameQuestionInAnotherTopicIsNeverDiscarded() {
        assertEquals(1, TopicImportMatcher.uniqueQuestions(listOf(question()), emptyList()).size)
    }

    @Test fun changedCorrectAnswerIsNotSilentlyIgnored() {
        val old = question(correct = "a")
        val corrected = question(correct = "b")
        assertEquals(1, TopicImportMatcher.uniqueQuestions(listOf(corrected), listOf(old)).size)
    }

    @Test fun answerOrderDoesNotCreateDuplicate() {
        val q = question()
        val reordering = q.copy(answers = q.answers.reversed())
        assertEquals(0, TopicImportMatcher.uniqueQuestions(listOf(reordering), listOf(q)).size)
    }
}
