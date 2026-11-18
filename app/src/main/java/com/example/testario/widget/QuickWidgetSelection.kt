package com.example.testario.widget

import com.example.testario.data.model.TestarioDatabase

/** Configuración local por instancia; los IDs apuntan al banco real de Testario. */
enum class QuickWidgetMode { RANDOM, TOPIC, TEMPLATE }

data class QuickWidgetSelection(
    val mode: QuickWidgetMode = QuickWidgetMode.RANDOM,
    val topicId: String? = null,
    val templateId: String? = null,
    val questionCount: Int = NexumQuickTestWidgetModel.DEFAULT_QUESTION_COUNT
) {
    fun normalized(): QuickWidgetSelection = copy(questionCount = questionCount.coerceIn(1, 500))

    fun topicIds(database: TestarioDatabase): List<String> = when (mode) {
        QuickWidgetMode.RANDOM -> NexumQuickTestWidgetModel.defaultTopicIds(database)
        QuickWidgetMode.TOPIC -> listOfNotNull(topicId)
        QuickWidgetMode.TEMPLATE -> emptyList() // La plantilla resuelve el alcance por sí misma.
    }

    fun isValid(database: TestarioDatabase): Boolean = when (mode) {
        QuickWidgetMode.RANDOM -> topicIds(database).isNotEmpty()
        QuickWidgetMode.TOPIC ->
            database.topics.any { it.id == topicId && it.questions.isNotEmpty() }
        QuickWidgetMode.TEMPLATE ->
            !templateId.isNullOrBlank() && database.testTemplates.any { it.id == templateId }
    }

    fun label(database: TestarioDatabase): String = when (mode) {
        QuickWidgetMode.RANDOM -> "${questionCount.coerceIn(1, 500)} preguntas · todos los temas"
        QuickWidgetMode.TOPIC -> {
            val name = database.topics.firstOrNull { it.id == topicId }?.name
                ?: "Tema eliminado: configurar"
            "$name · ${questionCount.coerceIn(1, 500)} preguntas"
        }
        QuickWidgetMode.TEMPLATE ->
            database.testTemplates.firstOrNull { it.id == templateId }?.name
                ?: "Plantilla eliminada: configurar"
    }
}
