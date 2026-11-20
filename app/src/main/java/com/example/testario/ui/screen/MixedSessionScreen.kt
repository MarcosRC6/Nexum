package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import com.example.testario.viewmodel.LibraryViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.MixedSessionViewModel
import kotlinx.coroutines.launch

/**
 * Test, Enumerar and Anki inside the SAME configured session and four-tab app.
 * Anki is manual study: no automatic grade and no scheduler mutation.
 */
@Composable
fun MixedSessionScreen(
    session: MixedSessionViewModel,
    libraryViewModel: LibraryViewModel,
    onEditQuestion: (String, String) -> Unit,
    onLeave: () -> Unit,
    onFinish: () -> Unit
) {
    val snapshot by session.snapshot.collectAsState()
    val result by session.result.collectAsState()
    val error by session.error.collectAsState()
    val scope = rememberCoroutineScope()
    var exitConfirm by remember { mutableStateOf(false) }
    var reviewingFinished by remember { mutableStateOf(false) }
    val active = snapshot?.takeUnless { it.completed }?.let { it.items.getOrNull(it.currentIndex) }
    val currentResponse = snapshot?.takeUnless { it.completed }?.let { it.responses.getOrNull(it.currentIndex) }
    var writtenInput by rememberSaveable(active?.id) {
        mutableStateOf(currentResponse?.writtenAnswers?.joinToString("\n").orEmpty())
    }
    fun persistDraft(): Boolean {
        if (active?.type != MixedItemType.WRITTEN || currentResponse?.revealed == true) return true
        val lines = writtenInput.lines().map(String::trim)
        return if (currentResponse?.writtenAnswers == lines) true
            else session.submitWritten(writtenInput, reveal = false)
    }


    BackHandler(enabled = snapshot != null && result == null) { exitConfirm = true }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            Surface(color = TestarioBackground) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (result == null) exitConfirm = true else onLeave() }) {
                        Text("✕", color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(if (snapshot?.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento",
                            style = MaterialTheme.typography.titleMedium, color = TestarioText)
                        val current = snapshot
                        if (current != null) Text(
                            "Pregunta ${current.currentIndex + 1} de ${current.items.size} · ⏱ %02d:%02d".format(
                                current.elapsedSeconds / 60, current.elapsedSeconds % 60
                            ), color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    snapshot?.remainingSeconds?.let { seconds ->
                        Text("%02d:%02d".format(seconds / 60, seconds % 60),
                            color = if (seconds < 60) TestarioRed else TestarioText)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val current = snapshot
            if (current == null) {
                Text("No existe ninguna sesión mixta activa.", color = TestarioMutedText)
                OutlinedButton(onClick = onLeave) { Text("Volver") }
                return@Column
            }

            if (error != null) Text(error.orEmpty(), color = TestarioRed)

            if (result != null) {
                val finished = requireNotNull(result)
                if (!reviewingFinished) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            val score = finished.scoreOutOfTen
                            Text(if (score == null) "Sin nota" else "${"%.2f".format(score)} / 10",
                                style = MaterialTheme.typography.displayLarge,
                                color = if (score == null) TestarioMutedText
                                    else if (score >= 5.0) TestarioGreen else TestarioRed)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "${finished.objectiveItems} evaluables · ${finished.manualReviewItems} Anki",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { reviewingFinished = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revisar preguntas") }
                    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al Inicio")
                    }
                    return@Column
                }
                OutlinedButton(onClick = { reviewingFinished = false },
                    modifier = Modifier.fillMaxWidth()) { Text("Volver a resultados") }
                current.items.zip(current.responses).forEachIndexed { idx, pair ->
                    val (item, response) = pair
                    HorizontalDivider()
                    Text("${idx + 1}. ${item.topicName} · ${item.type.name}",
                        color = TestarioMutedText)
                    when (item.type) {
                        MixedItemType.TEST -> {
                            val q = requireNotNull(item.question)
                            Text(q.text, color = TestarioText)
                            Text("Tu respuesta: " +
                                (q.answers.firstOrNull { it.id == response.selectedAnswerId }?.text ?: "En blanco"),
                                color = if (response.selectedAnswerId == q.correctAnswerId) TestarioGreen else TestarioRed)
                            Text("Correcta: " + q.answers.first { it.id == q.correctAnswerId }.text,
                                color = TestarioGreen)
                        }
                        MixedItemType.WRITTEN -> {
                            val ex = requireNotNull(item.exercise)
                            Text(ex.prompt, color = TestarioText)
                            val grade = StudyExerciseRules.gradeWritten(
                                ex, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong
                            )
                            Text("Nota: ${"%.2f".format(grade.scoreOutOfTen)} / 10 · " +
                                "${grade.correct} aciertos, ${grade.incorrect} errores, ${grade.omitted} omitidas",
                                color = TestarioText)
                            ex.solutions.forEachIndexed { index, solution ->
                                val hit = solution.id in grade.correctIds
                                Text("${index + 1}. ${solution.text}" + if (hit) " ✓" else " · Sin acertar",
                                    color = if (hit) TestarioGreen else TestarioRed)
                            }
                            grade.incorrectInputs.forEach { Text("$it ✗", color = TestarioRed) }
                        }
                        MixedItemType.ANKI -> {
                            val card = requireNotNull(item.exercise?.flashcard)
                            Text(card.front, color = TestarioText)
                            Text("Respuesta: " + card.back, color = TestarioGreen)
                            Text("Repaso manual · sin calificación", color = TestarioMutedText)
                        }
                    }
                }
                Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) { Text("Terminar") }
                return@Column
            }

            if (current.completed) {
                Text("El examen terminó, pero aún debe confirmarse el guardado de los resultados.",
                    color = TestarioRed)
                Button(onClick = { scope.launch { session.finish() } },
                    modifier = Modifier.fillMaxWidth()) { Text("Reintentar guardar resultados") }
                return@Column
            }

            val item = current.items[current.currentIndex]
            val response = current.responses[current.currentIndex]
            val showAnswer = response.revealed
            val locked = current.remainingSeconds == 0
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                current.items.forEachIndexed { index, content ->
                    val selected = index == current.currentIndex
                    val answered = current.responses[index].let { response ->
                        response.selectedAnswerId != null || response.revealed ||
                            response.writtenAnswers.isNotEmpty()
                    }
                    val flagged = content.question?.flagged == true
                    Surface(
                        modifier = Modifier.size(44.dp).clickable {
                            if (persistDraft()) session.goTo(index)
                        },
                        shape = CircleShape,
                        color = if (selected) TestarioPrimary else if (answered) TestarioSurfaceLight else TestarioSurface,
                        border = BorderStroke(if (flagged) 2.dp else 1.dp,
                            if (flagged) TestarioRed else if (selected) TestarioPrimary else TestarioSurfaceLight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}",
                                color = if (selected) TestarioBackground else TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.topicName.uppercase(), color = TestarioPrimary,
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    when (item.type) {
                MixedItemType.TEST -> {
                    val question = requireNotNull(item.question)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        var difficultyOpen by remember(question.id) { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { difficultyOpen = true }) {
                                Text(when (question.difficulty) {
                                    1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?"
                                }, color = when (question.difficulty) {
                                    1 -> TestarioGreen; 2 -> TestarioYellow; 3 -> TestarioRed;
                                    else -> TestarioMutedText
                                })
                            }
                            DropdownMenu(expanded = difficultyOpen,
                                onDismissRequest = { difficultyOpen = false }) {
                                listOf(null to "Sin asignar", 1 to "Fácil", 2 to "Medio", 3 to "Difícil").forEach { (level, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        libraryViewModel.updateQuestionDifficulty(item.topicId, question.id, level)
                                        session.updateTestQuestion(question.id, difficulty = level, updateDifficulty = true)
                                        difficultyOpen = false
                                    })
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { onEditQuestion(item.topicId, question.id) }) {
                            Text("✏", color = TestarioText)
                        }
                        IconButton(
                            enabled = !question.flagged,
                            onClick = {
                                if (!question.flagged && libraryViewModel.toggleQuestionFlag(question.id))
                                    session.updateTestQuestion(question.id, flagged = true)
                            }
                        ) { Text(if (question.flagged) "🚩" else "⚑", color = TestarioText) }
                    }
                    Text(question.text, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    question.explanationImageUri?.let { path ->
                        StudyVisualImage(imageUri = path,
                            annotations = question.statementAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                    question.answers.forEachIndexed { index, answer ->
                        val isSelected = response.selectedAnswerId == answer.id
                        val isCorrect = answer.id == question.correctAnswerId
                        val bg = when {
                            showAnswer && isCorrect -> TestarioGreenDark
                            showAnswer && isSelected -> TestarioRedDark
                            isSelected -> TestarioPrimaryDark
                            else -> TestarioSurfaceLight
                        }
                        val border = when {
                            showAnswer && isCorrect -> TestarioGreen
                            showAnswer && isSelected -> TestarioRed
                            isSelected -> TestarioPrimary
                            else -> TestarioSurfaceLight
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !locked &&
                                    !(current.mode == TestMode.TRAINING && response.revealed)) {
                                    session.selectTestAnswer(answer.id)
                                },
                            colors = CardDefaults.cardColors(containerColor = bg),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, border)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = TestarioBackground) {
                                    Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                        Text("${'A' + index}", color = TestarioText)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(answer.text, color = TestarioText,
                                    style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    if (showAnswer) {
                        Text("Correcta: " + question.answers.first {
                            it.id == question.correctAnswerId
                        }.text, color = TestarioGreen)
                        question.explanation.takeIf(String::isNotBlank)?.let { Text(it, color = TestarioText) }
                        question.correctionImageUri?.let { path ->
                            StudyVisualImage(imageUri = path, annotations = question.correctionAnnotations,
                                modifier = Modifier.fillMaxWidth().height(220.dp), showCorrection = true)
                        }
                    }
                }
                MixedItemType.WRITTEN -> {
                    val exercise = requireNotNull(item.exercise)
                    Text(exercise.prompt, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    exercise.baseImageUri?.let { path ->
                        val statuses = if (showAnswer) {
                            val grade = StudyExerciseRules.gradeWritten(
                                exercise, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong)
                            exercise.solutions.flatMap { solution ->
                                solution.points.map { point -> point.id to (solution.id in grade.correctIds) }
                            }.toMap()
                        } else emptyMap()
                        StudyVisualImage(imageUri = path, modifier = Modifier.fillMaxWidth().height(270.dp),
                            annotations = exercise.baseAnnotations,
                            markers = exercise.solutions.flatMap { it.points },
                            markerStatuses = statuses, showCorrection = true)
                    }
                    exercise.sketch?.let { drawing ->
                        StudySketchCanvas(
                            sketch = drawing, modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = if (showAnswer) {
                                val grade = StudyExerciseRules.gradeWritten(
                                    exercise, response.writtenAnswers,
                                    current.pointsCorrect, current.penaltyWrong
                                )
                                exercise.solutions.flatMap { solution ->
                                    solution.points.map { it.id to (solution.id in grade.correctIds) }
                                }.toMap()
                            } else emptyMap()
                        )
                    }
                    Text("Responde a cada solución numerada de la imagen.",
                        style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
                    val entries = writtenInput.split("\n")
                    exercise.solutions.forEachIndexed { answerIndex, solution ->
                        OutlinedTextField(
                            value = entries.getOrElse(answerIndex) { "" },
                            onValueChange = { typed ->
                                val next = MutableList(exercise.solutions.size) { n ->
                                    entries.getOrElse(n) { "" }
                                }
                                next[answerIndex] = typed.replace("\n", " ")
                                writtenInput = next.joinToString("\n")
                            },
                            label = { Text("Solución ${answerIndex + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !locked && !showAnswer,
                            singleLine = true
                        )
                    }
                    if (!showAnswer) {
                        Button(onClick = { session.submitWritten(writtenInput) },
                            enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text(if (current.mode == TestMode.EXAM) "Guardar respuestas" else "Comprobar")
                        }
                    } else {
                        val graded = StudyExerciseRules.gradeWritten(
                            exercise, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong
                        )
                        Text("Nota: ${"%.2f".format(graded.scoreOutOfTen)} / 10",
                            color = if (graded.scoreOutOfTen >= 5.0) TestarioGreen else TestarioRed)
                        response.writtenAnswers.forEach { entry ->
                            val solution = StudyExerciseRules.findAcceptedSolution(exercise, entry)
                            Text(entry + if (solution != null) " ✓" else " ✗",
                                color = if (solution == null) TestarioRed else TestarioGreen)
                        }
                        exercise.solutions.forEachIndexed { index, solution ->
                            val correct = solution.id in graded.correctIds
                            Text(
                                "${index + 1}. ${solution.text}" + if (correct) " ✓" else " · Sin acertar",
                                color = if (correct) TestarioGreen else TestarioRed
                            )
                        }
                    }
                }
                MixedItemType.ANKI -> {
                    val exercise = requireNotNull(item.exercise)
                    val card = requireNotNull(exercise.flashcard)
                    val reverse = item.variant == "reverse"
                    val questionText = if (reverse) card.back else card.front
                    val solutionText = if (reverse) card.front else card.back
                    val maskedQuestion = if (!reverse && card.kind == FlashcardKind.CLOZE)
                        ClozeMarkupEngine.mask(questionText, card.gaps, item.clozeCursor)
                        else questionText
                    Text(if (showAnswer) questionText else maskedQuestion,
                        color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    (if (reverse) card.backImageUri else card.frontImageUri)?.let { path ->
                        StudyVisualImage(imageUri = path,
                            annotations = if (reverse) card.backAnnotations else card.frontAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            masks = if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                                card.masks.filter { it.id == item.variant } else emptyList(),
                            showCorrection = showAnswer)
                    }
                    if (!showAnswer) {
                        Button(onClick = { session.revealAnki() },
                            enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text("Mostrar respuesta")
                        }
                    } else {
                        Text(solutionText.ifBlank { questionText }, color = TestarioGreen)
                        (if (reverse) card.frontImageUri else card.backImageUri)?.let { path ->
                            StudyVisualImage(imageUri = path,
                                annotations = if (reverse) card.frontAnnotations else card.backAnnotations,
                                modifier = Modifier.fillMaxWidth().height(220.dp), showCorrection = true)
                        }
                    }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (persistDraft()) session.goTo(current.currentIndex - 1) },
                    modifier = Modifier.weight(1f),
                    enabled = !locked && current.currentIndex > 0
                ) { Text("Anterior") }
                if (current.currentIndex < current.items.lastIndex) {
                    Button(onClick = { if (persistDraft()) session.goTo(current.currentIndex + 1) },
                        modifier = Modifier.weight(1f), enabled = !locked) { Text("Siguiente") }
                } else {
                    Button(onClick = { if (persistDraft()) scope.launch { session.finish() } },
                        modifier = Modifier.weight(1f)) { Text("Finalizar") }
                }
            }
            OutlinedButton(onClick = { exitConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar y salir")
            }
        }
    }

    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            title = { Text("Salir del ejercicio") },
            text = { Text("Puedes guardar el progreso para retomarlo o descartarlo sin registrar resultados.") },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Button(onClick = {
                        if (persistDraft() && session.saveAndLeave()) {
                            exitConfirm = false
                            onLeave()
                        }
                    }) { Text("Guardar y salir") }
                    TextButton(onClick = {
                        if (session.discardActive()) {
                            exitConfirm = false
                            onLeave()
                        }
                    }) { Text("Salir sin guardar", color = TestarioRed) }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { exitConfirm = false }) { Text("Continuar") }
            }
        )
    }
}
