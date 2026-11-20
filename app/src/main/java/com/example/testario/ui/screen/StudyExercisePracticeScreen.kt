package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.LibraryViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.util.UUID

private fun imageModel(path: String): Any = if (path.startsWith("/")) File(path) else path

/** Standalone Training for new exercises, without affecting existing Test sessions. */
@Composable
fun StudyExercisePracticeScreen(
    topicId: String, libraryViewModel: LibraryViewModel, onBack: () -> Unit,
    selectedExerciseId: String? = null
) {
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.find { it.id == topicId }
    val written = topic?.exercises.orEmpty().filter {
        it.kind == StudyExerciseKind.WRITTEN_MULTI && !it.draft &&
            (selectedExerciseId == null || it.id == selectedExerciseId)
    }
    // One note may generate several independently scheduled cards.
    val cards = topic?.exercises.orEmpty().filter {
        it.kind == StudyExerciseKind.ANKI_CARD && !it.draft &&
            (selectedExerciseId == null || it.id == selectedExerciseId)
    }.flatMap { ex ->
        val card = ex.flashcard
        when (card?.kind) {
            FlashcardKind.REVERSED -> listOf(ex to "direct", ex to "reverse")
            FlashcardKind.CLOZE -> listOf(ex to "cloze")
            FlashcardKind.IMAGE_OCCLUSION -> card.masks.map { ex to it.id }
            else -> listOf(ex to "direct")
        }
    }
    var tab by rememberSaveable(selectedExerciseId) {
        mutableStateOf(if (selectedExerciseId != null && cards.isNotEmpty() && written.isEmpty()) 1 else 0)
    }
    var index by rememberSaveable(tab) { mutableIntStateOf(0) }
    var retryCycle by rememberSaveable(tab) { mutableIntStateOf(0) }
    var entry by rememberSaveable(tab, index, retryCycle) { mutableStateOf("") }
    var answers by rememberSaveable(tab, index, retryCycle) { mutableStateOf(arrayListOf<String>()) }
    var completed by rememberSaveable(tab, index, retryCycle) { mutableStateOf(false) }
    var revealed by rememberSaveable(tab, index, retryCycle) { mutableStateOf(false) }
    var scheduledReview by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val attemptId = rememberSaveable(tab, index, retryCycle) { UUID.randomUUID().toString() }
    val keyboard = LocalSoftwareKeyboardController.current

    val schematicLineColor = TestarioPrimary

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioTopBar(title = "Practicar ejercicios", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (topic == null) {
                Text("Tema no encontrado", color = TestarioRed)
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 },
                    label = { Text("Respuesta escrita (${written.size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 },
                    label = { Text("Anki (${cards.size})") })
            }
            if (tab == 0) {
                if (written.isEmpty()) {
                    Text("Este tema no tiene ejercicios escritos publicados.", color = TestarioMutedText)
                    return@Column
                }
                val exercise = written[index.coerceIn(0, written.lastIndex)]
                val graded = if (completed) runCatching {
                    StudyExerciseRules.gradeWritten(exercise, answers,
                        1.0,
                        -0.25)
                }.getOrNull() else null
                val statusByMarker = if (graded != null) {
                    exercise.solutions.flatMap { solution ->
                        solution.points.map { it.id to (solution.id in graded.correctIds) }
                    }.toMap()
                } else emptyMap()
                Text("Ejercicio ${index + 1} de ${written.size}", color = TestarioMutedText)
                Text(exercise.prompt, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                exercise.diagram?.let { diagram ->
                    BoxWithConstraints(Modifier.fillMaxWidth().height(140.dp)
                        .background(TestarioSurface, RoundedCornerShape(12.dp))) {
                        Canvas(Modifier.fillMaxSize()) {
                            val start = Offset(size.width * diagram.startX.toFloat(),
                                size.height * diagram.startY.toFloat())
                            val end = Offset(size.width * diagram.endX.toFloat(),
                                size.height * diagram.endY.toFloat())
                            drawLine(schematicLineColor, start = start, end = end, strokeWidth = 8f)
                            if (diagram.directionArrow) {
                                val dx = end.x - start.x
                                val dy = end.y - start.y
                                val length = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                                val ux = dx / length
                                val uy = dy / length
                                val at = Offset(start.x + dx * .80f, start.y + dy * .80f)
                                drawLine(schematicLineColor, at,
                                    Offset(at.x - ux * 20f + uy * 12f, at.y - uy * 20f - ux * 12f),
                                    strokeWidth = 5f)
                                drawLine(schematicLineColor, at,
                                    Offset(at.x - ux * 20f - uy * 12f, at.y - uy * 20f + ux * 12f),
                                    strokeWidth = 5f)
                            }
                        }
                        if (completed) exercise.solutions.flatMap { it.points }.forEach { point ->
                            Text(point.order.toString(), modifier = Modifier.offset(
                                x = maxWidth * point.x.toFloat() - 8.dp,
                                y = maxHeight * point.y.toFloat() - 12.dp),
                                color = if (statusByMarker[point.id] == true)
                                    TestarioGreen else TestarioRed)
                        }
                    }
                }
                exercise.sketch?.let { drawing ->
                    StudySketchCanvas(
                        sketch = drawing,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        markers = exercise.solutions.flatMap { it.points },
                        mode = SketchMode.PREVIEW,
                        showMarkers = true,
                        markerStatuses = statusByMarker
                    )
                }
                exercise.baseImageUri?.let { path ->
                    StudyVisualImage(
                        imageUri = path,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        annotations = exercise.baseAnnotations,
                        markers = exercise.solutions.flatMap { it.points },
                        showCorrection = true,
                        markerStatuses = statusByMarker
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = entry, onValueChange = { entry = it },
                        label = { Text("Escribe una respuesta") },
                        enabled = !completed,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Text),
                        keyboardActions = KeyboardActions(onDone = {
                            val clean = entry.trim()
                            if (clean.isNotEmpty() && !answers.any {
                                StudyExerciseRules.normalize(it) == StudyExerciseRules.normalize(clean)
                            }) answers = ArrayList(answers + clean)
                            entry = ""
                            keyboard?.hide()
                        })
                    )
                    Button(
                        onClick = {
                            val clean = entry.trim()
                            if (clean.isNotEmpty() && !answers.any {
                                StudyExerciseRules.normalize(it) == StudyExerciseRules.normalize(clean)
                            }) answers = ArrayList(answers + clean)
                            entry = ""
                        },
                        enabled = !completed
                    ) { Text("Añadir") }
                }
                answers.forEachIndexed { i, value ->
                    val acceptedId = StudyExerciseRules.findAcceptedSolution(exercise, value)?.id
                    val correct = acceptedId != null && acceptedId in graded?.correctIds.orEmpty()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val feedbackColor = if (!completed) TestarioText
                            else if (correct) TestarioGreen else TestarioRed
                        Text("${i + 1}.", color = feedbackColor,
                            modifier = Modifier.width(34.dp))
                        Text(value, color = feedbackColor, modifier = Modifier.weight(1f))
                        if (!completed) TextButton(onClick = {
                            answers = ArrayList(answers.filterIndexed { j, _ -> j != i })
                        }) { Text("Quitar") }
                        else if (correct) {
                            val canonical = exercise.solutions.find { it.id == acceptedId }?.text
                            if (canonical != null && StudyExerciseRules.normalize(canonical) !=
                                StudyExerciseRules.normalize(value)) {
                                Text("✓ ${canonical}", color = TestarioGreen)
                            }
                        }
                    }
                }
                if (!completed) {
                    Button(onClick = {
                        runCatching {
                            libraryViewModel.recordWrittenAttempt(
                                topicId, exercise, answers, 1.0, -0.25, attemptId
                            )
                        }.onSuccess { completed = true; error = null }
                            .onFailure { error = it.message }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Comprobar") }
                } else {
                    val a = 1.0
                    val b = -0.25
                    val result = StudyExerciseRules.gradeWritten(exercise, answers, a, b)
                    Text("Nota: ${"%.2f".format(result.scoreOutOfTen)} / 10",
                        color = if (result.scoreOutOfTen >= 5.0) TestarioGreen else TestarioRed, style = MaterialTheme.typography.titleLarge)
                    Text("Aciertos: ${result.correct} · Errores: ${result.incorrect} · Omitidas: ${result.omitted}",
                        color = TestarioText)
                    val missing = exercise.solutions.filterNot { it.id in result.correctIds }
                    if (missing.isNotEmpty()) {
                        Text("Faltaban", color = TestarioPrimary)
                        missing.forEach { solution ->
                            Text("✗ ${solution.text} · Sin acertar", color = TestarioRed)
                        }
                    }
                }
                if (completed) {
                    if (index < written.lastIndex) {
                        OutlinedButton(onClick = { index++; error = null },
                            modifier = Modifier.fillMaxWidth()) { Text("Siguiente ejercicio") }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { retryCycle++; error = null },
                                modifier = Modifier.weight(1f)) { Text("Repetir") }
                            Button(onClick = onBack, modifier = Modifier.weight(1f)) {
                                Text("Terminar")
                            }
                        }
                    }
                }
            } else {
                if (cards.isEmpty()) {
                    Text("Este tema no tiene tarjetas publicadas.", color = TestarioMutedText)
                    return@Column
                }
                val (exercise, variant) = cards[index.coerceIn(0, cards.lastIndex)]
                val card = exercise.flashcard ?: return@Column
                val isReverse = variant == "reverse"
                val question = if (isReverse) card.back else card.front
                val solution = if (isReverse) card.front else card.back
                val cursor = remember(tab, index, retryCycle, exercise.id) {
                    database.ankiClozeCursors[exercise.id] ?: 0
                }
                val currentGroup = ClozeMarkupEngine.activeGroup(card.gaps, cursor)
                val activeGaps = if (card.kind == FlashcardKind.CLOZE)
                    ClozeMarkupEngine.activeGaps(card.gaps, cursor) else emptyList()
                val maskedQuestion = if (card.kind == FlashcardKind.CLOZE)
                    ClozeMarkupEngine.mask(question, card.gaps, cursor) else question
                Text("Tarjeta ${index + 1} de ${cards.size}", color = TestarioMutedText)
                Text(if (revealed) question else maskedQuestion,
                    color = TestarioText, style = MaterialTheme.typography.titleLarge)
                if (card.kind == FlashcardKind.CLOZE && currentGroup != null) {
                    Text("Turno ${currentGroup} · los fragmentos sin número se preguntan siempre",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }
                val promptImage = if (isReverse) card.backImageUri else card.frontImageUri
                promptImage?.let { path ->
                    if (card.kind == FlashcardKind.IMAGE_OCCLUSION) {
                        // Hide every other mask even after revealing the selected one.
                        val covered = if (!revealed) card.masks else card.masks.filterNot { it.id == variant }
                        StudyVisualImage(
                            imageUri = path,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            annotations = if (isReverse) card.backAnnotations else card.frontAnnotations,
                            masks = covered,
                            showCorrection = false
                        )
                    } else {
                        StudyVisualImage(
                            imageUri = path, modifier = Modifier.fillMaxWidth().height(240.dp),
                            annotations = if (isReverse) card.backAnnotations else card.frontAnnotations
                        )
                    }
                }
                if (card.kind == FlashcardKind.TYPE_ANSWER) {
                    OutlinedTextField(
                        value = entry, onValueChange = { entry = it },
                        label = { Text("Tu respuesta") }, enabled = !revealed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!revealed) {
                    Button(onClick = {
                        revealed = true
                        if (card.kind == FlashcardKind.CLOZE) {
                            libraryViewModel.advanceClozeRound(exercise.id, cursor)
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Mostrar respuesta") }
                } else {
                    Divider(color = TestarioSurfaceLight)
                    Text("Respuesta", color = TestarioGreen)
                    val mask = card.masks.find { it.id == variant }
                    val gapAnswer = when {
                        card.kind == FlashcardKind.CLOZE -> activeGaps.joinToString(" · ") { gap ->
                            question.substring(gap.start, gap.end)
                        }
                        mask != null -> mask.answer.ifBlank { solution }
                        else -> solution
                    }
                    Text(gapAnswer, color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    if (card.kind == FlashcardKind.CLOZE && solution.isNotBlank())
                        Text(solution, color = TestarioMutedText)
                    val answerImage = if (isReverse) card.frontImageUri else card.backImageUri
                    answerImage?.let { path ->
                        StudyVisualImage(
                            imageUri = path, modifier = Modifier.fillMaxWidth().height(240.dp),
                            annotations = if (isReverse) card.frontAnnotations else card.backAnnotations
                        )
                    }
                    if (card.kind == FlashcardKind.TYPE_ANSWER) {
                        val accepted = (listOf(solution) + card.acceptedAnswers).map(StudyExerciseRules::normalize)
                        Text(if (StudyExerciseRules.normalize(entry) in accepted) "Coincide con la solución"
                            else "No coincide literalmente; revisa la solución.",
                            color = TestarioMutedText)
                    }
                    if (database.ankiReviewEnabled && scheduledReview) {
                        Text("Repaso programado", color = TestarioMutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                ReviewRating.AGAIN to "Otra vez",
                                ReviewRating.HARD to "Difícil",
                                ReviewRating.GOOD to "Bien",
                                ReviewRating.EASY to "Fácil"
                            ).forEach { (rating, label) ->
                                OutlinedButton(onClick = {
                                    val cardId = if (card.kind == FlashcardKind.CLOZE)
                                        "${exercise.id}:cloze:${currentGroup ?: 0}"
                                        else "${exercise.id}:$variant"
                                    runCatching { libraryViewModel.recordAnkiReview(cardId, rating, attemptId) }
                                        .onFailure { error = it.message }
                                        .onSuccess {
                                            if (it) {
                                                if (index < cards.lastIndex) index++
                                                else onBack()
                                            }
                                        }
                                }, modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    }
                    if (index < cards.lastIndex) {
                        OutlinedButton(onClick = { index++ }, modifier = Modifier.fillMaxWidth()) {
                            Text("Siguiente tarjeta")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { retryCycle++ },
                                modifier = Modifier.weight(1f)) { Text("Repetir") }
                            Button(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Terminar") }
                        }
                    }
                }
                if (database.ankiReviewEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = scheduledReview, onCheckedChange = {
                            scheduledReview = it
                        })
                        Text("Esta sesión sí cuenta como repaso programado", color = TestarioText)
                    }
                } else {
                    Text("Modo libre: no se modifican las fechas de repaso.",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (error != null) Text(error.orEmpty(), color = TestarioRed)
            Spacer(Modifier.height(20.dp))
        }
    }
}
