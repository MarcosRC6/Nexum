package com.example.planario.data.local

import android.content.Context
import com.example.planario.app.widget.PlanarioWidgetUpdater
import com.example.planario.core.model.DayStatus
import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyDay
import com.example.planario.core.model.StudySettings
import com.example.planario.core.model.StudyTopic
import com.example.planario.core.model.ThemeMode
import com.example.planario.core.model.TimeEntry
import com.example.planario.core.model.TimeEntryType
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime

class StudyLocalDataSource(
    context: Context
) {
    private val appContext = context.applicationContext

    private val preferences = appContext.getSharedPreferences(
        "planario_study_data",
        Context.MODE_PRIVATE
    )

    private val activeTimerPreferences = appContext.getSharedPreferences(
        "planario_active_study_timer",
        Context.MODE_PRIVATE
    )

    fun load(): StoredStudyData {
        val rawJson = preferences.getString(KEY_DATA, null) ?: return StoredStudyData()

        return try {
            val root = JSONObject(rawJson)
            jsonObjectToStoredStudyData(root)
        } catch (_: Exception) {
            // Nunca descartamos el JSON dañado: el siguiente guardado podría
            // reemplazarlo por una base vacía. Guardamos la copia de recuperación
            // antes de devolver el estado predeterminado a la interfaz.
            if (!preferences.contains(KEY_RECOVERY_DATA)) {
                preferences.edit().putString(KEY_RECOVERY_DATA, rawJson).commit()
            }
            StoredStudyData()
        }
    }

    fun save(data: StoredStudyData) {
        val root = storedStudyDataToJsonObject(data)

        val saved = preferences.edit()
            .putString(KEY_DATA, root.toString())
            .commit()

        if (saved) {
            PlanarioWidgetUpdater.updateTodayWidget(appContext)
        }
    }

    fun exportToJson(data: StoredStudyData): String {
        return storedStudyDataToJsonObject(data).toString(2)
    }

    fun importFromJson(json: String): StoredStudyData? {
        return try {
            val root = JSONObject(json)
            jsonObjectToStoredStudyData(root)
        } catch (_: Exception) {
            null
        }
    }


    fun startActiveStudyTimerTracking(
        date: LocalDate = LocalDate.now()
    ) {
        saveActiveStudyTimerSnapshot(
            StoredActiveStudyTimerSnapshot(
                state = StoredActiveStudyTimerState.RUNNING,
                date = date,
                accumulatedSeconds = 0L,
                startedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    fun pauseActiveStudyTimerTracking() {
        val current = loadActiveStudyTimerSnapshot()
        if (current.state != StoredActiveStudyTimerState.RUNNING) return

        saveActiveStudyTimerSnapshot(
            current.copy(
                state = StoredActiveStudyTimerState.PAUSED,
                accumulatedSeconds = current.elapsedSeconds(),
                startedAtEpochMillis = 0L
            )
        )
    }

    fun resumeActiveStudyTimerTracking() {
        val current = loadActiveStudyTimerSnapshot()
        if (current.state == StoredActiveStudyTimerState.STOPPED) return

        saveActiveStudyTimerSnapshot(
            current.copy(
                state = StoredActiveStudyTimerState.RUNNING,
                accumulatedSeconds = current.elapsedSeconds(),
                startedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    fun clearActiveStudyTimerTracking() {
        saveActiveStudyTimerSnapshot(
            StoredActiveStudyTimerSnapshot()
        )
    }

    fun restorePausedActiveStudyTimerTracking(
        date: LocalDate,
        elapsedSeconds: Long
    ) {
        saveActiveStudyTimerSnapshot(
            StoredActiveStudyTimerSnapshot(
                state = StoredActiveStudyTimerState.PAUSED,
                date = date,
                accumulatedSeconds = elapsedSeconds.coerceAtLeast(0L),
                startedAtEpochMillis = 0L
            )
        )
    }

    fun loadActiveStudyTimerSnapshot(): StoredActiveStudyTimerSnapshot {
        val state = runCatching {
            StoredActiveStudyTimerState.valueOf(
                activeTimerPreferences.getString(
                    KEY_ACTIVE_TIMER_STATE,
                    StoredActiveStudyTimerState.STOPPED.name
                ) ?: StoredActiveStudyTimerState.STOPPED.name
            )
        }.getOrDefault(StoredActiveStudyTimerState.STOPPED)

        val date = runCatching {
            val rawDate = activeTimerPreferences.getString(KEY_ACTIVE_TIMER_DATE, null)
            if (rawDate.isNullOrBlank()) {
                null
            } else {
                LocalDate.parse(rawDate)
            }
        }.getOrNull()

        val snapshot = StoredActiveStudyTimerSnapshot(
            state = state,
            date = date,
            accumulatedSeconds = activeTimerPreferences.getLong(
                KEY_ACTIVE_TIMER_ACCUMULATED_SECONDS,
                0L
            ),
            startedAtEpochMillis = activeTimerPreferences.getLong(
                KEY_ACTIVE_TIMER_STARTED_AT,
                0L
            )
        )

        if (snapshot.date != null && snapshot.date != LocalDate.now()) {
            return StoredActiveStudyTimerSnapshot()
        }

        return snapshot
    }

    private fun saveActiveStudyTimerSnapshot(
        snapshot: StoredActiveStudyTimerSnapshot
    ) {
        val saved = activeTimerPreferences.edit()
            .putString(KEY_ACTIVE_TIMER_STATE, snapshot.state.name)
            .putString(KEY_ACTIVE_TIMER_DATE, snapshot.date?.toString())
            .putLong(KEY_ACTIVE_TIMER_ACCUMULATED_SECONDS, snapshot.accumulatedSeconds)
            .putLong(KEY_ACTIVE_TIMER_STARTED_AT, snapshot.startedAtEpochMillis)
            .commit()

        if (saved) {
            PlanarioWidgetUpdater.updateTodayWidget(appContext)
        }
    }

    companion object {
        private const val KEY_DATA = "study_data"
        private const val KEY_RECOVERY_DATA = "study_data_corrupt_recovery"

        private const val KEY_ACTIVE_TIMER_STATE = "active_timer_state"
        private const val KEY_ACTIVE_TIMER_DATE = "active_timer_date"
        private const val KEY_ACTIVE_TIMER_ACCUMULATED_SECONDS = "active_timer_accumulated_seconds"
        private const val KEY_ACTIVE_TIMER_STARTED_AT = "active_timer_started_at"
    }
}

data class StoredStudyData(
    val topics: List<StudyTopic> = emptyList(),
    val fixedObjectives: List<FixedObjective> = emptyList(),
    val studyDays: Map<LocalDate, StudyDay> = emptyMap(),
    val timeEntries: List<TimeEntry> = emptyList(),
    val settings: StudySettings = StudySettings()
)

enum class StoredActiveStudyTimerState {
    STOPPED,
    RUNNING,
    PAUSED
}

data class StoredActiveStudyTimerSnapshot(
    val state: StoredActiveStudyTimerState = StoredActiveStudyTimerState.STOPPED,
    val date: LocalDate? = null,
    val accumulatedSeconds: Long = 0L,
    val startedAtEpochMillis: Long = 0L
) {
    val isActive: Boolean
        get() = state != StoredActiveStudyTimerState.STOPPED && date == LocalDate.now()

    val isRunning: Boolean
        get() = state == StoredActiveStudyTimerState.RUNNING && date == LocalDate.now()

    fun elapsedSeconds(
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Long {
        if (!isActive) return 0L

        val runningSeconds = if (state == StoredActiveStudyTimerState.RUNNING && startedAtEpochMillis > 0L) {
            ((nowEpochMillis - startedAtEpochMillis) / 1000L).coerceAtLeast(0L)
        } else {
            0L
        }

        return (accumulatedSeconds + runningSeconds).coerceAtLeast(0L)
    }

    fun elapsedMinutes(
        nowEpochMillis: Long = System.currentTimeMillis()
    ): Int {
        return (elapsedSeconds(nowEpochMillis) / 60L).toInt()
    }
}

private fun studyTopicsToJsonArray(topics: List<StudyTopic>): JSONArray {
    val array = JSONArray()

    topics.forEach { topic ->
        array.put(
            JSONObject().apply {
                put("id", topic.id)
                put("name", topic.name)
                put("queuePosition", topic.queuePosition)
                put("round", topic.round)
                put("isArchived", topic.isArchived)
            }
        )
    }

    return array
}

private fun jsonArrayToStudyTopics(array: JSONArray?): List<StudyTopic> {
    if (array == null) return emptyList()

    val result = mutableListOf<StudyTopic>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue

        val id = item.optString("id")
        val name = item.optString("name")

        if (id.isBlank() || name.isBlank()) continue

        result.add(
            StudyTopic(
                id = id,
                name = name,
                queuePosition = item.optInt("queuePosition", 0).coerceAtLeast(0),
                round = item.optInt("round", 1).coerceAtLeast(1),
                isArchived = item.optBoolean("isArchived", false)
            )
        )
    }

    return result
}

private fun fixedObjectivesToJsonArray(objectives: List<FixedObjective>): JSONArray {
    val array = JSONArray()

    objectives.forEach { objective ->
        array.put(
            JSONObject().apply {
                put("id", objective.id)
                put("name", objective.name)
                put("targetMinutes", objective.targetMinutes)
                put("isEnabled", objective.isEnabled)
                put("order", objective.order)
                put("linkedTestTopicIds", JSONArray(objective.linkedTestTopicIds.toList()))
                put("linkedTestQuestionCount", objective.linkedTestQuestionCount.coerceIn(1, 500))
                objective.linkedTestTemplateId?.let { put("linkedTestTemplateId", it) }
            }
        )
    }

    return array
}

private fun jsonArrayToFixedObjectives(array: JSONArray?): List<FixedObjective> {
    if (array == null) return emptyList()

    val result = mutableListOf<FixedObjective>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue

        val id = item.optString("id")
        val name = item.optString("name")

        if (id.isBlank() || name.isBlank()) continue

        result.add(
            FixedObjective(
                id = id,
                name = name,
                targetMinutes = item.optInt("targetMinutes", 1).coerceAtLeast(1),
                isEnabled = item.optBoolean("isEnabled", true),
                order = item.optInt("order", 0),
                linkedTestTopicIds = buildSet {
                    val ids = item.optJSONArray("linkedTestTopicIds")
                    if (ids != null) {
                        for (index in 0 until ids.length()) {
                            val id = ids.optString(index).trim()
                            if (id.isNotEmpty()) add(id)
                        }
                    }
                },
                linkedTestQuestionCount = item.optInt("linkedTestQuestionCount", 25).coerceIn(1, 500),
                linkedTestTemplateId = if (item.isNull("linkedTestTemplateId")) null
                else item.optString("linkedTestTemplateId").trim().takeIf { it.isNotEmpty() }
            )
        )
    }

    return result
}

private fun studyDaysToJsonArray(days: List<StudyDay>): JSONArray {
    val array = JSONArray()

    days.forEach { day ->
        array.put(
            JSONObject().apply {
                put("date", day.date.toString())
                put("status", day.status.name)
                put("isRestDay", day.isRestDay)
                put("forceStudyDay", day.forceStudyDay)
                put("isFinishedManually", day.isFinishedManually)
                put("studiedMinutes", day.studiedMinutes)
                put("dailyGoalMinutes", day.dailyGoalMinutes)
                put("activeTopicIdSnapshot", day.activeTopicIdSnapshot)
                put("activeTopicNameSnapshot", day.activeTopicNameSnapshot)
                put("completedFixedObjectiveIds", stringSetToJsonArray(day.completedFixedObjectiveIds))
            }
        )
    }

    return array
}

private fun jsonArrayToStudyDays(array: JSONArray?): Map<LocalDate, StudyDay> {
    if (array == null) return emptyMap()

    val result = mutableMapOf<LocalDate, StudyDay>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue

        val date = runCatching {
            LocalDate.parse(item.optString("date"))
        }.getOrNull() ?: continue

        val status = runCatching {
            DayStatus.valueOf(item.optString("status"))
        }.getOrDefault(DayStatus.EMPTY)

        val day = StudyDay(
            date = date,
            status = status,
            isRestDay = item.optBoolean("isRestDay", false),
            forceStudyDay = item.optBoolean("forceStudyDay", false),
            isFinishedManually = item.optBoolean("isFinishedManually", false),
            studiedMinutes = item.optInt("studiedMinutes", 0),
            dailyGoalMinutes = item.optInt("dailyGoalMinutes", 360),
            activeTopicIdSnapshot = item.optNullableString("activeTopicIdSnapshot"),
            activeTopicNameSnapshot = item.optNullableString("activeTopicNameSnapshot"),
            completedFixedObjectiveIds = jsonArrayToStringSet(
                item.optJSONArray("completedFixedObjectiveIds")
            )
        )

        result[date] = day
    }

    return result
}

private fun stringSetToJsonArray(values: Set<String>): JSONArray {
    val array = JSONArray()

    values.forEach { value ->
        array.put(value)
    }

    return array
}

private fun jsonArrayToStringSet(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()

    val result = mutableSetOf<String>()

    for (index in 0 until array.length()) {
        val value = array.optString(index)
        if (value.isNotBlank()) {
            result.add(value)
        }
    }

    return result
}

private fun timeEntriesToJsonArray(entries: List<TimeEntry>): JSONArray {
    val array = JSONArray()

    entries.forEach { entry ->
        array.put(
            JSONObject().apply {
                put("id", entry.id)
                put("date", entry.date.toString())
                put("minutes", entry.minutes)
                put("type", entry.type.name)
                put("label", entry.label)
                put("createdAt", entry.createdAt.toString())
            }
        )
    }

    return array
}

private fun jsonArrayToTimeEntries(array: JSONArray?): List<TimeEntry> {
    if (array == null) return emptyList()

    val result = mutableListOf<TimeEntry>()

    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue

        val id = item.optString("id")

        val date = runCatching {
            LocalDate.parse(item.optString("date"))
        }.getOrNull() ?: continue

        val createdAt = runCatching {
            LocalDateTime.parse(item.optString("createdAt"))
        }.getOrDefault(LocalDateTime.now())

        val type = runCatching {
            TimeEntryType.valueOf(item.optString("type"))
        }.getOrDefault(TimeEntryType.MANUAL)

        val minutes = item.optInt("minutes", 0)
        if (id.isBlank() || minutes <= 0) continue

        result.add(
            TimeEntry(
                id = id,
                date = date,
                minutes = minutes,
                type = type,
                label = item.optString("label", "Estudio"),
                createdAt = createdAt
            )
        )
    }

    return result
}

private fun studySettingsToJsonObject(settings: StudySettings): JSONObject {
    return JSONObject().apply {
        put("dailyGoalMinutes", settings.dailyGoalMinutes)
        put("notificationsEnabled", settings.notificationsEnabled)

        put("studyReminderEnabled", settings.studyReminderEnabled)
        put("studyReminderTime", settings.studyReminderTime)

        put("fixedObjectivesReminderEnabled", settings.fixedObjectivesReminderEnabled)
        put("fixedObjectivesReminderTime", settings.fixedObjectivesReminderTime)

        put("restDayNotificationEnabled", settings.restDayNotificationEnabled)
        put("restDayNotificationTime", settings.restDayNotificationTime)

        put("dailySummaryEnabled", settings.dailySummaryEnabled)
        put("dailySummaryTime", settings.dailySummaryTime)

        put("defaultFreeWeekDays", intSetToJsonArray(settings.defaultFreeWeekDays))

        put("darkMode", settings.darkMode.name)
        put("lastPomodoroMinutes", settings.lastPomodoroMinutes)
    }
}

private fun jsonObjectToStudySettings(item: JSONObject?): StudySettings {
    if (item == null) return StudySettings()

    val themeMode = runCatching {
        ThemeMode.valueOf(item.optString("darkMode"))
    }.getOrDefault(ThemeMode.SYSTEM)

    return StudySettings(
        dailyGoalMinutes = item.optInt("dailyGoalMinutes", 360).coerceIn(30, 720),
        notificationsEnabled = item.optBoolean("notificationsEnabled", true),

        studyReminderEnabled = item.optBoolean("studyReminderEnabled", true),
        studyReminderTime = item.optTimeString("studyReminderTime", "09:00"),

        fixedObjectivesReminderEnabled = item.optBoolean("fixedObjectivesReminderEnabled", true),
        fixedObjectivesReminderTime = item.optTimeString("fixedObjectivesReminderTime", "20:00"),

        restDayNotificationEnabled = item.optBoolean("restDayNotificationEnabled", true),
        restDayNotificationTime = item.optTimeString("restDayNotificationTime", "09:00"),

        dailySummaryEnabled = item.optBoolean("dailySummaryEnabled", true),
        dailySummaryTime = item.optTimeString("dailySummaryTime", "22:00"),

        defaultFreeWeekDays = jsonArrayToIntSet(
            item.optJSONArray("defaultFreeWeekDays")
        ),

        darkMode = themeMode,
        lastPomodoroMinutes = item.optInt("lastPomodoroMinutes", 25).coerceIn(1, 24 * 60)
    )
}

private fun intSetToJsonArray(values: Set<Int>): JSONArray {
    val array = JSONArray()

    values.sorted().forEach { value ->
        array.put(value)
    }

    return array
}

private fun jsonArrayToIntSet(array: JSONArray?): Set<Int> {
    if (array == null) return emptySet()

    val result = mutableSetOf<Int>()

    for (index in 0 until array.length()) {
        val value = array.optInt(index, -1)
        if (value in 1..7) {
            result.add(value)
        }
    }

    return result
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key)) return null
    if (isNull(key)) return null

    return optString(key)
}

private fun JSONObject.optTimeString(
    key: String,
    defaultValue: String
): String {
    val value = optString(key, defaultValue)
    return if (value.isValidHourMinuteTime()) value else defaultValue
}

private fun String.isValidHourMinuteTime(): Boolean {
    val parts = split(":")
    if (parts.size != 2) return false

    val hours = parts[0].toIntOrNull() ?: return false
    val minutes = parts[1].toIntOrNull() ?: return false

    return hours in 0..23 && minutes in 0..59
}

private fun storedStudyDataToJsonObject(data: StoredStudyData): JSONObject {
    return JSONObject().apply {
        put("version", 2)
        put("topics", studyTopicsToJsonArray(data.topics))
        put("fixedObjectives", fixedObjectivesToJsonArray(data.fixedObjectives))
        put("studyDays", studyDaysToJsonArray(data.studyDays.values.toList()))
        put("timeEntries", timeEntriesToJsonArray(data.timeEntries))
        put("settings", studySettingsToJsonObject(data.settings))
    }
}

private fun jsonObjectToStoredStudyData(root: JSONObject): StoredStudyData {
    return StoredStudyData(
        topics = jsonArrayToStudyTopics(root.optJSONArray("topics")),
        fixedObjectives = jsonArrayToFixedObjectives(root.optJSONArray("fixedObjectives")),
        studyDays = jsonArrayToStudyDays(root.optJSONArray("studyDays")),
        timeEntries = jsonArrayToTimeEntries(root.optJSONArray("timeEntries")),
        settings = jsonObjectToStudySettings(root.optJSONObject("settings"))
    )
}