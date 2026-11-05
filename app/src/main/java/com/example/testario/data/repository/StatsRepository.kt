package com.example.testario.data.repository

import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.HistoricalResult
import com.example.testario.data.model.QuestionStats
import com.example.testario.data.model.TestQuestionState
import com.example.testario.data.model.TestResult

class StatsRepository(
    private val localStore: TestarioLocalStore,
    private val preferencesManager: PreferencesManager
) {

    fun clearStatsForTopic(topicId: String) {
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    if (topic.id == topicId) {
                        topic.copy(questions = topic.questions.map { it.copy(stats = QuestionStats()) })
                    } else topic
                }
            )
        )
    }

    fun clearAllStats() {
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                testsDone = 0,
                totalTrainingMinutes = 0,
                testHistory = emptyList(),
                topics = db.topics.map { topic ->
                    topic.copy(questions = topic.questions.map { it.copy(stats = QuestionStats()) })
                }
            )
        )
    }

    fun registerAnswer(questionId: String, selectedAnswerId: String?, timeSpentSeconds: Long = 0) {
        val db = localStore.database.value
        val failureExitCorrectStreak = preferencesManager.getFailureExitCorrectStreak()
        val newTopics = db.topics.map { topic ->
            topic.copy(
                questions = topic.questions.map { question ->
                    if (question.id != questionId) question else {
                        val isBlank = selectedAnswerId == null
                        val isCorrect = selectedAnswerId == question.correctAnswerId
                        val isWrong = !isBlank && !isCorrect
                        val newStreak = if (isCorrect) question.stats.correctStreak + 1 else 0
                        val stillInFailures = when {
                            isCorrect && newStreak >= failureExitCorrectStreak -> false
                            isWrong -> true
                            else -> question.stats.inFailures
                        }

                        question.copy(
                            stats = question.stats.copy(
                                timesAnswered = question.stats.timesAnswered + 1,
                                timesCorrect = question.stats.timesCorrect + if (isCorrect) 1 else 0,
                                timesWrong = question.stats.timesWrong + if (isWrong) 1 else 0,
                                timesBlank = question.stats.timesBlank + if (isBlank) 1 else 0,
                                lastAnswerId = selectedAnswerId,
                                lastAnsweredAt = System.currentTimeMillis(),
                                correctStreak = newStreak,
                                inFailures = stillInFailures,
                                totalTimeSpentSeconds = question.stats.totalTimeSpentSeconds + timeSpentSeconds
                            )
                        )
                    }
                }
            )
        }
        localStore.saveDatabase(db.copy(topics = newTopics))
    }

    fun saveTestResult(result: TestResult, totalTimeSpentMinutes: Int) {
        val db = localStore.database.value
        val history = db.testHistory.toMutableList()

        history.add(HistoricalResult(timestamp = System.currentTimeMillis(), score = result.score, percent = result.percent))
        if (history.size > 10) history.removeAt(0)

        localStore.saveDatabase(
            db.copy(
                testHistory = history,
                testsDone = db.testsDone + 1,
                totalTrainingMinutes = db.totalTrainingMinutes + totalTimeSpentMinutes
            )
        )
    }

    fun registerTestResults(results: List<TestQuestionState>, finalTestResult: TestResult, totalTimeSpentMinutes: Int) {
        val db = localStore.database.value
        val failureExitCorrectStreak = preferencesManager.getFailureExitCorrectStreak()
        val history = db.testHistory.toMutableList()

        // 1. Actualizar historial general (esto soluciona el guardado dividido que tenías)
        history.add(HistoricalResult(timestamp = System.currentTimeMillis(), score = finalTestResult.score, percent = finalTestResult.percent))
        if (history.size > 10) history.removeAt(0)

        // 2. Mapear los resultados para acceso rápido
        val resultsMap = results.associateBy { it.question.id }

        // 3. Procesar todas las preguntas del test en memoria
        val newTopics = db.topics.map { topic ->
            topic.copy(
                questions = topic.questions.map { question ->
                    val result = resultsMap[question.id]
                    if (result != null) {
                        val selectedAnswerId = result.selectedAnswerId
                        val isBlank = selectedAnswerId == null
                        val isCorrect = selectedAnswerId == question.correctAnswerId
                        val isWrong = !isBlank && !isCorrect

                        val newStreak = if (isCorrect) question.stats.correctStreak + 1 else 0
                        val stillInFailures = when {
                            isCorrect && newStreak >= failureExitCorrectStreak -> false
                            isWrong -> true
                            else -> question.stats.inFailures
                        }

                        question.copy(
                            stats = question.stats.copy(
                                timesAnswered = question.stats.timesAnswered + 1,
                                timesCorrect = question.stats.timesCorrect + if (isCorrect) 1 else 0,
                                timesWrong = question.stats.timesWrong + if (isWrong) 1 else 0,
                                timesBlank = question.stats.timesBlank + if (isBlank) 1 else 0,
                                lastAnswerId = selectedAnswerId,
                                lastAnsweredAt = System.currentTimeMillis(),
                                correctStreak = newStreak,
                                inFailures = stillInFailures,
                                totalTimeSpentSeconds = question.stats.totalTimeSpentSeconds + result.timeSpentSeconds
                            )
                        )
                    } else {
                        question // No estaba en el test, se queda igual
                    }
                }
            )
        }

        // 4. Guardar una ÚNICA vez en disco
        localStore.saveDatabase(
            db.copy(
                topics = newTopics,
                testHistory = history,
                testsDone = db.testsDone + 1,
                totalTrainingMinutes = db.totalTrainingMinutes + totalTimeSpentMinutes
            )
        )
    }

    fun removeFromFailures(questionId: String) {
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    topic.copy(
                        questions = topic.questions.map { question ->
                            if (question.id == questionId) {
                                question.copy(stats = question.stats.copy(inFailures = false, correctStreak = 0))
                            } else question
                        }
                    )
                }
            )
        )
    }
}