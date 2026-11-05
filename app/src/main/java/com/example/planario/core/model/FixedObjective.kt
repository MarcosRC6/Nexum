package com.example.planario.core.model

import java.util.UUID

data class FixedObjective(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetMinutes: Int,
    val isEnabled: Boolean = true,
    val order: Int = 0
)