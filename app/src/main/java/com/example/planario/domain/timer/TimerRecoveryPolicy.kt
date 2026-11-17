package com.example.planario.domain.timer

import kotlin.math.abs

/** Conservative recovery after an unexpected process death; never spans a reboot. */
internal object TimerRecoveryPolicy {
    fun safeRunningSeconds(
        savedSeconds: Long,
        lastWallMillis: Long,
        lastUptimeMillis: Long,
        nowWallMillis: Long,
        nowUptimeMillis: Long,
        endOfDayWallMillis: Long? = null
    ): Long? {
        if (lastWallMillis <= 0L || lastUptimeMillis <= 0L) return null
        val uptimeDelta = nowUptimeMillis - lastUptimeMillis
        val wallDelta = nowWallMillis - lastWallMillis
        if (uptimeDelta < 0L || wallDelta < 0L ||
            uptimeDelta > 48L * 60L * 60L * 1000L ||
            abs(uptimeDelta - wallDelta) > 60_000L) return null
        val cappedDelta = if (endOfDayWallMillis == null) uptimeDelta
            else minOf(uptimeDelta, (endOfDayWallMillis - lastWallMillis).coerceAtLeast(0L))
        return savedSeconds.coerceAtLeast(0L) + cappedDelta / 1000L
    }
}
