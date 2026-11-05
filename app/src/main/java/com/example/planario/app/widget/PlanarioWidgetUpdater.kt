package com.example.planario.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.planario.data.local.StudyLocalDataSource

object PlanarioWidgetUpdater {

    private const val UPDATE_INTERVAL_MILLIS = 60_000L
    private const val TODAY_WIDGET_REQUEST_CODE = 5101
    private const val CALENDAR_WIDGET_REQUEST_CODE = 5102

    fun updateTodayWidget(context: Context) {
        val appContext = context.applicationContext

        PlanarioTodayWidgetReceiver.updateAllWidgets(appContext)
        PlanarioCalendarWidgetReceiver.updateAllWidgets(appContext)

        appContext.sendBroadcast(
            Intent(
                appContext,
                PlanarioTodayWidgetReceiver::class.java
            ).apply {
                action = PlanarioTodayWidgetReceiver.ACTION_REFRESH_PLANARIO_WIDGET
            }
        )

        appContext.sendBroadcast(
            Intent(
                appContext,
                PlanarioCalendarWidgetReceiver::class.java
            ).apply {
                action = PlanarioCalendarWidgetReceiver.ACTION_REFRESH_PLANARIO_CALENDAR_WIDGET
            }
        )

        scheduleOrCancelRealtimeUpdates(appContext)
    }

    private fun scheduleOrCancelRealtimeUpdates(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val todayPendingIntent = createTodayWidgetPendingIntent(appContext)
        val calendarPendingIntent = createCalendarWidgetPendingIntent(appContext)

        val activeTimer = StudyLocalDataSource(appContext).loadActiveStudyTimerSnapshot()

        if (activeTimer.isRunning) {
            val triggerAtMillis = System.currentTimeMillis() + UPDATE_INTERVAL_MILLIS

            alarmManager.setRepeating(
                AlarmManager.RTC,
                triggerAtMillis,
                UPDATE_INTERVAL_MILLIS,
                todayPendingIntent
            )

            alarmManager.setRepeating(
                AlarmManager.RTC,
                triggerAtMillis,
                UPDATE_INTERVAL_MILLIS,
                calendarPendingIntent
            )
        } else {
            alarmManager.cancel(todayPendingIntent)
            alarmManager.cancel(calendarPendingIntent)
        }
    }

    private fun createTodayWidgetPendingIntent(
        context: Context
    ): PendingIntent {
        val intent = Intent(
            context,
            PlanarioTodayWidgetReceiver::class.java
        ).apply {
            action = PlanarioTodayWidgetReceiver.ACTION_REFRESH_PLANARIO_WIDGET
        }

        return PendingIntent.getBroadcast(
            context,
            TODAY_WIDGET_REQUEST_CODE,
            intent,
            pendingIntentFlags()
        )
    }

    private fun createCalendarWidgetPendingIntent(
        context: Context
    ): PendingIntent {
        val intent = Intent(
            context,
            PlanarioCalendarWidgetReceiver::class.java
        ).apply {
            action = PlanarioCalendarWidgetReceiver.ACTION_REFRESH_PLANARIO_CALENDAR_WIDGET
        }

        return PendingIntent.getBroadcast(
            context,
            CALENDAR_WIDGET_REQUEST_CODE,
            intent,
            pendingIntentFlags()
        )
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }
}
