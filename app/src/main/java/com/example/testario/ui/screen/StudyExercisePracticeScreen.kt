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
    topicId: String, libraryViewModel: LibraryViewModel, onBack: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.find { it.id == topicId }
    val written = topic?.exercises.orEmpty().filter {
        it.kind == StudyExerciseKind.WRITTEN_MULTI && !it.draft
    }
    // One note may generate several independently scheduled cards.
    val cards = topic?.exercises.orEmpty().filter {
        it.kind == StudyExerciseKind.ANKI_CARD && !it.draft
    }.flatMap { ex ->
        val card = ex.flashcard
        when (card?.kind) {
            FlashcardKind.REVERSED -> listOf(ex to "direct", ex to "reverse")
            FlashcardKind.CLOZE -> card.gaps.map { ex to it.id }
            FlashcardKind.IMAGE_OCCLUSION -> card.masks.map { ex to it.id }
            else -> listOf(ex to "direct")
        }
    }
    var tab by rememberSaveable { mutableStateOf(0) }
    var index by rememberSaveable(tab) { mutableIntStateOf(0) }
    var entry by rememberSaveable(tab, index) { mutableStateOf("") }
    var answers by rememberSaveable(tab, index) { mutableStateOf(arrayListOf<String>()) }
    var completed by rememberSaveable(tab, index) { mutableStateOf(false) }
    var revealed by rememberSaveable(tab, index) { mutableStateOf(false) }
    var points by rememberSaveable { mutableStateOf("1") }
    var penalty by rememberSaveable { mutableStateOf("-0.25") }
    var error by remember { mutableStateOf<String?>(null) }
    val attemptId = rememberSaveable(tab, index) { UUID.randomUUID().toString() }
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
                    label = { Text("Escritos (${written.size})") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 },
                    label = { Text("Anki (${cards.size})") })
            }
            if (tab == 0) {
                if (written.isEmpty()) {
                    Text("Este tema no tiene ejercicios escritos publicados.", color = TestarioMutedText)
                    return@Column
                }
                val exercise = written[index.coerceIn(0, written.lastIndex)]
                Text("Ejercicio ${index + 1} de ${written.size}", color = TestarioMutedText)
                Text(exercise.prompt, color = TestarioText, style = MaterialTheme.typography.titleLarge)
                exercise.diagram?.let { diagram ->
                    Box(Modifier.fillMaxWidth().height(140.dp)
                        .background(TestarioSurface, RoundedCornerShape(12.dp))) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(schematicLineColor,
                                start = Offset(size.width * diagram.startX.toFloat(),
                                    size.height * diagram.startY.toFloat()),
                                end = Offset(size.width * diagram.endX.toFloat(),
                                    size.height * diagram.endY.toFloat()),
                                strokeWidth = 8f
                            )
                        }
                    }
                }
                exercise.baseImageUri?.let { path ->
                    AsyncImage(model = imageModel(path), contentDescription = "Imagen del enunciado",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
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
                answers.forEach { value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(value, color = TestarioText, modifier = Modifier.weight(1f))
                        if (!completed) TextButton(onClick = {
                            answers = ArrayList(answers.filterNot { it == value })
                        }) { Text("Quitar") }
                    }
                }
                if (!completed) {
                    OutlinedTextField(value = points, onValueChange = { points = it },
                        label = { Text("Puntos por acierto") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = penalty, onValueChange = { penalty = it },
                        label = { Text("Penalización por fallo (negativa o cero)") },
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val a = points.replace(",", ".").toDoubleOrNull()
                        val b = penalty.replace(",", ".").toDoubleOrNull()
                        if (a == null || !a.isFinite() || a <= 0 ||
                            b == null || !b.isFinite() || b > 0) {
                            error = "Baremo inválido. Acierto positivo y penalización negativa o cero."
                        } else {
                            runCatching {
                                libraryViewModel.recordWrittenAttempt(
                                    topicId, exercise, answers, a, b, attemptId
                                )
                            }.onSuccess { completed = true; error = null }
                                .onFailure { error = it.message }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Comprobar") }
                } else {
                    val a = points.replace(",", ".").toDoubleOrNull() ?: 1.0
                    val b = penalty.replace(",", ".").toDoubleOrNull() ?: -0.25
                    val result = StudyExerciseRules.gradeWritten(exercise, answers, a, b)
                    Text("Nota: ${"%.2f".format(result.scoreOutOfTen)} / 10",
                        color = TestarioGreen, style = MaterialTheme.typography.titleLarge)
                    Text("Aciertos: ${result.correct} · Errores: ${result.incorrect} · Omitidas: ${result.omitted}",
                        color = TestarioText)
                    Text("Soluciones correctas", color = TestarioPrimary)
                    exercise.solutions.forEach { solution ->
                        Text("• ${solution.text}", color = if (solution.id in result.correctIds)
                            TestarioGreen else TestarioText)
                    }
                }
                OutlinedButton(
                    onClick = { index = (index + 1) % written.size; error = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Siguiente ejercicio") }
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
                val gap = card.gaps.find { it.id == variant }
                val maskedQuestion = if (gap != null &&
                    gap.start >= 0 && gap.end <= question.length && gap.start < gap.end)
                    question.replaceRange(gap.start, gap.end, "[________]")
                    else question
                Text("Tarjeta ${index + 1} de ${cards.size}", color = TestarioMutedText)
                Text(if (revealed) question else maskedQuestion,
                    color = TestarioText, style = MaterialTheme.typography.titleLarge)
                val promptImage = if (isReverse) card.backImageUri else card.frontImageUri
                // Image occlusion cannot reveal a hidden answer until masks are rendered safely.
                if (card.kind == FlashcardKind.IMAGE_OCCLUSION) {
                    Text("La ocultación de imagen requiere el visor de máscaras. No se muestran soluciones sin él.",
                        color = TestarioRed)
                    return@Column
                }
                promptImage?.let { path ->
                    AsyncImage(model = imageModel(path), contentDescription = "Imagen de la pregunta",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                }
                if (card.kind == FlashcardKind.TYPE_ANSWER) {
                    OutlinedTextField(
                        value = entry, onValueChange = { entry = it },
                        label = { Text("Tu respuesta") }, enabled = !revealed,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!revealed) {
                    Button(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Mostrar respuesta")
                    }
                } else {
                    Divider(color = TestarioSurfaceLight)
                    Text("Respuesta", color = TestarioGreen)
                    val gapAnswer = if (gap != null) question.substring(gap.start, gap.end) else solution
                    Text(gapAnswer, color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    if (gap != null && solution.isNotBlank())
                        Text(solution, color = TestarioMutedText)
                    val answerImage = if (isReverse) card.frontImageUri else card.backImageUri
                    answerImage?.let { path ->
                        AsyncImage(model = imageModel(path), contentDescription = "Imagen de la respuesta",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp), contentScale = ContentScale.Fit)
                    }
                    if (card.kind == FlashcardKind.TYPE_ANSWER) {
                        val accepted = (listOf(solution) + card.acceptedAnswers).map(StudyExerciseRules::normalize)
                        Text(if (StudyExerciseRules.normalize(entry) in accepted) "Coincide con la solución"
                            else "No coincide literalmente; revisa la solución.",
                            color = TestarioMutedText)
                    }
                    if (database.ankiReviewEnabled) {
                        Text("Repaso programado", color = TestarioMutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                ReviewRating.AGAIN to "Otra vez",
                                ReviewRating.HARD to "Difícil",
                                ReviewRating.GOOD to "Bien",
                                ReviewRating.EASY to "Fácil"
                            ).forEach { (rating, label) ->
                                OutlinedButton(onClick = {
                                    val cardId = "${exercise.id}:$variant"
                                    runCatching { libraryViewModel.recordAnkiReview(cardId, rating, attemptId) }
                                        .onFailure { error = it.message }
                                        .onSuccess { if (it) index = (index + 1) % cards.size }
                                }, modifier = Modifier.weight(1f)) { Text(label) }
                            }
                        }
                    }
                    OutlinedButton(onClick = { index = (index + 1) % cards.size },
                        modifier = Modifier.fillMaxWidth()) { Text("Siguiente tarjeta") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = database.ankiReviewEnabled, onCheckedChange = {
                        libraryViewModel.setAnkiReviewEnabled(it)
                    })
                    Text("Usar repetición espaciada", color = TestarioText)
                }
            }
            if (error != null) Text(error.orEmpty(), color = TestarioRed)
            Spacer(Modifier.height(20.dp))
        }
    }
}
