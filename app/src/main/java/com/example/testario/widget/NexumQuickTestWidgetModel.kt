package com.example.testario.widget

import com.example.testario.data.model.TestTemplate
import com.example.testario.data.model.TestarioDatabase

/**
 * The widget never stores questions or duplicates the Testario database:
 * it only persists the stable ID of an existing saved template.
 */
object NexumQuickTestWidgetModel {
    const val DEFAULT_QUESTION_COUNT = 10
    const val LAUNCH_ROUTE = "widget_quick_test"

    fun label(templates: List<TestTemplate>, selectedTemplateId: String?): String =
        when {
            selectedTemplateId == null -> "10 preguntas aleatorias"
            else -> templates.firstOrNull { it.id == selectedTemplateId }?.name
                ?: "Plantilla eliminada: elige otra"
        }

    fun isSelectionValid(templates: List<TestTemplate>, selectedTemplateId: String?): Boolean =
        selectedTemplateId == null || templates.any { it.id == selectedTemplateId }

    fun defaultTopicIds(database: TestarioDatabase): List<String> =
        database.topics.filter { it.questions.isNotEmpty() }.map { it.id }
}
