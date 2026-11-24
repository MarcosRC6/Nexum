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
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import com.example.testario.data.model.TestTemplate
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.LibraryViewModel


@Composable
fun UnifiedPlanScreen(
    libraryViewModel: LibraryViewModel,
    initialTab: String? = null,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onTest: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onStartQuickTest: (List<String>, Int, String?) -> Unit
) {
    val context = LocalContext.current
    val database by libraryViewModel.database.collectAsState()
    val activeTopic by PlanarioDependencies.studyRepository.activeTopic.collectAsState(initial = null)
    val planTopics by PlanarioDependencies.studyRepository.topics.collectAsState()
    val linkManager = remember { QuickTestLinkManager(context.applicationContext) }

    // Plan, Reloj y Calendario son páginas de un paginador: siguen al dedo y, al llegar
    // al borde, el gesto pasa al paginador de secciones de TestarioApp.
    val requestedPage = when (initialTab) { "plan" -> 0; "timer" -> 1; "calendar" -> 2; else -> null }
    val tabPager = rememberPagerState(initialPage = requestedPage ?: 0, pageCount = { 3 })
    val tabScope = rememberCoroutineScope()
    LaunchedEffect(initialTab) {
        if (requestedPage != null && tabPager.currentPage != requestedPage) {
            tabPager.animateScrollToPage(requestedPage)
        }
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
                Button(
                    onClick = { tabScope.launch { tabPager.animateScrollToPage(0) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    selected = tabPager.currentPage == 0
                ) { Text("Plan", maxLines = 1) }
                Button(
                    onClick = { tabScope.launch { tabPager.animateScrollToPage(1) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    selected = tabPager.currentPage == 1
                ) { Text("Reloj", maxLines = 1) }
                Button(
                    onClick = { tabScope.launch { tabPager.animateScrollToPage(2) } },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                    selected = tabPager.currentPage == 2
                ) { Text("Calendario", maxLines = 1) }
            }

            HorizontalPager(
                state = tabPager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                key = { it }
            ) { tab ->
            Column(Modifier.fillMaxSize()) {
            if (tab == 0) {
                activeTopic?.let { active ->
                    val link = linkManager.get(active.id)
                    val linkedTopics = database.topics.filter { it.id in link.libraryTopicIds }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Test rápido", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                            Text(
                                if (link.templateId != null) {
                                    val name = database.testTemplates.firstOrNull { it.id == link.templateId }?.name
                                    "Plantilla: ${name ?: "No disponible"}"
                                } else if (linkedTopics.isEmpty()) {
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
                                        if (link.libraryTopicIds.isNotEmpty() || link.templateId != null) {
                                            onStartQuickTest(link.libraryTopicIds.toList(), link.questionCount, link.templateId)
                                        }
                                    },
                                    enabled = link.libraryTopicIds.isNotEmpty() || link.templateId != null,
                                    modifier = Modifier.weight(1f)
                                ) { Text("Iniciar") }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Column(Modifier.weight(1f)) {
                    PlanScreen(
                        libraryTopics = database.topics,
                        libraryTemplates = database.testTemplates,
                        onStartLinkedTest = onStartQuickTest
                    )
                }
            } else if (tab == 1) {
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
        }
    }

    if (showLinkDialog) {
        val active = activeTopic
        if (active != null) {
            QuickTestLinkDialog(
                planTopicName = active.name,
                libraryTopics = database.topics,
                testTemplates = database.testTemplates,
                initialSelectedIds = linkManager.get(active.id).libraryTopicIds,
                initialQuestionCount = linkManager.get(active.id).questionCount,
                initialTemplateId = linkManager.get(active.id).templateId,
                onDismiss = { showLinkDialog = false },
                onSave = { selectedIds, count, templateId ->
                    linkManager.save(
                        com.example.testario.data.local.QuickTestLink(
                            planTopicId = active.id,
                            libraryTopicIds = selectedIds,
                            questionCount = count,
                            templateId = templateId
                        )
                    )
                    showLinkDialog = false
                }
            )
        }
    }
}

@Composable
internal fun QuickTestLinkDialog(
    planTopicName: String,
    libraryTopics: List<Topic>,
    testTemplates: List<TestTemplate>,
    initialSelectedIds: Set<String>,
    initialQuestionCount: Int,
    initialTemplateId: String?,
    onDismiss: () -> Unit,
    onSave: (Set<String>, Int, String?) -> Unit
) {
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }
    var countText by remember { mutableStateOf(initialQuestionCount.toString()) }
    var searchText by remember { mutableStateOf("") }
    var selectedTemplateId by remember { mutableStateOf(initialTemplateId) }
    var templateMenuExpanded by remember { mutableStateOf(false) }
    val selectedTemplate = testTemplates.firstOrNull { it.id == selectedTemplateId }
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
                    "Elige una plantilla guardada o configura manualmente un test rápido.",
                    color = TestarioMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = { templateMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedTemplate?.let { "Plantilla: ${it.name}" }
                        ?: if (selectedTemplateId != null) "Plantilla no disponible" else "Sin plantilla · Test rápido")
                }
                DropdownMenu(
                    expanded = templateMenuExpanded,
                    onDismissRequest = { templateMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Sin plantilla · Elegir temas y preguntas") },
                        onClick = { selectedTemplateId = null; templateMenuExpanded = false }
                    )
                    testTemplates.sortedBy { it.name.lowercase() }.forEach { template ->
                        DropdownMenuItem(
                            text = { Text(template.name) },
                            onClick = { selectedTemplateId = template.id; templateMenuExpanded = false }
                        )
                    }
                }
                if (selectedTemplateId != null) {
                    Text(
                        if (selectedTemplate == null)
                            "Esta plantilla ya no existe. Elige otra o desactiva la plantilla."
                        else "Se aplicará toda la configuración guardada: temas, cantidad, modo, dificultad, puntuación y temporizador.",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
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
            }
        },
        confirmButton = {
            Button(
                enabled = selectedTemplateId == null || selectedTemplate != null,
                onClick = {
                    onSave(selectedIds, (countText.toIntOrNull() ?: 25).coerceIn(1, 500), selectedTemplateId)
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
