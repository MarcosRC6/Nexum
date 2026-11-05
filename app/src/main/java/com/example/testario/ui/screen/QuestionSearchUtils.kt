package com.example.testario.ui.screen

import com.example.testario.data.model.Question
import java.text.Normalizer

enum class HomeSearchMode(val label: String) {
    TOPICS("Tema"),
    QUESTIONS("Preguntas")
}

enum class QuestionSearchScope(val label: String) {
    ALL("Todo"),
    QUESTION("Enunciado"),
    ANSWERS("Respuestas"),
    EXPLANATION("Explicación")
}

/**
 * Índice normalizado de una pregunta. Se construye una sola vez cuando cambian
 * las preguntas y evita normalizar todos los textos en cada pulsación del buscador.
 */
data class QuestionSearchIndex(
    val questionText: String,
    val answersText: String,
    val explanationText: String
) {
    fun matches(normalizedQuery: String, scope: QuestionSearchScope): Boolean {
        if (normalizedQuery.isBlank()) return true
        return when (scope) {
            QuestionSearchScope.ALL ->
                questionText.contains(normalizedQuery) ||
                    answersText.contains(normalizedQuery) ||
                    explanationText.contains(normalizedQuery)
            QuestionSearchScope.QUESTION -> questionText.contains(normalizedQuery)
            QuestionSearchScope.ANSWERS -> answersText.contains(normalizedQuery)
            QuestionSearchScope.EXPLANATION -> explanationText.contains(normalizedQuery)
        }
    }
}

fun Question.buildSearchIndex(): QuestionSearchIndex = QuestionSearchIndex(
    questionText = normalizeSearchText(text),
    answersText = answers.joinToString("\u0001") { normalizeSearchText(it.text) },
    explanationText = normalizeSearchText(explanation)
)

fun Question.matchesSearch(query: String, scope: QuestionSearchScope): Boolean =
    buildSearchIndex().matches(normalizeSearchText(query), scope)

private val combiningMarksRegex = Regex("\\p{Mn}+")

fun normalizeSearchText(value: String): String {
    return Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(combiningMarksRegex, "")
        .trim()
}
