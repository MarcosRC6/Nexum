package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import com.example.planario.app.PlanarioDependencies
import com.example.planario.features.calendar.CalendarScreen
import com.example.planario.features.plan.PlanScreen
import com.example.planario.features.timer.TimerScreen
import com.example.testario.data.local.QuickTestLinkManager
import com.example.testario.data.model.Topic
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.LibraryViewModel

private enum class PlanTab { PLAN, TIMER, CALENDAR }

@Composable
fun UnifiedPlanScreen(
    libraryViewModel: LibraryViewModel,
    initialTab: String? = null,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onTest: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onStartQuickTest: (List<String>, Int) -> Unit
) {
    val context = LocalContext.current
    val database by libraryViewModel.database.collectAsState()
    val activeTopic by PlanarioDependencies.studyRepository.activeTopic.collectAsState(initial = null)
    val planTopics by PlanarioDependencies.studyRepository.topics.collectAsState()
    val linkManager = remember { QuickTestLinkManager(context.applicationContext) }

    var selectedTab by remember(initialTab) {
        mutableStateOf(
            when (initialTab) {
                "timer" -> PlanTab.TIMER
                "calendar" -> PlanTab.CALENDAR
                else -> PlanTab.PLAN
            }
        )
    }
    var showLinkDialog by remember { mutableStateOf(false) }

    LaunchedEffect(planTopics, database.topics) {
        linkManager.prune(
            validPlanTopicIds = planTopics.mapTo(mutableSetOf()) { it.id },
            validLibraryTopicIds = database.topics.mapTo(mutableSetOf()) { it.id }
        )
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioMainTopBar("Plan", onSettings) },
        bottomBar = {
            MainBottomNavigation(
                selected = MainSection.PLAN,
                onHome = onHome,
                onLibrary = onLibrary,
                onTest = onTest,
                onPlan = {},
                onStats = onStats
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedTab == PlanTab.PLAN) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Plan") }
                } else {
                    OutlinedButton(onClick = { selectedTab = PlanTab.PLAN }, modifier = Modifier.weight(1f)) { Text("Plan") }
                }
                if (selectedTab == PlanTab.TIMER) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Reloj") }
                } else {
                    OutlinedButton(onClick = { selectedTab = PlanTab.TIMER }, modifier = Modifier.weight(1f)) { Text("Reloj") }
                }
                if (selectedTab == PlanTab.CALENDAR) {
                    Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Calendario") }
                } else {
                    OutlinedButton(onClick = { selectedTab = PlanTab.CALENDAR }, modifier = Modifier.weight(1f)) { Text("Calendario") }
                }
            }

            if (selectedTab == PlanTab.PLAN) {
                activeTopic?.let { active ->
                    val link = linkManager.get(active.id)
                    val linkedTopics = database.topics.filter { it.id in link.libraryTopicIds }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                        border = BorderStroke(1.dp, TestarioPrimary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Test rápido", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                            Text(
                                if (linkedTopics.isEmpty()) {
                                    "Vincula uno o varios bancos de Biblioteca a ${active.name}."
                                } else {
                                    "${link.questionCount} preguntas · Examen · ${linkedTopics.joinToString { it.name }}"
                                },
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showLinkDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) { Text(if (linkedTopics.isEmpty()) "Vincular" else "Configurar") }
                                Button(
                                    onClick = {
                                        if (link.libraryTopicIds.isNotEmpty()) {
                                            onStartQuickTest(link.libraryTopicIds.toList(), link.questionCount)
                                        }
                                    },
                                    enabled = link.libraryTopicIds.isNotEmpty(),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Iniciar") }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Column(Modifier.weight(1f)) {
                    PlanScreen()
                }
            } else if (selectedTab == PlanTab.TIMER) {
                Column(Modifier.weight(1f)) {
                    TimerScreen()
                }
            } else {
                Column(Modifier.weight(1f)) {
                    CalendarScreen()
                }
            }
        }
    }

    if (showLinkDialog) {
        val active = activeTopic
        if (active != null) {
            QuickTestLinkDialog(
                planTopicName = active.name,
                libraryTopics = database.topics,
                initialSelectedIds = linkManager.get(active.id).libraryTopicIds,
                initialQuestionCount = linkManager.get(active.id).questionCount,
                onDismiss = { showLinkDialog = false },
                onSave = { selectedIds, count ->
                    linkManager.save(
                        com.example.testario.data.local.QuickTestLink(
                            planTopicId = active.id,
                            libraryTopicIds = selectedIds,
                            questionCount = count
                        )
                    )
                    showLinkDialog = false
                }
            )
        }
    }
}

@Composable
private fun QuickTestLinkDialog(
    planTopicName: String,
    libraryTopics: List<Topic>,
    initialSelectedIds: Set<String>,
    initialQuestionCount: Int,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Int) -> Unit
) {
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }
    var countText by remember { mutableStateOf(initialQuestionCount.toString()) }
    var searchText by remember { mutableStateOf("") }
    val visibleTopics = remember(libraryTopics, searchText) {
        val query = searchText.trim()
        if (query.isBlank()) libraryTopics
        else libraryTopics.filter { it.name.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test rápido · $planTopicName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Elige los bancos de preguntas relacionados. Esto no limita los tests normales.",
                    color = TestarioMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = countText,
                    onValueChange = { value -> countText = value.filter { it.isDigit() }.take(3) },
                    label = { Text("Número de preguntas") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (libraryTopics.isEmpty()) {
                    Text("No hay temas en Biblioteca.", color = TestarioMutedText)
                } else {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Buscar en Biblioteca") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${selectedIds.size} seleccionados · ${visibleTopics.size} visibles",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.labelSmall
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(visibleTopics, key = { it.id }) { topic ->
                            val checked = topic.id in selectedIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { enabled ->
                                        selectedIds = if (enabled) selectedIds + topic.id else selectedIds - topic.id
                                    }
                                )
                                Text(topic.name, modifier = Modifier.weight(1f), color = TestarioText)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedIds, (countText.toIntOrNull() ?: 25).coerceIn(1, 500))
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
