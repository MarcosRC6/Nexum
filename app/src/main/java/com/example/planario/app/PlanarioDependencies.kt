package com.example.planario.app

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import android.util.Log
import com.example.planario.app.notifications.PlanarioNotificationScheduler
import com.example.planario.data.local.StudyLocalDataSource
import com.example.planario.data.repository.StudyRepository
import com.example.planario.domain.timer.StudyTimerController
import com.example.planario.domain.timer.StudyTimerForegroundService
import com.example.planario.domain.timer.StudyTimerState
import com.example.planario.domain.timer.StudyForegroundPolicy
import com.example.planario.domain.timer.PomodoroTimerState
import com.example.planario.app.widget.NexumStudyClockWidgetReceiver
import java.time.LocalDate

object PlanarioDependencies {

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (::appContext.isInitialized) return

        appContext = context.applicationContext
        studyTimerController.attachContext(appContext)

        val trackedSnapshot = studyRepository.getActiveStudyTimerSnapshot()
        val recoveredPreviousDay = studyTimerController.recoverPendingStudyTimerIfNeeded()

        when {
            recoveredPreviousDay != null -> {
                studyRepository.clearActiveStudyTimerTracking()
                if (recoveredPreviousDay.minutes > 0) {
                    studyRepository.addTimerTime(
                        minutes = recoveredPreviousDay.minutes,
                        date = recoveredPreviousDay.date
                    )
                }
            }

            studyTimerController.state.value.studyTimerState == StudyTimerState.PAUSED -> {
                studyRepository.restorePausedActiveStudyTimerTracking(
                    date = LocalDate.now(),
                    elapsedSeconds = studyTimerController.state.value.studyElapsedSeconds
                )
            }

            studyTimerController.state.value.studyTimerState == StudyTimerState.STOPPED &&
                trackedSnapshot.isActive &&
                trackedSnapshot.date != null -> {
                // Fallback defensivo para instalaciones antiguas en las que existiera
                // el snapshot del widget pero no el estado del controlador.
                val elapsedSeconds = trackedSnapshot.elapsedSeconds()
                studyTimerController.restorePausedStudyTimer(
                    date = trackedSnapshot.date,
                    elapsedSeconds = elapsedSeconds
                )
                studyRepository.restorePausedActiveStudyTimerTracking(
                    date = trackedSnapshot.date,
                    elapsedSeconds = elapsedSeconds
                )
            }
        }
    }

    val studyRepository: StudyRepository by lazy {
        StudyRepository(
            localDataSource = StudyLocalDataSource(appContext)
        )
    }

    val studyTimerController: StudyTimerController by lazy {
        StudyTimerController()
    }

    private val planarioNotificationScheduler: PlanarioNotificationScheduler by lazy {
        PlanarioNotificationScheduler(appContext)
    }

    fun startStudyTimerForegroundService() {
        val intent = Intent(
            appContext,
            StudyTimerForegroundService::class.java
        ).apply {
            action = StudyTimerForegroundService.ACTION_START
        }

        runCatching {
            ContextCompat.startForegroundService(appContext, intent)
        }.onFailure { error ->
            Log.w("NEXUM_Timer", "No se pudo iniciar el servicio del cronómetro", error)
        }
    }

    fun stopStudyTimerForegroundService() {
        // El mismo servicio sostiene el Pomodoro si el cronómetro de estudio terminó.
        val state = studyTimerController.state.value
        if (StudyForegroundPolicy.shouldKeepRunning(
                state.studyTimerState, state.pomodoroTimerState)) return
        val intent = Intent(
            appContext,
            StudyTimerForegroundService::class.java
        )

        runCatching {
            appContext.stopService(intent)
        }
    }

    fun finishStudyTimerAndSave() {
        if (!::appContext.isInitialized) return
        if (studyTimerController.state.value.studyTimerState == StudyTimerState.STOPPED) return

        val finished = studyTimerController.finishStudyTimerAndReturnSession()
        studyRepository.clearActiveStudyTimerTracking()
        if (finished.minutes > 0) {
            studyRepository.addTimerTime(minutes = finished.minutes, date = finished.date)
        }
        NexumStudyClockWidgetReceiver.updateAllWidgets(appContext)
        stopStudyTimerForegroundService()
    }

    fun refreshStudyClockWidgets() {
        if (::appContext.isInitialized) {
            NexumStudyClockWidgetReceiver.updateAllWidgets(appContext)
        }
    }

    fun schedulePlanarioNotifications() {
        if (!::appContext.isInitialized) return

        planarioNotificationScheduler.scheduleAll(
            settings = studyRepository.settings.value
        )
    }

    fun cancelPlanarioScheduledNotifications() {
        if (!::appContext.isInitialized) return

        planarioNotificationScheduler.cancelAll()
    }

}