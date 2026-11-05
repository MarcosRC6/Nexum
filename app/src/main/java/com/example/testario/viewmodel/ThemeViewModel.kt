package com.example.testario.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.planario.app.widget.PlanarioWidgetUpdater
import com.example.testario.data.local.PreferencesManager

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    enum class ThemeMode { LIGHT, DARK, SYSTEM }

    private val preferencesManager = PreferencesManager(application)

    var themeMode by mutableStateOf(loadThemeMode())
        private set

    var uiScale by mutableStateOf(loadUiScale())
        private set

    private fun loadThemeMode(): ThemeMode {
        val saved = preferencesManager.getThemeMode(ThemeMode.SYSTEM.name)
        return try { ThemeMode.valueOf(saved) } catch (_: Exception) { ThemeMode.SYSTEM }
    }

    fun changeThemeMode(mode: ThemeMode) {
        themeMode = mode
        preferencesManager.setThemeMode(mode.name)
        PlanarioWidgetUpdater.updateTodayWidget(getApplication())
    }

    private fun loadUiScale(): Float {
        return preferencesManager.getUiScale()
    }

    fun changeUiScale(scale: Float) {
        uiScale = scale
        preferencesManager.setUiScale(scale)
    }
}