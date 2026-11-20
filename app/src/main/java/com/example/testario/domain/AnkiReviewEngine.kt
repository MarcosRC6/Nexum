package com.example.testario.domain

import com.example.testario.data.model.*
import kotlin.math.roundToLong

/**
 * Simple deterministic scheduler, NOT FSRS. Used only when the user explicitly
 * chooses a scheduled review; Exams and ordinary training never call it.
 */
object AnkiReviewEngine {
    const val VERSION = 1

    fun validate(settings: AnkiScheduleSettings) {
        require(settings.againMinutes > 0 && settings.hardMinutes > 0 &&
            settings.graduatingMinutes > 0 && settings.easyMinutes > 0)
        require(settings.maxIntervalMinutes >= settings.easyMinutes)
        require(settings.goodMultiplier.isFinite() && settings.goodMultiplier >= 1.0)
        require(settings.hardMultiplier.isFinite() && settings.hardMultiplier >= 1.0)
        require(settings.easyMultiplier.isFinite() && settings.easyMultiplier >= 1.0)
        require(settings.newCardsPerDay in 0..10000 && settings.reviewsPerDay in 0..100000)
    }

    fun next(
        old: AnkiReviewState,
        rating: ReviewRating,
        nowMillis: Long,
        settings: AnkiScheduleSettings = AnkiScheduleSettings()
    ): AnkiReviewState {
        validate(settings)
        require(nowMillis >= 0) { "Fecha de repaso inválida." }
        require(!old.suspended) { "La tarjeta está suspendida." }
        val current = old.intervalMinutes.coerceAtLeast(0)
        val minutes = when (rating) {
            ReviewRating.AGAIN -> settings.againMinutes
            ReviewRating.HARD -> if (current == 0L) settings.hardMinutes
                else multiply(current, settings.hardMultiplier, settings.maxIntervalMinutes)
            ReviewRating.GOOD -> if (current == 0L) settings.graduatingMinutes
                else multiply(current, settings.goodMultiplier, settings.maxIntervalMinutes)
            ReviewRating.EASY -> if (current == 0L) settings.easyMinutes
                else multiply(current, settings.easyMultiplier, settings.maxIntervalMinutes)
        }.coerceIn(1L, settings.maxIntervalMinutes)
        val millis = if (minutes > (Long.MAX_VALUE - nowMillis) / 60000L)
            Long.MAX_VALUE else nowMillis + minutes * 60000L
        return old.copy(
            intervalMinutes = minutes,
            dueAtMillis = millis,
            repetitions = if (rating == ReviewRating.AGAIN) 0
                else if (old.repetitions == Int.MAX_VALUE) Int.MAX_VALUE
                else old.repetitions + 1
        )
    }

    private fun multiply(current: Long, factor: Double, ceiling: Long): Long =
        (current.toDouble() * factor).coerceAtMost(ceiling.toDouble())
            .coerceAtLeast(1.0).roundToLong()

    data class UpdatedReview(
        val states: List<AnkiReviewState>,
        val history: List<AnkiReviewEvent>,
        val applied: Boolean
    )

    /** Idempotent by event ID. Persist states and history in ONE atomic DB save. */
    fun apply(
        states: List<AnkiReviewState>,
        history: List<AnkiReviewEvent>,
        eventId: String,
        cardId: String,
        rating: ReviewRating,
        timestampMillis: Long,
        settings: AnkiScheduleSettings
    ): UpdatedReview {
        require(eventId.isNotBlank() && cardId.isNotBlank())
        if (history.any { it.id == eventId }) return UpdatedReview(states, history, false)
        val prior = states.find { it.cardId == cardId } ?: AnkiReviewState(cardId)
        val after = next(prior, rating, timestampMillis, settings)
        val newStates = states.filterNot { it.cardId == cardId } + after
        val event = AnkiReviewEvent(
            id = eventId, cardId = cardId, rating = rating,
            timestampMillis = timestampMillis,
            nextDueAtMillis = after.dueAtMillis, intervalMinutes = after.intervalMinutes
        )
        return UpdatedReview(newStates, history + event, true)
    }
}
