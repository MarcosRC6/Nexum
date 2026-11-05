package com.example.testario.domain

import com.example.testario.data.model.Question
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestGroupAllocation
import com.example.testario.data.model.TestMode
import kotlin.math.roundToInt

class SmartSelectionEngine {

    fun generateSmartSelection(
        availableByTopic: Map<String, List<Question>>,
        config: TestConfig,
        prioritizeFailuresInNormalTests: Boolean = true
    ): List<Question> {
        if (availableByTopic.isEmpty() || config.questionCount <= 0) return emptyList()

        val finalQuestions = mutableListOf<Question>()
        val totalAvailable = availableByTopic.values.sumOf { it.size }
        val totalNeeded = minOf(config.questionCount, totalAvailable)

        if (config.difficultyFilterEnabled) {
            val sum = config.easyPercent + config.mediumPercent + config.hardPercent
            if (sum > 0) {
                val easyNeeded = ((totalNeeded * config.easyPercent) / sum.toDouble()).roundToInt()
                val mediumNeeded = ((totalNeeded * config.mediumPercent) / sum.toDouble()).roundToInt()
                val hardNeeded = maxOf(0, totalNeeded - easyNeeded - mediumNeeded)

                val easyByTopic = availableByTopic.mapValues { (_, qs) -> qs.filter { it.difficulty == 1 } }
                val mediumByTopic = availableByTopic.mapValues { (_, qs) -> qs.filter { it.difficulty == 2 } }
                val hardByTopic = availableByTopic.mapValues { (_, qs) -> qs.filter { it.difficulty == 3 } }

                finalQuestions.addAll(
                    roundRobinSmartPick(easyByTopic, easyNeeded, config.mode, prioritizeFailuresInNormalTests)
                )
                finalQuestions.addAll(
                    roundRobinSmartPick(mediumByTopic, mediumNeeded, config.mode, prioritizeFailuresInNormalTests)
                )
                finalQuestions.addAll(
                    roundRobinSmartPick(hardByTopic, hardNeeded, config.mode, prioritizeFailuresInNormalTests)
                )
            }

            if (finalQuestions.size < totalNeeded) {
                val remainingNeeded = totalNeeded - finalQuestions.size
                val selectedIds = finalQuestions.mapTo(hashSetOf()) { it.id }
                val unusedByTopic = availableByTopic.mapValues { (_, qs) ->
                    qs.filterNot { it.id in selectedIds }
                }
                finalQuestions.addAll(
                    roundRobinSmartPick(unusedByTopic, remainingNeeded, config.mode, prioritizeFailuresInNormalTests)
                )
            }
        } else {
            finalQuestions.addAll(
                roundRobinSmartPick(availableByTopic, totalNeeded, config.mode, prioritizeFailuresInNormalTests)
            )
        }

        val result = finalQuestions.distinctBy { it.id }.take(totalNeeded)
        return if (config.randomOrder) result.shuffled() else result
    }

    /**
     * Comprueba matemáticamente que las cuotas por grupo pueden cumplirse sin reutilizar
     * ninguna pregunta, incluso cuando los grupos/subgrupos se solapan.
     */
    fun canFulfillGroupedSelection(
        availableByTopic: Map<String, List<Question>>,
        allocations: List<TestGroupAllocation>
    ): Boolean {
        if (allocations.isEmpty() || allocations.any { it.questionCount <= 0 }) return false
        val candidates = allocations.map { allocation ->
            questionsForTopics(availableByTopic, allocation.topicIds).map { it.id }
        }
        return matchGroupSlots(allocations, candidates) != null
    }

    /**
     * Selección exacta por cuotas. Primero ordena los candidatos de cada grupo con el mismo
     * motor inteligente y round-robin de Testario. Después usa matching bipartito para que
     * una pregunta perteneciente a varios grupos se asigne solo a uno y se cumplan todas
     * las cantidades solicitadas si existe una solución válida.
     */
    fun generateGroupedSmartSelection(
        availableByTopic: Map<String, List<Question>>,
        config: TestConfig,
        prioritizeFailuresInNormalTests: Boolean = true
    ): List<Question> {
        val allocations = config.groupAllocations
        if (allocations.isEmpty()) return generateSmartSelection(availableByTopic, config, prioritizeFailuresInNormalTests)

        val allQuestionsById = availableByTopic.values.flatten().associateBy { it.id }
        val rankedIdsByAllocation = allocations.map { allocation ->
            val subset = availableByTopic.filterKeys { it in allocation.topicIds }
            val availableCount = subset.values.sumOf { it.size }
            if (availableCount == 0) {
                emptyList()
            } else {
                generateSmartSelection(
                    subset,
                    config.copy(
                        questionCount = availableCount,
                        randomOrder = false,
                        groupAllocations = emptyList()
                    ),
                    prioritizeFailuresInNormalTests
                ).map { it.id }
            }
        }

        val matchedIds = matchGroupSlots(allocations, rankedIdsByAllocation) ?: return emptyList()
        val result = matchedIds.mapNotNull(allQuestionsById::get)
        return if (config.randomOrder) result.shuffled() else result
    }

    private fun questionsForTopics(
        availableByTopic: Map<String, List<Question>>,
        topicIds: List<String>
    ): List<Question> = topicIds.asSequence()
        .flatMap { availableByTopic[it].orEmpty().asSequence() }
        .distinctBy { it.id }
        .toList()

    /**
     * Matching de slots (cuotas) contra IDs de pregunta. Devuelve un ID por slot o null
     * cuando no existe forma de satisfacer simultáneamente todas las cuotas.
     */
    private fun matchGroupSlots(
        allocations: List<TestGroupAllocation>,
        candidateIdsByAllocation: List<List<String>>
    ): List<String>? {
        if (allocations.size != candidateIdsByAllocation.size) return null

        data class Slot(val allocationIndex: Int, val ordinal: Int)

        val slots = allocations.flatMapIndexed { index, allocation ->
            List(allocation.questionCount.coerceAtLeast(0)) { ordinal -> Slot(index, ordinal) }
        }
        if (slots.isEmpty()) return emptyList()

        val slotOrder = slots.indices.sortedWith(
            compareBy<Int> { candidateIdsByAllocation[slots[it].allocationIndex].size }
                .thenBy { slots[it].allocationIndex }
                .thenBy { slots[it].ordinal }
        )

        val questionToSlot = mutableMapOf<String, Int>()
        val slotToQuestion = arrayOfNulls<String>(slots.size)

        fun tryAssign(slotIndex: Int, seenQuestions: MutableSet<String>): Boolean {
            val allocationIndex = slots[slotIndex].allocationIndex
            for (questionId in candidateIdsByAllocation[allocationIndex]) {
                if (!seenQuestions.add(questionId)) continue
                val occupiedSlot = questionToSlot[questionId]
                if (occupiedSlot == null || tryAssign(occupiedSlot, seenQuestions)) {
                    questionToSlot[questionId] = slotIndex
                    slotToQuestion[slotIndex] = questionId
                    return true
                }
            }
            return false
        }

        for (slotIndex in slotOrder) {
            if (!tryAssign(slotIndex, mutableSetOf())) return null
        }

        return slotToQuestion.map { it ?: return null }
    }

    private fun roundRobinSmartPick(
        poolsByTopic: Map<String, List<Question>>,
        neededCount: Int,
        mode: TestMode,
        prioritizeFailuresInNormalTests: Boolean
    ): List<Question> {
        if (neededCount <= 0 || poolsByTopic.isEmpty()) return emptyList()

        val queues = poolsByTopic.mapValues { (_, questions) ->
            if (questions.isEmpty()) return@mapValues mutableListOf<Question>()

            questions.map { q ->
                Pair(
                    q,
                    calculatePriorityScore(q, mode, prioritizeFailuresInNormalTests) + (Math.random() * 50.0)
                )
            }
                .sortedByDescending { it.second }
                .map { it.first }
                .toMutableList()
        }.filterValues { it.isNotEmpty() }.toMutableMap()

        val result = mutableListOf<Question>()
        val selectedIds = mutableSetOf<String>()
        var remainingNeeded = neededCount

        while (remainingNeeded > 0 && queues.isNotEmpty()) {
            val topicIds = queues.keys.toList()
            for (topicId in topicIds) {
                if (remainingNeeded == 0) break
                val queue = queues[topicId]!!

                while (queue.isNotEmpty() && queue.first().id in selectedIds) queue.removeAt(0)
                if (queue.isNotEmpty()) {
                    val picked = queue.removeAt(0)
                    if (selectedIds.add(picked.id)) {
                        result.add(picked)
                        remainingNeeded--
                    }
                }

                if (queue.isEmpty()) queues.remove(topicId)
            }
        }

        return result
    }

    private fun calculatePriorityScore(
        question: Question,
        mode: TestMode,
        prioritizeFailuresInNormalTests: Boolean
    ): Double {
        val stats = question.stats
        if (stats.timesAnswered == 0) return 10000.0

        var score = 1000.0

        val useFailureReinforcement = mode == TestMode.FAILURES || prioritizeFailuresInNormalTests
        if (useFailureReinforcement) {
            if (stats.inFailures) score += 3000.0

            val accuracy = stats.timesCorrect.toDouble() / stats.timesAnswered.toDouble()
            score -= (accuracy * 2000.0)
        }

        score -= (stats.timesAnswered * 50.0)

        val now = System.currentTimeMillis()
        val lastAnswered = stats.lastAnsweredAt ?: 0L
        if (lastAnswered > 0) {
            val daysSince = (now - lastAnswered) / (1000.0 * 60 * 60 * 24)
            score += minOf(daysSince * 25.0, 1500.0)
        }

        return score
    }
}
