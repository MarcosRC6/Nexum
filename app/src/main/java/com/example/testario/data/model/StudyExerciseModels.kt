package com.example.testario.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Versioned, additive exercise models. Legacy Question records are left untouched. */
@Serializable
enum class StudyExerciseKind { WRITTEN_MULTI, ANKI_CARD }

@Serializable
enum class FlashcardKind { BASIC, REVERSED, CLOZE, TYPE_ANSWER, IMAGE_OCCLUSION }

@Serializable
data class ImagePoint(
    val id: String = UUID.randomUUID().toString(),
    val x: Double,
    val y: Double,
    val order: Int = 1,
    val side: String? = null
)

@Serializable
data class CanonicalSolution(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val aliases: List<String> = emptyList(),
    val points: List<ImagePoint> = emptyList()
)

@Serializable
data class ClozeGap(
    val id: String = UUID.randomUUID().toString(),
    val start: Int,
    val end: Int,
    val hint: String? = null,
    /** null: hide on every viewing; positive number: hide on that numbered round. */
    val groupNumber: Int? = null
)

@Serializable
data class ImageOcclusionMask(
    val id: String = UUID.randomUUID().toString(),
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val answer: String = ""
)

@Serializable
data class FlashcardContent(
    val kind: FlashcardKind = FlashcardKind.BASIC,
    val front: String,
    val back: String,
    val frontImageUri: String? = null,
    val backImageUri: String? = null,
    val acceptedAnswers: List<String> = emptyList(),
    // Character offsets of the FULL front string; do not overwrite original text.
    val gaps: List<ClozeGap> = emptyList(),
    val masks: List<ImageOcclusionMask> = emptyList()
)

/** Serializable freehand image, with normalized strokes editable across resolutions. */
@Serializable
data class SketchPoint(val x: Double, val y: Double)

@Serializable
data class SketchStroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<SketchPoint>,
    val brush: String = "purple"
)

@Serializable
data class SketchDrawing(
    val strokes: List<SketchStroke> = emptyList()
)

@Serializable
data class LineDiagram(
    val startX: Double = 0.10,
    val startY: Double = 0.50,
    val endX: Double = 0.90,
    val endY: Double = 0.50,
    val directionArrow: Boolean = false
)

@Serializable
data class StudyExercise(
    val id: String = UUID.randomUUID().toString(),
    val kind: StudyExerciseKind,
    val prompt: String,
    val solutions: List<CanonicalSolution> = emptyList(),
    val flashcard: FlashcardContent? = null,
    // The original unannotated image. Correction markers are stored only in solutions.
    val baseImageUri: String? = null,
    val diagram: LineDiagram? = null,
    val sketch: SketchDrawing? = null,
    val revision: Int = 1,
    val draft: Boolean = false
) {
    fun referencedImageUris(): List<String> =
        listOfNotNull(baseImageUri, flashcard?.frontImageUri, flashcard?.backImageUri)
            .filter { it.isNotBlank() }
}

@Serializable
data class AnkiScheduleSettings(
    val againMinutes: Long = 1,
    val hardMinutes: Long = 10,
    val graduatingMinutes: Long = 1440,
    val easyMinutes: Long = 5760,
    val goodMultiplier: Double = 2.5,
    val hardMultiplier: Double = 1.2,
    val easyMultiplier: Double = 4.0,
    val maxIntervalMinutes: Long = 5256000,
    val newCardsPerDay: Int = 20,
    val reviewsPerDay: Int = 200
)

@Serializable
enum class ReviewRating { AGAIN, HARD, GOOD, EASY }

@Serializable
data class AnkiReviewState(
    val cardId: String,
    val dueAtMillis: Long = 0L,
    val intervalMinutes: Long = 0L,
    val repetitions: Int = 0,
    val suspended: Boolean = false
)

@Serializable
data class AnkiReviewEvent(
    val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val timestampMillis: Long,
    val rating: ReviewRating,
    val nextDueAtMillis: Long,
    val intervalMinutes: Long
)

@Serializable
data class ExerciseAttempt(
    val id: String = UUID.randomUUID().toString(),
    val exerciseId: String,
    val topicId: String,
    val kind: StudyExerciseKind,
    val timestampMillis: Long,
    val submittedText: List<String> = emptyList(),
    val correct: Int = 0,
    val incorrect: Int = 0,
    val omitted: Int = 0,
    val scoreOutOfTen: Double? = null,
    val pointsCorrect: Double = 1.0,
    val penaltyWrong: Double = -0.25,
    val objective: Boolean = true
)
