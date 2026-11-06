package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.testario.ui.theme.NeonIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
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
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.Question
import com.example.testario.data.model.Topic
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioRedDark
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun LibraryTopicsScreen(
    libraryViewModel: LibraryViewModel,
    statsViewModel: StatsViewModel,
    onOpenTopic: (String) -> Unit,
    onHome: () -> Unit,
    onCreateTest: () -> Unit,
    onPlan: () -> Unit,
    onGroups: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val context = LocalContext.current

    var showActionDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showCombineDialog by remember { mutableStateOf(false) }

    var isDeleteMode by remember { mutableStateOf(false) }
    var selectedTopicIdsForDelete by remember { mutableStateOf<Set<String>>(emptySet()) }

    var topicSearchText by remember { mutableStateOf("") }
    var debouncedQuestionSearchText by remember { mutableStateOf("") }
    var homeSearchMode by remember { mutableStateOf(HomeSearchMode.TOPICS) }
    var searchModeMenuExpanded by remember { mutableStateOf(false) }
    var questionSearchScope by remember { mutableStateOf(QuestionSearchScope.ALL) }
    var searchScopeMenuExpanded by remember { mutableStateOf(false) }
    var selectedSearchQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showGlobalMoveDialog by remember { mutableStateOf(false) }
    var showGlobalCreateDialog by remember { mutableStateOf(false) }
    var preserveOriginInGlobalMove by remember { mutableStateOf(true) }
    var preserveOriginInGlobalCreate by remember { mutableStateOf(true) }
    var avoidDuplicatesInGlobalMove by remember { mutableStateOf(true) }
    var avoidDuplicatesInGlobalCreate by remember { mutableStateOf(true) }
    var globalNewTopicName by remember { mutableStateOf("") }

    var newTopicName by remember { mutableStateOf("") }
    var combineTopicName by remember { mutableStateOf("") }
    var selectedCombineTopicIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val listState = rememberLazyListState()

    val effectiveSearchMode = if (isDeleteMode) HomeSearchMode.TOPICS else homeSearchMode

    LaunchedEffect(topicSearchText, effectiveSearchMode) {
        if (effectiveSearchMode != HomeSearchMode.QUESTIONS || topicSearchText.isBlank()) {
            debouncedQuestionSearchText = ""
        } else {
            // Evita recalcular miles de preguntas en cada pulsación rápida.
            delay(220)
            debouncedQuestionSearchText = topicSearchText
        }
    }

    val filteredTopics = remember(database.topics, topicSearchText, effectiveSearchMode) {
        val q = normalizeSearchText(topicSearchText)
        database.topics
            .filter {
                q.isBlank() ||
                    (effectiveSearchMode == HomeSearchMode.TOPICS && normalizeSearchText(it.name).contains(q))
            }
            .sortedWith(
                compareByDescending<Topic> { it.isPinned }
                    .thenComparator { t1, t2 -> t1.name.naturalCompareTo(t2.name) }
            )
    }

    // El índice solo se construye en modo Preguntas y se reutiliza mientras escribes.
    val questionSearchIndex = remember(database.topics, effectiveSearchMode) {
        if (effectiveSearchMode != HomeSearchMode.QUESTIONS) {
            emptyList()
        } else {
            database.topics.flatMap { topic ->
                topic.questions.map { question ->
                    Triple(topic, question, question.buildSearchIndex())
                }
            }
        }
    }

    val questionMatches = remember(
        questionSearchIndex,
        topicSearchText,
        debouncedQuestionSearchText,
        questionSearchScope,
        effectiveSearchMode
    ) {
        if (
            effectiveSearchMode != HomeSearchMode.QUESTIONS ||
            debouncedQuestionSearchText.isBlank() ||
            debouncedQuestionSearchText != topicSearchText
        ) {
            emptyList()
        } else {
            val normalizedQuery = normalizeSearchText(debouncedQuestionSearchText)
            questionSearchIndex.asSequence()
                .filter { (_, _, index) -> index.matches(normalizedQuery, questionSearchScope) }
                .map { (topic, question, _) -> topic to question }
                .toList()
        }
    }

    val combineTopicsSorted = remember(database.topics) {
        database.topics.sortedWith(Comparator { t1, t2 -> t1.name.naturalCompareTo(t2.name) })
    }

    val targetTopicsSorted = combineTopicsSorted

    fun clearGlobalQuestionSelection() {
        selectedSearchQuestionIds = emptySet()
        showGlobalMoveDialog = false
        showGlobalCreateDialog = false
        globalNewTopicName = ""
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioMainTopBar("Biblioteca", onSettings) },
        bottomBar = {
            MainBottomNavigation(
                selected = MainSection.LIBRARY,
                onHome = onHome,
                onLibrary = {},
                onTest = onCreateTest,
                onPlan = onPlan,
                onStats = onStats
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text("Organiza temas, preguntas y grupos", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            LibrarySectionTabs(
                topicsSelected = true,
                onTopics = {},
                onGroups = onGroups,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isDeleteMode) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = TestarioRedDark),
                    border = BorderStroke(1.dp, TestarioRed)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${selectedTopicIdsForDelete.size} seleccionados", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { isDeleteMode = false }) { Text("Cancelar", color = TestarioText) }
                            Button(
                                onClick = {
                                    selectedTopicIdsForDelete.forEach { id -> libraryViewModel.deleteTopic(id) }
                                    isDeleteMode = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TestarioRed),
                                enabled = selectedTopicIdsForDelete.isNotEmpty()
                            ) {
                                Text("Eliminar", color = TestarioBackground)
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Nuevo tema")
                    }
                    OutlinedButton(
                        onClick = { showActionDialog = true },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, TestarioPrimary)
                    ) {
                        Text("Gestionar")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = topicSearchText,
                onValueChange = { value ->
                    topicSearchText = value
                    if (value.isBlank()) selectedSearchQuestionIds = emptySet()
                },
                label = {
                    Text(
                        if (effectiveSearchMode == HomeSearchMode.QUESTIONS) {
                            "Buscar preguntas..."
                        } else {
                            "Buscar temas..."
                        }
                    )
                },
                leadingIcon = { Text("🔍") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (topicSearchText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    topicSearchText = ""
                                    selectedSearchQuestionIds = emptySet()
                                }
                            ) { Text("✕", color = TestarioMutedText) }
                        }

                        Box {
                            IconButton(
                                enabled = !isDeleteMode,
                                onClick = { searchModeMenuExpanded = true }
                            ) {
                                Text("⋮", color = if (isDeleteMode) TestarioMutedText else TestarioText)
                            }
                            DropdownMenu(
                                expanded = searchModeMenuExpanded,
                                onDismissRequest = { searchModeMenuExpanded = false }
                            ) {
                                HomeSearchMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.label) },
                                        leadingIcon = {
                                            RadioButton(
                                                selected = homeSearchMode == mode,
                                                onClick = null
                                            )
                                        },
                                        onClick = {
                                            homeSearchMode = mode
                                            selectedSearchQuestionIds = emptySet()
                                            searchModeMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TestarioText,
                    unfocusedTextColor = TestarioText,
                    focusedBorderColor = TestarioPrimary,
                    unfocusedBorderColor = TestarioSurfaceLight,
                    focusedLabelColor = TestarioPrimary,
                    unfocusedLabelColor = TestarioMutedText,
                    cursorColor = TestarioPrimary,
                    focusedContainerColor = TestarioSurface,
                    unfocusedContainerColor = TestarioSurface
                )
            )

            if (!isDeleteMode && effectiveSearchMode == HomeSearchMode.QUESTIONS) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Buscar preguntas en:", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box {
                        TextButton(onClick = { searchScopeMenuExpanded = true }) {
                            Text("${questionSearchScope.label} ▼")
                        }
                        DropdownMenu(
                            expanded = searchScopeMenuExpanded,
                            onDismissRequest = { searchScopeMenuExpanded = false }
                        ) {
                            QuestionSearchScope.values().forEach { scope ->
                                DropdownMenuItem(
                                    text = { Text(scope.label) },
                                    leadingIcon = { RadioButton(selected = questionSearchScope == scope, onClick = null) },
                                    onClick = {
                                        questionSearchScope = scope
                                        searchScopeMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (topicSearchText.isNotBlank()) {
                        Text(
                            if (debouncedQuestionSearchText == topicSearchText) {
                                "${questionMatches.size} preguntas"
                            } else {
                                "Buscando…"
                            },
                            color = TestarioMutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (database.topics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tienes temas. Pulsa + para empezar.", color = TestarioMutedText)
                }
            } else if (isDeleteMode) {
                if (filteredTopics.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se han encontrado temas.", color = TestarioMutedText)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = filteredTopics, key = { it.id }) { topic ->
                                TopicCard(
                                    topic = topic,
                                    isDeleteMode = true,
                                    isSelectedForDelete = selectedTopicIdsForDelete.contains(topic.id),
                                    onToggleSelection = {
                                        selectedTopicIdsForDelete = if (selectedTopicIdsForDelete.contains(topic.id)) {
                                            selectedTopicIdsForDelete - topic.id
                                        } else {
                                            selectedTopicIdsForDelete + topic.id
                                        }
                                    },
                                    onOpen = { onOpenTopic(topic.id) },
                                    onDelete = { libraryViewModel.deleteTopic(topic.id) },
                                    onRename = { newName -> libraryViewModel.updateTopic(topic.id, newName) },
                                    onTogglePin = { libraryViewModel.togglePinTopic(topic.id) },
                                    onClearStats = { statsViewModel.clearStatsForTopic(topic.id) }
                                )
                            }
                        }
                        FastScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            } else if (topicSearchText.isNotBlank() && effectiveSearchMode == HomeSearchMode.TOPICS) {
                if (filteredTopics.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se han encontrado temas.", color = TestarioMutedText)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(items = filteredTopics, key = { it.id }) { topic ->
                                TopicCard(
                                    topic = topic,
                                    isDeleteMode = false,
                                    isSelectedForDelete = false,
                                    onToggleSelection = {},
                                    onOpen = { onOpenTopic(topic.id) },
                                    onDelete = { libraryViewModel.deleteTopic(topic.id) },
                                    onRename = { newName -> libraryViewModel.updateTopic(topic.id, newName) },
                                    onTogglePin = { libraryViewModel.togglePinTopic(topic.id) },
                                    onClearStats = { statsViewModel.clearStatsForTopic(topic.id) }
                                )
                            }
                        }
                        FastScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            } else if (topicSearchText.isNotBlank() && effectiveSearchMode == HomeSearchMode.QUESTIONS) {
                val searchReady = debouncedQuestionSearchText == topicSearchText

                if (!searchReady && selectedSearchQuestionIds.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Buscando preguntas…", color = TestarioMutedText)
                    }
                } else if (questionMatches.isEmpty() && selectedSearchQuestionIds.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No se han encontrado preguntas.", color = TestarioMutedText)
                    }
                } else {
                    if (questionMatches.isNotEmpty() || selectedSearchQuestionIds.isNotEmpty()) {
                        GlobalSearchActions(
                            selectedCount = selectedSearchQuestionIds.size,
                            resultCount = questionMatches.size,
                            allResultsSelected = questionMatches.isNotEmpty() &&
                                questionMatches.all { it.second.id in selectedSearchQuestionIds },
                            onSelectAllResults = {
                                selectedSearchQuestionIds =
                                    selectedSearchQuestionIds + questionMatches.map { it.second.id }
                            },
                            onClear = { selectedSearchQuestionIds = emptySet() },
                            onMove = { showGlobalMoveDialog = true },
                            onCreateTopic = {
                                globalNewTopicName = ""
                                showGlobalCreateDialog = true
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (questionMatches.isEmpty() && selectedSearchQuestionIds.isNotEmpty()) {
                                item {
                                    Text(
                                        "No hay resultados para esta búsqueda. Mantienes " +
                                            "${selectedSearchQuestionIds.size} pregunta(s) seleccionada(s).",
                                        color = TestarioMutedText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            if (questionMatches.isNotEmpty()) {
                                item {
                                    Text(
                                        "Preguntas encontradas (${questionMatches.size})",
                                        color = TestarioText,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                                items(questionMatches, key = { it.second.id }) { (topic, question) ->
                                    GlobalQuestionSearchCard(
                                        topic = topic,
                                        question = question,
                                        selected = question.id in selectedSearchQuestionIds,
                                        onToggle = {
                                            selectedSearchQuestionIds =
                                                if (question.id in selectedSearchQuestionIds) {
                                                    selectedSearchQuestionIds - question.id
                                                } else {
                                                    selectedSearchQuestionIds + question.id
                                                }
                                        }
                                    )
                                }
                            }
                        }
                        FastScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = filteredTopics, key = { it.id }) { topic ->
                            TopicCard(
                                topic = topic,
                                isDeleteMode = false,
                                isSelectedForDelete = false,
                                onToggleSelection = {},
                                onOpen = { onOpenTopic(topic.id) },
                                onDelete = { libraryViewModel.deleteTopic(topic.id) },
                                onRename = { newName -> libraryViewModel.updateTopic(topic.id, newName) },
                                onTogglePin = { libraryViewModel.togglePinTopic(topic.id) },
                                onClearStats = { statsViewModel.clearStatsForTopic(topic.id) }
                            )
                        }
                    }
                    FastScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }

    if (showGlobalMoveDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalMoveDialog = false },
            title = { Text("Mover preguntas encontradas") },
            text = {
                Column {
                    Text(
                        "Selecciona el tema de destino para ${selectedSearchQuestionIds.size} pregunta(s).",
                        color = TestarioMutedText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = preserveOriginInGlobalMove,
                            onCheckedChange = { preserveOriginInGlobalMove = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conservar tema de procedencia", color = TestarioText)
                            Text(
                                "Al hacer test se mostrará “Tema actual | Tema de origen”.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = avoidDuplicatesInGlobalMove,
                            onCheckedChange = { avoidDuplicatesInGlobalMove = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evitar preguntas repetidas", color = TestarioText)
                            Text(
                                "Si una seleccionada ya existe en el tema destino, no se moverá ni se duplicará.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(targetTopicsSorted, key = { it.id }) { target ->
                            TextButton(
                                onClick = {
                                    libraryViewModel.moveQuestionsFromMany(
                                        targetTopicId = target.id,
                                        questionIds = selectedSearchQuestionIds,
                                        preserveOrigin = preserveOriginInGlobalMove,
                                        avoidDuplicates = avoidDuplicatesInGlobalMove
                                    )
                                    clearGlobalQuestionSelection()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(target.name, modifier = Modifier.fillMaxWidth(), color = TestarioText)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showGlobalMoveDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showGlobalCreateDialog) {
        AlertDialog(
            onDismissRequest = { showGlobalCreateDialog = false },
            title = { Text("Crear tema con las seleccionadas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se moverán ${selectedSearchQuestionIds.size} pregunta(s) desde sus temas actuales.",
                        color = TestarioMutedText
                    )
                    OutlinedTextField(
                        value = globalNewTopicName,
                        onValueChange = { globalNewTopicName = it },
                        label = { Text("Nombre del nuevo tema") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = preserveOriginInGlobalCreate,
                            onCheckedChange = { preserveOriginInGlobalCreate = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conservar tema de procedencia", color = TestarioText)
                            Text(
                                "Ejemplo: ${globalNewTopicName.ifBlank { "Funciones" }} | Tema 12",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = avoidDuplicatesInGlobalCreate,
                            onCheckedChange = { avoidDuplicatesInGlobalCreate = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evitar preguntas repetidas", color = TestarioText)
                            Text(
                                "Si seleccionaste la misma pregunta en varios temas, el nuevo tema recibirá una sola copia.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = globalNewTopicName.isNotBlank() && selectedSearchQuestionIds.isNotEmpty(),
                    onClick = {
                        val newId = libraryViewModel.createTopicFromQuestions(
                            newTopicName = globalNewTopicName,
                            questionIds = selectedSearchQuestionIds,
                            preserveOrigin = preserveOriginInGlobalCreate,
                            avoidDuplicates = avoidDuplicatesInGlobalCreate
                        )
                        clearGlobalQuestionSelection()
                        topicSearchText = ""
                        if (newId != null) onOpenTopic(newId)
                    }
                ) { Text("Crear y mover") }
            },
            dismissButton = { TextButton(onClick = { showGlobalCreateDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showActionDialog) {
        AlertDialog(
            onDismissRequest = { showActionDialog = false },
            containerColor = TestarioSurface,
            titleContentColor = TestarioText,
            title = { Text("Gestionar temas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { showActionDialog = false; showCombineDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, TestarioPrimary)) { Text("Combinar varios temas") }
                    OutlinedButton(
                        onClick = { showActionDialog = false; isDeleteMode = true; selectedTopicIdsForDelete = emptySet() },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TestarioRed), border = BorderStroke(1.dp, TestarioRed)
                    ) { Text("Eliminar temas") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showActionDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = TestarioSurface,
            titleContentColor = TestarioText,
            title = { Text("Nuevo tema") },
            text = { OutlinedTextField(value = newTopicName, onValueChange = { newTopicName = it }, label = { Text("Nombre del tema") }, singleLine = true, shape = RoundedCornerShape(14.dp)) },
            confirmButton = {
                Button(onClick = {
                    if (newTopicName.isNotBlank()) {
                        libraryViewModel.addTopic(newTopicName)
                        newTopicName = ""
                        showCreateDialog = false
                    }
                }) { Text("Crear") }
            }
        )
    }

    if (showCombineDialog) {
        AlertDialog(
            onDismissRequest = { showCombineDialog = false; combineTopicName = ""; selectedCombineTopicIds = emptySet() },
            containerColor = TestarioSurface,
            titleContentColor = TestarioText,
            title = { Text("Combinar temas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Selecciona los temas a unir:", color = TestarioText)
                    LazyColumn(modifier = Modifier.heightIn(max = 250.dp)) {
                        items(combineTopicsSorted) { topic ->
                            val isSelected = selectedCombineTopicIds.contains(topic.id)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { selectedCombineTopicIds = if (isSelected) selectedCombineTopicIds - topic.id else selectedCombineTopicIds + topic.id }.padding(vertical = 4.dp)
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = TestarioPrimary))
                                Text(text = "${topic.name} (${topic.questions.size})", color = TestarioText)
                            }
                        }
                    }
                    OutlinedTextField(value = combineTopicName, onValueChange = { combineTopicName = it }, label = { Text("Nombre del tema conjunto") }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
            },
            confirmButton = {
                Button(
                    enabled = combineTopicName.isNotBlank() && selectedCombineTopicIds.size >= 2,
                    onClick = {
                        libraryViewModel.combineTopics(selectedCombineTopicIds.toList(), combineTopicName)
                        showCombineDialog = false
                        combineTopicName = ""
                        selectedCombineTopicIds = emptySet()
                    }
                ) { Text("Combinar") }
            },
            dismissButton = { TextButton(onClick = { showCombineDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun GlobalSearchActions(
    selectedCount: Int,
    resultCount: Int,
    allResultsSelected: Boolean,
    onSelectAllResults: () -> Unit,
    onClear: () -> Unit,
    onMove: () -> Unit,
    onCreateTopic: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
        border = BorderStroke(1.dp, TestarioPrimary)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$selectedCount seleccionadas", color = TestarioText, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onSelectAllResults,
                    enabled = resultCount > 0 && !allResultsSelected
                ) { Text("Seleccionar resultados") }
                TextButton(onClick = onClear, enabled = selectedCount > 0) { Text("Limpiar") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onMove,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Mover") }
                OutlinedButton(
                    onClick = onCreateTopic,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TestarioPrimary)
                ) { Text("Crear tema") }
            }
        }
    }
}

@Composable
private fun GlobalQuestionSearchCard(
    topic: Topic,
    question: Question,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val location = if (!question.originTopicName.isNullOrBlank() && !question.originTopicName.equals(topic.name, ignoreCase = true)) {
        "${topic.name} | ${question.originTopicName}"
    } else {
        topic.name
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) TestarioPrimaryDark else TestarioSurface),
        border = BorderStroke(1.dp, if (selected) TestarioPrimary else TestarioSurfaceLight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(location, color = TestarioPrimary, style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(question.text, color = TestarioText, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${question.answers.size} respuestas${if (question.explanation.isNotBlank()) " · Con explicación" else ""}",
                    color = TestarioMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun TopicCard(
    topic: Topic, isDeleteMode: Boolean, isSelectedForDelete: Boolean,
    onToggleSelection: () -> Unit, onOpen: () -> Unit, onDelete: () -> Unit,
    onRename: (String) -> Unit, onTogglePin: () -> Unit, onClearStats: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearStatsDialog by remember { mutableStateOf(false) }
    var renameValue by remember(topic.id, topic.name) { mutableStateOf(topic.name) }

    val total = topic.questions.size
    val totalAnswered = topic.questions.sumOf { it.stats.timesAnswered }
    val totalCorrect = topic.questions.sumOf { it.stats.timesCorrect }
    val percent = if (totalAnswered == 0) 0 else ((totalCorrect.toDouble() / totalAnswered) * 100).roundToInt()
    val failures = topic.questions.count { it.stats.inFailures }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { if (isDeleteMode) onToggleSelection() else onOpen() },
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDeleteMode && isSelectedForDelete) TestarioRedDark.copy(alpha = 0.3f) else TestarioSurface),
        shape = RoundedCornerShape(22.dp),
        border = if (isDeleteMode && isSelectedForDelete) BorderStroke(1.dp, TestarioRed) else if (topic.isPinned) BorderStroke(1.dp, TestarioPrimary) else null
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (isDeleteMode) {
                Checkbox(checked = isSelectedForDelete, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = TestarioRed))
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (topic.isPinned && !isDeleteMode) { Text("📌 ", style = MaterialTheme.typography.titleLarge) }
                    Text(text = topic.name, style = MaterialTheme.typography.titleLarge, color = TestarioText)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Preguntas: $total · Acierto: $percent% · Fallos: $failures", style = MaterialTheme.typography.bodyMedium, color = TestarioMutedText)
            }

            if (!isDeleteMode) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Text("⋮", color = TestarioText, style = MaterialTheme.typography.titleLarge) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(if (topic.isPinned) "Desfijar tema" else "Fijar tema") }, onClick = { menuExpanded = false; onTogglePin() })
                        DropdownMenuItem(text = { Text("Editar nombre") }, onClick = { menuExpanded = false; showRenameDialog = true })
                        DropdownMenuItem(text = { Text("Reiniciar estadísticas") }, onClick = { menuExpanded = false; showClearStatsDialog = true })
                        DropdownMenuItem(text = { Text("Eliminar tema", color = TestarioRed) }, onClick = { menuExpanded = false; showDeleteDialog = true })
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false }, title = { Text("Renombrar tema") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it }, singleLine = true) },
            confirmButton = { Button(onClick = { onRename(renameValue); showRenameDialog = false }) { Text("Guardar") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false }, title = { Text("Eliminar tema") },
            text = { Text("¿Seguro que quieres borrar \"${topic.name}\"? No se puede deshacer.") },
            confirmButton = { Button(onClick = { onDelete(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showClearStatsDialog) {
        AlertDialog(
            onDismissRequest = { showClearStatsDialog = false }, containerColor = TestarioSurface, titleContentColor = TestarioText, textContentColor = TestarioMutedText,
            title = { Text("Reiniciar estadísticas") }, text = { Text("¿Seguro que quieres poner a cero los aciertos, fallos y rachas del tema \"${topic.name}\"? Las preguntas no se borrarán.") },
            confirmButton = { Button(onClick = { onClearStats(); showClearStatsDialog = false }) { Text("Reiniciar") } },
            dismissButton = { TextButton(onClick = { showClearStatsDialog = false }) { Text("Cancelar") } }
        )
    }
}