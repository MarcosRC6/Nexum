package com.example.testario.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.testario.data.local.PreferencesManager

class HomeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)

    var showStudyToday by mutableStateOf(preferencesManager.getHomeShowStudyToday())
        private set

    var showStudyTopic by mutableStateOf(preferencesManager.getHomeShowStudyTopic())
        private set

    var showStudyTimer by mutableStateOf(preferencesManager.getHomeShowStudyTimer())
        private set

    var showStudyObjectives by mutableStateOf(preferencesManager.getHomeShowStudyObjectives())
        private set

    var showCreateTest by mutableStateOf(preferencesManager.getHomeShowCreateTest())
        private set

    var showSummary by mutableStateOf(preferencesManager.getHomeShowSummary())
        private set

    var showFailures by mutableStateOf(preferencesManager.getHomeShowFailures())
        private set

    var showTemplates by mutableStateOf(preferencesManager.getHomeShowTemplates())
        private set

    var showLibraryShortcut by mutableStateOf(preferencesManager.getHomeShowLibraryShortcut())
        private set

    var showGroupsShortcut by mutableStateOf(preferencesManager.getHomeShowGroupsShortcut())
        private set

    var showStatsShortcut by mutableStateOf(preferencesManager.getHomeShowStatsShortcut())
        private set

    var showTestShortcut by mutableStateOf(preferencesManager.getHomeShowTestShortcut())
        private set

    var showPlanShortcut by mutableStateOf(preferencesManager.getHomeShowPlanShortcut())
        private set

    var widgetOrder by mutableStateOf(preferencesManager.getHomeWidgetOrder(DEFAULT_WIDGET_ORDER))
        private set

    fun changeShowStudyToday(enabled: Boolean) {
        showStudyToday = enabled
        preferencesManager.setHomeShowStudyToday(enabled)
    }

    fun changeShowStudyTopic(enabled: Boolean) {
        showStudyTopic = enabled
        preferencesManager.setHomeShowStudyTopic(enabled)
    }

    fun changeShowStudyTimer(enabled: Boolean) {
        showStudyTimer = enabled
        preferencesManager.setHomeShowStudyTimer(enabled)
    }

    fun changeShowStudyObjectives(enabled: Boolean) {
        showStudyObjectives = enabled
        preferencesManager.setHomeShowStudyObjectives(enabled)
    }

    fun changeShowCreateTest(enabled: Boolean) {
        showCreateTest = enabled
        preferencesManager.setHomeShowCreateTest(enabled)
    }

    fun changeShowSummary(enabled: Boolean) {
        showSummary = enabled
        preferencesManager.setHomeShowSummary(enabled)
    }

    fun changeShowFailures(enabled: Boolean) {
        showFailures = enabled
        preferencesManager.setHomeShowFailures(enabled)
    }

    fun changeShowTemplates(enabled: Boolean) {
        showTemplates = enabled
        preferencesManager.setHomeShowTemplates(enabled)
    }

    fun changeShowLibraryShortcut(enabled: Boolean) {
        showLibraryShortcut = enabled
        preferencesManager.setHomeShowLibraryShortcut(enabled)
    }

    fun changeShowGroupsShortcut(enabled: Boolean) {
        showGroupsShortcut = enabled
        preferencesManager.setHomeShowGroupsShortcut(enabled)
    }

    fun changeShowStatsShortcut(enabled: Boolean) {
        showStatsShortcut = enabled
        preferencesManager.setHomeShowStatsShortcut(enabled)
    }

    fun changeShowTestShortcut(enabled: Boolean) {
        showTestShortcut = enabled
        preferencesManager.setHomeShowTestShortcut(enabled)
    }

    fun changeShowPlanShortcut(enabled: Boolean) {
        showPlanShortcut = enabled
        preferencesManager.setHomeShowPlanShortcut(enabled)
    }

    fun moveWidgetRelative(
        widgetId: String,
        direction: Int,
        visibleIds: List<String>,
        persistImmediately: Boolean = true
    ) {
        if (direction == 0) return
        val visibleIndex = visibleIds.indexOf(widgetId)
        if (visibleIndex < 0) return
        val targetVisibleIndex = (visibleIndex + direction).coerceIn(0, visibleIds.lastIndex)
        if (targetVisibleIndex == visibleIndex) return
        val targetId = visibleIds[targetVisibleIndex]
        val mutable = widgetOrder.toMutableList()
        val from = mutable.indexOf(widgetId)
        val to = mutable.indexOf(targetId)
        if (from < 0 || to < 0) return
        mutable[from] = targetId
        mutable[to] = widgetId
        widgetOrder = mutable

        if (persistImmediately) {
            persistWidgetOrder()
        }
    }

    fun persistWidgetOrder() {
        preferencesManager.setHomeWidgetOrder(widgetOrder)
    }

    fun restoreDefaults() {
        changeShowStudyToday(true)
        changeShowStudyTopic(true)
        changeShowStudyTimer(true)
        changeShowStudyObjectives(true)
        changeShowCreateTest(true)
        changeShowSummary(true)
        changeShowFailures(true)
        changeShowTemplates(true)
        changeShowLibraryShortcut(true)
        changeShowGroupsShortcut(false)
        changeShowTestShortcut(true)
        changeShowPlanShortcut(true)
        changeShowStatsShortcut(true)
        widgetOrder = DEFAULT_WIDGET_ORDER
        preferencesManager.setHomeWidgetOrder(widgetOrder)
    }

    fun isDefaultLayout(): Boolean =
        showStudyToday &&
            showStudyTopic &&
            showStudyTimer &&
            showStudyObjectives &&
            showCreateTest &&
            showSummary &&
            showFailures &&
            showTemplates &&
            showLibraryShortcut &&
            !showGroupsShortcut &&
            showTestShortcut &&
            showPlanShortcut &&
            showStatsShortcut &&
            widgetOrder == DEFAULT_WIDGET_ORDER

    companion object {
        const val WIDGET_TODAY = "today"
        const val WIDGET_TOPIC = "topic"
        const val WIDGET_TIMER = "timer"
        const val WIDGET_CREATE_TEST = "create_test"
        const val WIDGET_SUMMARY = "summary"
        const val WIDGET_FAILURES = "failures"
        const val WIDGET_TEMPLATES = "templates"
        const val WIDGET_QUICK_ACCESS = "quick_access"

        val DEFAULT_WIDGET_ORDER = listOf(
            WIDGET_TODAY,
            WIDGET_TOPIC,
            WIDGET_TIMER,
            WIDGET_CREATE_TEST,
            WIDGET_SUMMARY,
            WIDGET_FAILURES,
            WIDGET_TEMPLATES,
            WIDGET_QUICK_ACCESS
        )
    }
}
