package com.example.planario.domain.timer

/**
 * A SharedFlow with replay=0 discards emissions when there are no subscribers.
 * Never erase an active timer's on-disk snapshot before a consumer exists to
 * record its minutes in Planario.
 */
internal object TimerAutoClosePolicy {
    fun preserveForRecovery(minutes: Int, subscriberCount: Int): Boolean =
        minutes > 0 && subscriberCount <= 0
}
