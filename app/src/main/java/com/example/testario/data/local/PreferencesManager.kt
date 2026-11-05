package com.example.testario.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("testario_settings", Context.MODE_PRIVATE)

    fun getThemeMode(defaultMode: String): String {
        return prefs.getString("theme_mode", defaultMode) ?: defaultMode
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun getUiScale(): Float {
        return prefs.getFloat("ui_scale", 1.0f)
    }

    fun setUiScale(scale: Float) {
        prefs.edit().putFloat("ui_scale", scale).apply()
    }


    fun getHomeShowStudyToday(): Boolean =
        prefs.getBoolean("home_show_study_today", true)

    fun setHomeShowStudyToday(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_study_today", enabled).apply()
    }

    fun getHomeShowStudyTopic(): Boolean =
        prefs.getBoolean("home_show_study_topic", true)

    fun setHomeShowStudyTopic(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_study_topic", enabled).apply()
    }

    fun getHomeShowStudyTimer(): Boolean =
        prefs.getBoolean("home_show_study_timer", true)

    fun setHomeShowStudyTimer(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_study_timer", enabled).apply()
    }

    fun getHomeShowStudyObjectives(): Boolean =
        prefs.getBoolean("home_show_study_objectives", true)

    fun setHomeShowStudyObjectives(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_study_objectives", enabled).apply()
    }

    fun getHomeShowCreateTest(): Boolean =
        prefs.getBoolean("home_show_create_test", true)

    fun setHomeShowCreateTest(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_create_test", enabled).apply()
    }

    fun getHomeShowSummary(): Boolean =
        prefs.getBoolean("home_show_summary", true)

    fun setHomeShowSummary(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_summary", enabled).apply()
    }

    fun getHomeShowFailures(): Boolean =
        prefs.getBoolean("home_show_failures", true)

    fun setHomeShowFailures(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_failures", enabled).apply()
    }

    fun getHomeShowTemplates(): Boolean =
        prefs.getBoolean("home_show_templates", true)

    fun setHomeShowTemplates(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_templates", enabled).apply()
    }

    fun getHomeShowLibraryShortcut(): Boolean =
        prefs.getBoolean("home_show_library_shortcut", true)

    fun setHomeShowLibraryShortcut(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_library_shortcut", enabled).apply()
    }

    fun getHomeShowGroupsShortcut(): Boolean =
        prefs.getBoolean("home_show_groups_shortcut", false)

    fun setHomeShowGroupsShortcut(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_groups_shortcut", enabled).apply()
    }

    fun getHomeShowStatsShortcut(): Boolean =
        prefs.getBoolean("home_show_stats_shortcut", true)

    fun setHomeShowStatsShortcut(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_stats_shortcut", enabled).apply()
    }

    fun getHomeShowTestShortcut(): Boolean =
        prefs.getBoolean("home_show_test_shortcut", true)

    fun setHomeShowTestShortcut(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_test_shortcut", enabled).apply()
    }

    fun getHomeShowPlanShortcut(): Boolean =
        prefs.getBoolean("home_show_plan_shortcut", true)

    fun setHomeShowPlanShortcut(enabled: Boolean) {
        prefs.edit().putBoolean("home_show_plan_shortcut", enabled).apply()
    }

    fun getHomeWidgetOrder(defaultOrder: List<String>): List<String> {
        val raw = prefs.getString("home_widget_order", null) ?: return defaultOrder
        val saved = raw.split('|').filter { it.isNotBlank() }
        return (saved.filter { it in defaultOrder } + defaultOrder.filterNot { it in saved }).distinct()
    }

    fun setHomeWidgetOrder(order: List<String>) {
        prefs.edit().putString("home_widget_order", order.joinToString("|")).apply()
    }

    /**
     * Si está activo, los tests normales (Entrenamiento y Examen) conservan
     * el refuerzo inteligente de las preguntas que están en Fallos y de las
     * preguntas con peor porcentaje de acierto.
     *
     * true por defecto para mantener el comportamiento histórico de Testario.
     */
    fun getPrioritizeFailuresInNormalTests(): Boolean {
        return prefs.getBoolean("prioritize_failures_in_normal_tests", true)
    }

    fun setPrioritizeFailuresInNormalTests(enabled: Boolean) {
        prefs.edit().putBoolean("prioritize_failures_in_normal_tests", enabled).apply()
    }

    /**
     * Número de aciertos consecutivos necesarios para que una pregunta salga
     * automáticamente de la lista de Fallos.
     */
    fun getFailureExitCorrectStreak(): Int {
        return prefs.getInt("failure_exit_correct_streak", 3).coerceIn(1, 10)
    }

    fun setFailureExitCorrectStreak(value: Int) {
        prefs.edit().putInt("failure_exit_correct_streak", value.coerceIn(1, 10)).apply()
    }
}
