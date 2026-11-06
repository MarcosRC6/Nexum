package com.example.testario.domain

import com.example.testario.data.model.Question
import java.text.Normalizer

/**
 * Empareja preguntas para sincronizar sin suponer que un enunciado idéntico
 * identifica la misma pregunta. Una coincidencia ambigua nunca se sobrescribe.
 */
internal object QuestionSyncMatcher {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val nonAlphanumeric = Regex("[^a-z0-9]")

    fun findCompatible(local: Question, candidates: List<Question>): Question? {
        // La identidad estable prevalece, incluso si se han editado respuestas.
        val byId = candidates.filter { it.id == local.id }
        if (byId.size == 1) return byId.first()
        if (byId.size > 1) return null

        // Sin identidad estable, solo aceptar las mismas alternativas (sin importar orden).
        val localAnswers = answerFingerprint(local)
        val matches = candidates.filter { answerFingerprint(it) == localAnswers }
        return matches.singleOrNull()
    }

    private fun answerFingerprint(question: Question): List<String> =
        question.answers.map { answer ->
            Normalizer.normalize(answer.text.lowercase(), Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .replace(nonAlphanumeric, "")
        }.sorted()
}
