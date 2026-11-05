package com.example.testario.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

object DatabaseSchema {
    const val CURRENT_VERSION = 2
}

@Serializable
data class TestarioDatabase(
    val version: Int = DatabaseSchema.CURRENT_VERSION,
    val topics: List<Topic> = emptyList(),
    val groups: List<TopicGroup> = emptyList(),
    val testTemplates: List<TestTemplate> = emptyList(),
    val testsDone: Int = 0,
    val totalTrainingMinutes: Int = 0,
    val testHistory: List<HistoricalResult> = emptyList()
)

@Serializable
data class Topic(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val questions: List<Question> = emptyList(),
    val isPinned: Boolean = false
)

/**
 * Capa puramente organizativa. Los temas no se mueven ni se duplican físicamente:
 * el grupo solo conserva referencias por ID. Un grupo tiene como máximo un padre,
 * mientras que un mismo tema sí puede pertenecer a varios grupos.
 */
@Serializable
data class TopicGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentGroupId: String? = null,
    val topicIds: List<String> = emptyList()
)

@Serializable
data class GroupQuestionAllocation(
    val groupId: String,
    val questionCount: Int
)

@Serializable
enum class SavedTestScope { GLOBAL, TOPIC, GROUPS, FAILURES }

@Serializable
enum class SavedFailureScope { GLOBAL, TOPIC }

/**
 * Plantilla reutilizable de la pantalla Configurar test.
 * Se guardan IDs estables de temas/grupos, nunca nombres, por lo que renombrar
 * un tema o grupo no rompe una plantilla existente.
 */
@Serializable
data class TestTemplate(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val mode: TestMode = TestMode.TRAINING,
    val scope: SavedTestScope = SavedTestScope.GLOBAL,
    val selectedTopicIds: List<String> = emptyList(),
    val failureScope: SavedFailureScope = SavedFailureScope.GLOBAL,
    val selectedFailureTopicIds: List<String> = emptyList(),
    val groupAllocations: List<GroupQuestionAllocation> = emptyList(),
    val questionCount: Int = 10,
    val difficulty: Int? = null,
    val pointsCorrect: Double = 1.0,
    val penaltyWrong: Double = -0.25,
    val timerEnabled: Boolean = false,
    val timerMinutes: Int = 60,
    val easyPercent: Int = 33,
    val mediumPercent: Int = 33,
    val hardPercent: Int = 34,
    val difficultyFilterEnabled: Boolean = false
)

@Serializable
data class Question(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val answers: List<Answer>,
    val correctAnswerId: String,
    val difficulty: Int? = null,
    val explanation: String = "",
    // Compatibilidad: este campo histórico sigue representando la imagen mostrada con el enunciado.
    val explanationImageUri: String? = null,
    // Imagen independiente que solo se muestra al corregir/revisar la pregunta.
    val correctionImageUri: String? = null,
    // Tema original opcional cuando una pregunta se reorganiza mediante búsqueda/movimiento.
    val originTopicName: String? = null,
    val favorite: Boolean = false,
    val flagged: Boolean = false,
    val isFlashcard: Boolean = false,
    val stats: QuestionStats = QuestionStats()
)

@Serializable
data class Answer(
    val id: String = UUID.randomUUID().toString(),
    val text: String
)

@Serializable
data class QuestionStats(
    val timesAnswered: Int = 0,
    val timesCorrect: Int = 0,
    val timesWrong: Int = 0,
    val timesBlank: Int = 0,
    val lastAnswerId: String? = null,
    val lastAnsweredAt: Long? = null,
    val correctStreak: Int = 0,
    val inFailures: Boolean = false,
    val totalTimeSpentSeconds: Long = 0
)

@Serializable
data class HistoricalResult(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val score: Double,
    val percent: Int
)
