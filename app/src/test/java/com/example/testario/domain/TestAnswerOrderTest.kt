package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestAnswerOrderTest {
    private fun question(index: Int, correctIndex: Int = 0, options: Int = 3): Question {
        val answers = (0 until options).map { position ->
            Answer(id = "q${index}-${position}", text = "Opción ${position}")
        }
        return Question(
            id = "q${index}",
            text = "Pregunta ${index}",
            answers = answers,
            correctAnswerId = answers[correctIndex].id
        )
    }

    @Test
    fun firstAttemptBalancesCorrectLettersEvenWhenAllAuthoredAsA() {
        val original = (0 until 30).map { question(it) }
        val reordered = TestAnswerOrder.randomizeAndBalance(original, Random(83))
        val counts = (0 until 3).map { correctPosition ->
            reordered.count { q -> q.answers.indexOfFirst { it.id == q.correctAnswerId } == correctPosition }
        }
        assertEquals(listOf(10, 10, 10), counts)
        assertEquals(original.map { it.id }, reordered.map { it.id })
    }

    @Test
    fun answerIdsAndCorrectIdAreUnchangedAndStoredOrderNotMutated() {
        val original = (0 until 23).map { question(it, correctIndex = it % 3) }
        val snapshot = original.map { it.answers.map(Answer::id) }
        val reordered = TestAnswerOrder.randomizeAndBalance(original, Random(7))
        original.zip(reordered).forEachIndexed { i, (a, b) ->
            assertEquals(a.correctAnswerId, b.correctAnswerId)
            assertEquals(a.answers.map { it.id }.toSet(), b.answers.map { it.id }.toSet())
            assertEquals(snapshot[i], a.answers.map(Answer::id))
        }
        val counts = (0 until 3).map { target ->
            reordered.count { it.answers.indexOfFirst { a -> a.id == it.correctAnswerId } == target }
        }
        assertTrue(counts.maxOrNull()!! - counts.minOrNull()!! <= 1)
    }

    @Test
    fun differentOptionCountsBalanceSeparatelyWithoutDroppingAnswers() {
        val original = (0 until 17).map { question(it, options = 3) } +
            (17 until 27).map { question(it, options = 4) } +
            listOf(question(27, options = 1))
        val result = TestAnswerOrder.randomizeAndBalance(original, Random(15))
        assertEquals(original.size, result.size)
        (2..4).forEach { answerCount ->
            val group = result.filter { it.answers.size == answerCount }
            if (group.isNotEmpty()) {
                val counts = (0 until answerCount).map { target ->
                    group.count { it.answers.indexOfFirst { a -> a.id == it.correctAnswerId } == target }
                }
                assertTrue(counts.maxOrNull()!! - counts.minOrNull()!! <= 1)
            }
        }
        assertEquals(original.last().answers, result.last().answers)
    }

    @Test
    fun successiveSessionsProduceDifferentAnswerLayouts() {
        val original = (0 until 35).map { question(it) }
        val first = TestAnswerOrder.randomizeAndBalance(original, Random(2))
        val second = TestAnswerOrder.randomizeAndBalance(original, Random(84))
        assertTrue(first.zip(second).any { (a, b) ->
            a.answers.map(Answer::id) != b.answers.map(Answer::id)
        })
    }
}
