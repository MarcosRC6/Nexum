package com.example.planario.features.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.planario.app.PlanarioDependencies
import com.example.planario.data.repository.StudyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class PlanViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    val uiState = combine(
        repository.topics,
        repository.fixedObjectives
    ) { topics, fixedObjectives ->
        PlanUiState(
            isLoading = false,
            topics = topics
                .filter { !it.isArchived }
                .sortedBy { it.queuePosition },
            fixedObjectives = fixedObjectives.sortedBy { it.order }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PlanUiState()
    )

    fun addTopic(name: String) {
        repository.addTopic(name)
    }

    fun updateTopicName(topicId: String, newName: String) {
        repository.updateTopicName(topicId, newName)
    }

    fun archiveTopic(topicId: String) {
        repository.archiveTopic(topicId)
    }

    fun activateTopic(topicId: String) {
        repository.activateTopic(topicId)
    }

    fun moveTopicUp(topicId: String) {
        repository.moveTopicUp(topicId)
    }

    fun moveTopicDown(topicId: String) {
        repository.moveTopicDown(topicId)
    }

    fun completeActiveTopic() {
        repository.completeActiveTopic()
    }

    fun addFixedObjective(
        name: String,
        targetMinutes: Int
    ) {
        repository.addFixedObjective(
            name = name,
            targetMinutes = targetMinutes
        )
    }

    fun updateFixedObjective(
        objectiveId: String,
        name: String,
        targetMinutes: Int
    ) {
        repository.updateFixedObjective(
            objectiveId = objectiveId,
            name = name,
            targetMinutes = targetMinutes
        )
    }

    fun setFixedObjectiveEnabled(
        objectiveId: String,
        enabled: Boolean
    ) {
        repository.setFixedObjectiveEnabled(
            objectiveId = objectiveId,
            enabled = enabled
        )
    }

    fun deleteFixedObjective(objectiveId: String) {
        repository.deleteFixedObjective(objectiveId)
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlanViewModel(
                        repository = PlanarioDependencies.studyRepository
                    ) as T
                }
            }
        }
    }
}