package com.example.planario.features.plan

import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyTopic

data class PlanUiState(
    val isLoading: Boolean = true,
    val topics: List<StudyTopic> = emptyList(),
    val fixedObjectives: List<FixedObjective> = emptyList()
) {
    val activeTopic: StudyTopic?
        get() = topics.firstOrNull()

    val upcomingTopics: List<StudyTopic>
        get() = topics.drop(1)

    val hasTopics: Boolean
        get() = topics.isNotEmpty()

    val enabledFixedObjectives: List<FixedObjective>
        get() = fixedObjectives.filter { it.isEnabled }

    val disabledFixedObjectives: List<FixedObjective>
        get() = fixedObjectives.filter { !it.isEnabled }
}