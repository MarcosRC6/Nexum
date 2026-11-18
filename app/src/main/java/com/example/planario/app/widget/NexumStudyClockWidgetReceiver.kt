package com.example.planario.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import com.example.planario.domain.timer.StudyForegroundPolicy
import com.example.planario.domain.timer.PomodoroTimerState
import com.example.testario.MainActivity
import com.example.testario.R
import java.time.LocalDate

/** Un solo cronómetro de NEXUM; dos caras: estudio y pomodoro. */
class NexumStudyClockWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, manager: AppWidgetManager, appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        updateWidget(context, manager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> prefs(context).edit().remove(key(id)).apply() }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (oldId, newId) ->
            savePage(context, newId, getPage(context, oldId))
            prefs(context).edit().remove(key(oldId)).apply()
        }
        updateAllWidgets(context)
    }

    companion object {
        internal fun handleWidgetAction(context: Context, intent: Intent) {
            val action = intent.action ?: return
        if (action !in ACTIONS) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (action == ACTION_SWITCH_PAGE) {
            savePage(context, id, 1 - getPage(context, id))
            updateWidget(context, AppWidgetManager.getInstance(context), id)
            return
        }

        PlanarioDependencies.init(context.applicationContext)
        val controller = PlanarioDependencies.studyTimerController
        val repository = PlanarioDependencies.studyRepository
        if (action == ACTION_POMODORO_TOGGLE &&
            controller.state.value.pomodoroTimerState in listOf(
                PomodoroTimerState.STOPPED, PomodoroTimerState.FINISHED)) {
            // En modo widget no hay TimerViewModel: leer la duración guardada en Plan.
            PlanarioDependencies.synchronizeConfiguredPomodoroDuration()
        }
        when (action) {
            ACTION_STUDY_TOGGLE -> when (controller.state.value.studyTimerState) {
                StudyTimerState.STOPPED -> {
                    val today = LocalDate.now()
                    controller.startStudyTimer(today)
                    repository.startActiveStudyTimerTracking(today)
                    PlanarioDependencies.startStudyTimerForegroundService()
                }
                StudyTimerState.RUNNING -> {
                    controller.pauseStudyTimer()
                    repository.pauseActiveStudyTimerTracking()
                }
                StudyTimerState.PAUSED -> {
                    controller.resumeStudyTimer()
                    repository.resumeActiveStudyTimerTracking()
                    PlanarioDependencies.startStudyTimerForegroundService()
                }
            }
            ACTION_STUDY_SAVE -> PlanarioDependencies.finishStudyTimerAndSave()
            ACTION_POMODORO_TOGGLE -> when (controller.state.value.pomodoroTimerState) {
                PomodoroTimerState.RUNNING -> controller.pausePomodoro()
                PomodoroTimerState.PAUSED -> controller.resumePomodoro()
                PomodoroTimerState.STOPPED, PomodoroTimerState.FINISHED ->
                    controller.startPomodoro()
            }
            ACTION_POMODORO_RESET -> controller.resetPomodoro()
        }
        val current = controller.state.value
        if (StudyForegroundPolicy.shouldKeepRunning(
                current.studyTimerState, current.pomodoroTimerState)) {
            PlanarioDependencies.startStudyTimerForegroundService()
        } else {
            PlanarioDependencies.stopStudyTimerForegroundService()
        }
        updateAllWidgets(context)
        PlanarioWidgetUpdater.updateTodayWidget(context)
    }


        const val ACTION_REFRESH = "com.example.estudario.STUDY_WIDGET_REFRESH"
        private const val ACTION_SWITCH_PAGE = "com.example.estudario.STUDY_WIDGET_SWITCH"
        private const val ACTION_STUDY_TOGGLE = "com.example.estudario.STUDY_WIDGET_TOGGLE_STUDY"
        private const val ACTION_STUDY_SAVE = "com.example.estudario.STUDY_WIDGET_SAVE"
        private const val ACTION_POMODORO_TOGGLE = "com.example.estudario.STUDY_WIDGET_TOGGLE_POMO"
        private const val ACTION_POMODORO_RESET = "com.example.estudario.STUDY_WIDGET_RESET_POMO"
        private val ACTIONS = setOf(ACTION_SWITCH_PAGE, ACTION_STUDY_TOGGLE,
            ACTION_STUDY_SAVE, ACTION_POMODORO_TOGGLE, ACTION_POMODORO_RESET)
        private const val PREFS = "nexum_study_widgets"
        private fun prefs(context: Context) = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private fun key(id: Int) = "page_$id"

        fun getPage(context: Context, id: Int): Int =
            prefs(context).getInt(key(id), 0).coerceIn(0, 1)

        fun savePage(context: Context, id: Int, page: Int) {
            prefs(context).edit().putInt(key(id), page.coerceIn(0, 1)).apply()
        }

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context.applicationContext)
            val ids = manager.getAppWidgetIds(ComponentName(context.applicationContext,
                NexumStudyClockWidgetReceiver::class.java))
            if (ids.isEmpty()) return
            PlanarioDependencies.init(context.applicationContext)
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, id: Int) {
            updateWidget(context, AppWidgetManager.getInstance(context), id)
        }

        private fun action(context: Context, widgetId: Int, code: Int, action: String): PendingIntent {
            val intent = Intent(context, NexumStudyClockWidgetActionReceiver::class.java).apply {
                this.action = action
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            return PendingIntent.getBroadcast(context, widgetId * 16 + code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun openTimer(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = "com.example.estudario.STUDY_WIDGET_OPEN_TIMER"
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_START_DESTINATION_ROUTE, "timer")
            }
            return PendingIntent.getActivity(context, id * 16 + 7, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun formatRemaining(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val hours = safe / 3600L
            val minutes = (safe % 3600L) / 60L
            val secs = safe % 60L
            return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, secs)
                else "%02d:%02d".format(minutes, secs)
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
            PlanarioDependencies.init(context.applicationContext)
            val state = PlanarioDependencies.studyTimerController.state.value
            val page = getPage(context, widgetId)
            val options = manager.getAppWidgetOptions(widgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val layoutId = if (NexumClockSizePolicy.compact(width, height)) {
                R.layout.widget_nexum_study_clock_compact
            } else {
                R.layout.widget_nexum_study_clock
            }
            val views = RemoteViews(context.packageName, layoutId)
            views.setTextViewText(R.id.studyWidgetTitle,
                if (page == 0) "ESTUDIO" else "POMODORO")
            views.setTextViewText(R.id.studyWidgetPageIndicator,
                if (page == 0) "● ○" else "○ ●")
            views.setViewVisibility(R.id.studyWidgetStudyPane,
                if (page == 0) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.studyWidgetPomodoroPane,
                if (page == 1) View.VISIBLE else View.GONE)

            val baseStudy = SystemClock.elapsedRealtime() -
                state.studyElapsedSeconds.coerceAtLeast(0L) * 1000L
            views.setChronometer(R.id.studyWidgetStudyClock, baseStudy, null,
                state.studyTimerState == StudyTimerState.RUNNING)
            views.setTextViewText(R.id.studyWidgetStudyButton, when (state.studyTimerState) {
                StudyTimerState.STOPPED -> "Iniciar"
                StudyTimerState.RUNNING -> "Pausar"
                StudyTimerState.PAUSED -> "Reanudar"
            })
            views.setViewVisibility(R.id.studyWidgetSave,
                if (state.studyTimerState == StudyTimerState.STOPPED) View.GONE else View.VISIBLE)

            val basePomo = SystemClock.elapsedRealtime() +
                state.pomodoroRemainingSeconds.coerceAtLeast(0L) * 1000L
            views.setChronometer(R.id.studyWidgetPomodoroClock, basePomo, null,
                state.pomodoroTimerState == PomodoroTimerState.RUNNING)
            views.setChronometerCountDown(R.id.studyWidgetPomodoroClock, true)
            if (state.pomodoroTimerState != PomodoroTimerState.RUNNING) {
                // El Chronometer parado puede mostrar 59:59 en vez de 60:00 por
                // la latencia entre crear el base y renderizar las RemoteViews.
                views.setTextViewText(R.id.studyWidgetPomodoroClock,
                    formatRemaining(state.pomodoroRemainingSeconds))
            }
            views.setTextViewText(R.id.studyWidgetPomodoroButton, when (state.pomodoroTimerState) {
                PomodoroTimerState.STOPPED, PomodoroTimerState.FINISHED -> "Iniciar"
                PomodoroTimerState.RUNNING -> "Pausar"
                PomodoroTimerState.PAUSED -> "Reanudar"
            })
            views.setTextViewText(R.id.studyWidgetPomodoroMeta,
                "${state.lastPomodoroMinutes} min configurados")
            views.setOnClickPendingIntent(R.id.studyWidgetSwitchLeft,
                action(context, widgetId, 1, ACTION_SWITCH_PAGE))
            views.setOnClickPendingIntent(R.id.studyWidgetSwitchRight,
                action(context, widgetId, 1, ACTION_SWITCH_PAGE))
            views.setOnClickPendingIntent(R.id.studyWidgetStudyButton,
                action(context, widgetId, 2, ACTION_STUDY_TOGGLE))
            views.setOnClickPendingIntent(R.id.studyWidgetSave,
                action(context, widgetId, 3, ACTION_STUDY_SAVE))
            views.setOnClickPendingIntent(R.id.studyWidgetPomodoroButton,
                action(context, widgetId, 4, ACTION_POMODORO_TOGGLE))
            views.setOnClickPendingIntent(R.id.studyWidgetPomodoroReset,
                action(context, widgetId, 5, ACTION_POMODORO_RESET))
            val open = openTimer(context, widgetId)
            // El área del reloj abre la pantalla Reloj. Los botones y flechas
            // mantienen sus PendingIntent específicos y no navegan.
            views.setOnClickPendingIntent(R.id.studyWidgetClockRoot, open)
            views.setOnClickPendingIntent(R.id.studyWidgetTitle, open)
            views.setOnClickPendingIntent(R.id.studyWidgetPageIndicator, open)
            views.setOnClickPendingIntent(R.id.studyWidgetStudyClock, open)
            views.setOnClickPendingIntent(R.id.studyWidgetPomodoroClock, open)
            manager.updateAppWidget(widgetId, views)
        }
    }
}
