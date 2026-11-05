package com.example.planario.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.planario.app.PlanarioDependencies
import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyTopic
import com.example.planario.data.repository.StudyRepository
import com.example.planario.domain.timer.StudyTimerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class HomeViewModel(
    private val repository: StudyRepository,
    private val studyTimerController: StudyTimerController
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val baseState = combine(
        selectedDate,
        repository.activeTopic,
        repository.enabledFixedObjectives,
        repository.studyDays,
        repository.settings
    ) { date, activeTopic, fixedObjectives, _, _ ->

        val day = repository.getEffectiveStudyDay(date)
        val isFreeDay = repository.isFreeDay(date)

        HomeBaseState(
            date = date,
            activeTopic = activeTopic,
            fixedObjectives = fixedObjectives,
            completedFixedObjectiveIds = day.completedFixedObjectiveIds,
            studiedMinutes = day.studiedMinutes,
            dailyGoalMinutes = day.dailyGoalMinutes,
            dayStatus = day.status,
            isRestDay = day.isRestDay,
            isFreeDay = isFreeDay,
            isFinishedManually = day.isFinishedManually
        )
    }

    val uiState = combine(
        baseState,
        studyTimerController.state
    ) { base, timer ->

        HomeUiState(
            isLoading = false,
            dateText = base.date.toSpanishDateText(),
            activeTopic = base.activeTopic,
            fixedObjectives = base.fixedObjectives,
            completedFixedObjectiveIds = base.completedFixedObjectiveIds,
            studiedMinutes = base.studiedMinutes,
            dailyGoalMinutes = base.dailyGoalMinutes,
            studyElapsedSeconds = timer.studyElapsedSeconds,
            studyTimerState = timer.studyTimerState,
            dayStatus = base.dayStatus,
            isRestDay = base.isRestDay,
            isFreeDay = base.isFreeDay,
            isFinishedManually = base.isFinishedManually
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun completeActiveTopic() {
        repository.completeActiveTopic()
    }

    fun cancelTodayFreeDay() {
        repository.cancelFreeDay(
            date = LocalDate.now()
        )
    }

    fun markTodayFreeDay() {
        studyTimerController.discardStudyTimer()
        repository.clearActiveStudyTimerTracking()
        PlanarioDependencies.stopStudyTimerForegroundService()
        repository.setRestDay(
            date = LocalDate.now(),
            isRestDay = true
        )
    }

    fun addManualTime(
        hours: Int,
        minutes: Int
    ) {
        val totalMinutes = (hours * 60) + minutes

        repository.addManualTime(
            minutes = totalMinutes,
            date = LocalDate.now()
        )
    }

    fun startStudyFromHome() {
        studyTimerController.startStudyTimer()
        repository.startActiveStudyTimerTracking(LocalDate.now())
        PlanarioDependencies.startStudyTimerForegroundService()
    }

    fun restartStudyFromHome(saveCurrentTime: Boolean) {
        if (saveCurrentTime) {
            val session = studyTimerController.finishStudyTimerAndReturnSession()
            repository.clearActiveStudyTimerTracking()

            if (session.minutes > 0) {
                repository.addTimerTime(
                    minutes = session.minutes,
                    date = session.date
                )
            }
        } else {
            studyTimerController.discardStudyTimer()
            repository.clearActiveStudyTimerTracking()
        }

        studyTimerController.startStudyTimer()
        repository.startActiveStudyTimerTracking(LocalDate.now())
        PlanarioDependencies.startStudyTimerForegroundService()
    }

    fun pauseStudyFromHome() {
        studyTimerController.pauseStudyTimer()
        repository.pauseActiveStudyTimerTracking()
    }

    fun resumeStudyFromHome() {
        studyTimerController.resumeStudyTimer()
        repository.resumeActiveStudyTimerTracking()
        PlanarioDependencies.startStudyTimerForegroundService()
    }

    fun stopStudyFromHome() {
        val session = studyTimerController.finishStudyTimerAndReturnSession()
        repository.clearActiveStudyTimerTracking()
        if (session.minutes > 0) {
            repository.addTimerTime(
                minutes = session.minutes,
                date = session.date
            )
        }
        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    fun finishToday() {
        val minutes = studyTimerController.finishStudyTimerAndReturnMinutes()

        repository.clearActiveStudyTimerTracking()

        if (minutes > 0) {
            repository.addTimerTime(
                minutes = minutes,
                date = LocalDate.now()
            )
        }

        repository.finishDay(LocalDate.now())
        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    fun resetToday() {
        studyTimerController.discardStudyTimer()
        repository.clearActiveStudyTimerTracking()
        repository.resetDay(LocalDate.now())
        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    fun toggleFixedObjectiveCompleted(objectiveId: String) {
        repository.toggleFixedObjectiveCompleted(
            objectiveId = objectiveId,
            date = LocalDate.now()
        )
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(
                        repository = PlanarioDependencies.studyRepository,
                        studyTimerController = PlanarioDependencies.studyTimerController
                    ) as T
                }
            }
        }
    }
}

private data class HomeBaseState(
    val date: LocalDate,
    val activeTopic: StudyTopic?,
    val fixedObjectives: List<FixedObjective>,
    val completedFixedObjectiveIds: Set<String>,
    val studiedMinutes: Int,
    val dailyGoalMinutes: Int,
    val dayStatus: DayStatus,
    val isRestDay: Boolean,
    val isFreeDay: Boolean,
    val isFinishedManually: Boolean
)

private fun LocalDate.toSpanishDateText(): String {
    val formatter = DateTimeFormatter.ofPattern(
        "EEEE, d 'de' MMMM",
        Locale("es", "ES")
    )

    return format(formatter).replaceFirstChar { firstChar ->
        if (firstChar.isLowerCase()) {
            firstChar.titlecase(Locale("es", "ES"))
        } else {
            firstChar.toString()
        }
    }
}