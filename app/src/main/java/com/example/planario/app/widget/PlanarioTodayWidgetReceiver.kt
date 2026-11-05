package com.example.planario.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.testario.MainActivity
import com.example.testario.R
import com.example.planario.app.PlanarioDestination
import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.ThemeMode
import com.example.planario.data.local.StudyLocalDataSource
import java.time.LocalDate

class PlanarioTodayWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetIds = appWidgetIds
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId
        )
    }

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_REFRESH_PLANARIO_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_PLANARIO_WIDGET =
            "com.example.planario.action.REFRESH_PLANARIO_TODAY_WIDGET"

        private const val MEDIUM_MIN_HEIGHT_DP = 105
        private const val LARGE_MIN_HEIGHT_DP = 210
        private const val LARGE_MIN_WIDTH_DP = 250

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)

            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(
                    appContext,
                    PlanarioTodayWidgetReceiver::class.java
                )
            )

            if (appWidgetIds.isNotEmpty()) {
                updateWidgets(
                    context = appContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = appWidgetIds
                )
            }
        }

        private fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            appWidgetIds.forEach { widgetId ->
                updateWidget(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = widgetId
                )
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)

            val showMedium = minHeight >= MEDIUM_MIN_HEIGHT_DP || minWidth >= LARGE_MIN_WIDTH_DP
            val showLarge = minHeight >= LARGE_MIN_HEIGHT_DP && minWidth >= LARGE_MIN_WIDTH_DP

            val state = loadWidgetState(context)

            val views = buildRemoteViews(
                context = context,
                state = state,
                showMedium = showMedium,
                showLarge = showLarge
            )

            appWidgetManager.updateAppWidget(
                appWidgetId,
                views
            )
        }

        private fun buildRemoteViews(
            context: Context,
            state: PlanarioTodayWidgetState,
            showMedium: Boolean,
            showLarge: Boolean
        ): RemoteViews {
            return RemoteViews(
                context.packageName,
                R.layout.widget_planario_today
            ).apply {
                setInt(
                    R.id.widgetRoot,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_background_dark
                    } else {
                        R.drawable.widget_today_background_light
                    }
                )

                setInt(
                    R.id.widgetMainSection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setInt(
                    R.id.widgetDetailsSection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setInt(
                    R.id.widgetWeekSection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setTextViewText(
                    R.id.widgetTitle,
                    "Estudio de hoy"
                )

                setTextColor(
                    R.id.widgetTitle,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetStatus,
                    state.statusText
                )

                setTextColor(
                    R.id.widgetStatus,
                    state.statusTextColor
                )

                setInt(
                    R.id.widgetStatus,
                    "setBackgroundResource",
                    state.statusBackgroundRes
                )

                setTextViewText(
                    R.id.widgetTopicLabel,
                    "Tema activo"
                )

                setTextColor(
                    R.id.widgetTopicLabel,
                    state.secondaryTextColor
                )

                setTextViewText(
                    R.id.widgetTopic,
                    state.topicText
                )

                setTextColor(
                    R.id.widgetTopic,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetRoundText,
                    state.roundText
                )

                setTextColor(
                    R.id.widgetRoundText,
                    state.secondaryTextColor
                )

                setViewVisibility(
                    R.id.widgetRoundText,
                    if (state.roundText.isBlank()) View.GONE else View.VISIBLE
                )

                setTextViewText(
                    R.id.widgetProgressText,
                    state.progressText
                )

                setTextColor(
                    R.id.widgetProgressText,
                    state.primaryTextColor
                )

                setProgressBar(
                    R.id.widgetProgressBar,
                    100,
                    state.progressPercent,
                    false
                )

                setTextViewText(
                    R.id.widgetRemainingText,
                    state.remainingText
                )

                setTextColor(
                    R.id.widgetRemainingText,
                    state.secondaryTextColor
                )

                setViewVisibility(
                    R.id.widgetDetailsSection,
                    if (showMedium) View.VISIBLE else View.GONE
                )

                setTextViewText(
                    R.id.widgetObjectivesTitle,
                    "Objetivos fijos"
                )

                setTextColor(
                    R.id.widgetObjectivesTitle,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetObjectivesText,
                    state.objectivesText
                )

                setTextColor(
                    R.id.widgetObjectivesText,
                    state.secondaryTextColor
                )

                setTextViewText(
                    R.id.widgetTodayMetaText,
                    state.todayMetaText
                )

                setTextColor(
                    R.id.widgetTodayMetaText,
                    state.secondaryTextColor
                )

                setViewVisibility(
                    R.id.widgetWeekSection,
                    if (showLarge) View.VISIBLE else View.GONE
                )

                setTextViewText(
                    R.id.widgetWeekTitle,
                    "Esta semana"
                )

                setTextColor(
                    R.id.widgetWeekTitle,
                    state.primaryTextColor
                )

                bindWeekCells(
                    views = this,
                    state = state
                )

                setViewVisibility(
                    R.id.widgetFlexibleSpace,
                    if (showLarge) View.VISIBLE else View.GONE
                )

                setViewVisibility(
                    R.id.widgetActionsRow,
                    if (showLarge) View.VISIBLE else View.GONE
                )

                setTextViewText(
                    R.id.widgetTimerButton,
                    "Reloj"
                )

                setTextViewText(
                    R.id.widgetPlanButton,
                    "Plan"
                )

                setTextViewText(
                    R.id.widgetCalendarButton,
                    "Calendario"
                )

                setTextColor(
                    R.id.widgetTimerButton,
                    state.actionTextColor
                )

                setTextColor(
                    R.id.widgetPlanButton,
                    state.actionTextColor
                )

                setTextColor(
                    R.id.widgetCalendarButton,
                    state.actionTextColor
                )

                setOnClickPendingIntent(
                    R.id.widgetRoot,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Home.route,
                        requestCode = 4101
                    )
                )

                setOnClickPendingIntent(
                    R.id.widgetTimerButton,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Timer.route,
                        requestCode = 4102
                    )
                )

                setOnClickPendingIntent(
                    R.id.widgetPlanButton,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Plan.route,
                        requestCode = 4103
                    )
                )

                setOnClickPendingIntent(
                    R.id.widgetCalendarButton,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Calendar.route,
                        requestCode = 4104
                    )
                )
            }
        }

        private fun bindWeekCells(
            views: RemoteViews,
            state: PlanarioTodayWidgetState
        ) {
            val labelIds = listOf(
                R.id.widgetWeekLabel0,
                R.id.widgetWeekLabel1,
                R.id.widgetWeekLabel2,
                R.id.widgetWeekLabel3,
                R.id.widgetWeekLabel4,
                R.id.widgetWeekLabel5,
                R.id.widgetWeekLabel6
            )

            val cellIds = listOf(
                R.id.widgetWeekCell0,
                R.id.widgetWeekCell1,
                R.id.widgetWeekCell2,
                R.id.widgetWeekCell3,
                R.id.widgetWeekCell4,
                R.id.widgetWeekCell5,
                R.id.widgetWeekCell6
            )

            state.weekCells.forEachIndexed { index, cell ->
                views.setTextViewText(
                    labelIds[index],
                    cell.label
                )

                views.setTextColor(
                    labelIds[index],
                    state.secondaryTextColor
                )

                views.setTextViewText(
                    cellIds[index],
                    cell.text
                )

                views.setTextColor(
                    cellIds[index],
                    cell.textColor
                )

                views.setInt(
                    cellIds[index],
                    "setBackgroundResource",
                    cell.backgroundRes
                )
            }
        }

        private fun createOpenAppPendingIntent(
            context: Context,
            route: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(
                context,
                MainActivity::class.java
            ).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                putExtra(
                    MainActivity.EXTRA_START_DESTINATION_ROUTE,
                    route
                )
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                flags
            )
        }

        private fun loadWidgetState(context: Context): PlanarioTodayWidgetState {
            val dataSource = StudyLocalDataSource(context)
            val data = dataSource.load()
            val activeTimerSnapshot = dataSource.loadActiveStudyTimerSnapshot()
            val today = LocalDate.now()

            val appThemeMode = context
                .getSharedPreferences("testario_settings", Context.MODE_PRIVATE)
                .getString("theme_mode", "SYSTEM")

            val darkMode = when (appThemeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> context.isSystemDarkMode()
            }

            val activeTopic = data.topics
                .filter { !it.isArchived }
                .sortedBy { it.queuePosition }
                .firstOrNull()

            val todayDay = data.studyDays[today]

            val savedStudiedMinutes = data.timeEntries
                .filter { it.date == today }
                .sumOf { it.minutes }

            val activeTimerMinutes = if (activeTimerSnapshot.isActive && activeTimerSnapshot.date == today) {
                activeTimerSnapshot.elapsedMinutes()
            } else {
                0
            }

            val studiedMinutes = savedStudiedMinutes + activeTimerMinutes

            val dailyGoalMinutes = todayDay?.dailyGoalMinutes ?: data.settings.dailyGoalMinutes

            val enabledFixedObjectives = data.fixedObjectives
                .filter { it.isEnabled }
                .sortedBy { it.order }

            val completedFixedObjectiveIds = todayDay?.completedFixedObjectiveIds ?: emptySet()

            val completedFixedObjectives = enabledFixedObjectives.count { objective ->
                completedFixedObjectiveIds.contains(objective.id)
            }

            val pendingObjectiveNames = enabledFixedObjectives
                .filterNot { objective ->
                    completedFixedObjectiveIds.contains(objective.id)
                }
                .map { it.name }

            val isFreeDay = when {
                todayDay?.forceStudyDay == true -> false
                todayDay?.isRestDay == true -> true
                else -> data.settings.defaultFreeWeekDays.contains(today.dayOfWeek.value)
            }

            val isFinishedManually = todayDay?.isFinishedManually == true

            val dayStatus = calculateStatus(
                date = today,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = isFinishedManually
            )

            val weekCells = buildWeekCells(
                context = context,
                darkMode = darkMode
            )

            return PlanarioTodayWidgetState(
                darkMode = darkMode,
                activeTopicName = activeTopic?.name,
                activeTopicRound = activeTopic?.round,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                completedFixedObjectives = completedFixedObjectives,
                totalFixedObjectives = enabledFixedObjectives.size,
                pendingObjectiveNames = pendingObjectiveNames,
                dayStatus = dayStatus,
                isFinishedManually = isFinishedManually,
                weekCells = weekCells
            )
        }

        private fun buildWeekCells(
            context: Context,
            darkMode: Boolean
        ): List<PlanarioWeekCell> {
            val dataSource = StudyLocalDataSource(context)
            val data = dataSource.load()
            val activeTimerSnapshot = dataSource.loadActiveStudyTimerSnapshot()
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())

            val labels = listOf("L", "M", "X", "J", "V", "S", "D")

            return (0..6).map { offset ->
                val date = monday.plusDays(offset.toLong())
                val day = data.studyDays[date]

                val savedStudiedMinutes = data.timeEntries
                    .filter { it.date == date }
                    .sumOf { it.minutes }

                val activeTimerMinutes = if (date == today && activeTimerSnapshot.isActive) {
                    activeTimerSnapshot.elapsedMinutes()
                } else {
                    0
                }

                val studiedMinutes = savedStudiedMinutes + activeTimerMinutes

                val dailyGoalMinutes = day?.dailyGoalMinutes ?: data.settings.dailyGoalMinutes

                val isFreeDay = when {
                    day?.forceStudyDay == true -> false
                    day?.isRestDay == true -> true
                    else -> data.settings.defaultFreeWeekDays.contains(date.dayOfWeek.value)
                }

                val status = calculateStatus(
                    date = date,
                    studiedMinutes = studiedMinutes,
                    dailyGoalMinutes = dailyGoalMinutes,
                    isFreeDay = isFreeDay,
                    isFinishedManually = day?.isFinishedManually == true
                )

                PlanarioWeekCell(
                    label = labels[offset],
                    text = if (date == today) {
                        "•${date.dayOfMonth}"
                    } else {
                        date.dayOfMonth.toString()
                    },
                    backgroundRes = status.toStatusBackgroundRes(),
                    textColor = status.toStatusTextColor(darkMode)
                )
            }
        }

        private fun calculateStatus(
            date: LocalDate,
            studiedMinutes: Int,
            dailyGoalMinutes: Int,
            isFreeDay: Boolean,
            isFinishedManually: Boolean = false
        ): DayStatus {
            if (isFreeDay) return DayStatus.REST
            if (isFinishedManually) return DayStatus.COMPLETED
            if (dailyGoalMinutes > 0 && studiedMinutes >= dailyGoalMinutes) return DayStatus.COMPLETED
            if (studiedMinutes > 0) return DayStatus.IN_PROGRESS
            if (date.isBefore(LocalDate.now())) return DayStatus.NOT_COMPLETED

            return DayStatus.EMPTY
        }

        private fun Context.isSystemDarkMode(): Boolean {
            val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK

            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        private fun DayStatus.toStatusBackgroundRes(): Int {
            return when (this) {
                DayStatus.COMPLETED -> R.drawable.widget_today_status_completed
                DayStatus.IN_PROGRESS -> R.drawable.widget_today_status_in_progress
                DayStatus.REST -> R.drawable.widget_today_status_rest
                DayStatus.NOT_COMPLETED -> R.drawable.widget_today_status_not_completed
                DayStatus.EMPTY -> R.drawable.widget_today_status_empty
            }
        }

        private fun DayStatus.toStatusTextColor(darkMode: Boolean): Int {
            return when (this) {
                DayStatus.COMPLETED -> 0xFF166534.toInt()
                DayStatus.IN_PROGRESS -> 0xFF1D4ED8.toInt()
                DayStatus.REST -> 0xFF334155.toInt()
                DayStatus.NOT_COMPLETED -> 0xFFB91C1C.toInt()
                DayStatus.EMPTY -> 0xFF334155.toInt()
            }
        }
    }
}

private data class PlanarioTodayWidgetState(
    val darkMode: Boolean,
    val activeTopicName: String?,
    val activeTopicRound: Int?,
    val studiedMinutes: Int,
    val dailyGoalMinutes: Int,
    val completedFixedObjectives: Int,
    val totalFixedObjectives: Int,
    val pendingObjectiveNames: List<String>,
    val dayStatus: DayStatus,
    val isFinishedManually: Boolean,
    val weekCells: List<PlanarioWeekCell>
) {
    val primaryTextColor: Int
        get() = if (darkMode) {
            0xFFF8FAFC.toInt()
        } else {
            0xFF111827.toInt()
        }

    val secondaryTextColor: Int
        get() = if (darkMode) {
            0xFFCBD5E1.toInt()
        } else {
            0xFF475569.toInt()
        }

    val actionTextColor: Int
        get() = 0xFF172554.toInt()

    val statusText: String
        get() {
            if (isFinishedManually) return "Finalizado"

            return when (dayStatus) {
                DayStatus.EMPTY -> "Pendiente"
                DayStatus.IN_PROGRESS -> "En curso"
                DayStatus.COMPLETED -> "Completado"
                DayStatus.NOT_COMPLETED -> "No completado"
                DayStatus.REST -> "Día libre"
            }
        }

    val statusTextColor: Int
        get() = dayStatus.toReadableStatusColor(darkMode)

    val statusBackgroundRes: Int
        get() = when (dayStatus) {
            DayStatus.COMPLETED -> R.drawable.widget_today_status_completed
            DayStatus.IN_PROGRESS -> R.drawable.widget_today_status_in_progress
            DayStatus.REST -> R.drawable.widget_today_status_rest
            DayStatus.NOT_COMPLETED -> R.drawable.widget_today_status_not_completed
            DayStatus.EMPTY -> R.drawable.widget_today_status_empty
        }

    val topicText: String
        get() = activeTopicName ?: "Sin tema activo"

    val roundText: String
        get() {
            val round = activeTopicRound ?: return ""
            return "Vuelta $round"
        }

    val progressPercent: Int
        get() {
            if (dailyGoalMinutes <= 0) return 0

            return ((studiedMinutes.toFloat() / dailyGoalMinutes.toFloat()) * 100f)
                .toInt()
                .coerceIn(0, 100)
        }

    val progressText: String
        get() = "${studiedMinutes.toCompactHourMinuteText()} / ${dailyGoalMinutes.toCompactHourMinuteText()}"

    val remainingText: String
        get() {
            val remaining = (dailyGoalMinutes - studiedMinutes).coerceAtLeast(0)

            if (dayStatus == DayStatus.REST) {
                return "Hoy no se exige objetivo"
            }

            if (isFinishedManually) {
                val difference = dailyGoalMinutes - studiedMinutes

                return when {
                    difference > 0 -> "Faltaron ${difference.toCompactHourMinuteText()}"
                    difference < 0 -> "Extra ${(-difference).toCompactHourMinuteText()}"
                    else -> "Objetivo justo"
                }
            }

            return "Restan ${remaining.toCompactHourMinuteText()}"
        }

    val objectivesText: String
        get() {
            if (totalFixedObjectives == 0) return "Sin objetivos fijos"

            if (pendingObjectiveNames.isEmpty()) {
                return "$completedFixedObjectives/$totalFixedObjectives hechos · todo listo"
            }

            val pendingPreview = pendingObjectiveNames
                .take(2)
                .joinToString(separator = ", ")

            val extraCount = pendingObjectiveNames.size - 2

            val pendingText = if (extraCount > 0) {
                "$pendingPreview +$extraCount"
            } else {
                pendingPreview
            }

            return "$completedFixedObjectives/$totalFixedObjectives hechos · falta $pendingText"
        }

    val todayMetaText: String
        get() {
            if (isFinishedManually) {
                return "Día finalizado con ${studiedMinutes.toCompactHourMinuteText()}"
            }

            return when (dayStatus) {
                DayStatus.REST -> "Descanso sin deuda"
                DayStatus.COMPLETED -> "Objetivo diario cumplido"
                DayStatus.IN_PROGRESS -> "Estudio iniciado"
                DayStatus.NOT_COMPLETED -> "Día no completado"
                DayStatus.EMPTY -> "Aún sin registrar tiempo"
            }
        }
}

private data class PlanarioWeekCell(
    val label: String,
    val text: String,
    val backgroundRes: Int,
    val textColor: Int
)

private fun DayStatus.toReadableStatusColor(
    darkMode: Boolean
): Int {
    return when (this) {
        DayStatus.COMPLETED -> 0xFF166534.toInt()
        DayStatus.IN_PROGRESS -> 0xFF1D4ED8.toInt()
        DayStatus.REST -> 0xFF334155.toInt()
        DayStatus.NOT_COMPLETED -> 0xFFB91C1C.toInt()
        DayStatus.EMPTY -> 0xFF334155.toInt()
    }
}

private fun Int.toCompactHourMinuteText(): String {
    val hours = this / 60
    val minutes = this % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        else -> "${minutes} min"
    }
}