package com.example.testario.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.TestQuestionState
import com.example.testario.data.model.TestResult
import com.example.testario.data.repository.StatsRepository

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = TestarioLocalStore.get(application)
    private val preferencesManager = PreferencesManager(application)
    private val statsRepo = StatsRepository(localStore, preferencesManager)

    fun registerTestResults(results: List<TestQuestionState>, finalTestResult: TestResult, totalTimeSpentMinutes: Int) { statsRepo.registerTestResults(results, finalTestResult, totalTimeSpentMinutes) }
    fun clearStatsForTopic(topicId: String) = statsRepo.clearStatsForTopic(topicId)
    fun clearAllStats() = statsRepo.clearAllStats()
    fun removeFromFailures(questionId: String) = statsRepo.removeFromFailures(questionId)
    fun registerAnswer(questionId: String, selectedAnswerId: String?, timeSpentSeconds: Long = 0) = statsRepo.registerAnswer(questionId, selectedAnswerId, timeSpentSeconds)
    fun saveTestResult(result: TestResult, totalTimeSpentMinutes: Int) = statsRepo.saveTestResult(result, totalTimeSpentMinutes)
}