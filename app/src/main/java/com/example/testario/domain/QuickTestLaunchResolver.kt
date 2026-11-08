package com.example.testario.domain

import com.example.testario.data.model.SavedFailureScope
import com.example.testario.data.model.SavedTestScope
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestGroupAllocation
import com.example.testario.data.model.TestMode
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic

/**
 * Prepares a linked quick test using either the historical topic/count selection
 * or the full saved Testario template. A missing template must never silently
 * fall back to an unrelated exam.
 */
sealed interface QuickTestLaunchResult {
    data class Ready(val topics: List<Topic>, val config: TestConfig) : QuickTestLaunchResult
    data class Invalid(val message: String) : QuickTestLaunchResult
}

object QuickTestLaunchResolver {
    fun resolve(
        database: TestarioDatabase,
        topicIds: Collection<String>,
        questionCount: Int,
        templateId: String?
    ): QuickTestLaunchResult {
        if (templateId == null) {
            val topics = database.topics.filter { it.id in topicIds && it.questions.isNotEmpty() }
            if (topics.isEmpty()) {
                return QuickTestLaunchResult.Invalid("Vincula primero un tema con preguntas.")
            }
            return QuickTestLaunchResult.Ready(
                topics,
                TestConfig(
                    mode = TestMode.EXAM,
                    topicIds = topics.map { it.id },
                    questionCount = questionCount.coerceIn(1, 500),
                    difficulties = emptySet(),
                    difficultyFilterEnabled = false,
                    timerEnabled = false
                )
            )
        }

        val template = database.testTemplates.firstOrNull { it.id == templateId }
            ?: return QuickTestLaunchResult.Invalid(
                "La plantilla vinculada ya no existe. Configura de nuevo el vínculo."
            )

        val topics = when (template.scope) {
            SavedTestScope.GLOBAL -> database.topics
            SavedTestScope.TOPIC -> database.topics.filter { it.id in template.selectedTopicIds }
            SavedTestScope.GROUPS -> {
                val selectedGroupIds = template.groupAllocations.map { it.groupId }
                val groupedTopicIds = selectedGroupIds.flatMapTo(linkedSetOf()) { groupId ->
                    GroupHierarchy.resolveTopicIds(database.groups, groupId)
                }
                database.topics.filter { it.id in groupedTopicIds }
            }
            SavedTestScope.FAILURES -> when (template.failureScope) {
                SavedFailureScope.GLOBAL -> database.topics
                SavedFailureScope.TOPIC -> database.topics.filter { it.id in template.selectedFailureTopicIds }
            }
        }
        if (topics.isEmpty()) {
            return QuickTestLaunchResult.Invalid(
                "La plantilla no tiene temas disponibles. Revísala en Configurar test."
            )
        }

        val finalMode = if (template.scope == SavedTestScope.FAILURES) TestMode.FAILURES else template.mode
        val selectedDifficulty = template.difficulty.takeUnless { template.difficultyFilterEnabled }
        val filtered = topics.associate { topic ->
            topic.id to topic.questions.asSequence()
                .filter { finalMode != TestMode.FAILURES || it.stats.inFailures }
                .filter { selectedDifficulty == null || it.difficulty == selectedDifficulty }
                .distinctBy { it.id }
                .toList()
        }
        val available = filtered.values.flatten().distinctBy { it.id }
        if (available.isEmpty()) {
            return QuickTestLaunchResult.Invalid(
                "No hay preguntas disponibles para la plantilla seleccionada."
            )
        }
        if (template.questionCount <= 0 || template.questionCount > available.size) {
            return QuickTestLaunchResult.Invalid(
                "La plantilla pide ${template.questionCount} preguntas y solo hay ${available.size} disponibles."
            )
        }
        if (!template.pointsCorrect.isFinite() || template.pointsCorrect <= 0.0 ||
            !template.penaltyWrong.isFinite() || template.penaltyWrong > 0.0
        ) {
            return QuickTestLaunchResult.Invalid("La puntuación de la plantilla no es válida.")
        }
        if (template.difficultyFilterEnabled &&
            template.easyPercent + template.mediumPercent + template.hardPercent <= 0
        ) {
            return QuickTestLaunchResult.Invalid(
                "El reparto de dificultad de la plantilla no es válido."
            )
        }
        if (template.timerEnabled && finalMode == TestMode.EXAM && template.timerMinutes <= 0) {
            return QuickTestLaunchResult.Invalid("El temporizador de la plantilla no es válido.")
        }

        val groupAllocations = if (template.scope == SavedTestScope.GROUPS) {
            template.groupAllocations.map { allocation ->
                TestGroupAllocation(
                    groupId = allocation.groupId,
                    topicIds = GroupHierarchy.resolveTopicIds(database.groups, allocation.groupId).toList(),
                    questionCount = allocation.questionCount
                )
            }
        } else {
            emptyList()
        }
        if (template.scope == SavedTestScope.GROUPS &&
            (groupAllocations.isEmpty() ||
                groupAllocations.any { it.questionCount <= 0 || it.topicIds.isEmpty() } ||
                groupAllocations.sumOf { it.questionCount } != template.questionCount ||
                !SmartSelectionEngine().canFulfillGroupedSelection(filtered, groupAllocations))
        ) {
            return QuickTestLaunchResult.Invalid(
                "No se puede cumplir el reparto por grupos de la plantilla. Revísala en Configurar test."
            )
        }

        return QuickTestLaunchResult.Ready(
            topics,
            TestConfig(
                mode = finalMode,
                topicIds = topics.map { it.id },
                questionCount = template.questionCount,
                difficulties = if (selectedDifficulty == null) emptySet() else setOf(selectedDifficulty),
                randomOrder = true,
                pointsCorrect = template.pointsCorrect,
                penaltyWrong = template.penaltyWrong,
                timerEnabled = template.timerEnabled && finalMode == TestMode.EXAM,
                timerMinutes = template.timerMinutes,
                easyPercent = if (template.difficultyFilterEnabled) template.easyPercent else 0,
                mediumPercent = if (template.difficultyFilterEnabled) template.mediumPercent else 0,
                hardPercent = if (template.difficultyFilterEnabled) template.hardPercent else 0,
                difficultyFilterEnabled = template.difficultyFilterEnabled,
                groupAllocations = groupAllocations
            )
        )
    }
}
