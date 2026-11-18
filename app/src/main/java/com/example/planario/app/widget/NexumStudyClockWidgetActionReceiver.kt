package com.example.planario.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** No exportado: sólo los PendingIntent inmutables creados por NEXUM pueden
 * controlar el cronómetro de estudio y el Pomodoro desde el launcher.
 */
class NexumStudyClockWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NexumStudyClockWidgetReceiver.handleWidgetAction(context, intent)
    }
}
