package com.example.planario.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Refresca Hoy después del cambio de fecha aunque NEXUM no esté abierto.
 * No requiere permiso de alarmas exactas ni despierta el dispositivo cada minuto.
 */
object TodayRolloverScheduler {
    private const val ACTION_ROLLOVER = "com.example.estudario.action.TODAY_ROLLOVER"
    private const val REQUEST_CODE = 5217
    private const val PREFS = "nexum_today_rollover"
    private const val KEY_TARGET = "next_day_alarm_millis"

    fun nextRolloverMillis(now: ZonedDateTime): Long =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            .toInstant().toEpochMilli() + 60_000L

    fun schedule(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        val ids = manager.getAppWidgetIds(ComponentName(
            appContext, PlanarioTodayWidgetReceiver::class.java))
        if (ids.isEmpty()) {
            cancel(appContext)
            return
        }
        val next = nextRolloverMillis(ZonedDateTime.now(ZoneId.systemDefault()))
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!force && prefs.getLong(KEY_TARGET, -1L) == next) return
        val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // setAndAllowWhileIdle is inexact: Android may defer slightly under Doze,
        // so updatePeriodMillis=30m provides a second path to the next-day data.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pendingIntent(appContext))
        prefs.edit().putLong(KEY_TARGET, next).apply()
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarm = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pendingIntent(appContext))
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_TARGET).apply()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, TodayRolloverReceiver::class.java).apply {
            action = ACTION_ROLLOVER
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun isRollover(intent: Intent): Boolean = intent.action == ACTION_ROLLOVER
}
