package com.example.planario.core.model

data class StudySettings(
    val dailyGoalMinutes: Int = 360,

    val notificationsEnabled: Boolean = true,

    val studyReminderEnabled: Boolean = true,
    val studyReminderTime: String = "09:00",

    val fixedObjectivesReminderEnabled: Boolean = true,
    val fixedObjectivesReminderTime: String = "20:00",

    val restDayNotificationEnabled: Boolean = true,
    val restDayNotificationTime: String = "09:00",

    val dailySummaryEnabled: Boolean = true,
    val dailySummaryTime: String = "22:00",

    val defaultFreeWeekDays: Set<Int> = emptySet(),

    val darkMode: ThemeMode = ThemeMode.SYSTEM,
    val lastPomodoroMinutes: Int = 25
) {
    val hasAnyNotificationEnabled: Boolean
        get() {
            return notificationsEnabled && (
                studyReminderEnabled ||
                    fixedObjectivesReminderEnabled ||
                    restDayNotificationEnabled ||
                    dailySummaryEnabled
                )
        }

    fun isDefaultFreeDay(dayOfWeekValue: Int): Boolean {
        return defaultFreeWeekDays.contains(dayOfWeekValue)
    }
}