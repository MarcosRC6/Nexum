package com.example.testario.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import com.example.testario.ui.theme.NeonLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.planario.app.PlanarioDependencies
import com.example.testario.domain.StudyStreakCalculator
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.ui.theme.TestarioYellow
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    libraryViewModel: LibraryViewModel,
    statsViewModel: StatsViewModel,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onCreateTest: () -> Unit,
    onPlan: () -> Unit,
    onSettings: () -> Unit,
    onReviewFailures: (String) -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val studyDays by PlanarioDependencies.studyRepository.studyDays.collectAsState()
    val studySettings by PlanarioDependencies.studyRepository.settings.collectAsState()
    val streakSummary = remember(studyDays, studySettings.defaultFreeWeekDays) {
        StudyStreakCalculator.calculate(studyDays, studySettings.defaultFreeWeekDays)
    }
    val allQuestions = database.topics.flatMap { it.questions }

    val totalQuestions = allQuestions.size
    val totalAnswered = allQuestions.sumOf { it.stats.timesAnswered }
    val totalCorrect = allQuestions.sumOf { it.stats.timesCorrect }
    val totalWrong = allQuestions.sumOf { it.stats.timesWrong }
    val totalInFailures = allQuestions.count { it.stats.inFailures }
    val globalPercent = if (totalAnswered == 0) 0 else ((totalCorrect.toDouble() / totalAnswered) * 100).roundToInt()

    val globalColor = when {
        totalAnswered == 0 -> TestarioRed
        globalPercent >= 80 -> TestarioGreen
        globalPercent >= 50 -> TestarioYellow
        else -> TestarioRed
    }

    var expanded by remember { mutableStateOf(false) }
    var selectedTopicId by remember { mutableStateOf(database.topics.firstOrNull()?.id) }
    var showExtras by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    val selectedTopic = database.topics.firstOrNull { it.id == selectedTopicId }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioTopBar("Estadísticas", onBack) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- RENDIMIENTO GENERAL ---
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Rendimiento general", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "$globalPercent%", style = MaterialTheme.typography.displayMedium, color = globalColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBlock(totalQuestions.toString(), "Preguntas", TestarioText, Modifier.weight(1f))
                        StatBlock(totalAnswered.toString(), "Respondidas", TestarioText, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBlock(totalCorrect.toString(), "Aciertos", TestarioGreen, Modifier.weight(1f))
                        StatBlock(totalWrong.toString(), "Fallos", TestarioRed, Modifier.weight(1f))
                        StatBlock(totalInFailures.toString(), "En fallos", TestarioYellow, Modifier.weight(1f))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Racha de estudio", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBlock(streakSummary.current.toString(), "Actual", TestarioPrimary, Modifier.weight(1f))
                        StatBlock(streakSummary.best.toString(), "Mejor", TestarioGreen, Modifier.weight(1f))
                        StatBlock(streakSummary.studiedDays.toString(), "Días", TestarioText, Modifier.weight(1f))
                    }
                    Text("Los días libres programados no rompen la racha.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }
            }

            // --- POR TEMAS ---
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Por temas", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                if (database.topics.isEmpty()) {
                    Text("No hay temas creados.", color = TestarioMutedText)
                } else {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedTopic?.name ?: "Selecciona un tema", onValueChange = {}, readOnly = true,
                            modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = TestarioSurface, unfocusedContainerColor = TestarioSurface, focusedBorderColor = TestarioSurfaceLight, unfocusedBorderColor = TestarioSurfaceLight, focusedTextColor = TestarioText, unfocusedTextColor = TestarioText),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = TestarioSurface) {
                            database.topics.forEach { topic ->
                                DropdownMenuItem(text = { Text(topic.name, color = TestarioText) }, onClick = { selectedTopicId = topic.id; expanded = false })
                            }
                        }
                    }

                    selectedTopic?.let { topic ->
                        val tQuestions = topic.questions.size; val tAnswered = topic.questions.sumOf { it.stats.timesAnswered }; val tCorrect = topic.questions.sumOf { it.stats.timesCorrect }
                        val tWrong = topic.questions.sumOf { it.stats.timesWrong }; val tFailures = topic.questions.count { it.stats.inFailures }
                        val tUnanswered = tQuestions - topic.questions.count { it.stats.timesAnswered > 0 }
                        val tPercent = if (tAnswered == 0) 0 else ((tCorrect.toDouble() / tAnswered) * 100).roundToInt()

                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                                    Text(topic.name, style = MaterialTheme.typography.titleLarge, color = TestarioText, modifier = Modifier.weight(1f))
                                    Text("$tPercent%", style = MaterialTheme.typography.headlineMedium, color = when { tAnswered == 0 -> TestarioRed; tPercent >= 80 -> TestarioGreen; tPercent >= 50 -> TestarioYellow; else -> TestarioRed })
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "$tQuestions preguntas · $tUnanswered sin responder", color = TestarioMutedText, style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(progress = { if (tQuestions == 0) 0f else (tQuestions - tUnanswered).toFloat() / tQuestions.toFloat() }, modifier = Modifier.fillMaxWidth().height(6.dp), color = TestarioPrimary, trackColor = TestarioSurfaceLight)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    StatBlock(tAnswered.toString(), "Resp.", TestarioText, Modifier.weight(1f))
                                    StatBlock(tCorrect.toString(), "Aciertos", TestarioGreen, Modifier.weight(1f))
                                    StatBlock(tWrong.toString(), "Fallos", TestarioRed, Modifier.weight(1f))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                val diffs = topic.questions.mapNotNull { it.difficulty }
                                val avgDiff = if (diffs.isEmpty()) "sin datos" else when (diffs.average().roundToInt()) { 1 -> "Fácil"; 2 -> "Media"; 3 -> "Difícil"; else -> "Sin datos" }
                                Text(text = "Dificultad media: $avgDiff", color = TestarioMutedText, style = MaterialTheme.typography.bodyMedium)
                                if (tAnswered > 0) {
                                    val avgTime = topic.questions.sumOf { it.stats.totalTimeSpentSeconds } / tAnswered
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = "Ritmo medio: ${avgTime}s por pregunta", color = TestarioMutedText, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (tFailures > 0) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(onClick = { onReviewFailures(topic.id) }, modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, TestarioRed.copy(0.5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = TestarioRed)) {
                                        Text("Repasar $tFailures fallos del tema")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { showExtras = !showExtras }) { Text(text = if (showExtras) "Ocultar detalles ▲" else "Ver más detalles ▼", color = TestarioMutedText) }
                if (showExtras) {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val uniqueSeen = allQuestions.count { it.stats.timesAnswered > 0 }
                            val coverage = if (totalQuestions > 0) (uniqueSeen * 100 / totalQuestions) else 0
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cobertura del temario", color = TestarioMutedText); Text("$uniqueSeen de $totalQuestions ($coverage%)", color = TestarioText, style = MaterialTheme.typography.titleMedium) }
                            HorizontalDivider(color = TestarioSurfaceLight)
                            val mins = database.totalTrainingMinutes
                            val timeStr = if (mins >= 60) "${mins / 60}h ${mins % 60}min" else "${mins}min"
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Tiempo total invertido en tests", color = TestarioMutedText); Text(timeStr, color = TestarioText, style = MaterialTheme.typography.titleMedium) }
                            HorizontalDivider(color = TestarioSurfaceLight)
                            val recentTests = database.testHistory.takeLast(5)
                            val recentAvg = if (recentTests.isNotEmpty()) recentTests.map { it.score }.average() else 0.0
                            val formattedAvg = String.format(Locale("es", "ES"), "%.2f / 10", recentAvg)
                            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Nota media (últimos 5 tests)", color = TestarioMutedText); Text(if (recentTests.isEmpty()) "Sin datos" else formattedAvg, color = TestarioText, style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                border = BorderStroke(1.dp, TestarioRed.copy(alpha = 0.35f))
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gestión de estadísticas", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Text("Borra aciertos, fallos, blancos, tiempos y notas de los tests. No elimina preguntas ni temas.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { showClearStatsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, TestarioRed),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TestarioRed)
                    ) { Text("Borrar estadísticas") }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false },
            title = { Text("Borrar estadísticas") },
            text = { Text("¿Seguro que quieres borrar todas las estadísticas de tests? Las preguntas y los temas se conservarán.") },
            confirmButton = {
                Button(onClick = { statsViewModel.clearAllStats(); showClearStatsDialog = false }) {
                    Text("Borrar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearStatsDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun StatBlock(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier.height(64.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = valueColor)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TestarioMutedText)
        }
    }
}