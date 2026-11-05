package com.example.testario.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.testario.data.model.TestMode
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioGreenDark
import com.example.testario.ui.theme.TestarioMutedText
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
    var reviewingResults by rememberSaveable { mutableStateOf(false) }
    var reviewIndex by rememberSaveable { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val context = LocalContext.current

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
        if (questions.isNotEmpty()) {
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
    val currentTopicDisplayName = topicDisplayName(currentTopicName, current.question.originTopicName)

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = TestarioSurface,
            titleContentColor = TestarioText,
            textContentColor = TestarioMutedText,
            title = { Text("Abandonar test") },
            text = { Text("¿Qué deseas hacer con el progreso actual?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        val res = sessionViewModel.finish()
                        if (sessionViewModel.claimFinalStatsRegistration()) {
                            val elapsedMins = maxOf(1, (totalElapsedSeconds / 60L).toInt())
                            statsViewModel.registerTestResults(
                                results = questions,
                                finalTestResult = res,
                                totalTimeSpentMinutes = elapsedMins
                            )
                        }
                        onFinish()
                    }
                ) {
                    Text("Guardar y salir")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showExitDialog = false }) {
                        Text("Cancelar", color = TestarioMutedText)
                    }
                    TextButton(onClick = {
                        showExitDialog = false
                        sessionViewModel.cancelSession()
                        onFinish()
                    }) {
                        Text("Salir sin guardar", color = TestarioRed)
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
                    totalTimeSpentMinutes = elapsedMins
                )
            }
        }

        if (reviewingResults) {
            val reviewState = questions[reviewIndex.coerceIn(0, questions.lastIndex)]
            val reviewTopic = database.topics.firstOrNull { topic ->
                topic.questions.any { it.id == reviewState.question.id }
            }
            val reviewTopicName = reviewTopic?.name ?: "Tema desconocido"
            val reviewDisplayName = topicDisplayName(reviewTopicName, reviewState.question.originTopicName)

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
                                reviewDisplayName.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = TestarioPrimary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                reviewState.question.text,
                                style = MaterialTheme.typography.titleLarge,
                                color = TestarioText
                            )

                            reviewState.question.explanationImageUri?.let { path ->
                                Spacer(Modifier.height(14.dp))
                                TestQuestionImage(path = path, contentDescription = "Imagen del enunciado")
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
                                    border = BorderStroke(1.dp, border)
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
                                    correctionImageUri = reviewState.question.correctionImageUri
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
                    border = BorderStroke(1.dp, TestarioPrimary)
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
                    IconButton(onClick = { showExitDialog = true }) { Text("✕", style = MaterialTheme.typography.titleLarge, color = TestarioText) }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(if(mode == TestMode.EXAM) "Modo Examen" else "Entrenamiento", style = MaterialTheme.typography.titleMedium, color = TestarioText)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Pregunta ${currentIndex + 1} de ${questions.size}", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
                            Text(" · ", style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
                            val formatMins = totalElapsedSeconds / 60
                            val formatSecs = totalElapsedSeconds % 60
                            Text("⏱️ %02d:%02d".format(formatMins, formatSecs), style = MaterialTheme.typography.bodySmall, color = TestarioPrimary)
                        }
                    }
                    remainingSeconds?.let { Text("%02d:%02d".format(it / 60, it % 60), color = if(it < 60) TestarioRed else TestarioText, style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize()) {

            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(questions.size) { index ->
                    val qState = questions[index]
                    val isCurrent = index == currentIndex
                    val isFlagged = qState.question.flagged
                    val isAnswered = qState.selectedAnswerId != null

                    val bgColor = if (isCurrent) TestarioPrimary else if (isAnswered) TestarioSurfaceLight else TestarioSurface
                    val textColor = if (isCurrent) TestarioBackground else TestarioText
                    val borderColor = if (isFlagged) TestarioRed else if (isCurrent) TestarioPrimary else TestarioSurfaceLight

                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clickable { sessionViewModel.goTo(index) },
                        shape = CircleShape,
                        color = bgColor,
                        border = BorderStroke(if (isFlagged) 2.dp else 1.dp, borderColor)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text((index + 1).toString(), color = textColor, style = MaterialTheme.typography.titleMedium)

                            if (isFlagged) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(8.dp)
                                        .background(TestarioRed, CircleShape)
                                )
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                Column(modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentTopicDisplayName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TestarioPrimary,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            var difficultyMenuExpanded by remember { mutableStateOf(false) }
                            val diffText = when(current.question.difficulty) { 1 -> "Fácil"; 2 -> "Medio"; 3 -> "Difícil"; else -> "¿Dificultad?" }
                            val diffColor = when(current.question.difficulty) { 1 -> TestarioGreen; 2 -> TestarioYellow; 3 -> TestarioRed; else -> TestarioMutedText }

                            Box {
                                TextButton(
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

                            IconButton(onClick = {
                                if (topicId != null) {
                                    onEditQuestion(topicId, current.question.id)
                                }
                            }) { Text("✏️") }

                            IconButton(
                                onClick = {
                                    sessionViewModel.toggleFlag(current.question.id)
                                    libraryViewModel.toggleQuestionFlag(current.question.id)
                                }
                            ) {
                                Text(if (current.question.flagged) "🚩" else "🏳️")
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = current.question.text,
                        style = MaterialTheme.typography.titleLarge,
                        color = TestarioText,
                        modifier = Modifier.fillMaxWidth()
                    )

                    current.question.explanationImageUri?.let { path ->
                        Spacer(Modifier.height(14.dp))
                        TestQuestionImage(path = path, contentDescription = "Imagen del enunciado")
                    }

                    Spacer(Modifier.height(16.dp))
                    current.question.answers.forEachIndexed { i, ans ->
                        val active = current.selectedAnswerId == ans.id
                        val correct = current.question.correctAnswerId == ans.id
                        val col = when { current.corrected && correct -> TestarioGreenDark; current.corrected && active -> TestarioRedDark; active -> TestarioPrimaryDark; else -> TestarioSurfaceLight }
                        val border = when { current.corrected && correct -> TestarioGreen; current.corrected && active -> TestarioRed; active -> TestarioPrimary; else -> TestarioSurfaceLight }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                // NUEVO: Se desactiva el clic si la pregunta ya está corregida
                                .clickable(enabled = !current.corrected) { sessionViewModel.selectAnswer(ans.id) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = col),
                            border = BorderStroke(1.dp, border)
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = TestarioSurface, modifier = Modifier.size(28.dp)) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(('A'+i).toString(), style = MaterialTheme.typography.bodySmall, color = TestarioText)
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
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
                            correctionImageUri = current.question.correctionImageUri
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { sessionViewModel.previous() }, enabled = currentIndex > 0, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) { Text("Atrás") }

                if(!isTraining) {
                    OutlinedButton(
                        onClick = { sessionViewModel.leaveBlank() },
                        enabled = current.selectedAnswerId != null,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Limpiar") }
                }

                Button(onClick = { if(sessionViewModel.isLastQuestion()) finished = true else sessionViewModel.next() }, modifier = Modifier.weight(1f).height(50.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(if (sessionViewModel.isLastQuestion()) "Finalizar" else "Siguiente")
                }
            }
        }
    }
}

@Composable
private fun TestQuestionImage(path: String, contentDescription: String) {
    val context = LocalContext.current
    val imageSource = if (path.startsWith("content://")) {
        android.net.Uri.parse(path)
    } else {
        File(path)
    }
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imageSource)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(14.dp)),
        contentScale = ContentScale.Inside
    )
}

@Composable
private fun ExplanationCard(explanation: String, correctionImageUri: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioPrimaryDark.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, TestarioPrimary)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("💡 Explicación", style = MaterialTheme.typography.labelMedium, color = TestarioPrimary)
            if (explanation.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(explanation, color = TestarioText, style = MaterialTheme.typography.bodyMedium)
            }
            correctionImageUri?.let { path ->
                Spacer(Modifier.height(12.dp))
                TestQuestionImage(path = path, contentDescription = "Imagen de la explicación")
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

