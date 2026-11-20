package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
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
    var reviewingResults by remember { mutableStateOf(false) }
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
        topBar = { TestarioTopBar(
            title = if (result != null && !reviewingResults) "Resultados"
                else if (snapshot?.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento",
            onBack = { if (result == null) exitConfirm = true else onLeave() }
        ) }
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
                (if (current.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento") +
                    " · Pregunta ${current.currentIndex + 1} de ${current.items.size}",
                color = TestarioText,
                style = MaterialTheme.typography.titleLarge
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
                    Card(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                        Column(Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                if (finished.scoreOutOfTen == null) "Repaso completado"
                                else "${"%.2f".format(finished.scoreOutOfTen)} / 10",
                                color = if (finished.scoreOutOfTen == null) TestarioPrimary
                                    else if (finished.scoreOutOfTen >= 5.0) TestarioGreen
                                    else TestarioRed,
                                style = MaterialTheme.typography.headlineLarge
                            )
                            Text("${finished.objectiveItems} elementos puntuables · " +
                                "${finished.manualReviewItems} tarjetas manuales",
                                color = TestarioMutedText)
                        }
                    }
                    OutlinedButton(onClick = { reviewingResults = true },
                        modifier = Modifier.fillMaxWidth()) { Text("Revisar preguntas") }
                    OutlinedButton(onClick = onLeave,
                        modifier = Modifier.fillMaxWidth()) { Text("Volver al inicio") }
                    return@Column
                }
                Text("Revisión de preguntas", style = MaterialTheme.typography.titleLarge,
                    color = TestarioText)
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
                            Text("Soluciones: " + ex.solutions.joinToString(" · ") { it.text }, color = TestarioGreen)
                        }
                        MixedItemType.ANKI -> {
                            val card = requireNotNull(item.exercise?.flashcard)
                            Text(card.front, color = TestarioText)
                            Text("Respuesta: " + card.back, color = TestarioGreen)
                            Text("Repaso manual · sin calificación", color = TestarioMutedText)
                        }
                    }
                }
                OutlinedButton(onClick = { reviewingResults = false },
                    modifier = Modifier.fillMaxWidth()) { Text("Volver a resultados") }
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

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                current.items.forEachIndexed { index, element ->
                    val activeNumber = index == current.currentIndex
                    val answered = current.responses[index].selectedAnswerId != null ||
                        current.responses[index].writtenAnswers.isNotEmpty() ||
                        current.responses[index].revealed
                    val flagged = element.question?.flagged == true
                    Surface(
                        modifier = Modifier.size(43.dp).clickable {
                            if (persistDraft()) session.goTo(index)
                        },
                        shape = CircleShape,
                        color = if (activeNumber) TestarioPrimary
                            else if (answered) TestarioSurfaceLight else TestarioSurface,
                        border = BorderStroke(
                            if (flagged) 2.dp else 1.dp,
                            if (flagged) TestarioRed else
                                if (activeNumber) TestarioPrimary else TestarioSurfaceLight
                        )
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("${index + 1}",
                                color = if (activeNumber) TestarioBackground else TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                            if (flagged) Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(7.dp)
                                    .background(TestarioRed, CircleShape)
                            )
                        }
                    }
                }
            }
            val item = current.items[current.currentIndex]
            val response = current.responses[current.currentIndex]
            val showAnswer = response.revealed
            val locked = current.remainingSeconds == 0
            when (item.type) {
                MixedItemType.TEST -> {
                    val question = requireNotNull(item.question)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(item.topicName.uppercase(),
                                    color = TestarioPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f))
                                var diffMenu by remember(question.id) { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { diffMenu = true }) {
                                        Text(when (question.difficulty) {
                                            1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?"
                                        }, color = when(question.difficulty) {
                                            1 -> TestarioGreen; 2 -> TestarioYellow
                                            3 -> TestarioRed; else -> TestarioMutedText
                                        })
                                    }
                                    DropdownMenu(expanded = diffMenu, onDismissRequest = {
                                        diffMenu = false
                                    }) {
                                        listOf(1 to "Fácil", 2 to "Medio", 3 to "Difícil",
                                            null to "Sin asignar").forEach { (level,label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                if (session.updateTestDifficulty(question.id, level)) {
                                                    libraryViewModel.updateQuestionDifficulty(
                                                        item.topicId, question.id, level
                                                    )
                                                }
                                                diffMenu = false
                                            })
                                        }
                                    }
                                }
                                IconButton(onClick = {
                                    onEditQuestion(item.topicId, question.id)
                                }) { Text("✏️") }
                                IconButton(onClick = {
                                    if (session.toggleTestFlag(question.id)) {
                                        libraryViewModel.toggleQuestionFlag(question.id)
                                    }
                                }) { Text(if (question.flagged) "🚩" else "🏳️") }
                            }
                            Text(question.text, color = TestarioText,
                                style = MaterialTheme.typography.titleLarge)
                            question.explanationImageUri?.let { path ->
                                StudyVisualImage(
                                    imageUri = path, annotations = question.statementAnnotations,
                                    modifier = Modifier.fillMaxWidth().height(220.dp))
                            }
                            question.answers.forEachIndexed { index, answer ->
                                val selected = response.selectedAnswerId == answer.id
                                val correct = answer.id == question.correctAnswerId
                                val fill = when {
                                    showAnswer && correct -> TestarioGreenDark
                                    showAnswer && selected -> TestarioRedDark
                                    selected -> TestarioPrimaryDark
                                    else -> TestarioSurfaceLight
                                }
                                val stroke = when {
                                    showAnswer && correct -> TestarioGreen
                                    showAnswer && selected -> TestarioRed
                                    selected -> TestarioPrimary
                                    else -> TestarioSurfaceLight
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable(
                                        enabled = !locked && !showAnswer
                                    ) { session.selectTestAnswer(answer.id) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = fill),
                                    border = BorderStroke(1.dp, stroke)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Surface(modifier = Modifier.size(28.dp), shape = CircleShape,
                                            color = TestarioSurface) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(('A' + index).toString(),
                                                    color = TestarioText)
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(answer.text, color = TestarioText,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            if (showAnswer) {
                                if (question.explanation.isNotBlank())
                                    Text(question.explanation, color = TestarioText)
                                question.correctionImageUri?.let { path ->
                                    StudyVisualImage(imageUri = path,
                                        annotations = question.correctionAnnotations,
                                        modifier = Modifier.fillMaxWidth().height(220.dp),
                                        showCorrection = true)
                                }
                            }
                        }
                    }
                }
                MixedItemType.WRITTEN -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                        Column(Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.topicName.uppercase(), color = TestarioPrimary,
                                style = MaterialTheme.typography.labelMedium)
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
                        StudyVisualImage(imageUri = path, modifier = Modifier.fillMaxWidth().height(230.dp),
                            annotations = exercise.baseAnnotations,
                            markers = exercise.solutions.flatMap { it.points },
                            markerStatuses = statuses, showCorrection = showAnswer,
                            showAnswerMarkers = true)
                    }
                    exercise.sketch?.let { drawing ->
                        StudySketchCanvas(
                            sketch = drawing, modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = if (showAnswer) {
                                val graded = StudyExerciseRules.gradeWritten(exercise, response.writtenAnswers,
                                    current.pointsCorrect, current.penaltyWrong)
                                exercise.solutions.flatMap { sol -> sol.points.map { it.id to
                                    (sol.id in graded.correctIds) } }.toMap()
                            } else emptyMap()
                        )
                    }
                    val slots = writtenInput.split("\n").toMutableList()
                    val expected = exercise.solutions.size
                    repeat(expected) { idx ->
                        OutlinedTextField(
                            value = slots.getOrElse(idx) { "" },
                            onValueChange = { value ->
                                while (slots.size <= idx) slots.add("")
                                slots[idx] = value.replace("\n", " ")
                                writtenInput = slots.joinToString("\n")
                            },
                            label = { Text("Solución ${idx + 1}") },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            enabled = !locked && !showAnswer
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
                            color = if (graded.incorrect > 0 || graded.omitted > 0)
                                TestarioRed else TestarioGreen)
                        response.writtenAnswers.forEach { entry ->
                            val solution = StudyExerciseRules.findAcceptedSolution(exercise, entry)
                            Text(entry + (solution?.let { " ✓" } ?: " ✗"),
                                color = if (solution == null) TestarioRed else TestarioGreen)
                        }
                        val missing = exercise.solutions.filterNot { it.id in graded.correctIds }
                        if (missing.isNotEmpty()) Text("Faltaban: " +
                            missing.joinToString(" · ") { it.text }, color = TestarioRed)
                    }
                        }
                    }
                }
                MixedItemType.ANKI -> {
                    Card(modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                        Column(Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(item.topicName.uppercase(), color = TestarioPrimary,
                                style = MaterialTheme.typography.labelMedium)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { exitConfirm = false }) { Text("Continuar") }
                    TextButton(onClick = {
                        if (session.discardSaved()) {
                            exitConfirm = false
                            onLeave()
                        }
                    }) { Text("Salir sin guardar", color = TestarioRed) }
                }
            }
        )
    }
}
