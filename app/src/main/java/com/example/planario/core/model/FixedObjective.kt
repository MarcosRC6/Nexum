package com.example.planario.core.model

import java.util.UUID

data class FixedObjective(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetMinutes: Int,
    val isEnabled: Boolean = true,
    val order: Int = 0,
    // Bank IDs are stable across app restarts and included in Planario backups.
    val linkedTestTopicIds: Set<String> = emptySet(),
    val linkedTestQuestionCount: Int = 25
)