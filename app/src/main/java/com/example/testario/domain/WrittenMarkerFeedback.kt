package com.example.testario.domain

import com.example.testario.data.model.StudyExercise

/**
 * A pin represents the answer SLOT with the same index as its numbered solution.
 * Resolve directly from submitted text instead of inferring pin status from
 * aggregate recall/score (which can be order-independent in legacy exercises).
 * Empty or incorrect positions are red only after the answer is revealed.
 */
object WrittenMarkerFeedback {
    /**
     * Use the SAME graded result as the score and the correction rows. This
     * prevents a green image marker when its corresponding numbered answer
     * is red in the result list (including restored/legacy sessions).
     */
    fun fromGradedResult(
        exercise: StudyExercise,
        grade: StudyExerciseRules.WrittenResult
    ): Map<String, Boolean> {
        // The visible number (not the historical owner/insertion order)
        // determines which corrected answer colors the marker.
        val byNumber = StudyExerciseRules.positionalSolutions(exercise)
            .mapIndexed { index, solution ->
                (index + 1) to (solution.id in grade.correctIds)
            }.toMap()
        return exercise.solutions.flatMap { solution ->
            solution.points.map { marker ->
                marker.id to (byNumber[marker.order] ?: (solution.id in grade.correctIds))
            }
        }.toMap()
    }

    fun statusByMarker(exercise: StudyExercise, submitted: List<String>): Map<String, Boolean> {
        val ordered = StudyExerciseRules.positionalSolutions(exercise)
        val correctByNumber = ordered.mapIndexed { index, solution ->
            val text = submitted.getOrNull(index).orEmpty()
            val matched = StudyExerciseRules.findAcceptedSolution(exercise, text)
            (index + 1) to (matched?.id == solution.id)
        }.toMap()
        return exercise.solutions.flatMap { solution ->
            solution.points.map { marker ->
                marker.id to (correctByNumber[marker.order] ?: false)
            }
        }.toMap()
    }
}
