package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AnkiReviewEngineTest {
    @Test fun startsWithoutSchedulingUntilExplicitReview() {
        val state = AnkiReviewState(cardId = "card")
        assertEquals(0L, state.intervalMinutes)
        assertEquals(0, state.repetitions)
        val reviewed = AnkiReviewEngine.next(
            state, ReviewRating.GOOD, nowMillis = 1_000_000L
        )
        assertEquals(1440L, reviewed.intervalMinutes)
        assertEquals(1_000_000L + 1440L * 60000L, reviewed.dueAtMillis)
        assertEquals(1, reviewed.repetitions)
    }

    @Test fun appliesSameEventIdOnlyOnce() {
        val settings = AnkiScheduleSettings()
        val first = AnkiReviewEngine.apply(emptyList(), emptyList(),
            "review-event", "card", ReviewRating.GOOD, 500L, settings)
        val second = AnkiReviewEngine.apply(first.states, first.history,
            "review-event", "card", ReviewRating.GOOD, 500L, settings)
        assertTrue(first.applied)
        assertFalse(second.applied)
        assertEquals(first.states, second.states)
        assertEquals(first.history, second.history)
        assertEquals(1, second.history.size)
    }

    @Test fun againResetsRepetitionsWithoutLosingCardIdentity() {
        val settings = AnkiScheduleSettings(againMinutes = 3)
        val current = AnkiReviewState("card", repetitions = 5, intervalMinutes = 4000L)
        val next = AnkiReviewEngine.next(current, ReviewRating.AGAIN, 10_000L, settings)
        assertEquals("card", next.cardId)
        assertEquals(0, next.repetitions)
        assertEquals(3, next.intervalMinutes.toInt())
        assertEquals(10_000L + 180_000L, next.dueAtMillis)
    }

    @Test fun rejectsNonFiniteSettingsAndSuspendedCard() {
        assertThrows(IllegalArgumentException::class.java) {
            AnkiReviewEngine.validate(AnkiScheduleSettings(goodMultiplier = Double.NaN))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnkiReviewEngine.next(AnkiReviewState("card", suspended = true),
                ReviewRating.EASY, 1000)
        }
    }

    @Test fun overflowCannotWrapDueDate() {
        val reviewed = AnkiReviewEngine.next(AnkiReviewState("card"),
            ReviewRating.GOOD, Long.MAX_VALUE - 200)
        assertEquals(Long.MAX_VALUE, reviewed.dueAtMillis)
    }
}
