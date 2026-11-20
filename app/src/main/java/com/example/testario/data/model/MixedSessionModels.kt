package com.example.testario.data.model

import kotlinx.serialization.Serializable

/** Only TEST is the historical behaviour; older templates deserialize as TEST. */
@Serializable
enum class SessionContentFilter { TEST, WRITTEN, ANKI, MIXED }

@Serializable
enum class MixedItemType { TEST, WRITTEN, ANKI }

@Serializable
data class MixedSessionItem(
    val id: String,
    val topicId: String,
    val topicName: String,
    val type: MixedItemType,
    val question: Question? = null,
    val exercise: StudyExercise? = null,
    /** Cursor captured at creation so a resumed question cannot change mid-session. */
    val clozeCursor: Int = 0
)

@Serializable
data class MixedSessionResponse(
    val itemId: String,
    val selectedAnswerId: String? = null,
    val writtenAnswers: List<String> = emptyList(),
    val revealed: Boolean = false,
    val timeSpentSeconds: Long = 0L
)

/** A full snapshot, independent of editable/deletable source topics. */
@Serializable
data class MixedSessionSnapshot(
    val id: String,
    val mode: TestMode,
    val contentFilter: SessionContentFilter,
    val items: List<MixedSessionItem>,
    val responses: List<MixedSessionResponse>,
    val currentIndex: Int = 0,
    val elapsedSeconds: Long = 0L,
    val remainingSeconds: Int? = null,
    val pointsCorrect: Double = 1.0,
    val penaltyWrong: Double = -0.25,
    val completed: Boolean = false
)

@Serializable
data class MixedSessionHistory(
    val id: String,
    val timestampMillis: Long,
    val mode: TestMode,
    val contentFilter: SessionContentFilter,
    val totalItems: Int,
    val objectiveItems: Int,
    val manualReviewItems: Int,
    val scoreOutOfTen: Double?,
    val timeSpentSeconds: Long
)

data class MixedSessionResult(
    val totalItems: Int,
    val objectiveItems: Int,
    val manualReviewItems: Int,
    val correctTest: Int,
    val wrongTest: Int,
    val blankTest: Int,
    val writtenItems: Int,
    val scoreOutOfTen: Double?
)
