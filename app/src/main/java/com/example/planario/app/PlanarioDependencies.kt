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
import com.example.planario.domain.timer.PomodoroDurationPolicy
import com.example.planario.app.widget.NexumStudyClockWidgetReceiver
import java.time.LocalDate

object PlanarioDependencies {

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (::appContext.isInitialized) return

        appContext = context.applicationContext
        studyTimerController.attachContext(appContext)
        // If Planario's database cannot be parsed, DO NOT consume or clear
        // any saved active-timer snapshot: addTimerTime() cannot durably save
        // it while the planning store is locked for recovery. Keep the timer
        // bytes intact until a validated backup is restored and app restarted.
        if (studyRepository.recoveryError.value != null) {
            Log.w("NEXUM_Planario", "Planning recovery lock active; preserving timer snapshots")
            return
        }
        synchronizeConfiguredPomodoroDuration()

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

    /** También se usa al iniciar el Pomodoro desde el widget, sin entrar en Plan. */
    fun synchronizeConfiguredPomodoroDuration() {
        if (!::appContext.isInitialized) return
        val configured = studyRepository.settings.value.lastPomodoroMinutes
        val state = studyTimerController.state.value
        if (PomodoroDurationPolicy.shouldApplyConfiguredMinutes(
                state.pomodoroTimerState, state.lastPomodoroMinutes, configured)) {
            studyTimerController.setPomodoroDuration(configured)
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
        // Never consume a study timer when the target planning database is
        // locked or unwritable. Preserve the timer snapshot for recovery.
        if (studyRepository.recoveryError.value != null) {
            studyTimerController.persistCurrentStudyTimerNow()
            return
        }
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