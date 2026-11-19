package com.example.testario.domain

import com.example.testario.data.model.*
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * Pure validation and correction engine. No Android dependencies, I/O, global mutable
 * state, or silent fuzzy matching. Always validate the exercise before scoring.
 */
object StudyExerciseRules {
    fun normalize(text: String): String =
        Normalizer.normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim().replace(Regex("\\s+"), " ")

    private fun valid01(v: Double): Boolean = v.isFinite() && v in 0.0..1.0

    /** Returns all reasons; draft records may be saved without passing validation. */
    fun errors(exercise: StudyExercise): List<String> {
        val errors = mutableListOf<String>()
        if (exercise.id.isBlank()) errors += "ID de ejercicio vacío."
        if (exercise.revision < 1) errors += "Revisión de ejercicio inválida."
        if (exercise.prompt.isBlank() && exercise.kind == StudyExerciseKind.WRITTEN_MULTI)
            errors += "Falta el enunciado."
        if (exercise.kind == StudyExerciseKind.WRITTEN_MULTI) {
            if (exercise.flashcard != null) errors += "Un ejercicio escrito no debe incluir tarjeta Anki."
            if (exercise.solutions.isEmpty()) errors += "Debe incluir al menos una solución."
            val ids = hashSetOf<String>()
            val names = hashMapOf<String, String>()
            val markerIds = hashSetOf<String>()
            val orders = hashSetOf<Int>()
            exercise.solutions.forEach { solution ->
                if (solution.id.isBlank() || !ids.add(solution.id))
                    errors += "ID de solución duplicado o vacío."
                if (normalize(solution.text).isBlank()) errors += "Solución vacía."
                (listOf(solution.text) + solution.aliases).forEach { alias ->
                    val key = normalize(alias)
                    if (key.isBlank()) errors += "Alias vacío."
                    else {
                        val owner = names.putIfAbsent(key, solution.id)
                        if (owner != null && owner != solution.id)
                            errors += "Alias ambiguo entre dos soluciones: $alias"
                    }
                }
                solution.points.forEach { point ->
                    if (point.id.isBlank() || !markerIds.add(point.id))
                        errors += "Marcador duplicado o sin ID."
                    if (!valid01(point.x) || !valid01(point.y))
                        errors += "Marcador fuera de la imagen."
                    if (point.order < 1 || !orders.add(point.order))
                        errors += "El orden de los marcadores debe ser único y positivo."
                    if (exercise.baseImageUri == null && exercise.diagram == null)
                        errors += "Un marcador requiere imagen o esquema base."
                }
            }
        } else {
            if (exercise.solutions.isNotEmpty()) errors += "Una tarjeta no incluye lista de soluciones múltiples."
            val card = exercise.flashcard
            if (card == null) errors += "Faltan los datos de la tarjeta."
            else {
                if (card.front.isBlank() && card.frontImageUri.isNullOrBlank())
                    errors += "Falta pregunta o imagen."
                if (card.back.isBlank() && card.backImageUri.isNullOrBlank() &&
                    card.kind !in setOf(FlashcardKind.CLOZE, FlashcardKind.IMAGE_OCCLUSION))
                    errors += "Falta respuesta o imagen."
                val ids = hashSetOf<String>()
                var previousEnd = -1
                card.gaps.sortedBy { it.start }.forEach { gap ->
                    if (gap.id.isBlank() || !ids.add(gap.id)) errors += "ID de hueco duplicado."
                    if (gap.start < 0 || gap.end > card.front.length ||
                        gap.start >= gap.end) errors += "Hueco fuera del texto."
                    else {
                        if (card.front.substring(gap.start, gap.end).isBlank())
                            errors += "Hueco sin contenido."
                        if (gap.start < previousEnd) errors += "Huecos superpuestos."
                        previousEnd = gap.end
                    }
                }
                if (card.kind == FlashcardKind.CLOZE && card.gaps.isEmpty())
                    errors += "La tarjeta de huecos no tiene huecos."
                if (card.kind == FlashcardKind.IMAGE_OCCLUSION &&
                    (card.frontImageUri.isNullOrBlank() || card.masks.isEmpty()))
                    errors += "La ocultación de imagen requiere imagen y máscaras."
                val maskIds = hashSetOf<String>()
                card.masks.forEach { mask ->
                    if (mask.id.isBlank() || !maskIds.add(mask.id))
                        errors += "Máscara duplicada."
                    if (!valid01(mask.x) || !valid01(mask.y) ||
                        !mask.width.isFinite() || !mask.height.isFinite() ||
                        mask.width <= 0.0 || mask.height <= 0.0 ||
                        mask.x + mask.width > 1.0 || mask.y + mask.height > 1.0)
                        errors += "Máscara fuera de la imagen."
                }
                if (card.kind == FlashcardKind.TYPE_ANSWER) {
                    val accepted = (listOf(card.back) + card.acceptedAnswers).map(::normalize)
                    if (accepted.any { it.isBlank() }) errors += "Respuesta escrita vacía."
                }
            }
        }
        exercise.diagram?.let { diagram ->
            if (!valid01(diagram.startX) || !valid01(diagram.startY) ||
                !valid01(diagram.endX) || !valid01(diagram.endY) ||
                (diagram.startX == diagram.endX && diagram.startY == diagram.endY))
                errors += "Esquema lineal fuera del lienzo o de longitud cero."
        }
        return errors.distinct()
    }

    fun requireValid(exercise: StudyExercise) {
        val errors = errors(exercise)
        require(errors.isEmpty()) { errors.joinToString(" ") }
    }

    data class WrittenResult(
        val correct: Int,
        val incorrect: Int,
        val omitted: Int,
        val correctIds: Set<String>,
        val incorrectInputs: List<String>,
        val percent: Double,
        val scoreOutOfTen: Double,
        val recallPercent: Double
    )

    /**
     * The raw submitted SET is scored once. Changing text before delivery has no
     * cost; duplicate aliases of an already accepted solution have no penalty.
     */
    fun gradeWritten(
        exercise: StudyExercise,
        submitted: List<String>,
        pointsCorrect: Double = 1.0,
        penaltyWrong: Double = -0.25
    ): WrittenResult {
        require(exercise.kind == StudyExerciseKind.WRITTEN_MULTI) { "Tipo de ejercicio incorrecto." }
        requireValid(exercise)
        require(pointsCorrect.isFinite() && pointsCorrect > 0) { "Acierto no válido." }
        require(penaltyWrong.isFinite() && penaltyWrong <= 0) { "Penalización no válida." }
        val aliases = buildMap<String, String> {
            exercise.solutions.forEach { solution ->
                (listOf(solution.text) + solution.aliases).forEach { put(normalize(it), solution.id) }
            }
        }
        val correctIds = linkedSetOf<String>()
        val invalid = linkedMapOf<String, String>()
        submitted.forEach { input ->
            val key = normalize(input)
            if (key.isEmpty()) return@forEach
            val owner = aliases[key]
            if (owner == null) invalid.putIfAbsent(key, input.trim())
            else correctIds.add(owner)
        }
        val count = exercise.solutions.size
        // Avoid 0 * Infinity => NaN for finite but extremely unbalanced baremos.
        val correctFraction = correctIds.size.toDouble() / count.toDouble()
        val penaltyFraction = if (invalid.isEmpty() || penaltyWrong == 0.0) 0.0
            else (invalid.size.toDouble() / count.toDouble()) * (penaltyWrong / pointsCorrect)
        val normalized = correctFraction + penaltyFraction
        val score = normalized.coerceIn(0.0, 1.0)
        return WrittenResult(
            correct = correctIds.size,
            incorrect = invalid.size,
            omitted = count - correctIds.size,
            correctIds = correctIds,
            incorrectInputs = invalid.values.toList(),
            percent = score * 100.0,
            scoreOutOfTen = score * 10.0,
            recallPercent = 100.0 * correctIds.size / count
        )
    }
}
