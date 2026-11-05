package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.Question
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.ui.theme.TestarioYellow
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import kotlinx.coroutines.delay

@Composable
fun TopicDetailScreen(
    topicId: String,
    libraryViewModel: LibraryViewModel,
    statsViewModel: StatsViewModel,
    onBack: () -> Unit,
    onAddQuestion: () -> Unit,
    onEditQuestion: (String) -> Unit,
    onStartTest: (String) -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.firstOrNull { it.id == topicId }

    var searchText by remember { mutableStateOf("") }
    var debouncedSearchText by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf(QuestionSearchScope.ALL) }
    var filterFlagged by remember { mutableStateOf(false) }
    var selectedDifficultyFilter by remember { mutableStateOf<Int?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    var topicMenuExpanded by remember(topicId) { mutableStateOf(false) }
    var selectionMode by remember(topicId) { mutableStateOf(false) }
    var selectedQuestionIds by remember(topicId) { mutableStateOf<Set<String>>(emptySet()) }
    var showMoveDialog by remember(topicId) { mutableStateOf(false) }
    var showCreateFromSelectionDialog by remember(topicId) { mutableStateOf(false) }
    var showBulkDeleteDialog by remember(topicId) { mutableStateOf(false) }
    var preserveOriginInMove by remember(topicId) { mutableStateOf(true) }
    var preserveOriginInNewTopic by remember(topicId) { mutableStateOf(true) }
    var avoidDuplicatesInMove by remember(topicId) { mutableStateOf(true) }
    var avoidDuplicatesInNewTopic by remember(topicId) { mutableStateOf(true) }
    var newTopicFromSelectionName by remember(topicId) { mutableStateOf("") }

    val listState = rememberLazyListState()

    if (topic == null) {
        Scaffold(
            containerColor = TestarioBackground,
            topBar = { TestarioTopBar(title = "Tema", onBack = onBack) }
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Tema no encontrado", color = TestarioText)
            }
        }
        return
    }

    LaunchedEffect(searchText) {
        if (searchText.isBlank()) {
            debouncedSearchText = ""
        } else {
            delay(180)
            debouncedSearchText = searchText
        }
    }

    val indexedQuestions = remember(topic.questions) {
        topic.questions.map { question -> question to question.buildSearchIndex() }
    }

    val filteredQuestions = remember(
        indexedQuestions,
        searchText,
        debouncedSearchText,
        searchScope,
        filterFlagged,
        selectedDifficultyFilter
    ) {
        var list = indexedQuestions.asSequence()

        if (filterFlagged) {
            list = list.filter { (question, _) -> question.flagged }
        }

        if (selectedDifficultyFilter != null) {
            list = if (selectedDifficultyFilter == -1) {
                list.filter { (question, _) -> question.difficulty == null }
            } else {
                list.filter { (question, _) -> question.difficulty == selectedDifficultyFilter }
            }
        }

        if (searchText.isNotBlank()) {
            if (debouncedSearchText != searchText) {
                emptyList()
            } else {
                val normalizedQuery = normalizeSearchText(debouncedSearchText)
                list.filter { (_, index) -> index.matches(normalizedQuery, searchScope) }
                    .map { (question, _) -> question }
                    .toList()
            }
        } else {
            list.map { (question, _) -> question }.toList()
        }
    }

    val targetTopics = remember(database.topics, topicId) {
        database.topics
            .filterNot { it.id == topicId }
            .sortedWith { a, b -> a.name.naturalCompareTo(b.name) }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedQuestionIds = emptySet()
        showMoveDialog = false
        showCreateFromSelectionDialog = false
        showBulkDeleteDialog = false
        newTopicFromSelectionName = ""
    }

    fun toggleSelection(questionId: String) {
        selectedQuestionIds = if (questionId in selectedQuestionIds) {
            selectedQuestionIds - questionId
        } else {
            selectedQuestionIds + questionId
        }
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            TestarioTopBar(
                title = if (selectionMode) {
                    "${selectedQuestionIds.size} seleccionadas"
                } else {
                    topic.name
                },
                onBack = {
                    if (selectionMode) exitSelectionMode() else onBack()
                },
                actions = {
                    if (!selectionMode) {
                        Box {
                            IconButton(onClick = { topicMenuExpanded = true }) {
                                Text(
                                    "⋮",
                                    color = TestarioText,
                                    style = MaterialTheme.typography.titleLarge
                                )
                            }
                            DropdownMenu(
                                expanded = topicMenuExpanded,
                                onDismissRequest = { topicMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Seleccionar preguntas") },
                                    enabled = topic.questions.isNotEmpty(),
                                    onClick = {
                                        topicMenuExpanded = false
                                        selectionMode = true
                                        selectedQuestionIds = emptySet()
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            TopicHeaderCard(
                questionCount = topic.questions.size,
                failureCount = topic.questions.count { it.stats.inFailures },
                answeredCount = topic.questions.sumOf { it.stats.timesAnswered }
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (selectionMode) {
                SelectionActions(
                    selectedCount = selectedQuestionIds.size,
                    visibleCount = filteredQuestions.size,
                    allVisibleSelected = filteredQuestions.isNotEmpty() &&
                            filteredQuestions.all { it.id in selectedQuestionIds },
                    canMove = selectedQuestionIds.isNotEmpty() && targetTopics.isNotEmpty(),
                    onSelectVisible = {
                        selectedQuestionIds = selectedQuestionIds + filteredQuestions.map { it.id }
                    },
                    onClearSelection = { selectedQuestionIds = emptySet() },
                    onMove = { showMoveDialog = true },
                    onCreateTopic = {
                        newTopicFromSelectionName = ""
                        showCreateFromSelectionDialog = true
                    },
                    onDelete = { showBulkDeleteDialog = true }
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAddQuestion,
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("Añadir pregunta") }

                    OutlinedButton(
                        onClick = { onStartTest(topicId) },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, TestarioPrimary)
                    ) { Text("Hacer Test") }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text("Buscar pregunta") },
                    placeholder = { Text("Enunciado, respuesta o explicación") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .height(62.dp),
                    shape = RoundedCornerShape(18.dp),
                    trailingIcon = {
                        if (searchText.isNotBlank()) {
                            IconButton(onClick = { searchText = "" }) { Text("✕") }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TestarioText,
                        unfocusedTextColor = TestarioText,
                        focusedBorderColor = TestarioPrimary,
                        unfocusedBorderColor = TestarioSurfaceLight,
                        focusedContainerColor = TestarioSurface,
                        unfocusedContainerColor = TestarioSurface
                    )
                )

                Box {
                    IconButton(
                        onClick = { filterMenuExpanded = true },
                        modifier = Modifier
                            .size(62.dp)
                            .then(
                                if (filterFlagged || selectedDifficultyFilter != null || searchScope != QuestionSearchScope.ALL) {
                                    Modifier.padding(2.dp)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Card(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (
                                    filterFlagged || selectedDifficultyFilter != null || searchScope != QuestionSearchScope.ALL
                                ) {
                                    TestarioPrimaryDark
                                } else {
                                    TestarioSurface
                                }
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (filterFlagged || selectedDifficultyFilter != null || searchScope != QuestionSearchScope.ALL) {
                                    TestarioPrimary
                                } else {
                                    TestarioSurfaceLight
                                }
                            )
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    "≡",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TestarioText
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = filterMenuExpanded,
                        onDismissRequest = { filterMenuExpanded = false },
                        modifier = Modifier
                            .width(220.dp)
                            .background(TestarioSurface)
                    ) {
                        Text(
                            "Filtrar por",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TestarioMutedText
                        )
                        Text(
                            "Buscar en",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TestarioMutedText
                        )
                        QuestionSearchScope.values().forEach { scope ->
                            DropdownMenuItem(
                                text = { Text(scope.label) },
                                leadingIcon = {
                                    RadioButton(selected = searchScope == scope, onClick = null)
                                },
                                onClick = {
                                    searchScope = scope
                                    filterMenuExpanded = false
                                }
                            )
                        }
                        Divider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = TestarioSurfaceLight
                        )
                        DropdownMenuItem(
                            text = { Text("Solo marcadas 🚩") },
                            trailingIcon = {
                                Checkbox(checked = filterFlagged, onCheckedChange = null)
                            },
                            onClick = { filterFlagged = !filterFlagged }
                        )
                        Divider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = TestarioSurfaceLight
                        )
                        Text(
                            "Dificultad",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TestarioMutedText
                        )

                        val difficulties = listOf(
                            Triple("Todas", null, TestarioText),
                            Triple("Nada", -1, TestarioMutedText),
                            Triple("Fácil", 1, TestarioGreen),
                            Triple("Media", 2, TestarioYellow),
                            Triple("Difícil", 3, TestarioRed)
                        )

                        difficulties.forEach { (label, value, color) ->
                            DropdownMenuItem(
                                text = { Text(label, color = color) },
                                leadingIcon = {
                                    RadioButton(
                                        selected = selectedDifficultyFilter == value,
                                        onClick = null
                                    )
                                },
                                onClick = {
                                    selectedDifficultyFilter = value
                                    filterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            if (searchText.isNotBlank() || filterFlagged || selectedDifficultyFilter != null || searchScope != QuestionSearchScope.ALL) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (searchText.isNotBlank() && debouncedSearchText != searchText) {
                            "Buscando…"
                        } else {
                            "${filteredQuestions.size} resultados"
                        },
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            searchText = ""
                            filterFlagged = false
                            selectedDifficultyFilter = null
                            searchScope = QuestionSearchScope.ALL
                        }
                    ) {
                        Text("Limpiar filtros", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (topic.questions.isEmpty()) {
                EmptyTopicContent()
            } else if (filteredQuestions.isEmpty()) {
                EmptySearchContent()
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items = filteredQuestions, key = { it.id }) { question ->
                            QuestionCard(
                                question = question,
                                selectionMode = selectionMode,
                                selected = question.id in selectedQuestionIds,
                                onToggleSelection = { toggleSelection(question.id) },
                                onEnterSelection = {
                                    selectionMode = true
                                    selectedQuestionIds = setOf(question.id)
                                },
                                onEdit = { onEditQuestion(question.id) },
                                onDeleteConfirmed = {
                                    libraryViewModel.deleteQuestion(topicId, question.id)
                                },
                                onDuplicate = {
                                    libraryViewModel.duplicateQuestion(topicId, question.id)
                                },
                                onRemoveFailure = {
                                    statsViewModel.removeFromFailures(question.id)
                                }
                            )
                        }
                    }
                    FastScrollbar(
                        listState = listState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Mover preguntas") },
            text = {
                Column {
                    Text(
                        text = "Elige el tema de destino para ${selectedQuestionIds.size} pregunta(s).",
                        color = TestarioMutedText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = preserveOriginInMove,
                            onCheckedChange = { preserveOriginInMove = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conservar tema de procedencia", color = TestarioText)
                            Text(
                                "Se mostrará como “Tema actual | Tema de origen”.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = avoidDuplicatesInMove,
                            onCheckedChange = { avoidDuplicatesInMove = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evitar preguntas repetidas", color = TestarioText)
                            Text(
                                "Si ya existe una pregunta equivalente en el destino, se quedará en este tema y no se duplicará.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(items = targetTopics, key = { it.id }) { targetTopic ->
                            TextButton(
                                onClick = {
                                    libraryViewModel.moveQuestions(
                                        sourceTopicId = topicId,
                                        targetTopicId = targetTopic.id,
                                        questionIds = selectedQuestionIds,
                                        preserveOrigin = preserveOriginInMove,
                                        avoidDuplicates = avoidDuplicatesInMove
                                    )
                                    exitSelectionMode()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = targetTopic.name,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = TestarioText
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreateFromSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFromSelectionDialog = false },
            title = { Text("Crear tema con las seleccionadas") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Se moverán ${selectedQuestionIds.size} pregunta(s) al nuevo tema.",
                        color = TestarioMutedText
                    )
                    OutlinedTextField(
                        value = newTopicFromSelectionName,
                        onValueChange = { newTopicFromSelectionName = it },
                        label = { Text("Nombre del nuevo tema") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = preserveOriginInNewTopic,
                            onCheckedChange = { preserveOriginInNewTopic = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Conservar tema de procedencia", color = TestarioText)
                            Text(
                                "Ejemplo: ${newTopicFromSelectionName.ifBlank { "Funciones" }} | ${topic.name}",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = avoidDuplicatesInNewTopic,
                            onCheckedChange = { avoidDuplicatesInNewTopic = it }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Evitar preguntas repetidas", color = TestarioText)
                            Text(
                                "Si la selección contiene preguntas equivalentes, el nuevo tema recibirá una sola copia.",
                                color = TestarioMutedText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = newTopicFromSelectionName.isNotBlank() && selectedQuestionIds.isNotEmpty(),
                    onClick = {
                        libraryViewModel.createTopicFromQuestions(
                            newTopicName = newTopicFromSelectionName,
                            questionIds = selectedQuestionIds,
                            preserveOrigin = preserveOriginInNewTopic,
                            avoidDuplicates = avoidDuplicatesInNewTopic
                        )
                        exitSelectionMode()
                    }
                ) { Text("Crear y mover") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFromSelectionDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Eliminar preguntas") },
            text = {
                Text(
                    "¿Seguro que quieres eliminar definitivamente " +
                            "${selectedQuestionIds.size} pregunta(s)? Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        libraryViewModel.deleteQuestions(topicId, selectedQuestionIds)
                        exitSelectionMode()
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SelectionActions(
    selectedCount: Int,
    visibleCount: Int,
    allVisibleSelected: Boolean,
    canMove: Boolean,
    onSelectVisible: () -> Unit,
    onClearSelection: () -> Unit,
    onMove: () -> Unit,
    onCreateTopic: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
        border = BorderStroke(1.dp, TestarioPrimary)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$selectedCount seleccionadas",
                    color = TestarioText,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    onClick = onSelectVisible,
                    enabled = visibleCount > 0 && !allVisibleSelected
                ) {
                    Text("Seleccionar visibles")
                }

                TextButton(
                    onClick = onClearSelection,
                    enabled = selectedCount > 0
                ) {
                    Text("Limpiar")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onMove,
                    enabled = canMove,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Mover")
                }

                OutlinedButton(
                    onClick = onCreateTopic,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TestarioPrimary)
                ) {
                    Text("Crear tema")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onDelete,
                enabled = selectedCount > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TestarioRed)
            ) {
                Text("Eliminar", color = if (selectedCount > 0) TestarioRed else TestarioMutedText)
            }
        }
    }
}

@Composable
private fun TopicHeaderCard(questionCount: Int, failureCount: Int, answeredCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
    ) {
        Row(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TopicMiniStat("Preguntas", questionCount.toString(), Modifier.weight(1f))
            TopicMiniStat("Fallos", failureCount.toString(), Modifier.weight(1f))
            TopicMiniStat("Respondidas", answeredCount.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun TopicMiniStat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TestarioText
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TestarioMutedText
            )
        }
    }
}

@Composable
private fun EmptyTopicContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Todavía no hay preguntas. Pulsa “Añadir pregunta” para empezar.",
            color = TestarioMutedText
        )
    }
}

@Composable
private fun EmptySearchContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "No hay resultados para los filtros seleccionados.",
            color = TestarioMutedText
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuestionCard(
    question: Question,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
    onEnterSelection: () -> Unit,
    onEdit: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDuplicate: () -> Unit,
    onRemoveFailure: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val difficultyText = when (question.difficulty) {
        null -> "Sin dificultad"
        1 -> "Fácil"
        2 -> "Media"
        3 -> "Difícil"
        else -> "Sin dificultad"
    }
    val difficultyColor = when (question.difficulty) {
        1 -> TestarioGreen
        2 -> TestarioYellow
        3 -> TestarioRed
        else -> TestarioMutedText
    }

    val cardBorder = when {
        selected -> BorderStroke(2.dp, TestarioPrimary)
        question.stats.inFailures -> BorderStroke(1.dp, TestarioRed)
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (selectionMode) onToggleSelection()
                },
                onLongClick = {
                    if (!selectionMode) onEnterSelection()
                }
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) TestarioPrimaryDark else TestarioSurface
        ),
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelection() }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = question.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = TestarioText
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${question.answers.size} respuestas",
                        color = TestarioMutedText
                    )
                    if (!question.originTopicName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Origen: ${question.originTopicName}",
                            color = TestarioPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (!selectionMode) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Text(
                                "⋮",
                                color = TestarioText,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Editar") },
                                onClick = {
                                    expanded = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duplicar") },
                                onClick = {
                                    expanded = false
                                    onDuplicate()
                                }
                            )
                            if (question.stats.inFailures) {
                                DropdownMenuItem(
                                    text = { Text("Quitar de fallos") },
                                    onClick = {
                                        expanded = false
                                        onRemoveFailure()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    expanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoPill(difficultyText, difficultyColor)
                if (question.flagged) InfoPill("Marcada 🚩", TestarioPrimary)
                if (question.stats.inFailures) InfoPill("En fallos", TestarioRed)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Divider(color = TestarioSurfaceLight)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Respondida ${question.stats.timesAnswered} veces · " +
                        "${question.stats.timesCorrect} aciertos · " +
                        "${question.stats.timesWrong} fallos",
                color = TestarioMutedText,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Eliminar pregunta") },
            text = {
                Text("¿Seguro que quieres eliminar esta pregunta? Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConfirmed()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun InfoPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = TestarioSurfaceLight,
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
