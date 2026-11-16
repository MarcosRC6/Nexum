package com.example.testario.domain

import com.example.testario.data.model.*
import java.util.UUID

/**
 * Bounded history: the five latest EXAM attempts store question/answer snapshots.
 * Trends are compact, bounded to 30 sessions and calculated by stable topic/group IDs.
 */
object TestHistoryAnalytics {
    const val MAX_DETAILED_EXAMS = 5
    const val MAX_TREND_SESSIONS = 30

    fun detailed(
        db: TestarioDatabase,
        results: List<TestQuestionState>,
        result: TestResult,
        mode: TestMode,
        elapsedSeconds: Long,
        sessionId: String,
        timestamp: Long = System.currentTimeMillis()
    ): DetailedExam? {
        if (mode != TestMode.EXAM) return null
        val owners = db.topics.flatMap { topic ->
            topic.questions.map { it.id to topic }
        }.toMap()
        return DetailedExam(
            id = sessionId,
            timestamp = timestamp,
            score = result.score,
            percent = result.percent,
            correct = result.correct,
            wrong = result.wrong,
            blank = result.blank,
            elapsedSeconds = elapsedSeconds,
            questions = results.map { state ->
                val topic = owners[state.question.id]
                ExamQuestionRecord(
                    topicId = topic?.id.orEmpty(),
                    topicName = topic?.name ?: "Tema eliminado",
                    // Freeze the answer order and content without duplicating mutable lifetime stats.
                    question = state.question.copy(stats = QuestionStats()),
                    selectedAnswerId = state.selectedAnswerId,
                    timeSpentSeconds = state.timeSpentSeconds
                )
            }
        )
    }

    fun trend(
        db: TestarioDatabase,
        results: List<TestQuestionState>,
        mode: TestMode,
        sessionId: String,
        timestamp: Long = System.currentTimeMillis()
    ): TestPerformancePoint {
        val owners = db.topics.flatMap { topic -> topic.questions.map { it.id to topic } }.toMap()
        val groupMembership = db.groups.associate { group ->
            group.id to GroupHierarchy.resolveTopicIds(db.groups, group.id)
        }
        val counts = linkedMapOf<String, MutableList<TestQuestionState>>()
        results.forEach { state ->
            val id = owners[state.question.id]?.id ?: return@forEach
            counts.getOrPut(id) { mutableListOf() }.add(state)
        }
        val topics = counts.mapNotNull { (id, answers) ->
            val topic = db.topics.firstOrNull { it.id == id } ?: return@mapNotNull null
            TopicPerformancePoint(
                topicId = id,
                topicName = topic.name,
                groupIds = groupMembership.filterValues { id in it }.keys.toList(),
                correct = answers.count { it.selectedAnswerId != null && it.selectedAnswerId == it.question.correctAnswerId },
                wrong = answers.count { it.selectedAnswerId != null && it.selectedAnswerId != it.question.correctAnswerId },
                blank = answers.count { it.selectedAnswerId == null }
            )
        }
        return TestPerformancePoint(id = sessionId, timestamp = timestamp, mode = mode, topics = topics)
    }

    fun appendExam(existing: List<DetailedExam>, item: DetailedExam?): List<DetailedExam> =
        if (item == null) existing else (existing.filterNot { it.id == item.id } + item).takeLast(MAX_DETAILED_EXAMS)

    fun appendTrend(existing: List<TestPerformancePoint>, item: TestPerformancePoint): List<TestPerformancePoint> =
        (existing.filterNot { it.id == item.id } + item).takeLast(MAX_TREND_SESSIONS)

    fun series(
        history: List<TestPerformancePoint>,
        subjectId: String,
        group: Boolean
    ): List<TrendSeriesPoint> = history.mapNotNull { sample ->
        val matching = sample.topics.filter {
            if (group) subjectId in it.groupIds else it.topicId == subjectId
        }
        val answered = matching.sumOf { it.correct + it.wrong + it.blank }
        if (answered == 0) null else TrendSeriesPoint(
            timestamp = sample.timestamp,
            percent = matching.sumOf { it.correct } * 100 / answered,
            correct = matching.sumOf { it.correct },
            total = answered
        )
    }
}

data class TrendSeriesPoint(
    val timestamp: Long,
    val percent: Int,
    val correct: Int,
    val total: Int
)
