package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.MixedSessionViewModel
import com.example.testario.viewmodel.LibraryViewModel
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
    onLeave: () -> Unit
) {
    val snapshot by session.snapshot.collectAsState()
    val result by session.result.collectAsState()
    val error by session.error.collectAsState()
    val scope = rememberCoroutineScope()
    var exitConfirm by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var reviewingResults by rememberSaveable { mutableStateOf(false) }
    var visitingQuestionEditor by rememberSaveable { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestVisiting by rememberUpdatedState(visitingQuestionEditor)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && latestVisiting) {
                session.resume()
                visitingQuestionEditor = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val active = snapshot?.takeUnless { it.completed }?.let { it.items.getOrNull(it.currentIndex) }
    val currentResponse = snapshot?.takeUnless { it.completed }?.let { it.responses.getOrNull(it.currentIndex) }
    var writtenInput by rememberSaveable(active?.id) {
        mutableStateOf(currentResponse?.writtenAnswers?.joinToString("\n").orEmpty())
    }
    fun persistDraft(): Boolean {
        if (active?.type != MixedItemType.WRITTEN || currentResponse?.revealed == true) return true
        val lines = writtenInput.lines().map(String::trim).filter(String::isNotBlank)
        return if (currentResponse?.writtenAnswers == lines) true
            else session.submitWritten(writtenInput, reveal = false)
    }


    BackHandler(enabled = snapshot != null && result == null) { exitConfirm = true }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioTopBar(title = if (snapshot?.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento", onBack = {
            if (result == null) exitConfirm = true else onLeave()
        }) }
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

            Text(
                "Pregunta ${current.currentIndex + 1} de ${current.items.size}",
                color = TestarioMutedText,
                style = MaterialTheme.typography.bodyMedium
            )
            if (current.remainingSeconds != null) {
                Text(
                    "Tiempo restante: ${current.remainingSeconds!! / 60} min " +
                        "${current.remainingSeconds!! % 60} s",
                    color = if (current.remainingSeconds == 0) TestarioRed else TestarioPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text("Tiempo registrado: ${current.elapsedSeconds / 60} min ${current.elapsedSeconds % 60} s",
                color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
            if (error != null) Text(error.orEmpty(), color = TestarioRed)

            if (result != null) {
                val finished = requireNotNull(result)
                if (!reviewingResults) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Resultados", color = TestarioText,
                                style = MaterialTheme.typography.titleLarge)
                            Text(
                                finished.scoreOutOfTen?.let { "${"%.2f".format(it)} / 10" }
                                    ?: "Repaso completado",
                                style = MaterialTheme.typography.displayLarge,
                                color = if ((finished.scoreOutOfTen ?: 10.0) >= 5.0)
                                    TestarioGreen else TestarioRed
                            )
                            Text("${finished.objectiveItems} evaluados · " +
                                "${finished.manualReviewItems} tarjetas Anki",
                                color = TestarioMutedText)
                        }
                    }
                    OutlinedButton(
                        onClick = { reviewingResults = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Revisar preguntas") }
                    Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al inicio")
                    }
                    return@Column
                }
                TextButton(onClick = { reviewingResults = false }) {
                    Text("Volver a resultados")
                }
                if (finished.scoreOutOfTen == null) {
                    Text("Sesión completada", color = TestarioPrimary)
                } else {
                    Text("Nota objetiva: ${"%.2f".format(finished.scoreOutOfTen)} / 10",
                        color = TestarioGreen, style = MaterialTheme.typography.titleLarge)
                    Text("${finished.objectiveItems} ejercicios puntuables · " +
                        "${finished.manualReviewItems} tarjetas Anki",
                        color = TestarioMutedText)
                }
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
                            val grade = StudyExerciseRules.gradeWrittenForQuestion(
                                ex, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong
                            )
                            Text("Nota: ${"%.2f".format(grade.scoreOutOfTen)} / 10 · " +
                                "${grade.correct} aciertos, ${grade.incorrect} errores, ${grade.omitted} omitidas",
                                color = TestarioText)
                            Text("Soluciones: " + ex.solutions.joinToString(" · ") { it.text }, color = TestarioGreen)
                        }
                        MixedItemType.ANKI -> {
                            val card = requireNotNull(item.exercise?.flashcard)
                            Text(card.front, color = TestarioText)
                            Text("Respuesta: " + card.back, color = TestarioGreen)

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

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(current.items) { idx, _ ->
                    val isCurrent = idx == current.currentIndex
                    OutlinedButton(
                        onClick = { if (persistDraft()) session.goTo(idx) },
                        enabled = !current.completed,
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("${idx + 1}",
                        color = if (isCurrent) TestarioPrimary else TestarioText) }
                }
            }
            val item = current.items[current.currentIndex]
            val response = current.responses[current.currentIndex]
            val showAnswer = response.revealed
            val locked = current.remainingSeconds == 0
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(TestarioSurface, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (item.type != MixedItemType.TEST) {
                    Text(item.topicName.uppercase(), color = TestarioPrimary,
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            when (item.type) {
                MixedItemType.TEST -> {
                    val question = requireNotNull(item.question)
                    var diffMenu by remember(item.id) { mutableStateOf(false) }
                    val diffText = when (question.difficulty) {
                        1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(item.topicName.uppercase(),
                            modifier = Modifier.weight(1f), color = TestarioPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold)
                        Box {
                            TextButton(onClick = { diffMenu = true }) {
                                Text(diffText, color = TestarioMutedText)
                            }
                            DropdownMenu(expanded = diffMenu, onDismissRequest = { diffMenu = false }) {
                                listOf(
                                    null to "Sin dificultad",
                                    1 to "Fácil", 2 to "Medio", 3 to "Difícil"
                                ).forEach { (difficulty, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        libraryViewModel.updateQuestionDifficulty(
                                            item.topicId, question.id, difficulty
                                        )
                                        session.setTestDifficulty(question.id, difficulty)
                                        diffMenu = false
                                    })
                                }
                            }
                        }
                        IconButton(onClick = {
                            if (persistDraft() && session.saveAndLeave()) {
                                visitingQuestionEditor = true
                                onEditQuestion(item.topicId, question.id)
                            }
                        }) {
                            Text("✎", color = TestarioPrimary,
                                style = MaterialTheme.typography.titleLarge)
                        }
                        IconButton(onClick = {
                            if (libraryViewModel.toggleQuestionFlag(question.id))
                                session.toggleTestFlag(question.id)
                        }) {
                            Text(if (question.flagged) "🚩" else "⚑",
                                color = TestarioPrimary,
                                style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Text(question.text, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    question.explanationImageUri?.let { path ->
                        StudyVisualImage(imageUri = path,
                            annotations = question.statementAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp))
                    }
                    question.answers.forEachIndexed { index, answer ->
                        val selected = response.selectedAnswerId == answer.id
                        val correct = showAnswer && answer.id == question.correctAnswerId
                        val wrong = showAnswer && selected && !correct
                        val bg = when {
                            correct -> TestarioGreenDark
                            wrong -> TestarioRedDark
                            selected -> TestarioPrimaryDark
                            else -> TestarioSurfaceLight
                        }
                        val borderColor = when {
                            correct -> TestarioGreen
                            wrong -> TestarioRed
                            selected -> TestarioPrimary
                            else -> TestarioSurfaceLight
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !locked &&
                                    !(current.mode == TestMode.TRAINING && response.revealed)) {
                                    session.selectTestAnswer(answer.id)
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = bg),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape, color = TestarioSurface,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(('A' + index).toString(), color = TestarioText)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(answer.text, color = TestarioText,
                                    modifier = Modifier.weight(1f))
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
                    val statuses = if (showAnswer) {
                        val grade = StudyExerciseRules.gradeWrittenForQuestion(
                            exercise, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong)
                        exercise.solutions.flatMap { solution ->
                            solution.points.map { point -> point.id to (solution.id in grade.correctIds) }
                        }.toMap()
                    } else emptyMap()
                    exercise.baseImageUri?.let { path ->
                        StudyVisualImage(imageUri = path, modifier = Modifier.fillMaxWidth().height(230.dp),
                            annotations = exercise.baseAnnotations,
                            markers = exercise.solutions.flatMap { it.points },
                            markerStatuses = statuses, showCorrection = showAnswer,
                            showMarkersBeforeCorrection = true)
                    }
                    exercise.sketch?.let { drawing ->
                        StudySketchCanvas(
                            sketch = drawing, modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = statuses
                        )
                    }
                    if (exercise.solutions.any { it.points.isNotEmpty() }) {
                        Text("Responde a cada número señalado en la imagen.",
                            color = TestarioMutedText)
                        val slots = writtenInput.split("\n")
                        exercise.solutions.forEachIndexed { index, _ ->
                            OutlinedTextField(
                                value = slots.getOrNull(index).orEmpty(),
                                onValueChange = { text ->
                                    val entries = writtenInput.split("\n").toMutableList()
                                    while (entries.size < exercise.solutions.size) entries.add("")
                                    entries[index] = text.replace("\n", " ")
                                    writtenInput = entries.joinToString("\n")
                                },
                                label = { Text("Respuesta ${index + 1}") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = !locked && !showAnswer
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = writtenInput,
                            onValueChange = { writtenInput = it },
                            label = { Text("Una respuesta por línea") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            enabled = !locked && !showAnswer
                        )
                    }
                    if (!showAnswer) {
                        Button(onClick = { session.submitWritten(writtenInput) },
                            enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text(if (current.mode == TestMode.EXAM) "Guardar respuestas" else "Comprobar")
                        }
                    } else {
                        val graded = StudyExerciseRules.gradeWrittenForQuestion(
                            exercise, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong
                        )
                        Text("Nota: ${"%.2f".format(graded.scoreOutOfTen)} / 10",
                            color = if (graded.correct == exercise.solutions.size &&
                                graded.incorrect == 0) TestarioGreen else TestarioRed)
                        if (exercise.solutions.any { it.points.isNotEmpty() }) {
                            exercise.solutions.forEachIndexed { index, solution ->
                                val entry = response.writtenAnswers.getOrNull(index).orEmpty()
                                val isCorrect = StudyExerciseRules.findAcceptedSolution(
                                    exercise, entry
                                )?.id == solution.id
                                Text("${index + 1}. " +
                                    (entry.ifBlank { "(sin responder)" }) +
                                    (if (isCorrect) " ✓" else " ✗"),
                                    color = if (isCorrect) TestarioGreen else TestarioRed
                                )
                                if (!isCorrect) Text("Correcta: ${solution.text}",
                                    color = TestarioGreen, style = MaterialTheme.typography.bodySmall)
                            }
                        } else response.writtenAnswers.forEach { entry ->
                            val solution = StudyExerciseRules.findAcceptedSolution(exercise, entry)
                            Text(entry + (solution?.let { " ✓" } ?: " ✗"),
                                color = if (solution == null) TestarioRed else TestarioGreen)
                        }
                        val missing = exercise.solutions.filterNot { it.id in graded.correctIds }
                        if (missing.isNotEmpty()) Text("Faltaban: " +
                            missing.joinToString(" · ") { it.text }, color = TestarioRed)
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

    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text("Salir sin guardar") },
            text = { Text("Se descartará este examen y no podrás retomarlo. Las respuestas no se guardarán.") },
            confirmButton = {
                Button(onClick = {
                    if (session.discardSaved()) {
                        discardConfirm = false
                        exitConfirm = false
                        onLeave()
                    }
                }) { Text("Descartar sesión") }
            },
            dismissButton = {
                OutlinedButton(onClick = { discardConfirm = false }) { Text("Cancelar") }
            }
        )
    }
    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            title = { Text("Guardar sesión") },
            text = { Text("Se conservarán los elementos, las respuestas y el tiempo para retomarla después.") },
            confirmButton = {
                Button(onClick = {
                    if (persistDraft() && session.saveAndLeave()) {
                        exitConfirm = false
                        onLeave()
                    }
                }) { Text("Guardar y salir") }
            },
            dismissButton = {
                Column {
                    TextButton(onClick = { exitConfirm = false; discardConfirm = true }) {
                        Text("Salir sin guardar", color = TestarioRed)
                    }
                    OutlinedButton(onClick = { exitConfirm = false }) { Text("Continuar") }
                }
            }
        )
    }
}
