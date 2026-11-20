package com.example.testario.domain

import com.example.testario.data.model.DuplicateTopicAction
import com.example.testario.data.model.Topic

/** One explicit decision for each incoming topic; no writes occur during planning. */
data class ImportBatchDecision(
    val topic: Topic,
    val action: DuplicateTopicAction,
    val customName: String? = null
)

data class ImportBatchPlan(
    val topics: List<Topic>,
    val outcomes: List<ImportTopicOutcome>
) {
    val created: Int get() = outcomes.count { it.created }
    val merged: Int get() = outcomes.count { it.merged }
    val identical: Int get() = outcomes.count { !it.created && !it.merged }
    val addedQuestions: Int get() = outcomes.sumOf { it.addedQuestions }
    val addedExercises: Int get() = outcomes.sumOf { it.addedExercises }
    val duplicatedQuestions: Int get() = outcomes.sumOf { it.duplicateQuestions }
    val duplicatedExercises: Int get() = outcomes.sumOf { it.duplicateExercises }
}

/**
 * Pure, all-or-nothing import plan. Existing questions are deduplicated ONLY
 * within the destination topic. A repeated fact in another topic is not lost.
 *
 * The caller supplies a copier to regenerate IDs and resolve images. This
 * method itself neither modifies user files nor persists partial progress.
 */
object ImportBatchPlanner {
    fun prepare(
        existing: List<Topic>,
        decisions: List<ImportBatchDecision>,
        copyTopic: (Topic) -> Topic
    ): ImportBatchPlan {
        require(decisions.isNotEmpty()) { "No hay temas que importar." }
        val planned = existing.toMutableList()
        val outcomes = mutableListOf<ImportTopicOutcome>()
        decisions.forEachIndexed { position, decision ->
            val incoming = decision.topic
            val name = incoming.name.trim().ifBlank { "Importado" }
            val existingIndex = planned.indexOfFirst { it.name.trim().equals(name, ignoreCase = true) }
            val previous = planned.getOrNull(existingIndex)
            if (previous != null && decision.action == DuplicateTopicAction.MERGE) {
                val questions = TopicImportMatcher.uniqueQuestions(incoming.questions, previous.questions)
                val exercises = StudyExerciseMerge.unique(incoming.exercises, previous.exercises)
                val duplicates = incoming.questions.size - questions.size
                val duplicateExercises = incoming.exercises.size - exercises.size
                if (questions.isEmpty() && exercises.isEmpty()) {
                    outcomes += ImportTopicOutcome(
                        created = false, merged = false, addedQuestions = 0,
                        duplicateQuestions = duplicates, destinationName = previous.name,
                        addedExercises = 0, duplicateExercises = duplicateExercises
                    )
                } else {
                    val fresh = copyTopic(incoming.copy(questions = questions, exercises = exercises))
                    planned[existingIndex] = previous.copy(
                        questions = previous.questions + fresh.questions,
                        exercises = previous.exercises + fresh.exercises
                    )
                    outcomes += ImportTopicOutcome(
                        created = false, merged = true, addedQuestions = questions.size,
                        duplicateQuestions = duplicates, destinationName = previous.name,
                        addedExercises = exercises.size, duplicateExercises = duplicateExercises
                    )
                }
            } else {
                val destination = when (decision.action) {
                    DuplicateTopicAction.MERGE, DuplicateTopicAction.RENAME_AUTO ->
                        uniqueName(name, planned)
                    DuplicateTopicAction.RENAME -> {
                        val chosen = decision.customName?.trim().orEmpty()
                        require(chosen.isNotBlank()) {
                            "Falta el nuevo nombre del tema ${position + 1}."
                        }
                        require(planned.none { it.name.trim().equals(chosen, ignoreCase = true) }) {
                            "Ya existe el tema «$chosen». Cambia el nombre o fusiona."
                        }
                        chosen
                    }
                }
                planned += copyTopic(incoming.copy(name = destination))
                outcomes += ImportTopicOutcome(
                    created = true, merged = false, addedQuestions = incoming.questions.size,
                    duplicateQuestions = 0, destinationName = destination,
                    addedExercises = incoming.exercises.size, duplicateExercises = 0
                )
            }
        }
        return ImportBatchPlan(planned, outcomes)
    }

    private fun uniqueName(base: String, existing: List<Topic>): String {
        if (existing.none { it.name.trim().equals(base, ignoreCase = true) }) return base
        var suffix = 2
        while (existing.any { it.name.trim().equals("$base ($suffix)", ignoreCase = true) }) suffix++
        return "$base ($suffix)"
    }
}
