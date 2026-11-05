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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

class PlanarioCalendarWidgetReceiver : AppWidgetProvider() {

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

        if (intent.action == ACTION_REFRESH_PLANARIO_CALENDAR_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_PLANARIO_CALENDAR_WIDGET =
            "com.example.planario.action.REFRESH_PLANARIO_CALENDAR_WIDGET"

        private const val LARGE_MIN_HEIGHT_DP = 210
        private const val LARGE_MIN_WIDTH_DP = 250

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val appWidgetManager = AppWidgetManager.getInstance(appContext)

            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(
                    appContext,
                    PlanarioCalendarWidgetReceiver::class.java
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

            val showLarge = minHeight >= LARGE_MIN_HEIGHT_DP && minWidth >= LARGE_MIN_WIDTH_DP

            val state = loadWidgetState(context)

            val views = buildRemoteViews(
                context = context,
                state = state,
                showLarge = showLarge
            )

            appWidgetManager.updateAppWidget(
                appWidgetId,
                views
            )
        }

        private fun buildRemoteViews(
            context: Context,
            state: PlanarioCalendarWidgetState,
            showLarge: Boolean
        ): RemoteViews {
            return RemoteViews(
                context.packageName,
                R.layout.widget_planario_calendar
            ).apply {
                setInt(
                    R.id.widgetCalendarRoot,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_background_dark
                    } else {
                        R.drawable.widget_today_background_light
                    }
                )

                setInt(
                    R.id.widgetCalendarTodaySection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setInt(
                    R.id.widgetCalendarWeekSection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setInt(
                    R.id.widgetCalendarMonthSection,
                    "setBackgroundResource",
                    if (state.darkMode) {
                        R.drawable.widget_today_section_dark
                    } else {
                        R.drawable.widget_today_section_light
                    }
                )

                setTextViewText(
                    R.id.widgetCalendarTitle,
                    "Calendario de estudio"
                )

                setTextColor(
                    R.id.widgetCalendarTitle,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetCalendarSubtitle,
                    state.weekTitle
                )

                setTextColor(
                    R.id.widgetCalendarSubtitle,
                    state.secondaryTextColor
                )

                setTextViewText(
                    R.id.widgetCalendarStatus,
                    state.todayStatusText
                )

                setTextColor(
                    R.id.widgetCalendarStatus,
                    state.todayStatusTextColor
                )

                setInt(
                    R.id.widgetCalendarStatus,
                    "setBackgroundResource",
                    state.todayStatusBackgroundRes
                )

                setTextViewText(
                    R.id.widgetCalendarTodayTitle,
                    "Hoy"
                )

                setTextColor(
                    R.id.widgetCalendarTodayTitle,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetCalendarTodayProgress,
                    state.todayProgressText
                )

                setTextColor(
                    R.id.widgetCalendarTodayProgress,
                    state.primaryTextColor
                )

                setProgressBar(
                    R.id.widgetCalendarTodayProgressBar,
                    100,
                    state.todayProgressPercent,
                    false
                )

                setTextViewText(
                    R.id.widgetCalendarTodayMeta,
                    state.todayMetaText
                )

                setTextColor(
                    R.id.widgetCalendarTodayMeta,
                    state.secondaryTextColor
                )

                setTextViewText(
                    R.id.widgetCalendarWeekTitle,
                    "Semana actual"
                )

                setTextColor(
                    R.id.widgetCalendarWeekTitle,
                    state.primaryTextColor
                )

                setTextViewText(
                    R.id.widgetCalendarWeekSummary,
                    state.weekSummaryText
                )

                setTextColor(
                    R.id.widgetCalendarWeekSummary,
                    state.secondaryTextColor
                )

                bindWeekCells(
                    views = this,
                    state = state
                )

                setViewVisibility(
                    R.id.widgetCalendarMonthSection,
                    if (showLarge) View.VISIBLE else View.GONE
                )

                setTextViewText(
                    R.id.widgetCalendarMonthTitle,
                    state.monthTitle
                )

                setTextColor(
                    R.id.widgetCalendarMonthTitle,
                    state.primaryTextColor
                )

                bindMonthCells(
                    views = this,
                    state = state
                )

                setViewVisibility(
                    R.id.widgetCalendarActionsRow,
                    if (showLarge) View.VISIBLE else View.GONE
                )

                setTextViewText(
                    R.id.widgetCalendarHomeButton,
                    "Inicio"
                )

                setTextViewText(
                    R.id.widgetCalendarOpenButton,
                    "Calendario"
                )

                setTextColor(
                    R.id.widgetCalendarHomeButton,
                    state.actionTextColor
                )

                setTextColor(
                    R.id.widgetCalendarOpenButton,
                    state.actionTextColor
                )

                setOnClickPendingIntent(
                    R.id.widgetCalendarRoot,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Home.route,
                        requestCode = 4201
                    )
                )

                setOnClickPendingIntent(
                    R.id.widgetCalendarHomeButton,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Home.route,
                        requestCode = 4202
                    )
                )

                setOnClickPendingIntent(
                    R.id.widgetCalendarOpenButton,
                    createOpenAppPendingIntent(
                        context = context,
                        route = PlanarioDestination.Plan.route,
                        requestCode = 4203
                    )
                )
            }
        }

        private fun bindWeekCells(
            views: RemoteViews,
            state: PlanarioCalendarWidgetState
        ) {
            val labelIds = listOf(
                R.id.calendarWeekLabel0,
                R.id.calendarWeekLabel1,
                R.id.calendarWeekLabel2,
                R.id.calendarWeekLabel3,
                R.id.calendarWeekLabel4,
                R.id.calendarWeekLabel5,
                R.id.calendarWeekLabel6
            )

            val cellIds = listOf(
                R.id.calendarWeekCell0,
                R.id.calendarWeekCell1,
                R.id.calendarWeekCell2,
                R.id.calendarWeekCell3,
                R.id.calendarWeekCell4,
                R.id.calendarWeekCell5,
                R.id.calendarWeekCell6
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

        private fun bindMonthCells(
            views: RemoteViews,
            state: PlanarioCalendarWidgetState
        ) {
            val rowIds = listOf(
                R.id.calendarMonthRow0,
                R.id.calendarMonthRow1,
                R.id.calendarMonthRow2,
                R.id.calendarMonthRow3,
                R.id.calendarMonthRow4,
                R.id.calendarMonthRow5
            )

            val cellIds = listOf(
                listOf(R.id.calendarMonthCell00, R.id.calendarMonthCell01, R.id.calendarMonthCell02, R.id.calendarMonthCell03, R.id.calendarMonthCell04, R.id.calendarMonthCell05, R.id.calendarMonthCell06),
                listOf(R.id.calendarMonthCell10, R.id.calendarMonthCell11, R.id.calendarMonthCell12, R.id.calendarMonthCell13, R.id.calendarMonthCell14, R.id.calendarMonthCell15, R.id.calendarMonthCell16),
                listOf(R.id.calendarMonthCell20, R.id.calendarMonthCell21, R.id.calendarMonthCell22, R.id.calendarMonthCell23, R.id.calendarMonthCell24, R.id.calendarMonthCell25, R.id.calendarMonthCell26),
                listOf(R.id.calendarMonthCell30, R.id.calendarMonthCell31, R.id.calendarMonthCell32, R.id.calendarMonthCell33, R.id.calendarMonthCell34, R.id.calendarMonthCell35, R.id.calendarMonthCell36),
                listOf(R.id.calendarMonthCell40, R.id.calendarMonthCell41, R.id.calendarMonthCell42, R.id.calendarMonthCell43, R.id.calendarMonthCell44, R.id.calendarMonthCell45, R.id.calendarMonthCell46),
                listOf(R.id.calendarMonthCell50, R.id.calendarMonthCell51, R.id.calendarMonthCell52, R.id.calendarMonthCell53, R.id.calendarMonthCell54, R.id.calendarMonthCell55, R.id.calendarMonthCell56)
            )

            rowIds.forEachIndexed { rowIndex, rowId ->
                val rowVisible = rowIndex < state.monthRows.size

                views.setViewVisibility(
                    rowId,
                    if (rowVisible) View.VISIBLE else View.GONE
                )
            }

            state.monthRows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { columnIndex, cell ->
                    val cellId = cellIds[rowIndex][columnIndex]

                    views.setTextViewText(
                        cellId,
                        cell.text
                    )

                    views.setTextColor(
                        cellId,
                        cell.textColor
                    )

                    views.setInt(
                        cellId,
                        "setBackgroundResource",
                        cell.backgroundRes
                    )
                }
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

        private fun loadWidgetState(context: Context): PlanarioCalendarWidgetState {
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

            val todayDay = data.studyDays[today]
            val savedTodayStudiedMinutes = data.timeEntries
                .filter { it.date == today }
                .sumOf { it.minutes }

            val activeTimerMinutes = if (activeTimerSnapshot.isActive && activeTimerSnapshot.date == today) {
                activeTimerSnapshot.elapsedMinutes()
            } else {
                0
            }

            val todayStudiedMinutes = savedTodayStudiedMinutes + activeTimerMinutes

            val todayGoalMinutes = todayDay?.dailyGoalMinutes ?: data.settings.dailyGoalMinutes

            val todayIsFreeDay = when {
                todayDay?.forceStudyDay == true -> false
                todayDay?.isRestDay == true -> true
                else -> data.settings.defaultFreeWeekDays.contains(today.dayOfWeek.value)
            }

            val todayIsFinishedManually = todayDay?.isFinishedManually == true

            val todayStatus = calculateStatus(
                date = today,
                studiedMinutes = todayStudiedMinutes,
                dailyGoalMinutes = todayGoalMinutes,
                isFreeDay = todayIsFreeDay,
                isFinishedManually = todayIsFinishedManually
            )

            val weekCells = buildWeekCells(
                context = context,
                darkMode = darkMode
            )

            val weekStudiedMinutes = calculateWeekStudiedMinutes(context)
            val completedWeekDays = weekCells.count { cell ->
                cell.status == DayStatus.COMPLETED
            }

            val restWeekDays = weekCells.count { cell ->
                cell.status == DayStatus.REST
            }

            val month = YearMonth.from(today)
            val monthRows = buildMonthRows(
                context = context,
                month = month,
                darkMode = darkMode
            )

            return PlanarioCalendarWidgetState(
                darkMode = darkMode,
                todayStatus = todayStatus,
                todayStudiedMinutes = todayStudiedMinutes,
                todayGoalMinutes = todayGoalMinutes,
                todayIsFinishedManually = todayIsFinishedManually,
                weekStudiedMinutes = weekStudiedMinutes,
                completedWeekDays = completedWeekDays,
                restWeekDays = restWeekDays,
                weekCells = weekCells,
                monthTitle = month.toSpanishMonthTitle(),
                monthRows = monthRows
            )
        }

        private fun buildWeekCells(
            context: Context,
            darkMode: Boolean
        ): List<PlanarioCalendarCell> {
            val data = StudyLocalDataSource(context).load()
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())

            val labels = listOf("L", "M", "X", "J", "V", "S", "D")

            return (0..6).map { offset ->
                val date = monday.plusDays(offset.toLong())
                val status = calculateDateStatus(
                    context = context,
                    date = date
                )

                PlanarioCalendarCell(
                    label = labels[offset],
                    text = if (date == today) {
                        "•${date.dayOfMonth}"
                    } else {
                        date.dayOfMonth.toString()
                    },
                    status = status,
                    backgroundRes = status.toStatusBackgroundRes(),
                    textColor = status.toStatusTextColor(darkMode)
                )
            }
        }

        private fun buildMonthRows(
            context: Context,
            month: YearMonth,
            darkMode: Boolean
        ): List<List<PlanarioCalendarCell>> {
            val firstDay = month.atDay(1)
            val leadingDays = firstDay.dayOfWeek.value - 1
            val calendarStart = firstDay.minusDays(leadingDays.toLong())

            val cells = (0 until 42).map { index ->
                val date = calendarStart.plusDays(index.toLong())
                val isCurrentMonth = YearMonth.from(date) == month

                if (!isCurrentMonth) {
                    PlanarioCalendarCell.empty(
                        label = "",
                        darkMode = darkMode
                    )
                } else {
                    val status = calculateDateStatus(
                        context = context,
                        date = date
                    )

                    PlanarioCalendarCell(
                        label = "",
                        text = if (date == LocalDate.now()) {
                            "•${date.dayOfMonth}"
                        } else {
                            date.dayOfMonth.toString()
                        },
                        status = status,
                        backgroundRes = status.toStatusBackgroundRes(),
                        textColor = status.toStatusTextColor(darkMode)
                    )
                }
            }

            return cells.chunked(7)
        }

        private fun calculateDateStatus(
            context: Context,
            date: LocalDate
        ): DayStatus {
            val dataSource = StudyLocalDataSource(context)
            val data = dataSource.load()
            val activeTimerSnapshot = dataSource.loadActiveStudyTimerSnapshot()
            val day = data.studyDays[date]

            val savedStudiedMinutes = data.timeEntries
                .filter { it.date == date }
                .sumOf { it.minutes }

            val activeTimerMinutes = if (date == LocalDate.now() && activeTimerSnapshot.isActive) {
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

            return calculateStatus(
                date = date,
                studiedMinutes = studiedMinutes,
                dailyGoalMinutes = dailyGoalMinutes,
                isFreeDay = isFreeDay,
                isFinishedManually = day?.isFinishedManually == true
            )
        }

        private fun calculateWeekStudiedMinutes(context: Context): Int {
            val dataSource = StudyLocalDataSource(context)
            val data = dataSource.load()
            val activeTimerSnapshot = dataSource.loadActiveStudyTimerSnapshot()
            val today = LocalDate.now()
            val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
            val sunday = monday.plusDays(6)

            val savedMinutes = data.timeEntries
                .filter { entry ->
                    !entry.date.isBefore(monday) && !entry.date.isAfter(sunday)
                }
                .sumOf { it.minutes }

            val activeTimerMinutes = if (activeTimerSnapshot.isActive && activeTimerSnapshot.date == today) {
                activeTimerSnapshot.elapsedMinutes()
            } else {
                0
            }

            return savedMinutes + activeTimerMinutes
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

        private fun YearMonth.toSpanishMonthTitle(): String {
            val formatter = DateTimeFormatter.ofPattern(
                "MMMM yyyy",
                Locale("es", "ES")
            )

            return atDay(1).format(formatter).replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase(Locale("es", "ES"))
                } else {
                    char.toString()
                }
            }
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

private data class PlanarioCalendarWidgetState(
    val darkMode: Boolean,
    val todayStatus: DayStatus,
    val todayStudiedMinutes: Int,
    val todayGoalMinutes: Int,
    val todayIsFinishedManually: Boolean,
    val weekStudiedMinutes: Int,
    val completedWeekDays: Int,
    val restWeekDays: Int,
    val weekCells: List<PlanarioCalendarCell>,
    val monthTitle: String,
    val monthRows: List<List<PlanarioCalendarCell>>
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

    val weekTitle: String
        get() = "Hoy · ${todayStatusText}"

    val todayStatusText: String
        get() {
            if (todayIsFinishedManually) return "Finalizado"

            return when (todayStatus) {
                DayStatus.EMPTY -> "Pendiente"
                DayStatus.IN_PROGRESS -> "En curso"
                DayStatus.COMPLETED -> "Completado"
                DayStatus.NOT_COMPLETED -> "No completado"
                DayStatus.REST -> "Día libre"
            }
        }

    val todayStatusTextColor: Int
        get() = when (todayStatus) {
            DayStatus.COMPLETED -> 0xFF166534.toInt()
            DayStatus.IN_PROGRESS -> 0xFF1D4ED8.toInt()
            DayStatus.REST -> 0xFF334155.toInt()
            DayStatus.NOT_COMPLETED -> 0xFFB91C1C.toInt()
            DayStatus.EMPTY -> 0xFF334155.toInt()
        }

    val todayStatusBackgroundRes: Int
        get() = when (todayStatus) {
            DayStatus.COMPLETED -> R.drawable.widget_today_status_completed
            DayStatus.IN_PROGRESS -> R.drawable.widget_today_status_in_progress
            DayStatus.REST -> R.drawable.widget_today_status_rest
            DayStatus.NOT_COMPLETED -> R.drawable.widget_today_status_not_completed
            DayStatus.EMPTY -> R.drawable.widget_today_status_empty
        }

    val todayProgressPercent: Int
        get() {
            if (todayGoalMinutes <= 0) return 0

            return ((todayStudiedMinutes.toFloat() / todayGoalMinutes.toFloat()) * 100f)
                .toInt()
                .coerceIn(0, 100)
        }

    val todayProgressText: String
        get() {
            return if (todayStatus == DayStatus.REST) {
                "Día libre"
            } else {
                "${todayStudiedMinutes.toCompactHourMinuteText()} / ${todayGoalMinutes.toCompactHourMinuteText()}"
            }
        }

    val todayMetaText: String
        get() {
            if (todayIsFinishedManually) {
                val difference = todayGoalMinutes - todayStudiedMinutes

                return when {
                    difference > 0 -> "Finalizado · faltaron ${difference.toCompactHourMinuteText()}"
                    difference < 0 -> "Finalizado · extra ${(-difference).toCompactHourMinuteText()}"
                    else -> "Finalizado · objetivo justo"
                }
            }

            return when (todayStatus) {
                DayStatus.REST -> "Hoy no se exige objetivo"
                DayStatus.COMPLETED -> "Objetivo diario cumplido"
                DayStatus.IN_PROGRESS -> "Estudio iniciado"
                DayStatus.NOT_COMPLETED -> "Día no completado"
                DayStatus.EMPTY -> "Aún sin registrar tiempo"
            }
        }

    val weekSummaryText: String
        get() = "${weekStudiedMinutes.toCompactHourMinuteText()} esta semana · $completedWeekDays completado(s) · $restWeekDays libre(s)"
}

private data class PlanarioCalendarCell(
    val label: String,
    val text: String,
    val status: DayStatus,
    val backgroundRes: Int,
    val textColor: Int
) {
    companion object {
        fun empty(
            label: String,
            darkMode: Boolean
        ): PlanarioCalendarCell {
            return PlanarioCalendarCell(
                label = label,
                text = "",
                status = DayStatus.EMPTY,
                backgroundRes = R.drawable.widget_today_status_empty,
                textColor = if (darkMode) {
                    0x00000000
                } else {
                    0x00000000
                }
            )
        }
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