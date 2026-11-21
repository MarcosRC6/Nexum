package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MixedSessionClockTest {
    private fun session(index: Int = 0) = MixedSessionSnapshot(
        id = "session-1",
        mode = TestMode.EXAM,
        contentFilter = SessionContentFilter.TEST,
        items = listOf(
            MixedSessionItem(id = "a", topicId = "topic", topicName = "Tema", type = MixedItemType.TEST),
            MixedSessionItem(id = "b", topicId = "topic", topicName = "Tema", type = MixedItemType.TEST)
        ),
        responses = listOf(MixedSessionResponse("a"), MixedSessionResponse("b")),
        currentIndex = index,
        remainingSeconds = 60
    )

    @Test fun delayedCoroutineTickAccountsForAllElapsedSeconds() {
        val after = MixedSessionClock.advance(session(), 7L, 53)
        assertEquals(7L, after.elapsedSeconds)
        assertEquals(7L, after.responses[0].timeSpentSeconds)
        assertEquals(0L, after.responses[1].timeSpentSeconds)
        assertEquals(53, after.remainingSeconds)
    }

    @Test fun repeatedTickDoesNotDoubleCount() {
        val first = MixedSessionClock.advance(session(), 5L, 55)
        val repeated = MixedSessionClock.advance(first, 5L, 55)
        assertEquals(5L, repeated.responses[0].timeSpentSeconds)
        assertEquals(5L, repeated.elapsedSeconds)
    }

    @Test fun navigationChargesOnlyNewCurrentQuestion() {
        val first = MixedSessionClock.advance(session(), 3L, 57)
        val second = MixedSessionClock.advance(first.copy(currentIndex = 1), 8L, 52)
        assertEquals(3L, second.responses[0].timeSpentSeconds)
        assertEquals(5L, second.responses[1].timeSpentSeconds)
    }

    @Test fun clockDoesNotGoBackwards() {
        val first = MixedSessionClock.advance(session(), 9L, 51)
        val after = MixedSessionClock.advance(first, 7L, 51)
        assertEquals(9L, after.elapsedSeconds)
        assertEquals(9L, after.responses[0].timeSpentSeconds)
    }
}
