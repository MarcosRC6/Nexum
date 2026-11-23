package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.DetailedExam
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.domain.GroupHierarchy
import com.example.testario.domain.TestHistoryAnalytics
import com.example.testario.domain.TrendSeriesPoint
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exam history is intentionally capped at five; trends use at most 30 compact session records.
 * No new bottom-navigation destination is introduced.
 */
@Composable
fun ExamHistoryAndTrends(database: TestarioDatabase) {
    var openedExamId by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Historial de exámenes", style = MaterialTheme.typography.titleLarge, color = TestarioText)
        Text(
            "Puedes revisar los 5 últimos exámenes finalizados. Los más antiguos se retiran automáticamente.",
            style = MaterialTheme.typography.bodySmall,
            color = TestarioMutedText
        )
        if (database.detailedExamHistory.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
                Text(
                    "Los próximos exámenes aparecerán aquí. Las notas anteriores siguen en las estadísticas generales.",
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    color = TestarioMutedText
                )
            }
        } else {
            database.detailedExamHistory.asReversed().forEach { exam ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { openedExamId = exam.id },
                    colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, TestarioSurfaceLight)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${examDate(exam.timestamp)} · Examen", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${formatScore(exam.score)}/10 · ${exam.percent}% · ${exam.correct} aciertos · ${exam.wrong} fallos · ${exam.blank} blancos",
                            color = TestarioMutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text("Toca para revisar las preguntas", color = TestarioPrimary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        val opened = database.detailedExamHistory.firstOrNull { it.id == openedExamId }
        if (opened != null) {
            ExamHistoryReviewDialog(opened, onDismiss = { openedExamId = null })
        }
    }

    Spacer(Modifier.height(14.dp))
    PerformanceTrends(database)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PerformanceTrends(database: TestarioDatabase) {
    var showGroups by remember { mutableStateOf(false) }
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    val subjects = if (showGroups) {
        database.groups.map { it.id to GroupHierarchy.path(database.groups, it.id) }
    } else {
        database.topics.map { it.id to it.name }
    }
    val requestedId = if (showGroups) selectedGroupId else selectedTopicId
    val currentId = requestedId?.takeIf { id -> subjects.any { it.first == id } } ?: subjects.firstOrNull()?.first
    val selectedName = subjects.firstOrNull { it.first == currentId }?.second.orEmpty()
    val values = if (currentId == null) emptyList() else TestHistoryAnalytics.series(
        database.performanceHistory, currentId, showGroups
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Evolución de resultados", style = MaterialTheme.typography.titleLarge, color = TestarioText)
        Text(
            "Últimas 30 sesiones completadas, resumidas por temas y grupos. " +
                "Cada punto muestra el porcentaje de aciertos en ese ámbito.",
            color = TestarioMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showGroups) {
                OutlinedButton(onClick = { showGroups = false; expanded = false }) { Text("Temas") }
                Button(onClick = {}) { Text("Grupos") }
            } else {
                Button(onClick = {}) { Text("Temas") }
                OutlinedButton(onClick = { showGroups = true; expanded = false }) { Text("Grupos") }
            }
        }

        if (subjects.isEmpty()) {
            Text("No hay ${if (showGroups) "grupos" else "temas"} definidos.", color = TestarioMutedText)
        } else {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TestarioSurface,
                        unfocusedContainerColor = TestarioSurface,
                        focusedTextColor = TestarioText,
                        unfocusedTextColor = TestarioText
                    ),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = TestarioSurface
                ) {
                    subjects.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name, color = TestarioText) },
                            onClick = {
                                if (showGroups) selectedGroupId = id else selectedTopicId = id
                                expanded = false
                            }
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (values.isEmpty()) {
                        Text(
                            "Todavía no hay resultados finalizados para este ${if (showGroups) "grupo" else "tema"}.",
                            color = TestarioMutedText
                        )
                    } else {
                        Text(
                            "${values.last().percent}% de aciertos en la última sesión · ${values.size} puntos",
                            color = TestarioText,
                            style = MaterialTheme.typography.titleMedium
                        )
                        TrendLineGraph(values)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(examDate(values.first().timestamp), color = TestarioMutedText, style = MaterialTheme.typography.labelSmall)
                            Text(examDate(values.last().timestamp), color = TestarioMutedText, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendLineGraph(values: List<TrendSeriesPoint>) {
    // Read theme colors in the composable scope; Canvas's DrawScope isn't composable.
    val primary = TestarioPrimary
    val grid = TestarioSurfaceLight
    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 12.dp.toPx()
        val bottom = size.height - 12.dp.toPx()
        for (percent in listOf(0, 50, 100)) {
            val y = bottom - (bottom - top) * percent / 100f
            drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        val points = values.mapIndexed { index, item ->
            val x = if (values.size == 1) (left + right) / 2f
                    else left + (right - left) * index / (values.size - 1).toFloat()
            val y = bottom - (bottom - top) * item.percent.coerceIn(0, 100) / 100f
            androidx.compose.ui.geometry.Offset(x, y)
        }
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, primary, style = Stroke(width = 2.5.dp.toPx()))
        }
        points.forEach { drawCircle(primary, radius = 3.5.dp.toPx(), center = it) }
    }
}

@Composable
private fun ExamHistoryReviewDialog(exam: DetailedExam, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = TestarioText,
        title = { Text("Examen · ${examDate(exam.timestamp)}") },
        text = {
            Column(
                modifier = Modifier.height(400.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Nota ${formatScore(exam.score)}/10 · ${exam.correct} correctas, " +
                        "${exam.wrong} incorrectas, ${exam.blank} en blanco",
                    color = TestarioText
                )
                exam.questions.forEachIndexed { index, record ->
                    val correct = record.question.answers.firstOrNull { it.id == record.question.correctAnswerId }
                    val selected = record.question.answers.firstOrNull { it.id == record.selectedAnswerId }
                    val valid = selected?.id == correct?.id
                    Card(colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "${index + 1}. ${record.topicName}: ${record.question.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TestarioText
                            )
                            Text(
                                "Tu respuesta: ${selected?.text ?: "En blanco"}",
                                color = if (valid) TestarioGreen else TestarioRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Correcta: ${correct?.text ?: "No disponible"}",
                                color = TestarioGreen,
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (record.question.explanation.isNotBlank()) {
                                Text(record.question.explanation, color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

private fun examDate(timestamp: Long) =
    SimpleDateFormat("dd/MM/yy HH:mm", Locale("es", "ES")).format(Date(timestamp))

private fun formatScore(score: Double) = String.format(Locale("es", "ES"), "%.2f", score)
