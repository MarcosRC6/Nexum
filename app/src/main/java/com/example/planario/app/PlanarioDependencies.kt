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
import com.example.planario.domain.timer.FinishedStudyTimerSession
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
                completeStagedStudyTimer(recoveredPreviousDay)
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

    /**
     * Single completion path for UI, notification, service and midnight
     * recovery. Normal operation remains one tap; on a write failure the
     * original timer + pending journal remain available for a safe retry.
     */
    @Synchronized
    fun finishStudyTimerAndSave() {
        if (!::appContext.isInitialized) return
        if (studyRepository.recoveryError.value != null) {
            studyTimerController.persistCurrentStudyTimerNow()
            return
        }
        val staged = studyTimerController.prepareStudyTimerCompletion() ?: return
        completeStagedStudyTimer(staged)
    }

    @Synchronized
    fun completeStagedStudyTimer(session: FinishedStudyTimerSession): Boolean {
        if (!::appContext.isInitialized || studyRepository.recoveryError.value != null) return false
        // The journal ID becomes TimeEntry.id in a synchronous Planario
        // SharedPreferences.commit(). Retrying after a crash is idempotent.
        if (!studyRepository.recordTimerSessionOnce(session)) return false
        // Clear the timer snapshot AND its journal together only after the
        // committed entry exists on disk; an ack failure is safe to replay.
        if (!studyTimerController.acknowledgeSavedStudyTimerSession(session)) return false
        studyRepository.clearActiveStudyTimerTracking()
        NexumStudyClockWidgetReceiver.updateAllWidgets(appContext)
        stopStudyTimerForegroundService()
        return true
    }

    @Synchronized
    fun recoverPendingStudyTimerIfNeeded() {
        if (!::appContext.isInitialized || studyRepository.recoveryError.value != null) return
        studyTimerController.recoverPendingStudyTimerIfNeeded()
            ?.let(::completeStagedStudyTimer)
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