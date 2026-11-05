package com.example.testario.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class TestMode { TRAINING, EXAM, FAILURES, FAVORITES, HARD }

data class TestGroupAllocation(
    val groupId: String,
    val topicIds: List<String>,
    val questionCount: Int
)

data class TestConfig(
    val mode: TestMode,
    val topicIds: List<String>,
    val questionCount: Int,
    val difficulties: Set<Int?>,
    val randomOrder: Boolean = true,
    val pointsCorrect: Double = 1.0,
    val penaltyWrong: Double = -0.25,
    val timerEnabled: Boolean = false,
    val timerMinutes: Int = 60,
    val easyPercent: Int = 33,
    val mediumPercent: Int = 33,
    val hardPercent: Int = 34,
    val difficultyFilterEnabled: Boolean = true,
    val groupAllocations: List<TestGroupAllocation> = emptyList()
)

data class TestQuestionState(
    val question: Question,
    val selectedAnswerId: String? = null,
    val corrected: Boolean = false,
    val answersRevealed: Boolean = true,
    val timeSpentSeconds: Long = 0
)

data class TestResult(
    val total: Int, val correct: Int, val wrong: Int, val blank: Int,
    val rawScore: Double, val maxScore: Double, val score: Double,
    val percent: Int, val timeExpired: Boolean = false
)
