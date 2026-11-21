package com.example.testario.domain

import com.example.testario.data.model.Question
import java.text.Normalizer

/** Semantic-safe exact fingerprinting within the destination topic only. */
object TopicImportMatcher {
    // Keep meaningful operators and numeric separators during deduplication.
    // Dropping ALL punctuation would silently merge C++, C# and C, or 1,5 and 15.
    // Ordinary question marks, accents, whitespace and decorative punctuation
    // are still ignored so a re-import remains idempotent.
    private fun normalize(text: String): String {
        val folded = Normalizer.normalize(text.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return buildString {
            folded.forEachIndexed { index, char ->
                when {
                    char.isLetterOrDigit() -> append(char)
                    char in "+-*/%=<>#^" -> append(char)
                    char in ".," && index > 0 && index + 1 < folded.length &&
                        folded[index - 1].isDigit() && folded[index + 1].isDigit() ->
                        append(if (char == ',') '.' else char)
                }
            }
        }
    }

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
