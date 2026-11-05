package com.example.planario.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.planario.app.notifications.PlanarioNotificationScheduler
import com.example.planario.data.local.StudyLocalDataSource
import com.example.planario.data.repository.StudyRepository
import com.example.planario.domain.timer.StudyTimerController
import com.example.planario.domain.timer.StudyTimerForegroundService

object PlanarioDependencies {

    private lateinit var appContext: Context

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
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
        if (!canUseTimerNotification()) return

        val intent = Intent(
            appContext,
            StudyTimerForegroundService::class.java
        ).apply {
            action = StudyTimerForegroundService.ACTION_START
        }

        runCatching {
            ContextCompat.startForegroundService(
                appContext,
                intent
            )
        }
    }

    fun stopStudyTimerForegroundService() {
        val intent = Intent(
            appContext,
            StudyTimerForegroundService::class.java
        )

        runCatching {
            appContext.stopService(intent)
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

    private fun canUseTimerNotification(): Boolean {
        if (!::appContext.isInitialized) return false

        val notificationsEnabled = studyRepository.settings.value.notificationsEnabled
        if (!notificationsEnabled) return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}