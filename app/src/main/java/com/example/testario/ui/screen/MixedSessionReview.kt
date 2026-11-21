package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.domain.OcclusionRevealRules
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.ui.theme.*

/** Post-exam review of frozen results; never changes grades or Anki scheduling. */
@Composable
internal fun MixedSessionReviewCard(snapshot: MixedSessionSnapshot, index: Int) {
    val item = snapshot.items[index]
    val response = snapshot.responses[index]
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(item.topicName.uppercase(), color = TestarioPrimary,
                style = MaterialTheme.typography.labelLarge)
            when (item.type) {
                MixedItemType.TEST -> {
                    val q = requireNotNull(item.question)
                    Text(q.text, color = TestarioText,
                        style = MaterialTheme.typography.titleLarge)
                    q.explanationImageUri?.let {
                        StudyVisualImage(imageUri = it, annotations = q.statementAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                    q.answers.forEachIndexed { answerIndex, answer ->
                        val right = answer.id == q.correctAnswerId
                        val selected = answer.id == response.selectedAnswerId
                        val background = when {
                            right -> TestarioGreenDark
                            selected -> TestarioRedDark
                            else -> TestarioSurfaceLight
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = background),
                            border = BorderStroke(1.dp, when {
                                right -> TestarioGreen
                                selected -> TestarioRed
                                else -> TestarioSurfaceLight
                            }),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("${'A' + answerIndex}", color = TestarioText)
                                Spacer(Modifier.width(14.dp))
                                Text(answer.text, color = TestarioText)
                            }
                        }
                    }
                    if (response.selectedAnswerId == null) {
                        Text("Sin responder", color = TestarioMutedText)
                    }
                    if (q.explanation.isNotBlank()) Text(q.explanation, color = TestarioText)
                    q.correctionImageUri?.let {
                        StudyVisualImage(imageUri = it, annotations = q.correctionAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp), showCorrection = true)
                    }
                }
                MixedItemType.WRITTEN -> {
                    val exercise = requireNotNull(item.exercise)
                    val grade = StudyExerciseRules.gradeWrittenInSession(
                        exercise, response.writtenAnswers,
                        snapshot.pointsCorrect, snapshot.penaltyWrong
                    )
                    Text(exercise.prompt, color = TestarioText,
                        style = MaterialTheme.typography.titleLarge)
                    val statuses = exercise.solutions.flatMap { solution ->
                        solution.points.map { it.id to (solution.id in grade.correctIds) }
                    }.toMap()
                    exercise.baseImageUri?.let {
                        StudyVisualImage(imageUri = it, annotations = exercise.baseAnnotations,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            markerStatuses = statuses, showMarkerNumbers = true)
                    }
                    exercise.sketch?.let {
                        StudySketchCanvas(sketch = it,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { solution -> solution.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = statuses)
                    }
                    WrittenCorrectionRows(exercise, response.writtenAnswers, grade)
                    Text("Nota: ${"%.2f".format(grade.scoreOutOfTen)} / 10",
                        color = if (grade.scoreOutOfTen >= 5) TestarioGreen else TestarioRed)
                }
                MixedItemType.ANKI -> {
                    val card = requireNotNull(item.exercise?.flashcard)
                    val reverse = item.variant == "reverse"
                    val question = if (reverse) card.back else card.front
                    val answer = if (reverse) card.front else card.back
                    Text(if (!reverse && card.kind == FlashcardKind.CLOZE)
                        ClozeMarkupEngine.mask(question, card.gaps, item.clozeCursor)
                        else question, color = TestarioText,
                        style = MaterialTheme.typography.titleLarge)
                    (if (reverse) card.backImageUri else card.frontImageUri)?.let {
                        StudyVisualImage(
                            imageUri = it,
                            annotations = if (reverse) card.backAnnotations else card.frontAnnotations,
                            masks = if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                                OcclusionRevealRules.covered(card.masks, item.variant, true)
                                else emptyList(),
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            showCorrection = card.kind != FlashcardKind.IMAGE_OCCLUSION
                        )
                    }
                    val revealedText = if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                        card.back else answer
                    if (revealedText.isNotBlank()) {
                        Text(if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                            "Explicación" else "Respuesta", color = TestarioGreen)
                        Text(revealedText, color = TestarioText)
                    }
                }
            }
        }
    }
}
