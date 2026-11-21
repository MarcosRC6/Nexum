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
    ): Map<String, Boolean> =
        exercise.solutions.flatMap { solution ->
            solution.points.map { point -> point.id to (solution.id in grade.correctIds) }
        }.toMap()


    fun statusByMarker(exercise: StudyExercise, submitted: List<String>): Map<String, Boolean> =
        StudyExerciseRules.positionalSolutions(exercise).flatMapIndexed { index, solution ->
            val submittedText = submitted.getOrNull(index).orEmpty()
            val matched = StudyExerciseRules.findAcceptedSolution(exercise, submittedText)
            solution.points.map { marker -> marker.id to (matched?.id == solution.id) }
        }.toMap()
}
