package com.example.planario.core.model

import java.time.LocalDate

data class StudyDay(
    val date: LocalDate,
    val status: DayStatus = DayStatus.EMPTY,
    val isRestDay: Boolean = false,
    val forceStudyDay: Boolean = false,
    val isFinishedManually: Boolean = false,
    val studiedMinutes: Int = 0,
    val dailyGoalMinutes: Int = 360,
    val activeTopicIdSnapshot: String? = null,
    val activeTopicNameSnapshot: String? = null,
    val completedFixedObjectiveIds: Set<String> = emptySet()
)
