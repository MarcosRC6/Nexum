package com.example.testario.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.example.testario.data.local.PreferencesManager

class BehaviorSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferencesManager = PreferencesManager(application)

    var prioritizeFailuresInNormalTests by mutableStateOf(
        preferencesManager.getPrioritizeFailuresInNormalTests()
    )
        private set

    var failureExitCorrectStreak by mutableStateOf(
        preferencesManager.getFailureExitCorrectStreak()
    )
        private set

    fun changePrioritizeFailuresInNormalTests(enabled: Boolean) {
        prioritizeFailuresInNormalTests = enabled
        preferencesManager.setPrioritizeFailuresInNormalTests(enabled)
    }

    fun changeFailureExitCorrectStreak(value: Int) {
        val safeValue = value.coerceIn(1, 10)
        failureExitCorrectStreak = safeValue
        preferencesManager.setFailureExitCorrectStreak(safeValue)
    }

    fun restoreDefaults() {
        changePrioritizeFailuresInNormalTests(true)
        changeFailureExitCorrectStreak(3)
    }
}
