package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import java.util.UUID
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


    /**
     * Reasigna cada respuesta del banco externo a un ID inequívoco sin perder
     * las respuestas históricas que todavía existen.
     *
     * Un correctAnswerId externo inexistente o repetido invalida la sincronización
     * de esa pregunta; nunca se cambia silenciosamente la respuesta correcta.
     */
    data class RemappedAnswers(
        val answers: List<Answer>,
        val correctAnswerId: String,
        val previousLastAnswerId: String?
    )

    fun remapAnswers(local: Question, external: Question): RemappedAnswers? {
        if (external.answers.isEmpty() ||
            external.correctAnswerId.isBlank() ||
            external.answers.any { it.id.isBlank() } ||
            external.answers.map { it.id }.toSet().size != external.answers.size
        ) return null

        val correctIndex = external.answers.indexOfFirst { it.id == external.correctAnswerId }
        if (correctIndex < 0) return null

        val localIds = local.answers.mapTo(mutableSetOf()) { it.id }
        val claimed = mutableSetOf<String>()
        val answers = external.answers.map { incoming ->
            // Emparejar el contenido antes que el ID: algunos orígenes reutilizan
            // los IDs por posición aunque reordenen o reescriban alternativas.
            // Una alternativa realmente nueva recibe un ID nuevo y no hereda
            // estadísticas históricas de otro texto.
            val sameText = local.answers.filter { it.id !in claimed &&
                normalizeAnswer(it.text) == normalizeAnswer(incoming.text) }
            val existing = sameText.firstOrNull { it.id == incoming.id }
                ?: sameText.firstOrNull()
            val stableId = existing?.id ?: generateUniqueId(localIds, claimed)
            claimed.add(stableId)
            incoming.copy(id = stableId)
        }
        val correctAnswerId = answers[correctIndex].id
        val lastAnswer = local.stats.lastAnswerId?.takeIf { it in claimed }
        return RemappedAnswers(answers, correctAnswerId, lastAnswer)
    }

    private fun generateUniqueId(localIds: Set<String>, claimed: Set<String>): String {
        while (true) {
            val candidate = UUID.randomUUID().toString()
            if (candidate !in localIds && candidate !in claimed) return candidate
        }
    }

    private fun normalizeAnswer(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace(nonAlphanumeric, "")

    private fun answerFingerprint(question: Question): List<String> =
        question.answers.map { answer ->
            Normalizer.normalize(answer.text.lowercase(), Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .replace(nonAlphanumeric, "")
        }.sorted()
}
