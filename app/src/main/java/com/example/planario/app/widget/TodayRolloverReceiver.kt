package com.example.planario.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Privado: responde al cambio de día, reinicio y ajustes de fecha o zona horaria. */
class TodayRolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (TodayRolloverScheduler.isRollover(intent) ||
            action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Siempre reconstruir desde los datos del día actual. Jamás copiar
            // la barra de progreso de ayer ni reiniciar su historial.
            TodayRolloverScheduler.schedule(context, force = true)
            PlanarioTodayWidgetReceiver.updateAllWidgets(context)
        }
    }
}
