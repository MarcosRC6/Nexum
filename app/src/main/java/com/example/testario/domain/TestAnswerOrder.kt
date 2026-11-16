package com.example.testario.domain

import com.example.testario.data.model.Question
import kotlin.random.Random

/**
 * Randomizes the displayed answers at the start of EVERY test (including the
 * first attempt), while spreading correct positions evenly through the test.
 *
 * Never mutate persisted question answers or their IDs; a correct answer is
 * tracked by correctAnswerId, not by its original A/B/C letter.
 *
 * Each answer-count group has its own balanced random schedule. A correct
 * option may still occasionally keep its authoring letter: forbidding that
 * would create a new exploitable bias when many questions were authored with A.
 */
object TestAnswerOrder {
    fun randomizeAndBalance(
        questions: List<Question>,
        random: Random = Random.Default
    ): List<Question> {
        val targetsByCount = questions.groupingBy { it.answers.size }
            .eachCount()
            .mapValues { (answerCount, questionCount) ->
                val targets = ArrayList<Int>(questionCount)
                if (answerCount >= 2) {
                    while (targets.size < questionCount) {
                        targets += (0 until answerCount).shuffled(random)
                    }
                }
                ArrayDeque(targets.take(questionCount))
            }

        return questions.map { question ->
            val shuffled = question.answers.shuffled(random).toMutableList()
            val target = targetsByCount[shuffled.size]?.removeFirstOrNull()
            val current = shuffled.indexOfFirst { it.id == question.correctAnswerId }
            if (target != null && current >= 0 && current != target) {
                val other = shuffled[target]
                shuffled[target] = shuffled[current]
                shuffled[current] = other
            }
            question.copy(answers = shuffled)
        }
    }
}
