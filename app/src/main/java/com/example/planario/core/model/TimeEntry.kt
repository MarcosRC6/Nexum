package com.example.planario.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class TimeEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: LocalDate,
    val minutes: Int,
    val type: TimeEntryType,
    val label: String = "Estudio",
    val createdAt: LocalDateTime = LocalDateTime.now()
)