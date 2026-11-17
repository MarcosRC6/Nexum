package com.example.testario.domain

import com.example.testario.data.model.Question
import java.text.Normalizer

/** Semantic-safe exact fingerprinting within the destination topic only. */
object TopicImportMatcher {
    private fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]"), "")

    fun fingerprint(question: Question): String {
        val statement = normalize(question.text)
        val alternatives = question.answers.map { normalize(it.text) }
            .sorted().joinToString("|")
        val correct = question.answers
            .firstOrNull { it.id == question.correctAnswerId }
            ?.text?.let(::normalize).orEmpty()
        return "$statement::$alternatives::$correct"
    }

    fun uniqueQuestions(incoming: List<Question>, alreadyInTarget: List<Question>): List<Question> {
        val known = alreadyInTarget.mapTo(hashSetOf(), ::fingerprint)
        return incoming.filter { known.add(fingerprint(it)) }
    }
}
