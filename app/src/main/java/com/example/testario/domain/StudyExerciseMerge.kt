package com.example.testario.domain

import com.example.testario.data.model.*
import java.io.File
import java.util.UUID

/**
 * Exact content-based deduplication within a destination topic only. Never
 * deduplicate merely because two exercises have similar wording.
 */
object StudyExerciseMerge {
    fun signature(value: StudyExercise): String {
        val prompt = StudyExerciseRules.normalize(value.prompt)
        return when (value.kind) {
            StudyExerciseKind.WRITTEN_MULTI -> {
                val solutions = value.solutions.map { solution ->
                    listOf(StudyExerciseRules.normalize(solution.text)) +
                        solution.aliases.map(StudyExerciseRules::normalize).sorted() +
                        solution.points.map { p -> "${p.x}:${p.y}:${p.order}:${p.side}" }.sorted()
                }.map { it.joinToString(";") }.sorted()
                "${value.kind}|$prompt|${solutions.joinToString("|")}|"+
                    "${value.baseImageUri?.let { File(it).name }}|${value.diagram}|${sketchSignature(value.sketch)}|${ImageAnnotationRules.signature(value.baseAnnotations)}"
            }
            StudyExerciseKind.ANKI_CARD -> {
                val card = value.flashcard
                "${value.kind}|$prompt|${card?.kind}|" +
                    "${card?.front}|${card?.back}|" +
                    "${card?.frontImageUri?.let { File(it).name }}|" +
                    "${card?.backImageUri?.let { File(it).name }}|" +
                    "${card?.acceptedAnswers?.sorted()}|" +
                    "${card?.gaps?.map { listOf(it.start, it.end, it.hint, it.groupNumber) }}|" +
                    "${card?.masks?.map { listOf(it.x,it.y,it.width,it.height,it.answer) }}|" +
                    "${ImageAnnotationRules.signature(card?.frontAnnotations ?: ImageAnnotations())}|" +
                    "${ImageAnnotationRules.signature(card?.backAnnotations ?: ImageAnnotations())}"
            }
        }
    }

    /**
     * Drawings are compared by visual content, not transient stroke UUIDs.
     * Stroke order, brush and normalized points remain significant.
     */
    private fun sketchSignature(drawing: SketchDrawing?): String =
        drawing?.strokes?.joinToString("|") { stroke ->
            "${stroke.brush}:${stroke.colorArgb}:${stroke.opacity}:${stroke.widthFraction}:" +
                stroke.points.joinToString(";") { point ->
                    "${point.x},${point.y}"
                }
        }.orEmpty()

    fun unique(incoming: List<StudyExercise>, existing: List<StudyExercise>): List<StudyExercise> {
        val known = existing.mapTo(hashSetOf(), ::signature)
        return incoming.filter { known.add(signature(it)) }
    }

    private fun copyAnnotations(layer: ImageAnnotations): ImageAnnotations =
        layer.copy(strokes = layer.strokes.map { it.copy(id = UUID.randomUUID().toString()) })

    /** Imported copies must not collide with source IDs or existing review progress. */
    fun duplicate(exercise: StudyExercise): StudyExercise =
        exercise.copy(
            id = UUID.randomUUID().toString(),
            revision = 1,
            baseAnnotations = copyAnnotations(exercise.baseAnnotations),
            solutions = exercise.solutions.map { solution ->
                solution.copy(
                    id = UUID.randomUUID().toString(),
                    points = solution.points.map { it.copy(id = UUID.randomUUID().toString()) }
                )
            },
            flashcard = exercise.flashcard?.let { card ->
                card.copy(
                    frontAnnotations = copyAnnotations(card.frontAnnotations),
                    backAnnotations = copyAnnotations(card.backAnnotations),
                    gaps = card.gaps.map { it.copy(id = UUID.randomUUID().toString()) },
                    masks = card.masks.map { it.copy(id = UUID.randomUUID().toString()) }
                )
            }
        )
}
