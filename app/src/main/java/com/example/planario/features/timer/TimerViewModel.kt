package com.example.planario.features.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.planario.app.PlanarioDependencies
import com.example.planario.data.repository.StudyRepository
import com.example.planario.domain.timer.FinishedStudyTimerSession
import com.example.planario.domain.timer.StudyTimerController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

class TimerViewModel(
    private val repository: StudyRepository,
    private val studyTimerController: StudyTimerController
) : ViewModel() {

    init {
        studyTimerController.setPomodoroDuration(
            repository.settings.value.lastPomodoroMinutes
        )

        recoverPendingStudyTimerIfNeeded()
    }

    val uiState = combine(
        repository.activeTopic,
        repository.studyDays,
        repository.settings,
        studyTimerController.state
    ) { activeTopic, studyDays, settings, timer ->

        val today = LocalDate.now()
        val day = studyDays[today] ?: repository.getStudyDay(today)

        TimerUiState(
            isLoading = false,
            activeTopicName = activeTopic?.name,
            savedTodayMinutes = day.studiedMinutes,
            dailyGoalMinutes = settings.dailyGoalMinutes,
            studyElapsedSeconds = timer.studyElapsedSeconds,
            studyTimerState = timer.studyTimerState,
            pomodoroRemainingSeconds = timer.pomodoroRemainingSeconds,
            pomodoroTotalSeconds = timer.pomodoroTotalSeconds,
            pomodoroTimerState = timer.pomodoroTimerState,
            lastPomodoroMinutes = timer.lastPomodoroMinutes
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimerUiState()
    )

    fun startStudyTimer() {
        val today = LocalDate.now()

        studyTimerController.startStudyTimer(
            date = today
        )

        repository.startActiveStudyTimerTracking(today)
        PlanarioDependencies.startStudyTimerForegroundService()
    }

    fun pauseStudyTimer() {
        studyTimerController.pauseStudyTimer()
        repository.pauseActiveStudyTimerTracking()
    }

    fun resumeStudyTimer() {
        studyTimerController.resumeStudyTimer()
        repository.resumeActiveStudyTimerTracking()
        PlanarioDependencies.startStudyTimerForegroundService()
    }

    fun finishStudyTimer() {
        val finishedSession = studyTimerController.finishStudyTimerAndReturnSession()

        repository.clearActiveStudyTimerTracking()
        saveFinishedStudyTimerSession(finishedSession)

        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    fun discardStudyTimer() {
        studyTimerController.discardStudyTimer()
        repository.clearActiveStudyTimerTracking()
        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    fun setPomodoroDuration(minutes: Int) {
        repository.setLastPomodoroMinutes(minutes)
        studyTimerController.setPomodoroDuration(minutes)
    }

    fun startPomodoro() {
        studyTimerController.startPomodoro()
    }

    fun pausePomodoro() {
        studyTimerController.pausePomodoro()
    }

    fun resumePomodoro() {
        studyTimerController.resumePomodoro()
    }

    fun resetPomodoro() {
        studyTimerController.resetPomodoro()
    }

    private fun recoverPendingStudyTimerIfNeeded() {
        val recoveredSession = studyTimerController.recoverPendingStudyTimerIfNeeded()
            ?: return

        repository.clearActiveStudyTimerTracking()
        saveFinishedStudyTimerSession(recoveredSession)
        PlanarioDependencies.stopStudyTimerForegroundService()
    }

    private fun saveFinishedStudyTimerSession(
        session: FinishedStudyTimerSession
    ) {
        if (session.minutes <= 0) return

        repository.addTimerTime(
            minutes = session.minutes,
            date = session.date
        )
    }

    companion object {
        fun factory(): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimerViewModel(
                        repository = PlanarioDependencies.studyRepository,
                        studyTimerController = PlanarioDependencies.studyTimerController
                    ) as T
                }
            }
        }
    }
}
