package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
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
import com.example.testario.domain.OcclusionRevealRules
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
    onLeave: () -> Unit,
    onEditQuestion: (String, String) -> Unit = { _, _ -> }
) {
    val snapshot by session.snapshot.collectAsState()
    val result by session.result.collectAsState()
    val error by session.error.collectAsState()
    val scope = rememberCoroutineScope()
    var exitConfirm by remember { mutableStateOf(false) }
    var reviewingResults by rememberSaveable { mutableStateOf(false) }
    var reviewIndex by rememberSaveable { mutableIntStateOf(0) }
    val active = snapshot?.takeUnless { it.completed }?.let { it.items.getOrNull(it.currentIndex) }
    val currentResponse = snapshot?.takeUnless { it.completed }?.let { it.responses.getOrNull(it.currentIndex) }
    var writtenInput by rememberSaveable(active?.id) {
        mutableStateOf(currentResponse?.writtenAnswers?.joinToString("\n").orEmpty())
    }
    fun persistDraft(): Boolean {
        if (active?.type != MixedItemType.WRITTEN || currentResponse?.revealed == true) return true
        val count = active.exercise?.solutions?.size ?: 0
        val parsed = writtenInput.lines().take(count).map(String::trim)
        val lines = if (parsed.all(String::isBlank)) emptyList() else parsed
        return if (currentResponse?.writtenAnswers == lines) true
            else session.submitWritten(writtenInput, reveal = false)
    }


    BackHandler(enabled = snapshot != null && result == null) { exitConfirm = true }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            Surface(color = TestarioBackground) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (result == null) exitConfirm = true else onLeave()
                    }) { Text("✕", style = MaterialTheme.typography.titleLarge, color = TestarioText) }
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(if (snapshot?.mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento",
                            style = MaterialTheme.typography.titleMedium, color = TestarioText)
                        snapshot?.let { data ->
                            Text("Pregunta ${data.currentIndex + 1} de ${data.items.size} · ⏱ %02d:%02d".format(
                                data.elapsedSeconds / 60, data.elapsedSeconds % 60
                            ), color = TestarioPrimary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val current = snapshot
            if (current == null) {
                Text("No existe ninguna sesión mixta activa.", color = TestarioMutedText)
                OutlinedButton(onClick = onLeave) { Text("Volver") }
                return@Column
            }


            if (current.remainingSeconds != null) {
                Text(
                    "Tiempo restante: ${current.remainingSeconds!! / 60} min " +
                        "${current.remainingSeconds!! % 60} s",
                    color = if (current.remainingSeconds == 0) TestarioRed else TestarioPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            if (error != null) Text(error.orEmpty(), color = TestarioRed)

            if (result != null) {
                val finished = requireNotNull(result)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (reviewingResults) {
                    Text("Revisión del test", style = MaterialTheme.typography.titleLarge,
                        color = TestarioText)
                    Text("Pregunta ${reviewIndex + 1} de ${current.items.size}",
                        color = TestarioMutedText)
                    MixedSessionReviewCard(current, reviewIndex)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { reviewIndex-- }, enabled = reviewIndex > 0,
                            modifier = Modifier.weight(1f)) { Text("Anterior") }
                        Button(onClick = {
                            if (reviewIndex < current.items.lastIndex) reviewIndex++
                            else reviewingResults = false
                        }, modifier = Modifier.weight(1f)) {
                            Text(if (reviewIndex == current.items.lastIndex) "Resultados" else "Siguiente")
                        }
                    }
                    OutlinedButton(onClick = { reviewingResults = false },
                        modifier = Modifier.fillMaxWidth()) { Text("Volver a resultados") }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Resultados", color = TestarioText,
                                style = MaterialTheme.typography.titleLarge)
                            if (finished.scoreOutOfTen == null) {
                                Text("Sesión de tarjetas completada", color = TestarioPrimary,
                                    style = MaterialTheme.typography.headlineMedium)
                            } else {
                                Text("${"%.2f".format(finished.scoreOutOfTen)}/10",
                                    color = if (finished.scoreOutOfTen >= 5) TestarioGreen else TestarioRed,
                                    style = MaterialTheme.typography.displayLarge)
                                Text("${finished.objectiveItems} elementos puntuables",
                                    color = TestarioMutedText)
                            }
                        }
                    }
                    OutlinedButton(onClick = {
                        reviewIndex = 0
                        reviewingResults = true
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Revisar preguntas")
                    }
                    Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
                        Text("Volver al inicio de Test")
                    }
                }
                }
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
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                current.items.forEachIndexed { index, navigationItem ->
                    val chosen = index == current.currentIndex
                    val completed = current.responses[index].selectedAnswerId != null ||
                        current.responses[index].writtenAnswers.isNotEmpty() ||
                        current.responses[index].revealed
                    Surface(
                        modifier = Modifier.size(42.dp).clickable {
                            if (!locked && persistDraft()) session.goTo(index)
                        },
                        shape = CircleShape,
                        color = if (chosen) TestarioPrimary else if (completed)
                            TestarioSurfaceLight else TestarioSurface,
                        border = BorderStroke(1.dp, if (chosen) TestarioPrimary else TestarioSurfaceLight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}",
                                color = if (chosen) TestarioBackground else TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(18.dp).then(
                        if (item.type == MixedItemType.WRITTEN) Modifier
                        else Modifier.verticalScroll(rememberScrollState())
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp
                    )
                ) {
            if (item.type != MixedItemType.TEST) {
                Text(item.topicName.uppercase(), color = TestarioPrimary,
                    style = MaterialTheme.typography.labelLarge)
            }
            when (item.type) {
                MixedItemType.TEST -> {
                    val question = requireNotNull(item.question)
                    var difficultyExpanded by remember(item.id) { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(item.topicName.uppercase(), color = TestarioPrimary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { difficultyExpanded = true }) {
                                Text(when (question.difficulty) {
                                    1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?"
                                }, color = when (question.difficulty) {
                                    1 -> TestarioGreen; 2 -> TestarioYellow
                                    3 -> TestarioRed; else -> TestarioMutedText
                                })
                            }
                            DropdownMenu(expanded = difficultyExpanded,
                                onDismissRequest = { difficultyExpanded = false }) {
                                listOf(
                                    1 to "Fácil", 2 to "Medio", 3 to "Difícil", null to "Sin asignar"
                                ).forEach { (value, label) ->
                                    DropdownMenuItem(text = { Text(label) }, onClick = {
                                        session.updateTestDifficulty(value)
                                        difficultyExpanded = false
                                    })
                                }
                            }
                        }
                        IconButton(onClick = { onEditQuestion(item.topicId, question.id) }) {
                            Text("✏️")
                        }
                        IconButton(onClick = { session.toggleTestFlag() }) {
                            Text(if (question.flagged) "🚩" else "🏳️")
                        }
                    }
                    Text(question.text, color = TestarioText,
                        style = MaterialTheme.typography.titleLarge)
                    question.explanationImageUri?.let { path ->
                        StudyVisualImage(
                            imageUri = path, annotations = question.statementAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                    }
                    question.answers.forEachIndexed { index, answer ->
                        val chosen = response.selectedAnswerId == answer.id
                        val correct = answer.id == question.correctAnswerId
                        val background = when {
                            showAnswer && correct -> TestarioGreenDark
                            showAnswer && chosen -> TestarioRedDark
                            chosen -> TestarioPrimaryDark
                            else -> TestarioSurfaceLight
                        }
                        val borderColor = when {
                            showAnswer && correct -> TestarioGreen
                            showAnswer && chosen -> TestarioRed
                            chosen -> TestarioPrimary
                            else -> TestarioSurfaceLight
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(
                                enabled = !locked &&
                                    !(current.mode == TestMode.TRAINING && showAnswer)
                            ) { session.selectTestAnswer(answer.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = background),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = TestarioSurface,
                                    modifier = Modifier.size(28.dp)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(('A' + index).toString(), color = TestarioText)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(answer.text, color = TestarioText)
                            }
                        }
                    }
                    if (showAnswer) {
                        if (question.explanation.isNotBlank()) {
                            Text(question.explanation, color = TestarioText)
                        }
                        question.correctionImageUri?.let { path ->
                            StudyVisualImage(
                                imageUri = path, annotations = question.correctionAnnotations,
                                modifier = Modifier.fillMaxWidth().height(220.dp),
                                showCorrection = true
                            )
                        }
                    }
                }
                MixedItemType.WRITTEN -> {
                    val exercise = requireNotNull(item.exercise)
                    Text(exercise.prompt, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                    exercise.baseImageUri?.let { path ->
                        val statuses = if (showAnswer) {
                            val grade = StudyExerciseRules.gradeWrittenInSession(
                                exercise, response.writtenAnswers, current.pointsCorrect, current.penaltyWrong)
                            exercise.solutions.flatMap { solution ->
                                solution.points.map { point -> point.id to (solution.id in grade.correctIds) }
                            }.toMap()
                        } else emptyMap()
                        StudyVisualImage(imageUri = path, modifier = Modifier.fillMaxWidth().height(230.dp),
                            annotations = exercise.baseAnnotations,
                            markers = exercise.solutions.flatMap { it.points },
                            markerStatuses = statuses, showCorrection = showAnswer,
                            showMarkerNumbers = true)
                    }
                    exercise.sketch?.let { drawing ->
                        StudySketchCanvas(
                            sketch = drawing, modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = if (showAnswer) exercise.solutions.flatMap { solution ->
                                solution.points.map { point ->
                                    point.id to (solution.id in StudyExerciseRules.gradeWrittenInSession(
                                        exercise, response.writtenAnswers,
                                        current.pointsCorrect, current.penaltyWrong
                                    ).correctIds)
                                }
                            }.toMap() else emptyMap()
                        )
                    }
                    // The photograph/sketch stays visible; only the answer fields scroll.
                    // This also prevents very long enumerations from pushing the image away.
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!showAnswer) {
                            Text(if (exercise.solutions.any { it.points.isNotEmpty() })
                                "Responde las soluciones marcadas del 1 al ${exercise.solutions.size}."
                                else "Escribe las ${exercise.solutions.size} soluciones.",
                                color = TestarioMutedText)
                            repeat(exercise.solutions.size) { index ->
                                val inputParts = writtenInput.split("\n")
                                OutlinedTextField(
                                    value = inputParts.getOrElse(index) { "" },
                                    onValueChange = { newValue ->
                                        val parts = writtenInput.split("\n").toMutableList()
                                        while (parts.size <= index) parts.add("")
                                        parts[index] = newValue.replace("\n", " ")
                                        writtenInput = parts.joinToString("\n")
                                    },
                                    label = { Text("Respuesta ${index + 1}") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !locked
                                )
                            }
                            Button(onClick = { session.submitWritten(writtenInput) },
                                enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                                Text(if (current.mode == TestMode.EXAM)
                                    "Guardar respuestas" else "Comprobar")
                            }
                        } else {
                            val graded = StudyExerciseRules.gradeWrittenInSession(
                                exercise, response.writtenAnswers,
                                current.pointsCorrect, current.penaltyWrong
                            )
                            Text("Nota: ${"%.2f".format(graded.scoreOutOfTen)} / 10",
                                color = if (graded.scoreOutOfTen >= 5)
                                    TestarioGreen else TestarioRed)
                            WrittenCorrectionRows(exercise, response.writtenAnswers, graded)
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
                                OcclusionRevealRules.covered(
                                    card.masks, item.variant, showAnswer
                                ) else emptyList(),
                            showCorrection = showAnswer &&
                                card.kind != FlashcardKind.IMAGE_OCCLUSION)
                    }
                    if (!showAnswer) {
                        Button(onClick = { session.revealAnki() },
                            enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text("Mostrar respuesta")
                        }
                    } else {
                        // The image itself is the answer for image-occlusion cards.
                        // A blank explanation must not repeat the question below it.
                        val revealedText = if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                            card.back else solutionText
                        if (revealedText.isNotBlank()) {
                            Text(if (card.kind == FlashcardKind.IMAGE_OCCLUSION)
                                "Explicación" else "Respuesta", color = TestarioGreen)
                            Text(revealedText, color = TestarioText)
                        }
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
            title = { Text("Salir de la sesión") },
            text = { Text("Puedes guardar el progreso para continuar o descartarlo sin conservar una sesión pendiente.") },
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
                    TextButton(onClick = {
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
}
