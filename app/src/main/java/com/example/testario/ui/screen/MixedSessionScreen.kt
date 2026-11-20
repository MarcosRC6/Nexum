package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.testario.viewmodel.LibraryViewModel
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
    onLeave: () -> Unit
) {
    val snapshot by session.snapshot.collectAsState()
    val libraryDatabase by libraryViewModel.database.collectAsState()
    val result by session.result.collectAsState()
    val error by session.error.collectAsState()
    val scope = rememberCoroutineScope()
    var exitConfirm by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
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
                "Elemento ${current.currentIndex + 1} de ${current.items.size} · " +
                    if (current.mode == TestMode.EXAM) "Examen" else "Entrenamiento",
                color = TestarioMutedText
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
                Text("Sesión finalizada", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                if (finished.scoreOutOfTen == null) {
                    Text("Sin nota objetiva · ${finished.manualReviewItems} tarjetas de repaso manual.",
                        color = TestarioPrimary)
                } else {
                    Text("Nota objetiva: ${"%.2f".format(finished.scoreOutOfTen)} / 10",
                        color = TestarioGreen, style = MaterialTheme.typography.titleLarge)
                    Text("${finished.objectiveItems} elementos puntuables · " +
                        "${finished.manualReviewItems} tarjetas excluidas de la nota.",
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
            if (item.type != MixedItemType.TEST)
                Text(item.topicName, color = TestarioMutedText)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                current.items.forEachIndexed { idx, candidate ->
                    val isCurrent = idx == current.currentIndex
                    val marked = candidate.question?.let { question ->
                        libraryDatabase.topics.asSequence().flatMap { it.questions.asSequence() }
                            .firstOrNull { it.id == question.id }?.flagged ?: question.flagged
                    } ?: false
                    val answered = current.responses.getOrNull(idx)?.let { response ->
                        response.selectedAnswerId != null || response.writtenAnswers.isNotEmpty() ||
                            response.revealed
                    } == true
                    Surface(
                        modifier = Modifier.size(42.dp).clickable(enabled = !locked) {
                            if (persistDraft()) session.goTo(idx)
                        },
                        color = if (isCurrent) TestarioPrimary else
                            if (answered) TestarioSurfaceLight else TestarioSurface,
                        shape = CircleShape,
                        border = BorderStroke(if (marked) 2.dp else 1.dp,
                            if (marked) TestarioRed else
                                if (isCurrent) TestarioPrimary else TestarioSurfaceLight)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${idx + 1}",
                                color = if (isCurrent) TestarioBackground else TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            when (item.type) {
                MixedItemType.TEST -> {
                    val question = requireNotNull(item.question)
                    val currentQuestion = libraryDatabase.topics.asSequence()
                        .flatMap { it.questions.asSequence() }
                        .firstOrNull { it.id == question.id }
                    val marked = currentQuestion?.flagged ?: question.flagged
                    val difficulty = currentQuestion?.difficulty ?: question.difficulty
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.topicName.uppercase(), color = TestarioPrimary,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium)
                                var showDifficultyMenu by remember(question.id) { mutableStateOf(false) }
                                Box {
                                    TextButton(onClick = { showDifficultyMenu = true }) {
                                        Text(when (difficulty) {
                                            1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"
                                            else -> "¿Dificultad?"
                                        }, color = when (difficulty) {
                                            1 -> TestarioGreen; 2 -> TestarioYellow; 3 -> TestarioRed
                                            else -> TestarioMutedText
                                        })
                                    }
                                    DropdownMenu(expanded = showDifficultyMenu,
                                        onDismissRequest = { showDifficultyMenu = false }) {
                                        listOf(
                                            1 to "Fácil", 2 to "Medio", 3 to "Difícil",
                                            null to "Sin asignar"
                                        ).forEach { (level, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                libraryViewModel.updateQuestionDifficulty(
                                                    item.topicId, question.id, level)
                                                showDifficultyMenu = false
                                            })
                                        }
                                    }
                                }
                                IconButton(onClick = { libraryViewModel.toggleQuestionFlag(question.id) }) {
                                    Text(if (marked) "🚩" else "🏳️")
                                }
                            }
                            Text(question.text, color = TestarioText,
                                style = MaterialTheme.typography.titleLarge)
                            question.explanationImageUri?.let { path ->
                                StudyVisualImage(imageUri = path,
                                    annotations = question.statementAnnotations,
                                    modifier = Modifier.fillMaxWidth().height(220.dp))
                            }
                            question.answers.forEachIndexed { idx, answer ->
                                val selected = response.selectedAnswerId == answer.id
                                val correct = answer.id == question.correctAnswerId
                                val background = when {
                                    showAnswer && correct -> TestarioGreenDark
                                    showAnswer && selected -> TestarioRedDark
                                    selected -> TestarioPrimaryDark
                                    else -> TestarioSurfaceLight
                                }
                                val border = when {
                                    showAnswer && correct -> TestarioGreen
                                    showAnswer && selected -> TestarioRed
                                    selected -> TestarioPrimary
                                    else -> TestarioSurfaceLight
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable(enabled = !locked &&
                                            !(current.mode == TestMode.TRAINING && showAnswer)) {
                                            session.selectTestAnswer(answer.id)
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = background),
                                    border = BorderStroke(1.dp, border)
                                ) {
                                    Row(Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Surface(shape = CircleShape, color = TestarioSurface,
                                            modifier = Modifier.size(28.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(('A' + idx).toString(), color = TestarioText,
                                                    style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(answer.text, color = TestarioText,
                                            modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            if (showAnswer) {
                                Text("Correcta: " + question.answers.first {
                                    it.id == question.correctAnswerId
                                }.text, color = TestarioGreen)
                                question.explanation.takeIf(String::isNotBlank)?.let {
                                    Text(it, color = TestarioText)
                                }
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
                            showMarkerNumbersBeforeReveal = true)
                    }
                    exercise.sketch?.let { drawing ->
                        StudySketchCanvas(
                            sketch = drawing, modifier = Modifier.fillMaxWidth().height(220.dp),
                            markers = exercise.solutions.flatMap { it.points },
                            mode = SketchMode.PREVIEW, showMarkers = true,
                            markerStatuses = statuses
                        )
                    }
                    Column(
                        Modifier.fillMaxWidth()
                            .background(TestarioSurface, RoundedCornerShape(18.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Soluciones", style = MaterialTheme.typography.titleMedium,
                            color = TestarioText)
                        OutlinedTextField(
                            value = writtenInput,
                            onValueChange = { writtenInput = it },
                            label = { Text("Una respuesta por línea") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = maxOf(3, exercise.solutions.size),
                            enabled = !locked && !showAnswer
                        )
                        Text("${exercise.solutions.size} soluciones esperadas. Los números de la imagen señalan qué debes identificar.",
                            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
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
                            color = if (graded.incorrect > 0 || graded.omitted > 0) TestarioRed else TestarioGreen)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exitConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text("Guardar y salir")
                }
                TextButton(onClick = { discardConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text("Salir sin guardar", color = TestarioRed)
                }
            }
        }
    }

    if (discardConfirm) AlertDialog(
        onDismissRequest = { discardConfirm = false },
        title = { Text("Salir sin guardar") },
        text = { Text("Se perderá el progreso, tiempo y respuestas de esta sesión. No podrá retomarse ni registrarse en los resultados.") },
        confirmButton = {
            TextButton(onClick = {
                if (session.discardSaved()) {
                    discardConfirm = false
                    onLeave()
                }
            }) { Text("Descartar sesión", color = TestarioRed) }
        },
        dismissButton = { TextButton(onClick = { discardConfirm = false }) { Text("Continuar") } }
    )

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
            dismissButton = { OutlinedButton(onClick = { exitConfirm = false }) { Text("Continuar") } }
        )
    }
}
