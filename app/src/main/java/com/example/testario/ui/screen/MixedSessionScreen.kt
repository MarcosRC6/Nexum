package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.MixedSessionViewModel
import kotlinx.coroutines.launch

/**
 * Uses the original Testario test visual language, preserving the separate
 * mixed-session engine and its durable state. No automatic Anki review events.
 */
@Composable
fun MixedSessionScreen(
    session: MixedSessionViewModel,
    libraryViewModel: LibraryViewModel,
    onLeave: () -> Unit,
    onEditQuestion: (topicId: String, questionId: String) -> Unit = { _, _ -> }
) {
    val snapshot by session.snapshot.collectAsState()
    val result by session.result.collectAsState()
    val error by session.error.collectAsState()
    val scope = rememberCoroutineScope()
    val current = snapshot
    val item = current?.takeUnless { it.completed }?.items?.getOrNull(current.currentIndex)
    val response = current?.takeUnless { it.completed }?.responses?.getOrNull(current.currentIndex)
    var exitConfirm by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var reviewIndex by remember { mutableIntStateOf(0) }
    val numberState = rememberLazyListState()
    var writtenInput by rememberSaveable(item?.id) {
        mutableStateOf(response?.writtenAnswers?.joinToString("\n").orEmpty())
    }
    fun persistWritten(): Boolean {
        if (item?.type != MixedItemType.WRITTEN || response?.revealed == true) return true
        val normalized = writtenInput.lines().map(String::trim)
        return if (response?.writtenAnswers == normalized) true
            else session.submitWritten(writtenInput, reveal = false)
    }

    BackHandler(enabled = current != null && result == null) { exitConfirm = true }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            Surface(color = TestarioBackground) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (result == null) exitConfirm = true else onLeave() }) {
                        Text("✕", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            if (result != null) "Resultados"
                            else if (current?.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento",
                            style = MaterialTheme.typography.titleMedium, color = TestarioText
                        )
                        current?.let { running ->
                            Text(
                                "Pregunta ${running.currentIndex + 1} de ${running.items.size} · ⏱️ %02d:%02d"
                                    .format(running.elapsedSeconds / 60, running.elapsedSeconds % 60),
                                style = MaterialTheme.typography.bodySmall, color = TestarioPrimary
                            )
                        }
                    }
                    current?.remainingSeconds?.let { remaining ->
                        Text("%02d:%02d".format(remaining / 60, remaining % 60),
                            color = if (remaining < 60) TestarioRed else TestarioText)
                    }
                }
            }
        }
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (current == null) {
                Text("No existe ninguna sesión mixta activa.", color = TestarioMutedText)
                OutlinedButton(onClick = onLeave) { Text("Volver") }
                return@Column
            }
            error?.let { Text(it, color = TestarioRed) }

            if (result != null) {
                val finished = requireNotNull(result)
                if (!reviewing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(22.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                finished.scoreOutOfTen?.let { "%.2f/10".format(it) } ?: "Sin nota",
                                style = MaterialTheme.typography.displayLarge,
                                color = when {
                                    finished.scoreOutOfTen == null -> TestarioPrimary
                                    finished.scoreOutOfTen!! >= 5.0 -> TestarioGreen
                                    else -> TestarioRed
                                }
                            )
                            Text("${finished.objectiveItems} ejercicios puntuables · " +
                                "${finished.manualReviewItems} tarjetas Anki",
                                color = TestarioMutedText)
                        }
                    }
                    OutlinedButton(onClick = { reviewIndex = 0; reviewing = true },
                        modifier = Modifier.fillMaxWidth()) { Text("Revisar preguntas") }
                    Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver a Test")
                    }
                } else {
                    val i = reviewIndex.coerceIn(current.items.indices)
                    val review = current.items[i]
                    val answer = current.responses[i]
                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${i + 1} / ${current.items.size} · ${review.topicName}",
                                color = TestarioPrimary)
                            when (review.type) {
                                MixedItemType.TEST -> {
                                    val q = requireNotNull(review.question)
                                    Text(q.text, color = TestarioText,
                                        style = MaterialTheme.typography.titleLarge)
                                    Text("Tu respuesta: " +
                                        (q.answers.firstOrNull { it.id == answer.selectedAnswerId }?.text
                                            ?: "En blanco"),
                                        color = if (answer.selectedAnswerId == q.correctAnswerId)
                                            TestarioGreen else TestarioRed)
                                    Text("Correcta: " +
                                        q.answers.first { it.id == q.correctAnswerId }.text,
                                        color = TestarioGreen)
                                    if (q.explanation.isNotBlank()) Text(q.explanation, color = TestarioText)
                                    q.correctionImageUri?.let { path ->
                                        StudyVisualImage(path,
                                            modifier = Modifier.fillMaxWidth().height(220.dp),
                                            annotations = q.correctionAnnotations, showCorrection = true)
                                    }
                                }
                                MixedItemType.WRITTEN -> {
                                    val ex = requireNotNull(review.exercise)
                                    Text(ex.prompt, color = TestarioText,
                                        style = MaterialTheme.typography.titleLarge)
                                    MixedWrittenReview(ex, answer, current.pointsCorrect, current.penaltyWrong)
                                }
                                MixedItemType.ANKI -> {
                                    val card = requireNotNull(review.exercise?.flashcard)
                                    Text(card.front, color = TestarioText)
                                    Text(card.back, color = TestarioGreen)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { reviewIndex = (reviewIndex - 1).coerceAtLeast(0) },
                            enabled = reviewIndex > 0, modifier = Modifier.weight(1f)
                        ) { Text("Anterior") }
                        Button(onClick = {
                            if (reviewIndex < current.items.lastIndex) reviewIndex++
                            else reviewing = false
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (reviewIndex < current.items.lastIndex) "Siguiente" else "Resultados")
                        }
                    }
                }
                return@Column
            }

            if (current.completed) {
                Text("La sesión terminó, pero falta confirmar el guardado de los resultados.",
                    color = TestarioRed)
                Button(onClick = { scope.launch { session.finish() } }) {
                    Text("Reintentar guardar resultados")
                }
                return@Column
            }

            LaunchedEffect(current.currentIndex) {
                numberState.animateScrollToItem((current.currentIndex - 2).coerceAtLeast(0))
            }
            LazyRow(
                state = numberState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(current.items, key = { _, it -> it.id }) { index, candidate ->
                    val active = index == current.currentIndex
                    val answered = current.responses[index].selectedAnswerId != null ||
                        current.responses[index].writtenAnswers.any(String::isNotBlank) ||
                        current.responses[index].revealed
                    val color = when {
                        active -> TestarioPrimary
                        answered -> TestarioSurfaceLight
                        else -> TestarioSurface
                    }
                    Surface(
                        modifier = Modifier.size(42.dp).clickable {
                            if (persistWritten()) session.goTo(index)
                        },
                        shape = CircleShape, color = color,
                        border = BorderStroke(1.dp, if (active) TestarioPrimary else TestarioSurfaceLight)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("${index + 1}", color = if (active) TestarioBackground else TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            val active = requireNotNull(item)
            val answered = requireNotNull(response)
            val locked = current.remainingSeconds == 0
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(18.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (active.type) {
                        MixedItemType.TEST -> MixedTestQuestion(
                            item = active, response = answered, mode = current.mode,
                            locked = locked, session = session,
                            libraryViewModel = libraryViewModel, onEditQuestion = onEditQuestion
                        )
                        MixedItemType.WRITTEN -> {
                            val ex = requireNotNull(active.exercise)
                            Text(active.topicName.uppercase(), color = TestarioPrimary,
                                style = MaterialTheme.typography.labelLarge)
                            Text(ex.prompt, style = MaterialTheme.typography.titleLarge,
                                color = TestarioText)
                            val grade = if (answered.revealed) StudyExerciseRules.gradeWritten(
                                ex, answered.writtenAnswers, current.pointsCorrect, current.penaltyWrong
                            ) else null
                            val statuses = if (grade == null) emptyMap() else
                                ex.solutions.flatMap { solution ->
                                    solution.points.map { marker ->
                                        marker.id to (solution.id in grade.correctIds)
                                    }
                                }.toMap()
                            ex.baseImageUri?.let { uri ->
                                StudyVisualImage(
                                    imageUri = uri, annotations = ex.baseAnnotations,
                                    modifier = Modifier.fillMaxWidth().height(270.dp),
                                    markers = ex.solutions.flatMap { it.points },
                                    markerStatuses = statuses,
                                    showCorrection = answered.revealed
                                )
                            }
                            ex.sketch?.let { drawing ->
                                StudySketchCanvas(
                                    sketch = drawing, modifier = Modifier.fillMaxWidth().height(270.dp),
                                    markers = ex.solutions.flatMap { it.points },
                                    mode = SketchMode.PREVIEW, showMarkers = true,
                                    markerStatuses = statuses
                                )
                            }
                            val slots = writtenInput.split("\n")
                            ex.solutions.indices.forEach { index ->
                                OutlinedTextField(
                                    value = slots.getOrElse(index) { "" },
                                    onValueChange = { newValue ->
                                        val updated = MutableList(ex.solutions.size) { n ->
                                            slots.getOrElse(n) { "" }
                                        }
                                        updated[index] = newValue.replace("\n", " ")
                                        writtenInput = updated.joinToString("\n")
                                    },
                                    label = { Text("Respuesta ${index + 1}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !locked && !answered.revealed
                                )
                            }
                            if (!answered.revealed) {
                                Button(onClick = { session.submitWritten(writtenInput) },
                                    enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (current.mode == TestMode.EXAM) "Guardar respuestas" else "Comprobar")
                                }
                            } else MixedWrittenReview(
                                ex, answered, current.pointsCorrect, current.penaltyWrong
                            )
                        }
                        MixedItemType.ANKI -> {
                            val ex = requireNotNull(active.exercise)
                            val card = requireNotNull(ex.flashcard)
                            val reverse = active.variant == "reverse"
                            val front = if (reverse) card.back else card.front
                            val back = if (reverse) card.front else card.back
                            val masked = if (!reverse && card.kind == FlashcardKind.CLOZE)
                                ClozeMarkupEngine.mask(front, card.gaps, active.clozeCursor) else front
                            Text(active.topicName.uppercase(), color = TestarioPrimary)
                            Text(if (answered.revealed) front else masked,
                                color = TestarioText, style = MaterialTheme.typography.titleLarge)
                            (if (reverse) card.backImageUri else card.frontImageUri)?.let { uri ->
                                StudyVisualImage(
                                    imageUri = uri,
                                    annotations = if (reverse) card.backAnnotations else card.frontAnnotations,
                                    modifier = Modifier.fillMaxWidth().height(260.dp),
                                    masks = if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                                        card.masks.filter { it.id == active.variant } else emptyList(),
                                    showCorrection = answered.revealed
                                )
                            }
                            if (!answered.revealed) {
                                Button(onClick = { session.revealAnki() },
                                    enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                                    Text("Mostrar respuesta")
                                }
                            } else {
                                Text(back.ifBlank { front }, color = TestarioGreen)
                                (if (reverse) card.frontImageUri else card.backImageUri)?.let { uri ->
                                    StudyVisualImage(
                                        imageUri = uri,
                                        annotations = if (reverse) card.frontAnnotations else card.backAnnotations,
                                        modifier = Modifier.fillMaxWidth().height(260.dp),
                                        showCorrection = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { if (persistWritten()) session.goTo(current.currentIndex - 1) },
                    enabled = !locked && current.currentIndex > 0,
                    modifier = Modifier.weight(1f).height(50.dp)) { Text("Atrás") }
                if (current.currentIndex < current.items.lastIndex) {
                    Button(onClick = { if (persistWritten()) session.goTo(current.currentIndex + 1) },
                        enabled = !locked,
                        modifier = Modifier.weight(1f).height(50.dp)) { Text("Siguiente") }
                } else {
                    Button(onClick = { if (persistWritten()) scope.launch { session.finish() } },
                        modifier = Modifier.weight(1f).height(50.dp)) { Text("Finalizar") }
                }
            }
        }
    }

    if (exitConfirm) AlertDialog(
        onDismissRequest = { exitConfirm = false },
        title = { Text("Abandonar sesión") },
        text = { Text("¿Quieres guardar para continuar después o salir sin guardar?") },
        confirmButton = {
            Button(onClick = {
                if (persistWritten() && session.saveAndLeave()) {
                    exitConfirm = false
                    onLeave()
                }
            }) { Text("Guardar y salir") }
        },
        dismissButton = {
            Column {
                OutlinedButton(onClick = {
                    if (session.discardSaved()) {
                        exitConfirm = false
                        onLeave()
                    }
                }) { Text("Salir sin guardar", color = TestarioRed) }
                TextButton(onClick = { exitConfirm = false }) { Text("Continuar") }
            }
        }
    )
}

@Composable
private fun MixedTestQuestion(
    item: MixedSessionItem,
    response: MixedSessionResponse,
    mode: TestMode,
    locked: Boolean,
    session: MixedSessionViewModel,
    libraryViewModel: LibraryViewModel,
    onEditQuestion: (String, String) -> Unit
) {
    val question = requireNotNull(item.question)
    val database by libraryViewModel.database.collectAsState()
    val live = database.topics.firstOrNull { it.id == item.topicId }
        ?.questions?.firstOrNull { it.id == question.id }
    val flagged = live?.flagged ?: question.flagged
    val difficulty = live?.difficulty ?: question.difficulty
    var difficultyOpen by remember(question.id) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(item.topicName.uppercase(), color = TestarioPrimary,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { difficultyOpen = true }) {
                Text(when (difficulty) { 1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?" },
                    color = when (difficulty) {
                        1 -> TestarioGreen; 2 -> TestarioYellow; 3 -> TestarioRed
                        else -> TestarioMutedText
                    })
            }
            DropdownMenu(expanded = difficultyOpen,
                onDismissRequest = { difficultyOpen = false }) {
                listOf(
                    1 to "Fácil", 2 to "Medio", 3 to "Difícil", null to "Sin asignar"
                ).forEach { (value, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        libraryViewModel.updateQuestionDifficulty(item.topicId, question.id, value)
                        difficultyOpen = false
                    })
                }
            }
        }
        IconButton(onClick = { onEditQuestion(item.topicId, question.id) },
            enabled = live != null) { Text("✏️") }
        IconButton(
            onClick = { libraryViewModel.toggleQuestionFlag(question.id) },
            enabled = live != null
        ) { Text(if (flagged) "🚩" else "🏳️") }
    }
    Text(question.text, color = TestarioText, style = MaterialTheme.typography.titleLarge)
    question.explanationImageUri?.let { uri ->
        StudyVisualImage(imageUri = uri,
            annotations = question.statementAnnotations,
            modifier = Modifier.fillMaxWidth().height(230.dp))
    }
    question.answers.forEachIndexed { index, answer ->
        val selected = response.selectedAnswerId == answer.id
        val correct = question.correctAnswerId == answer.id
        val revealed = mode == TestMode.TRAINING && response.revealed
        val background = when {
            revealed && correct -> TestarioGreenDark
            revealed && selected -> TestarioRedDark
            selected -> TestarioPrimaryDark
            else -> TestarioSurfaceLight
        }
        val border = when {
            revealed && correct -> TestarioGreen
            revealed && selected -> TestarioRed
            selected -> TestarioPrimary
            else -> TestarioSurfaceLight
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                .clickable(enabled = !locked && !revealed) {
                    session.selectTestAnswer(answer.id)
                },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = background),
            border = BorderStroke(1.dp, border)
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = TestarioSurface,
                    modifier = Modifier.size(28.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(('A' + index).toString(), color = TestarioText)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(answer.text, modifier = Modifier.weight(1f), color = TestarioText)
            }
        }
    }
    if (mode == TestMode.TRAINING && response.revealed) {
        question.explanation.takeIf { it.isNotBlank() }?.let { Text(it, color = TestarioText) }
        question.correctionImageUri?.let { uri ->
            StudyVisualImage(imageUri = uri,
                annotations = question.correctionAnnotations,
                modifier = Modifier.fillMaxWidth().height(230.dp),
                showCorrection = true)
        }
    }
}

@Composable
private fun MixedWrittenReview(
    exercise: StudyExercise,
    response: MixedSessionResponse,
    pointsCorrect: Double,
    penaltyWrong: Double
) {
    val grade = StudyExerciseRules.gradeWritten(
        exercise, response.writtenAnswers, pointsCorrect, penaltyWrong
    )
    Text("Nota: %.2f / 10".format(grade.scoreOutOfTen),
        color = if (grade.scoreOutOfTen >= 5.0) TestarioGreen else TestarioRed)
    exercise.solutions.forEachIndexed { index, solution ->
        val submitted = response.writtenAnswers.getOrNull(index).orEmpty()
        val accepted = StudyExerciseRules.findAcceptedSolution(exercise, submitted)
        Text(
            "Respuesta ${index + 1}: " + (if (submitted.isBlank()) "Sin responder"
                else submitted) + when {
                submitted.isBlank() -> " ✗"
                accepted != null -> " ✓"
                else -> " ✗"
            },
            color = if (accepted != null) TestarioGreen else TestarioRed
        )
        if (solution.id !in grade.correctIds)
            Text("Faltaba: ${solution.text}", color = TestarioRed,
                style = MaterialTheme.typography.bodySmall)
    }
}
