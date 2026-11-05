package com.example.planario.core.model

import java.util.UUID

data class StudyTopic(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val queuePosition: Int = 0,
    val round: Int = 1,
    val isArchived: Boolean = false
)