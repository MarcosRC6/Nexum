package com.example.testario.domain

import com.example.testario.data.model.MixedSessionSnapshot

/**
 * Advances a saved exam by actual elapsed monotonic seconds, not by one second
 * per coroutine wake-up. Android can defer coroutine ticks in the background.
 * Only the active response receives time, and repeated wake-ups are idempotent.
 */
object MixedSessionClock {
    fun advance(
        snapshot: MixedSessionSnapshot,
        elapsedSeconds: Long,
        remainingSeconds: Int?
    ): MixedSessionSnapshot {
        val advancedElapsed = elapsedSeconds.coerceAtLeast(snapshot.elapsedSeconds)
        val delta = advancedElapsed - snapshot.elapsedSeconds
        return snapshot.copy(
            elapsedSeconds = advancedElapsed,
            remainingSeconds = remainingSeconds?.coerceAtLeast(0),
            responses = snapshot.responses.mapIndexed { index, response ->
                if (index == snapshot.currentIndex) {
                    response.copy(timeSpentSeconds = response.timeSpentSeconds + delta)
                } else response
            }
        )
    }
}
