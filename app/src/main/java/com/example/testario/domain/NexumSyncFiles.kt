package com.example.testario.domain

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The filename allows users to identify the winning device BEFORE downloading
 * a potentially large complete backup from a shared SAF/cloud folder.
 */
object NexumSyncFiles {
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT)
    private val pattern = Regex("^NEXUM_SINCRONIZACION_COMPLETO_(\\d{8}_\\d{6})_([a-f0-9]{8})_([A-Za-z0-9_-]{1,35})\\.zip$")

    data class Identity(
        val createdAt: Long,
        val deviceId: String,
        val deviceName: String
    )

    fun name(deviceId: String, deviceName: String, at: LocalDateTime): String {
        require(Regex("[a-f0-9]{8}").matches(deviceId))
        val safeName = deviceName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_")
            .trim('_').take(35).ifBlank { "Android" }
        return "NEXUM_SINCRONIZACION_COMPLETO_${at.format(utc)}_${deviceId}_${safeName}.zip"
    }

    fun parse(filename: String): Identity? {
        val m = pattern.matchEntire(filename) ?: return null
        val at = runCatching { LocalDateTime.parse(m.groupValues[1], utc) }.getOrNull() ?: return null
        return Identity(at.toEpochSecond(ZoneOffset.UTC) * 1000, m.groupValues[2], m.groupValues[3].replace('_', ' '))
    }
}
