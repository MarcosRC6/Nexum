package com.example.planario.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.planario.core.model.StudySettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PlanarioNotificationScheduler(
    private val context: Context
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleAll(settings: StudySettings) {
        cancelAll()

        if (settings.studyReminderEnabled) {
            scheduleNotification(
                type = PlanarioNotificationReceiver.TYPE_STUDY_REMINDER,
                time = settings.studyReminderTime
            )
        }

        if (settings.fixedObjectivesReminderEnabled) {
            scheduleNotification(
                type = PlanarioNotificationReceiver.TYPE_FIXED_OBJECTIVES,
                time = settings.fixedObjectivesReminderTime
            )
        }

        if (settings.restDayNotificationEnabled) {
            scheduleNotification(
                type = PlanarioNotificationReceiver.TYPE_REST_DAY,
                time = settings.restDayNotificationTime
            )
        }

        if (settings.dailySummaryEnabled) {
            scheduleNotification(
                type = PlanarioNotificationReceiver.TYPE_DAILY_SUMMARY,
                time = settings.dailySummaryTime
            )
        }
    }

    fun cancelAll() {
        cancelNotification(PlanarioNotificationReceiver.TYPE_STUDY_REMINDER)
        cancelNotification(PlanarioNotificationReceiver.TYPE_FIXED_OBJECTIVES)
        cancelNotification(PlanarioNotificationReceiver.TYPE_REST_DAY)
        cancelNotification(PlanarioNotificationReceiver.TYPE_DAILY_SUMMARY)
    }

    private fun scheduleNotification(
        type: String,
        time: String
    ) {
        val triggerMillis = calculateNextTriggerMillis(time)
        val pendingIntent = createPendingIntent(type)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager?.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        } else {
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                triggerMillis,
                pendingIntent
            )
        }
    }

    private fun cancelNotification(type: String) {
        alarmManager?.cancel(
            createPendingIntent(type)
        )
    }

    private fun createPendingIntent(type: String): PendingIntent {
        val intent = Intent(
            context,
            PlanarioNotificationReceiver::class.java
        ).apply {
            action = PlanarioNotificationReceiver.ACTION_SCHEDULED_NOTIFICATION
            putExtra(PlanarioNotificationReceiver.EXTRA_TYPE, type)
        }

        return PendingIntent.getBroadcast(
            context,
            type.toRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun calculateNextTriggerMillis(time: String): Long {
        val parsedTime = time.toLocalTimeOrDefault()
        val now = LocalDateTime.now()

        var nextTrigger = LocalDate.now().atTime(parsedTime)

        if (!nextTrigger.isAfter(now)) {
            nextTrigger = nextTrigger.plusDays(1)
        }

        return nextTrigger
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun String.toLocalTimeOrDefault(): LocalTime {
        val parts = split(":")
        if (parts.size != 2) return LocalTime.of(9, 0)

        val hour = parts[0].toIntOrNull() ?: return LocalTime.of(9, 0)
        val minute = parts[1].toIntOrNull() ?: return LocalTime.of(9, 0)

        if (hour !in 0..23 || minute !in 0..59) {
            return LocalTime.of(9, 0)
        }

        return LocalTime.of(hour, minute)
    }

    private fun String.toRequestCode(): Int {
        return when (this) {
            PlanarioNotificationReceiver.TYPE_STUDY_REMINDER -> 3101
            PlanarioNotificationReceiver.TYPE_FIXED_OBJECTIVES -> 3102
            PlanarioNotificationReceiver.TYPE_REST_DAY -> 3103
            PlanarioNotificationReceiver.TYPE_DAILY_SUMMARY -> 3104
            else -> 3199
        }
    }
}