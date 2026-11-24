package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.TestMode
import com.example.testario.data.model.TestQuestionState
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioGreenDark
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioNavBackground
import com.example.testario.ui.theme.TestarioNeonSelected
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioRedDark
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.ui.theme.TestarioYellow
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import com.example.testario.viewmodel.TestSessionViewModel
import java.io.File

@Composable
fun TestScreen(
    libraryViewModel: LibraryViewModel,
    sessionViewModel: TestSessionViewModel,
    statsViewModel: StatsViewModel,
    onFinish: () -> Unit,
    onEditQuestion: (String, String) -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val questions by sessionViewModel.questions.collectAsState()
    val currentIndex by sessionViewModel.currentIndex.collectAsState()
    val mode by sessionViewModel.mode.collectAsState()
    val remainingSeconds by sessionViewModel.remainingSeconds.collectAsState()
    val timeExpired by sessionViewModel.timeExpired.collectAsState()
    val isPreparing by sessionViewModel.isPreparing.collectAsState()

    val totalElapsedSeconds by sessionViewModel.totalElapsedSeconds.collectAsState()

    var finished by rememberSaveable { mutableStateOf(false) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    var showReplaceSavedDialog by rememberSaveable { mutableStateOf(false) }
    var reviewingResults by rememberSaveable { mutableStateOf(false) }
    var reviewIndex by rememberSaveable { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val context = LocalContext.current
    // En tablet las preguntas se navegan desde un panel lateral y no desde la fila superior.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840

    BackHandler(enabled = !finished && !timeExpired) {
        showExitDialog = true
    }
    BackHandler(enabled = (finished || timeExpired) && reviewingResults) {
        reviewingResults = false
    }

    LaunchedEffect(database) {
        sessionViewModel.refreshQuestionsFromDatabase(database.topics)
    }

    LaunchedEffect(currentIndex) {
        if (questions.isNotEmpty() && !wide) {
            listState.animateScrollToItem(maxOf(0, currentIndex - 2))
        }
    }

    if (isPreparing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(color = TestarioPrimary)
                Text("Preparando test…", color = TestarioMutedText)
            }
        }
        return
    }

    if (questions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No hay preguntas.", color = TestarioText)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        sessionViewModel.cancelSession()
                        onFinish()
                    }
                ) { Text("Volver") }
            }
        }
        return
    }

    val current = questions[currentIndex]
    val isTraining = mode == TestMode.TRAINING || mode == TestMode.FAILURES

    val topicId = remember(current.question.id, database.topics) {
        database.topics.firstOrNull { t -> t.questions.any { it.id == current.question.id } }?.id
    }
    val currentTopicName = database.topics.firstOrNull { it.id == topicId }?.name ?: "Tema desconocido"
    val showOrigin = LocalShowQuestionOrigin.current
    val currentTopicDisplayName = topicDisplayName(currentTopicName, current.question.originTopicName.takeIf { showOrigin })

    fun saveAndLeave() {
        if (sessionViewModel.saveForLater()) {
            showExitDialog = false
            showReplaceSavedDialog = false
            onFinish()
        } else {
            Toast.makeText(
                context, "No se pudo guardar el test. Inténtalo de nuevo.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    if (showReplaceSavedDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceSavedDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Reemplazar test guardado") },
            text = {
                Text(
                    "Hay otro test suspendido. Solo se conserva uno a la vez. " +
                        "¿Quieres reemplazarlo por el test actual?"
                )
            },
            confirmButton = { Button(onClick = { saveAndLeave() }) { Text("Reemplazar y guardar") } },
            dismissButton = {
                OutlinedButton(onClick = { showReplaceSavedDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = TestarioText,
            textContentColor = TestarioMutedText,
            title = { Text("Abandonar test") },
            text = { Text("¿Qué deseas hacer con el progreso actual?") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val previous = sessionViewModel.pendingSavedSession.value
                            if (previous != null && previous.sessionId != sessionViewModel.sessionId()) {
                                showExitDialog = false
                                showReplaceSavedDialog = true
                            } else {
                                saveAndLeave()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Guardar para continuar") }
                    OutlinedButton(
                        onClick = {
                            showExitDialog = false
                            val res = sessionViewModel.finish()
                            if (sessionViewModel.claimFinalStatsRegistration()) {
                                val elapsedMins = maxOf(1, (totalElapsedSeconds / 60L).toInt())
                                statsViewModel.registerTestResults(
                                    results = questions,
                                    finalTestResult = res,
                                    totalTimeSpentMinutes = elapsedMins,
                                    mode = mode,
                                    elapsedSeconds = totalElapsedSeconds,
                                    sessionId = sessionViewModel.sessionId()
                                )
                            }
                            onFinish()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Finalizar y registrar resultados") }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { showExitDialog = false }) {
                            Text("Cancelar", color = TestarioMutedText)
                        }
                        TextButton(onClick = {
                            showExitDialog = false
                            sessionViewModel.cancelSession()
                            onFinish()
                        }) { Text("Salir sin guardar", color = TestarioRed) }
                    }
                }
            }
        )
    }

    if (finished || timeExpired) {
        val res = remember { sessionViewModel.finish() }
        LaunchedEffect(res, totalElapsedSeconds) {
            if (sessionViewModel.claimFinalStatsRegistration()) {
                val elapsedMins = maxOf(1, (totalElapsedSeconds / 60L).toInt())
                statsViewModel.registerTestResults(
                    results = questions,
                    finalTestResult = res,
                    totalTimeSpentMinutes = elapsedMins,
                    mode = mode,
                    elapsedSeconds = totalElapsedSeconds,
                    sessionId = sessionViewModel.sessionId()
                )
            }
        }

        if (reviewingResults) {
            val reviewState = questions[reviewIndex.coerceIn(0, questions.lastIndex)]
            val reviewTopic = database.topics.firstOrNull { topic ->
                topic.questions.any { it.id == reviewState.question.id }
            }
            val reviewTopicName = reviewTopic?.name ?: "Tema desconocido"
            val reviewDisplayName = topicDisplayName(reviewTopicName, reviewState.question.originTopicName.takeIf { showOrigin })

            Scaffold(
                containerColor = TestarioBackground,
                topBar = { TestarioTopBar("Revisión del test", onBack = { reviewingResults = false }) }
            ) { p ->
                Column(
                    modifier = Modifier.padding(p).padding(16.dp).fillMaxSize()
                ) {
                    Text(
                        "Pregunta ${reviewIndex + 1} de ${questions.size}",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                reviewDisplayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = TestarioMutedText
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                reviewState.question.text,
                                style = MaterialTheme.typography.titleLarge,
                                color = TestarioText
                            )

                            reviewState.question.explanationImageUri?.let { path ->
                                Spacer(Modifier.height(14.dp))
                                TestQuestionImage(path = path, contentDescription = "Imagen del enunciado", annotations = reviewState.question.statementAnnotations)
                            }

                            Spacer(Modifier.height(16.dp))
                            reviewState.question.answers.forEachIndexed { i, answer ->
                                val isCorrectAnswer = answer.id == reviewState.question.correctAnswerId
                                val isSelectedAnswer = answer.id == reviewState.selectedAnswerId
                                val bg = when {
                                    isCorrectAnswer -> TestarioGreenDark
                                    isSelectedAnswer -> TestarioRedDark
                                    else -> TestarioSurfaceLight
                                }
                                val border = when {
                                    isCorrectAnswer -> TestarioGreen
                                    isSelectedAnswer -> TestarioRed
                                    else -> TestarioSurfaceLight
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = bg),
                                    border = null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(shape = CircleShape, color = TestarioSurface, modifier = Modifier.size(28.dp)) {
                                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                Text(('A' + i).toString(), style = MaterialTheme.typography.bodySmall, color = TestarioText)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(answer.text, color = TestarioText, modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            if (reviewState.selectedAnswerId == null) {
                                Spacer(Modifier.height(8.dp))
                                Text("Tu respuesta: en blanco", color = TestarioMutedText)
                            }

                            if (reviewState.question.explanation.isNotBlank() || reviewState.question.correctionImageUri != null) {
                                Spacer(Modifier.height(16.dp))
                                ExplanationCard(
                                    explanation = reviewState.question.explanation,
                                    correctionImageUri = reviewState.question.correctionImageUri,
                                    annotations = reviewState.question.correctionAnnotations
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { if (reviewIndex > 0) reviewIndex-- },
                            enabled = reviewIndex > 0,
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Anterior") }

                        Button(
                            onClick = {
                                if (reviewIndex < questions.lastIndex) reviewIndex++
                                else reviewingResults = false
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (reviewIndex < questions.lastIndex) "Siguiente" else "Resultados")
                        }
                    }
                }
            }
            return
        }

        Scaffold(containerColor = TestarioBackground, topBar = { TestarioTopBar("Resultados", onFinish) }) { p ->
            Column(
                modifier = Modifier.padding(p).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${res.score}/10", style = MaterialTheme.typography.displayLarge, color = if(res.score >= 5.0) TestarioGreen else TestarioRed)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aciertos: ${res.percent}% · Correctas: ${res.correct} · Fallos: ${res.wrong}", color = TestarioMutedText)
                    }
                }
                OutlinedButton(
                    onClick = {
                        reviewIndex = 0
                        reviewingResults = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("Revisar preguntas") }
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Volver al Inicio") }
            }
        }
        return
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            Surface(color = TestarioBackground) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showExitDialog = true },
                        modifier = Modifier.semantics { contentDescription = "Salir del test" }
                    ) { EstudarioIcon(EstudarioIconKind.CLOSE, TestarioText, Modifier.size(22.dp)) }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(if(mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento", style = MaterialTheme.typography.titleMedium, color = TestarioText)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pregunta ${currentIndex + 1} de ${questions.size}", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
                            Text(" · ", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
                            val formatMins = totalElapsedSeconds / 60
                            val formatSecs = totalElapsedSeconds % 60
                            EstudarioIcon(EstudarioIconKind.CLOCK, TestarioPrimary, Modifier.size(14.dp))
                            Text(
                                " %02d:%02d".format(formatMins, formatSecs),
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = TestarioPrimary
                            )
                        }
                    }
                    remainingSeconds?.let { Text("%02d:%02d".format(it / 60, it % 60), color = if(it < 60) TestarioRed else TestarioText, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum")) }
                }
            }
        }
    ) { padding ->
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (!wide) LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(questions.size) { index ->
                    val qState = questions[index]
                    val isCurrent = index == currentIndex
                    val isFlagged = qState.question.flagged
                    val isAnswered = qState.selectedAnswerId != null

                    QuestionChip(
                        number = index + 1,
                        isCurrent = isCurrent,
                        isAnswered = isAnswered,
                        isFlagged = isFlagged,
                        modifier = Modifier.size(48.dp),
                        onClick = { sessionViewModel.goTo(index) }
                    )
                }
            }

            Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentTopicDisplayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = TestarioMutedText,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var difficultyMenuExpanded by remember { mutableStateOf(false) }
                            val diffText = when(current.question.difficulty) { 1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?" }
                            val diffColor = when(current.question.difficulty) { 1 -> TestarioGreen; 2 -> TestarioYellow; 3 -> TestarioRed; else -> TestarioMutedText }

                            Box {
                                androidx.compose.material3.TextButton(
                                    onClick = { difficultyMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(diffText, color = diffColor, style = MaterialTheme.typography.labelLarge)
                                }
                                DropdownMenu(
                                    expanded = difficultyMenuExpanded,
                                    onDismissRequest = { difficultyMenuExpanded = false }
                                ) {
                                    val updateDiff = { diff: Int? ->
                                        sessionViewModel.updateQuestionDifficulty(current.question.id, diff)
                                        if (topicId != null) {
                                            libraryViewModel.updateQuestionDifficulty(topicId, current.question.id, diff)
                                        }
                                        difficultyMenuExpanded = false
                                    }
                                    DropdownMenuItem(text = { Text("Fácil", color = TestarioGreen) }, onClick = { updateDiff(1) })
                                    DropdownMenuItem(text = { Text("Medio", color = TestarioYellow) }, onClick = { updateDiff(2) })
                                    DropdownMenuItem(text = { Text("Difícil", color = TestarioRed) }, onClick = { updateDiff(3) })
                                    DropdownMenuItem(text = { Text("Sin asignar", color = TestarioMutedText) }, onClick = { updateDiff(null) })
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (topicId != null) {
                                        onEditQuestion(topicId, current.question.id)
                                    }
                                },
                                modifier = Modifier.semantics { contentDescription = "Editar pregunta" }
                            ) { EstudarioIcon(EstudarioIconKind.EDIT, TestarioMutedText, Modifier.size(22.dp)) }

                            IconButton(
                                onClick = {
                                    sessionViewModel.toggleFlag(current.question.id)
                                    libraryViewModel.toggleQuestionFlag(current.question.id)
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = if (current.question.flagged) "Quitar marca" else "Marcar pregunta"
                                }
                            ) {
                                EstudarioIcon(
                                    EstudarioIconKind.FLAG,
                                    if (current.question.flagged) TestarioRed else TestarioMutedText,
                                    Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = current.question.text,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal, lineHeight = 28.sp),
                        color = TestarioText,
                        modifier = Modifier.fillMaxWidth()
                    )

                    current.question.explanationImageUri?.let { path ->
                        Spacer(Modifier.height(14.dp))
                        TestQuestionImage(path = path, contentDescription = "Imagen del enunciado", annotations = current.question.statementAnnotations)
                    }

                    Spacer(Modifier.height(16.dp))
                    current.question.answers.forEachIndexed { i, ans ->
                        val active = current.selectedAnswerId == ans.id
                        val correct = current.question.correctAnswerId == ans.id
                        val col = when { current.corrected && correct -> TestarioGreenDark; current.corrected && active -> TestarioRedDark; active -> TestarioNeonSelected; else -> TestarioSurface }
                        val badge = when { current.corrected && correct -> TestarioGreen; current.corrected && active -> TestarioRed; active -> TestarioPrimary; else -> TestarioSurfaceLight }
                        val badgeText = if (badge == TestarioSurfaceLight) TestarioMutedText else TestarioBackground

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(18.dp))
                                // Se desactiva el clic si la pregunta ya está corregida
                                .selectable(
                                    selected = active,
                                    enabled = !current.corrected,
                                    role = Role.RadioButton,
                                    onClick = { sessionViewModel.selectAnswer(ans.id) }
                                ),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = col)
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = badge, modifier = Modifier.size(28.dp)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        when {
                                            current.corrected && correct ->
                                                EstudarioIcon(EstudarioIconKind.CHECK, badgeText, Modifier.size(16.dp))
                                            current.corrected && active ->
                                                EstudarioIcon(EstudarioIconKind.CLOSE, badgeText, Modifier.size(16.dp))
                                            else ->
                                                Text(('A'+i).toString(), style = MaterialTheme.typography.labelLarge, color = badgeText)
                                        }
                                    }
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(ans.text, color = TestarioText, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    if (
                        current.corrected &&
                        (current.question.explanation.isNotBlank() || current.question.correctionImageUri != null)
                    ) {
                        Spacer(Modifier.height(16.dp))
                        ExplanationCard(
                            explanation = current.question.explanation,
                            correctionImageUri = current.question.correctionImageUri,
                            annotations = current.question.correctionAnnotations
                        )
                    }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                NexumTonalButton("Atrás", onClick = { sessionViewModel.previous() }, enabled = currentIndex > 0, modifier = Modifier.weight(1f))

                if (!isTraining) {
                    NexumTonalButton(
                        "Limpiar",
                        onClick = { sessionViewModel.leaveBlank() },
                        enabled = current.selectedAnswerId != null,
                        modifier = Modifier.weight(1f)
                    )
                }

                NexumTonalButton(
                    if (sessionViewModel.isLastQuestion()) "Finalizar" else "Siguiente",
                    primary = true,
                    onClick = { if (sessionViewModel.isLastQuestion()) finished = true else sessionViewModel.next() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (wide) {
            QuestionNavigatorPanel(
                questions = questions,
                currentIndex = currentIndex,
                onGoTo = { sessionViewModel.goTo(it) }
            )
        }
        }
    }
}

/** Número de pregunta: fondo tonal, sin borde; el actual en latón y la marcada con un punto. */
@Composable
private fun QuestionChip(
    number: Int,
    isCurrent: Boolean,
    isAnswered: Boolean,
    isFlagged: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when { isCurrent -> TestarioNeonSelected; isAnswered -> TestarioSurfaceLight; else -> TestarioSurface }
    val fg = when { isCurrent -> TestarioPrimary; isAnswered -> TestarioText; else -> TestarioMutedText }
    Surface(
        modifier = modifier
            .semantics(mergeDescendants = true) {
                contentDescription = "Pregunta $number, " +
                    (if (isAnswered) "respondida" else "sin responder") +
                    (if (isFlagged) ", marcada" else "")
                selected = isCurrent
            }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bg
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                number.toString(),
                color = fg,
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
            )
            if (isFlagged) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(9.dp)
                        .background(TestarioYellow, CircleShape)
                )
            }
        }
    }
}

/** Panel de tablet: todas las preguntas a la vista, sin revelar aciertos en pleno test. */
@Composable
private fun QuestionNavigatorPanel(
    questions: List<TestQuestionState>,
    currentIndex: Int,
    onGoTo: (Int) -> Unit
) {
    val answered = questions.count { it.selectedAnswerId != null }
    val flagged = questions.count { it.question.flagged }
    Column(
        modifier = Modifier
            .width(340.dp)
            .fillMaxHeight()
            .background(TestarioNavBackground)
            .padding(horizontal = 28.dp, vertical = 28.dp)
    ) {
        Text("Preguntas", style = MaterialTheme.typography.titleMedium, color = TestarioText)
        Spacer(Modifier.height(14.dp))
        val gridState = rememberLazyGridState()
        LaunchedEffect(currentIndex) { gridState.animateScrollToItem(currentIndex) }
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            state = gridState,
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(questions.size) { index ->
                QuestionChip(
                    number = index + 1,
                    isCurrent = index == currentIndex,
                    isAnswered = questions[index].selectedAnswerId != null,
                    isFlagged = questions[index].question.flagged,
                    modifier = Modifier.height(48.dp),
                    onClick = { onGoTo(index) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column {
                Text("$answered", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TestarioText)
                Text("respondidas", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
            }
            Column {
                Text("${questions.size - answered}", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TestarioText)
                Text("por hacer", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
            }
            Column {
                Text("$flagged", style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), color = TestarioYellow)
                Text("marcadas", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
            }
        }
    }
}

@Composable
private fun TestQuestionImage(
    path: String, contentDescription: String, annotations: ImageAnnotations
) {
    StudyVisualImage(
        imageUri = path, annotations = annotations,
        modifier = Modifier.fillMaxWidth().height(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .semantics { this.contentDescription = contentDescription }
    )
}

@Composable
private fun ExplanationCard(explanation: String, correctionImageUri: String?, annotations: ImageAnnotations) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioPrimaryDark.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Explicación", style = MaterialTheme.typography.labelMedium, color = TestarioPrimary)
            if (explanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(explanation, color = TestarioText, style = MaterialTheme.typography.bodyMedium)
            }
            correctionImageUri?.let { path ->
                Spacer(Modifier.height(12.dp))
                TestQuestionImage(path = path, contentDescription = "Imagen de la explicación", annotations = annotations)
            }
        }
    }
}

private fun topicDisplayName(currentTopicName: String, originTopicName: String?): String {
    val origin = originTopicName?.trim().orEmpty()
    return if (origin.isNotBlank() && !origin.equals(currentTopicName, ignoreCase = true)) {
        "$currentTopicName | $origin"
    } else {
        currentTopicName
    }
}

