package com.example.planario.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.testario.MainActivity
import com.example.testario.R
import com.example.planario.app.PlanarioDependencies
import com.example.planario.core.model.StudySettings
import com.example.planario.data.repository.StudyRepository
import java.time.LocalDate

class PlanarioNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent?
    ) {
        PlanarioDependencies.init(context.applicationContext)

        if (intent.isSystemRescheduleAction()) {
            PlanarioDependencies.schedulePlanarioNotifications()
            return
        }

        if (intent?.action != ACTION_SCHEDULED_NOTIFICATION) return

        val type = intent.getStringExtra(EXTRA_TYPE) ?: return

        val repository = PlanarioDependencies.studyRepository
        val settings = repository.settings.value

        // Cada alarma reprograma únicamente su siguiente ejecución. Reprogramar
        // todas aquí podía cancelar otra notificación prevista para la misma hora.
        PlanarioNotificationScheduler(context.applicationContext)
            .scheduleNextForType(type, settings)

        if (!settings.isTypeEnabled(type)) return
        if (!canPostNotifications(context)) return

        createNotificationChannel(context)

        when (type) {
            TYPE_STUDY_REMINDER -> showStudyReminder(
                context = context,
                repository = repository
            )

            TYPE_FIXED_OBJECTIVES -> showFixedObjectivesReminder(
                context = context,
                repository = repository
            )

            TYPE_REST_DAY -> showRestDayNotification(
                context = context,
                repository = repository
            )

            TYPE_DAILY_SUMMARY -> showDailySummary(
                context = context,
                repository = repository
            )
        }
    }

    private fun showStudyReminder(
        context: Context,
        repository: StudyRepository
    ) {
        val today = LocalDate.now()

        if (repository.isFreeDay(today)) return

        val activeTopicName = repository.getActiveTopicNameForNotification()
        val dailyGoalText = repository.settings.value.dailyGoalMinutes.toHourMinuteText()

        val content = if (activeTopicName == null) {
            "Hoy tienes un objetivo diario de $dailyGoalText."
        } else {
            "Hoy toca estudiar: $activeTopicName · Objetivo: $dailyGoalText."
        }

        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_STUDY_REMINDER,
            title = "Recordatorio de estudio",
            content = content
        )
    }

    private fun showFixedObjectivesReminder(
        context: Context,
        repository: StudyRepository
    ) {
        val today = LocalDate.now()

        if (repository.isFreeDay(today)) return

        val todayDay = repository.getEffectiveStudyDay(today)

        val enabledObjectives = repository.fixedObjectives.value
            .filter { it.isEnabled }
            .sortedBy { it.order }

        if (enabledObjectives.isEmpty()) return

        val pendingObjectives = enabledObjectives
            .filterNot { objective ->
                todayDay.completedFixedObjectiveIds.contains(objective.id)
            }

        if (pendingObjectives.isEmpty()) return

        val pendingText = pendingObjectives
            .joinToString(separator = ", ") { it.name }

        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_FIXED_OBJECTIVES,
            title = "Objetivos fijos pendientes",
            content = "Te quedan por marcar: $pendingText."
        )
    }

    private fun showRestDayNotification(
        context: Context,
        repository: StudyRepository
    ) {
        val today = LocalDate.now()

        if (!repository.isFreeDay(today)) return

        val content = if (repository.isDefaultFreeDay(today)) {
            "Hoy es uno de tus días libres por defecto. No se exige objetivo y no se acumula nada."
        } else {
            "Hoy está marcado como descanso. No se exige objetivo y no se acumula nada."
        }

        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_REST_DAY,
            title = "Día libre",
            content = content
        )
    }

    private fun showDailySummary(
        context: Context,
        repository: StudyRepository
    ) {
        val today = LocalDate.now()
        val todayDay = repository.getEffectiveStudyDay(today)

        if (repository.isFreeDay(today)) {
            val content = if (repository.isDefaultFreeDay(today)) {
                "Hoy fue día libre por defecto. No se exigía objetivo."
            } else {
                "Hoy fue día de descanso. No se exigía objetivo."
            }

            showNotification(
                context = context,
                notificationId = NOTIFICATION_ID_DAILY_SUMMARY,
                title = "Resumen de hoy",
                content = content
            )
            return
        }

        val studiedText = todayDay.studiedMinutes.toHourMinuteText()
        val goalText = todayDay.dailyGoalMinutes.toHourMinuteText()

        val enabledObjectives = repository.fixedObjectives.value
            .filter { it.isEnabled }

        val completedObjectives = enabledObjectives.count { objective ->
            todayDay.completedFixedObjectiveIds.contains(objective.id)
        }

        val objectivesText = if (enabledObjectives.isEmpty()) {
            "Sin objetivos fijos."
        } else {
            "Objetivos fijos: $completedObjectives/${enabledObjectives.size} hechos."
        }

        showNotification(
            context = context,
            notificationId = NOTIFICATION_ID_DAILY_SUMMARY,
            title = "Resumen de hoy",
            content = "Has estudiado $studiedText de $goalText. $objectivesText"
        )
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(
        context: Context,
        notificationId: Int,
        title: String,
        content: String
    ) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(createOpenAppPendingIntent(context))
            .build()

        NotificationManagerCompat.from(context).notify(
            notificationId,
            notification
        )
    }

    private fun createOpenAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(
            context,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Notificaciones de estudio",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Recordatorios, objetivos fijos, descansos y resúmenes de estudio."
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun StudySettings.isTypeEnabled(type: String): Boolean {
        if (!notificationsEnabled) return false

        return when (type) {
            TYPE_STUDY_REMINDER -> studyReminderEnabled
            TYPE_FIXED_OBJECTIVES -> fixedObjectivesReminderEnabled
            TYPE_REST_DAY -> restDayNotificationEnabled
            TYPE_DAILY_SUMMARY -> dailySummaryEnabled
            else -> false
        }
    }

    private fun Intent?.isSystemRescheduleAction(): Boolean {
        return when (this?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> true

            else -> false
        }
    }

    private fun StudyRepository.getActiveTopicNameForNotification(): String? {
        return topics.value
            .filter { !it.isArchived }
            .sortedBy { it.queuePosition }
            .firstOrNull()
            ?.name
    }

    private fun Int.toHourMinuteText(): String {
        val hours = this / 60
        val minutes = this % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
            hours > 0 -> "${hours} h"
            else -> "${minutes} min"
        }
    }

    companion object {
        const val ACTION_SCHEDULED_NOTIFICATION = "com.example.planario.action.SCHEDULED_NOTIFICATION"

        const val EXTRA_TYPE = "extra_type"

        const val TYPE_STUDY_REMINDER = "study_reminder"
        const val TYPE_FIXED_OBJECTIVES = "fixed_objectives"
        const val TYPE_REST_DAY = "rest_day"
        const val TYPE_DAILY_SUMMARY = "daily_summary"

        private const val CHANNEL_ID = "planario_scheduled_notifications"

        private const val NOTIFICATION_ID_STUDY_REMINDER = 3201
        private const val NOTIFICATION_ID_FIXED_OBJECTIVES = 3202
        private const val NOTIFICATION_ID_REST_DAY = 3203
        private const val NOTIFICATION_ID_DAILY_SUMMARY = 3204

        private const val OPEN_APP_REQUEST_CODE = 3301
    }
}