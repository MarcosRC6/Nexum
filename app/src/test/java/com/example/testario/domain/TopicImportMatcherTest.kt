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

    @Test fun operatorSymbolsCannotSilentlyDeleteDistinctAnswers() {
        val code = question().copy(answers = listOf(
            Answer("a", "C++"), Answer("b", "Java"), Answer("c", "Rust")))
        val changed = code.copy(answers = listOf(
            Answer("a", "C#"), Answer("b", "Java"), Answer("c", "Rust")))
        assertEquals(1, TopicImportMatcher.uniqueQuestions(listOf(changed), listOf(code)).size)
    }

    @Test fun decimalSeparatorCannotDisappearIntoInteger() {
        val decimal = question().copy(answers = listOf(
            Answer("a", "1,5"), Answer("b", "2"), Answer("c", "3")))
        val integer = decimal.copy(answers = listOf(
            Answer("a", "15"), Answer("b", "2"), Answer("c", "3")))
        assertEquals(1, TopicImportMatcher.uniqueQuestions(listOf(integer), listOf(decimal)).size)
    }

    @Test fun commaAndDotDecimalEquivalenceStillDeduplicates() {
        val decimal = question().copy(answers = listOf(
            Answer("a", "1,5"), Answer("b", "2"), Answer("c", "3")))
        val equivalent = decimal.copy(answers = listOf(
            Answer("a", "1.5"), Answer("b", "2"), Answer("c", "3")))
        assertEquals(0, TopicImportMatcher.uniqueQuestions(listOf(equivalent), listOf(decimal)).size)
    }

    @Test fun legacyDecorativePunctuationAndAccentsRemainDeduplicated() {
        val original = question()
        val equivalent = original.copy(
            text = "Que elemento soporta la carga",
            answers = listOf(Answer("a", "Pílar."), Answer("b", "Viga!"), Answer("c", "Zapata?")))
        assertEquals(0, TopicImportMatcher.uniqueQuestions(listOf(equivalent), listOf(original)).size)
    }

    @Test fun answerOrderDoesNotCreateDuplicate() {
        val q = question()
        val reordering = q.copy(answers = q.answers.reversed())
        assertEquals(0, TopicImportMatcher.uniqueQuestions(listOf(reordering), listOf(q)).size)
    }
}
