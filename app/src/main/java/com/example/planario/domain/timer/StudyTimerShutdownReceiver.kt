package com.example.planario.domain.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.planario.app.PlanarioDependencies

class StudyTimerShutdownReceiver : BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        val action = intent.action

        if (
            action == Intent.ACTION_SHUTDOWN ||
            action == ACTION_QUICKBOOT_POWEROFF
        ) {
            PlanarioDependencies.init(context.applicationContext)
            PlanarioDependencies.studyTimerController.attachContext(context.applicationContext)
            PlanarioDependencies.studyTimerController.persistCurrentStudyTimerNow()
        }
    }

    companion object {
        private const val ACTION_QUICKBOOT_POWEROFF = "android.intent.action.QUICKBOOT_POWEROFF"
    }
}
