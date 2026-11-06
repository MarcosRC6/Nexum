package com.example.testario.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.planario.app.PlanarioDependencies
import com.example.testario.data.model.GroupQuestionAllocation
import com.example.testario.data.model.SavedFailureScope
import com.example.testario.data.model.SavedTestScope
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestGroupAllocation
import com.example.testario.data.model.TestMode
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.model.Topic
import com.example.testario.data.model.TopicGroup
import com.example.testario.domain.GroupHierarchy
import com.example.testario.domain.SmartSelectionEngine
import com.example.testario.ui.theme.FourItemDropdown
import com.example.testario.ui.theme.TestarioNeonSurface
import com.example.testario.ui.theme.TestarioNeonSelected
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
import com.example.testario.viewmodel.TestSessionViewModel
import kotlin.math.roundToInt

enum class TestScope { GLOBAL, TOPIC, GROUPS, FAILURES }
private enum class FailureScope { GLOBAL, TOPIC }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConfigScreen(
    libraryViewModel: LibraryViewModel,
    sessionViewModel: TestSessionViewModel,
    preselectedTopicId: String? = null,
    preselectedTemplateId: String? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onPlan: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onStart: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val studySettings by PlanarioDependencies.studyRepository.settings.collectAsState()
    val selectionEngine = remember { SmartSelectionEngine() }

    var mode by remember { mutableStateOf(TestMode.TRAINING) }
    var scope by remember { mutableStateOf(if (preselectedTopicId != null) TestScope.TOPIC else TestScope.GLOBAL) }
    var failureScope by remember { mutableStateOf(FailureScope.GLOBAL) }

    var selectedTopicIds by remember {
        mutableStateOf<Set<String>>(if (preselectedTopicId != null) setOf(preselectedTopicId) else emptySet())
    }
    var selectedFailureTopicIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var groupAllocations by remember { mutableStateOf<List<GroupQuestionAllocation>>(emptyList()) }

    var questionCountText by remember { mutableStateOf("10") }
    var difficulty by remember { mutableStateOf<Int?>(null) }
    var pointsCorrectText by remember { mutableStateOf("1") }
    var penaltyText by remember { mutableStateOf("-0.25") }
    var timerEnabled by remember { mutableStateOf(false) }
    var timerMinutesText by remember { mutableStateOf("60") }
    var easyPercent by remember { mutableStateOf(33) }
    var mediumPercent by remember { mutableStateOf(33) }
    var hardPercent by remember { mutableStateOf(34) }
    var difficultyFilterEnabled by remember { mutableStateOf(false) }

    var selectedTemplateId by remember { mutableStateOf<String?>(null) }
    var initialTemplateApplied by remember(preselectedTemplateId) { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PlanarioDependencies.studyRepository.setNotificationsEnabled(granted)
        PlanarioDependencies.schedulePlanarioNotifications()
        infoMessage = if (granted) {
            "Notificaciones activadas."
        } else {
            "Android no concedió permiso para mostrar notificaciones."
        }
    }

    LaunchedEffect(database.topics, database.groups) {
        val validTopicIds = database.topics.mapTo(hashSetOf()) { it.id }
        val validGroupIds = database.groups.mapTo(hashSetOf()) { it.id }

        selectedTopicIds = selectedTopicIds.intersect(validTopicIds)
        if (selectedTopicIds.isEmpty() && database.topics.isNotEmpty() && preselectedTopicId == null) {
            selectedTopicIds = setOf(database.topics.first().id)
        }

        selectedFailureTopicIds = selectedFailureTopicIds.intersect(validTopicIds)
        if (selectedFailureTopicIds.isEmpty() && database.topics.isNotEmpty()) {
            selectedFailureTopicIds = setOf(database.topics.first().id)
        }

        groupAllocations = groupAllocations
            .filter { it.groupId in validGroupIds }
            .distinctBy { it.groupId }
    }

    val sortedTopicsForSelect = remember(database.topics) {
        database.topics.sortedWith(Comparator { t1, t2 -> t1.name.naturalCompareTo(t2.name) })
    }
    val sortedGroupsForSelect = remember(database.groups) {
        database.groups.sortedBy { GroupHierarchy.path(database.groups, it.id).lowercase() }
    }
    val sortedTemplates = remember(database.testTemplates) {
        database.testTemplates.sortedWith(Comparator { a, b -> a.name.naturalCompareTo(b.name) })
    }

    val selectedGroupTopicIds = remember(database.groups, groupAllocations) {
        groupAllocations.flatMapTo(linkedSetOf()) { allocation ->
            GroupHierarchy.resolveTopicIds(database.groups, allocation.groupId)
        }
    }

    val selectedTopics = when (scope) {
        TestScope.GLOBAL -> database.topics
        TestScope.TOPIC -> database.topics.filter { it.id in selectedTopicIds }
        TestScope.GROUPS -> database.topics.filter { it.id in selectedGroupTopicIds }
        TestScope.FAILURES -> when (failureScope) {
            FailureScope.GLOBAL -> database.topics
            FailureScope.TOPIC -> database.topics.filter { it.id in selectedFailureTopicIds }
        }
    }

    val baseQuestions = selectedTopics.flatMap { it.questions }.distinctBy { it.id }
    val availableQuestions = when (scope) {
        TestScope.FAILURES -> baseQuestions.filter { it.stats.inFailures }
        else -> baseQuestions
    }.filter { question -> difficulty == null || question.difficulty == difficulty }

    val availableByTopic = remember(selectedTopics, scope, difficulty) {
        selectedTopics.associate { topic ->
            topic.id to topic.questions
                .asSequence()
                .filter { scope != TestScope.FAILURES || it.stats.inFailures }
                .filter { difficulty == null || it.difficulty == difficulty }
                .distinctBy { it.id }
                .toList()
        }
    }

    val runtimeGroupAllocations = remember(database.groups, groupAllocations) {
        groupAllocations.map { allocation ->
            TestGroupAllocation(
                groupId = allocation.groupId,
                topicIds = GroupHierarchy.resolveTopicIds(database.groups, allocation.groupId).toList(),
                questionCount = allocation.questionCount
            )
        }
    }

    val assignedGroupQuestions = groupAllocations.sumOf { it.questionCount.coerceAtLeast(0) }
    val requestedCount = questionCountText.toIntOrNull() ?: 0
    val groupedFeasible = remember(availableByTopic, runtimeGroupAllocations) {
        runtimeGroupAllocations.isNotEmpty() &&
            runtimeGroupAllocations.all { it.questionCount > 0 && it.topicIds.isNotEmpty() } &&
            selectionEngine.canFulfillGroupedSelection(availableByTopic, runtimeGroupAllocations)
    }
    val difficultyPercentTotal = easyPercent + mediumPercent + hardPercent

    fun loadTemplate(template: TestTemplate) {
        val validTopicIds = database.topics.mapTo(hashSetOf()) { it.id }
        val validGroupIds = database.groups.mapTo(hashSetOf()) { it.id }

        mode = template.mode
        scope = when (template.scope) {
            SavedTestScope.GLOBAL -> TestScope.GLOBAL
            SavedTestScope.TOPIC -> TestScope.TOPIC
            SavedTestScope.GROUPS -> TestScope.GROUPS
            SavedTestScope.FAILURES -> TestScope.FAILURES
        }
        failureScope = when (template.failureScope) {
            SavedFailureScope.GLOBAL -> FailureScope.GLOBAL
            SavedFailureScope.TOPIC -> FailureScope.TOPIC
        }
        selectedTopicIds = template.selectedTopicIds.filter { it in validTopicIds }.toSet()
        selectedFailureTopicIds = template.selectedFailureTopicIds.filter { it in validTopicIds }.toSet()
        groupAllocations = template.groupAllocations
            .filter { it.groupId in validGroupIds }
            .distinctBy { it.groupId }
        questionCountText = template.questionCount.coerceAtLeast(0).toString()
        difficulty = if (template.difficultyFilterEnabled) null else template.difficulty
        pointsCorrectText = template.pointsCorrect.toString()
        penaltyText = template.penaltyWrong.toString()
        timerEnabled = template.timerEnabled
        timerMinutesText = template.timerMinutes.toString()
        easyPercent = template.easyPercent
        mediumPercent = template.mediumPercent
        hardPercent = template.hardPercent
        difficultyFilterEnabled = template.difficultyFilterEnabled
        selectedTemplateId = template.id
        error = null
        infoMessage = if (template.scope == SavedTestScope.GROUPS && groupAllocations.size != template.groupAllocations.size) {
            "La plantilla se cargó, pero se omitieron grupos que ya no existen."
        } else {
            "Plantilla “${template.name}” cargada."
        }
    }

    LaunchedEffect(preselectedTemplateId, database.testTemplates) {
        if (!initialTemplateApplied && preselectedTemplateId != null) {
            val template = database.testTemplates.firstOrNull { it.id == preselectedTemplateId }
            if (template != null) {
                loadTemplate(template)
                initialTemplateApplied = true
            }
        }
    }

    fun currentTemplate(name: String): TestTemplate = TestTemplate(
        name = name.trim(),
        mode = mode,
        scope = when (scope) {
            TestScope.GLOBAL -> SavedTestScope.GLOBAL
            TestScope.TOPIC -> SavedTestScope.TOPIC
            TestScope.GROUPS -> SavedTestScope.GROUPS
            TestScope.FAILURES -> SavedTestScope.FAILURES
        },
        selectedTopicIds = selectedTopicIds.toList(),
        failureScope = when (failureScope) {
            FailureScope.GLOBAL -> SavedFailureScope.GLOBAL
            FailureScope.TOPIC -> SavedFailureScope.TOPIC
        },
        selectedFailureTopicIds = selectedFailureTopicIds.toList(),
        groupAllocations = groupAllocations,
        questionCount = requestedCount,
        difficulty = difficulty,
        pointsCorrect = pointsCorrectText.replace(",", ".").toDoubleOrNull() ?: 1.0,
        penaltyWrong = penaltyText.replace(",", ".").toDoubleOrNull() ?: -0.25,
        timerEnabled = timerEnabled,
        timerMinutes = timerMinutesText.toIntOrNull() ?: 60,
        easyPercent = easyPercent,
        mediumPercent = mediumPercent,
        hardPercent = hardPercent,
        difficultyFilterEnabled = difficultyFilterEnabled
    )

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            TestarioTopBar(
                title = "Configurar test",
                onBack = onBack,
                actions = {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            if (studySettings.notificationsEnabled) {
                                PlanarioDependencies.studyRepository.setNotificationsEnabled(false)
                                PlanarioDependencies.schedulePlanarioNotifications()
                                infoMessage = "Notificaciones desactivadas."
                            } else {
                                val needsPermission =
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) != PackageManager.PERMISSION_GRANTED

                                if (needsPermission) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    PlanarioDependencies.studyRepository.setNotificationsEnabled(true)
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                    infoMessage = "Notificaciones activadas."
                                }
                            }
                        }
                    ) {
                        EstudarioIcon(
                            EstudarioIconKind.NOTIFICATIONS,
                            if (studySettings.notificationsEnabled) TestarioPrimary else TestarioMutedText
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = onSettings) {
                        EstudarioIcon(EstudarioIconKind.SETTINGS, TestarioText)
                    }
                }
            )
        },
        bottomBar = {
            MainBottomNavigation(
                selected = MainSection.TEST,
                onHome = onHome,
                onLibrary = onLibrary,
                onTest = {},
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
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionTitle("Plantillas")
            SettingsLikeCard {
                Text("Configuraciones guardadas", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Guarda una configuración completa para reutilizarla después con un toque.",
                    color = TestarioMutedText,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                TemplateDropdown(
                    templates = sortedTemplates,
                    selectedTemplateId = selectedTemplateId,
                    onTemplateSelected = { loadTemplate(it) }
                )
                if (selectedTemplateId != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        val id = selectedTemplateId
                        if (id != null) libraryViewModel.deleteTestTemplate(id)
                        selectedTemplateId = null
                        infoMessage = "Plantilla eliminada."
                    }) { Text("Eliminar plantilla seleccionada", color = TestarioRed) }
                }
            }

            SectionTitle("Modo")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                SelectableBigCard("Entrenamiento", "Corrige al momento", mode == TestMode.TRAINING, Modifier.weight(1f)) { mode = TestMode.TRAINING; error = null }
                SelectableBigCard("Examen", "Corrige al final", mode == TestMode.EXAM, Modifier.weight(1f)) { mode = TestMode.EXAM; error = null }
            }

            SectionTitle("Número de preguntas")
            OutlinedTextField(
                value = questionCountText,
                onValueChange = { questionCountText = it.filter(Char::isDigit); error = null },
                label = { Text("Cantidad") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = testarioTextFieldColors()
            )
            if (scope == TestScope.GROUPS) {
                InfoCard("Asignadas por grupos", "$assignedGroupQuestions / $requestedCount")
            }


            SectionTitle("Origen de preguntas")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactScopeButton("Todos", scope == TestScope.GLOBAL, Modifier.weight(1f)) { scope = TestScope.GLOBAL; error = null }
                CompactScopeButton("Temas", scope == TestScope.TOPIC, Modifier.weight(1f)) { scope = TestScope.TOPIC; error = null }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                CompactScopeButton("Grupos", scope == TestScope.GROUPS, Modifier.weight(1f)) {
                    scope = TestScope.GROUPS
                    if (groupAllocations.isEmpty() && sortedGroupsForSelect.isNotEmpty()) {
                        groupAllocations = listOf(
                            GroupQuestionAllocation(
                                sortedGroupsForSelect.first().id,
                                (questionCountText.toIntOrNull() ?: 10).coerceAtLeast(1)
                            )
                        )
                    }
                    error = null
                }
                CompactScopeButton("Fallos", scope == TestScope.FAILURES, Modifier.weight(1f)) { scope = TestScope.FAILURES; error = null }
            }

            when (scope) {
                TestScope.GLOBAL -> InfoCard("Selección", "Todos los temas")
                TestScope.TOPIC -> MultiSelectTopicDropdown("Temas", sortedTopicsForSelect, selectedTopicIds) { toggledId ->
                    selectedTopicIds = if (toggledId in selectedTopicIds) selectedTopicIds - toggledId else selectedTopicIds + toggledId
                    error = null
                }
                TestScope.GROUPS -> {
                    GroupAllocationEditor(
                        groups = sortedGroupsForSelect,
                        allGroups = database.groups,
                        allocations = groupAllocations,
                        availableByTopic = availableByTopic,
                        requestedCount = requestedCount,
                        onAllocationsChanged = { groupAllocations = it; error = null }
                    )
                }
                TestScope.FAILURES -> {
                    SettingsLikeCard {
                        Text("Fallos", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            CompactScopeButton("Globales", failureScope == FailureScope.GLOBAL, Modifier.weight(1f)) { failureScope = FailureScope.GLOBAL; error = null }
                            CompactScopeButton("Por tema", failureScope == FailureScope.TOPIC, Modifier.weight(1f)) { failureScope = FailureScope.TOPIC; error = null }
                        }
                        if (failureScope == FailureScope.TOPIC) {
                            Spacer(Modifier.height(12.dp))
                            MultiSelectTopicDropdown("Temas con fallos", sortedTopicsForSelect, selectedFailureTopicIds) { toggledId ->
                                selectedFailureTopicIds = if (toggledId in selectedFailureTopicIds) selectedFailureTopicIds - toggledId else selectedFailureTopicIds + toggledId
                                error = null
                            }
                        }
                    }
                }
            }

            SectionTitle("Baremo")
            SettingsLikeCard {
                Text("Puntuación", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                Spacer(Modifier.height(8.dp))
                Text("La nota final se ajusta automáticamente a escala 0-10 según el número de preguntas.", color = TestarioMutedText, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pointsCorrectText,
                    onValueChange = { pointsCorrectText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }; error = null },
                    label = { Text("Puntos por acierto") },
                    supportingText = { Text("Base: 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = testarioTextFieldColors()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = penaltyText,
                    onValueChange = { penaltyText = it.filter { c -> c.isDigit() || c == '-' || c == '.' || c == ',' }; error = null },
                    label = { Text("Penalización por fallo") },
                    supportingText = { Text("Base: -0.25") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = testarioTextFieldColors()
                )
            }

            if (mode == TestMode.EXAM && scope != TestScope.FAILURES) {
                SettingsLikeCard {
                    Text("Temporizador modo examen", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("Activar temporizador", color = TestarioText)
                        Switch(checked = timerEnabled, onCheckedChange = { timerEnabled = it; error = null }, colors = SwitchDefaults.colors(checkedThumbColor = TestarioPrimary))
                    }
                    if (timerEnabled) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = timerMinutesText,
                            onValueChange = { timerMinutesText = it.filter(Char::isDigit); error = null },
                            label = { Text("Minutos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = testarioTextFieldColors()
                        )
                    }
                }
            }

            SectionTitle("Dificultad")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                DifficultyChip("Todas", difficulty == null, TestarioPrimary, Modifier.weight(1f)) {
                    difficulty = null
                    error = null
                }
                DifficultyChip("Fácil", difficulty == 1, TestarioGreen, Modifier.weight(1f)) {
                    difficulty = 1
                    difficultyFilterEnabled = false
                    error = null
                }
                DifficultyChip("Medio", difficulty == 2, TestarioYellow, Modifier.weight(1f)) {
                    difficulty = 2
                    difficultyFilterEnabled = false
                    error = null
                }
                DifficultyChip("Difícil", difficulty == 3, TestarioRed, Modifier.weight(1f)) {
                    difficulty = 3
                    difficultyFilterEnabled = false
                    error = null
                }
            }

            SettingsLikeCard {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Repartir dificultad", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                        Text(
                            if (difficultyFilterEnabled) "Equilibra el porcentaje de dificultad" else "Preguntas 100% aleatorias",
                            style = MaterialTheme.typography.bodySmall,
                            color = TestarioMutedText
                        )
                        if (difficultyFilterEnabled) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Activar cuando tengas un buen mazo de preguntas asignadas a dificultades, si no se irán repitiendo",
                                style = MaterialTheme.typography.labelSmall,
                                color = TestarioRed
                            )
                        }
                    }
                    Switch(
                        checked = difficultyFilterEnabled,
                        onCheckedChange = { enabled ->
                            difficultyFilterEnabled = enabled
                            if (enabled) difficulty = null
                            error = null
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TestarioPrimary,
                            uncheckedThumbColor = TestarioMutedText,
                            uncheckedTrackColor = TestarioSurfaceLight
                        )
                    )
                }
                AnimatedVisibility(difficultyFilterEnabled) {
                    Column {
                        Spacer(Modifier.height(16.dp))
                        SettingsTestCard(
                            easyPercent,
                            mediumPercent,
                            hardPercent,
                            onEasyChange = { easyPercent = it; error = null },
                            onMediumChange = { mediumPercent = it; error = null },
                            onHardChange = { hardPercent = it; error = null }
                        )
                    }
                }
            }

            InfoCard("Preguntas disponibles", "${availableQuestions.size}")
            if (difficultyFilterEnabled) InfoCard("Reparto dificultad", "$difficultyPercentTotal%")
            if (infoMessage != null) Text(infoMessage.orEmpty(), color = TestarioPrimary, modifier = Modifier.padding(horizontal = 4.dp))
            if (error != null) Text(error.orEmpty(), color = TestarioRed, modifier = Modifier.padding(horizontal = 4.dp))

            OutlinedButton(
                onClick = {
                    val parsedPoints = pointsCorrectText.replace(",", ".").toDoubleOrNull()
                    val parsedPenalty = penaltyText.replace(",", ".").toDoubleOrNull()
                    val parsedTimer = timerMinutesText.toIntOrNull() ?: 0
                    val groupError = when {
                        scope != TestScope.GROUPS -> null
                        groupAllocations.isEmpty() -> "Añade al menos un grupo antes de guardar la plantilla."
                        groupAllocations.any { it.questionCount <= 0 } -> "Todas las cantidades por grupo deben ser mayores que 0."
                        assignedGroupQuestions != requestedCount -> "Las cantidades de los grupos deben sumar exactamente $requestedCount."
                        runtimeGroupAllocations.any { it.topicIds.isEmpty() } -> "Uno de los grupos seleccionados no contiene ningún tema."
                        !groupedFeasible -> "Las cuotas no pueden cumplirse sin repetir preguntas con la selección actual."
                        else -> null
                    }
                    error = when {
                        requestedCount <= 0 -> "El número de preguntas debe ser mayor que 0."
                        scope == TestScope.TOPIC && selectedTopicIds.isEmpty() -> "Selecciona al menos un tema."
                        scope == TestScope.FAILURES && failureScope == FailureScope.TOPIC && selectedFailureTopicIds.isEmpty() -> "Selecciona al menos un tema para los fallos."
                        parsedPoints == null || parsedPoints <= 0 -> "Los puntos por acierto no son válidos."
                        parsedPenalty == null || parsedPenalty > 0 -> "La penalización debe ser 0 o negativa."
                        mode == TestMode.EXAM && scope != TestScope.FAILURES && timerEnabled && parsedTimer <= 0 -> "El temporizador debe ser mayor que 0 minutos."
                        difficultyFilterEnabled && difficultyPercentTotal <= 0 -> "El reparto de dificultad no puede ser 0% en todo."
                        groupError != null -> groupError
                        else -> null
                    }
                    if (error == null) {
                        templateName = selectedTemplateId?.let { id -> database.testTemplates.firstOrNull { it.id == id }?.name }.orEmpty()
                        showSaveTemplateDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TestarioPrimary)
            ) { Text("Guardar como plantilla") }

            Button(
                onClick = {
                    val pointsCorrect = pointsCorrectText.replace(",", ".").toDoubleOrNull()
                    val penaltyWrong = penaltyText.replace(",", ".").toDoubleOrNull()
                    val timerMinutes = timerMinutesText.toIntOrNull() ?: 0

                    when {
                        database.topics.isEmpty() -> error = "Primero crea al menos un tema."
                        scope == TestScope.TOPIC && selectedTopicIds.isEmpty() -> error = "Selecciona al menos un tema."
                        scope == TestScope.GROUPS && database.groups.isEmpty() -> error = "Primero crea al menos un grupo."
                        scope == TestScope.GROUPS && groupAllocations.isEmpty() -> error = "Añade al menos un grupo al reparto."
                        scope == TestScope.GROUPS && groupAllocations.any { it.questionCount <= 0 } -> error = "Todas las cantidades por grupo deben ser mayores que 0."
                        scope == TestScope.GROUPS && assignedGroupQuestions != requestedCount -> error = "El reparto por grupos debe sumar exactamente $requestedCount preguntas."
                        scope == TestScope.GROUPS && runtimeGroupAllocations.any { it.topicIds.isEmpty() } -> error = "Uno de los grupos seleccionados no contiene ningún tema."
                        scope == TestScope.GROUPS && !groupedFeasible -> error = "No se pueden cumplir simultáneamente esas cuotas sin repetir preguntas. Reduce alguna cantidad o revisa los grupos solapados."
                        scope == TestScope.FAILURES && failureScope == FailureScope.TOPIC && selectedFailureTopicIds.isEmpty() -> error = "Selecciona al menos un tema para los fallos."
                        requestedCount <= 0 -> error = "El número de preguntas debe ser mayor que 0."
                        pointsCorrect == null -> error = "Los puntos por acierto no son válidos."
                        pointsCorrect <= 0 -> error = "Los puntos por acierto deben ser mayores que 0."
                        penaltyWrong == null -> error = "La penalización no es válida."
                        penaltyWrong > 0 -> error = "La penalización debe ser 0 o negativa."
                        mode == TestMode.EXAM && scope != TestScope.FAILURES && timerEnabled && timerMinutes <= 0 -> error = "El temporizador debe ser mayor que 0 minutos."
                        availableQuestions.isEmpty() -> error = "No hay preguntas disponibles para esa selección."
                        scope != TestScope.GROUPS && requestedCount > availableQuestions.size -> error = "Solo hay ${availableQuestions.size} preguntas disponibles; reduce la cantidad."
                        difficultyFilterEnabled && difficultyPercentTotal <= 0 -> error = "El reparto de dificultad no puede ser 0% en todo."
                        else -> {
                            val finalMode = if (scope == TestScope.FAILURES) TestMode.FAILURES else mode
                            val config = TestConfig(
                                mode = finalMode,
                                topicIds = selectedTopics.map { it.id },
                                questionCount = requestedCount,
                                difficulties = if (difficulty == null) emptySet() else setOf(difficulty),
                                randomOrder = true,
                                pointsCorrect = pointsCorrect,
                                penaltyWrong = penaltyWrong,
                                timerEnabled = timerEnabled && finalMode == TestMode.EXAM,
                                timerMinutes = timerMinutes,
                                easyPercent = if (difficultyFilterEnabled) easyPercent else 0,
                                mediumPercent = if (difficultyFilterEnabled) mediumPercent else 0,
                                hardPercent = if (difficultyFilterEnabled) hardPercent else 0,
                                difficultyFilterEnabled = difficultyFilterEnabled,
                                groupAllocations = if (scope == TestScope.GROUPS) runtimeGroupAllocations else emptyList()
                            )
                            sessionViewModel.start(selectedTopics, config)
                            onStart()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp)
            ) { Text("Empezar Test", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showSaveTemplateDialog) {
        AlertDialog(
            onDismissRequest = { showSaveTemplateDialog = false },
            title = { Text("Guardar plantilla") },
            text = {
                Column {
                    Text(
                        "Si ya existe una plantilla con ese nombre, se actualizará en lugar de crear un duplicado.",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = templateName,
                        onValueChange = { templateName = it },
                        label = { Text("Nombre de la plantilla") },
                        singleLine = true,
                        colors = testarioTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (templateName.isBlank()) {
                        error = "Escribe un nombre para la plantilla."
                    } else {
                        val id = libraryViewModel.saveTestTemplate(currentTemplate(templateName))
                        if (id != null) {
                            selectedTemplateId = id
                            infoMessage = "Plantilla “${templateName.trim()}” guardada."
                            error = null
                        } else {
                            error = "No se pudo guardar la plantilla."
                        }
                    }
                    showSaveTemplateDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplateDialog = false }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateDropdown(
    templates: List<TestTemplate>,
    selectedTemplateId: String?,
    onTemplateSelected: (TestTemplate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val display = templates.firstOrNull { it.id == selectedTemplateId }?.name ?: "Seleccionar plantilla"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = testarioTextFieldColors(),
            trailingIcon = { Text("▼", color = TestarioMutedText) }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = TestarioSurface) {
            if (templates.isEmpty()) {
                DropdownMenuItem(text = { Text("No hay plantillas guardadas", color = TestarioMutedText) }, onClick = { expanded = false }, enabled = false)
            } else {
                templates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.name, color = TestarioText) },
                        onClick = { expanded = false; onTemplateSelected(template) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupAllocationEditor(
    groups: List<TopicGroup>,
    allGroups: List<TopicGroup>,
    allocations: List<GroupQuestionAllocation>,
    availableByTopic: Map<String, List<com.example.testario.data.model.Question>>,
    requestedCount: Int,
    onAllocationsChanged: (List<GroupQuestionAllocation>) -> Unit
) {
    var groupAllocationsExpanded by remember { mutableStateOf(true) }
    SettingsLikeCard {
        Text("Reparto por grupos", style = MaterialTheme.typography.titleLarge, color = TestarioText)
        Spacer(Modifier.height(6.dp))
        Text(
            "Puedes seleccionar un grupo principal o cualquier subgrupo. Un grupo principal incluye automáticamente todos sus descendientes.",
            color = TestarioMutedText,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(14.dp))

        if (groups.isEmpty()) {
            Text("No hay grupos creados. Créelos desde la pestaña Grupos.", color = TestarioRed)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${allocations.size} grupos configurados", color = TestarioMutedText, modifier = Modifier.weight(1f))
                TextButton(onClick = { groupAllocationsExpanded = !groupAllocationsExpanded }, enabled = allocations.isNotEmpty()) {
                    Text(if (groupAllocationsExpanded) "⌃" else "⌄", color = TestarioPrimary, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (groupAllocationsExpanded) {
            FourItemDropdown(items = allocations, rowSpacing = 10.dp) { index, allocation ->
                val topicIds = GroupHierarchy.resolveTopicIds(allGroups, allocation.groupId)
                val availableCount = topicIds.asSequence()
                    .flatMap { availableByTopic[it].orEmpty().asSequence() }
                    .distinctBy { it.id }
                    .count()
                GroupAllocationRow(
                    groups = groups,
                    allGroups = allGroups,
                    selectedGroupId = allocation.groupId,
                    usedGroupIds = allocations.mapTo(hashSetOf()) { it.groupId } - allocation.groupId,
                    count = allocation.questionCount,
                    availableCount = availableCount,
                    onGroupChanged = { newId ->
                        onAllocationsChanged(allocations.toMutableList().also { list -> list[index] = allocation.copy(groupId = newId) })
                    },
                    onCountChanged = { newCount ->
                        onAllocationsChanged(allocations.toMutableList().also { list -> list[index] = allocation.copy(questionCount = newCount) })
                    },
                    onRemove = { onAllocationsChanged(allocations.filterIndexed { i, _ -> i != index }) }
                )
            }
            }

            val unused = groups.firstOrNull { candidate -> allocations.none { it.groupId == candidate.id } }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (unused != null) onAllocationsChanged(allocations + GroupQuestionAllocation(unused.id, 0))
                },
                enabled = unused != null,
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Añadir grupo") }

            val assigned = allocations.sumOf { it.questionCount.coerceAtLeast(0) }
            Spacer(Modifier.height(8.dp))
            Text(
                "Asignadas: $assigned / $requestedCount",
                color = if (assigned == requestedCount && requestedCount > 0) TestarioGreen else TestarioMutedText,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupAllocationRow(
    groups: List<TopicGroup>,
    allGroups: List<TopicGroup>,
    selectedGroupId: String,
    usedGroupIds: Set<String>,
    count: Int,
    availableCount: Int,
    onGroupChanged: (String) -> Unit,
    onCountChanged: (Int) -> Unit,
    onRemove: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = GroupHierarchy.path(allGroups, selectedGroupId).ifBlank { "Seleccionar grupo" }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)
    ) {
        Column(Modifier.padding(10.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = testarioTextFieldColors(),
                    trailingIcon = { Text("▼", color = TestarioMutedText) }
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = TestarioSurface) {
                    groups.forEach { group ->
                        DropdownMenuItem(
                            text = { Text(GroupHierarchy.path(allGroups, group.id), color = TestarioText) },
                            onClick = { expanded = false; onGroupChanged(group.id) },
                            enabled = group.id !in usedGroupIds
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (count == 0) "" else count.toString(),
                    onValueChange = { raw -> onCountChanged(raw.filter(Char::isDigit).toIntOrNull() ?: 0) },
                    label = { Text("Preguntas") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = testarioTextFieldColors()
                )
                TextButton(onClick = onRemove) { Text("Quitar", color = TestarioRed) }
            }
            Text("$availableCount disponibles en este grupo", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = TestarioText, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

@Composable
fun SelectableBigCard(title: String, subtitle: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) TestarioNeonSelected else TestarioNeonSurface),
        border = BorderStroke(1.4.dp, TestarioPrimary)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) TestarioPrimary else TestarioText)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
        }
    }
}

@Composable
fun CompactScopeButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(44.dp),
        selected = selected
    ) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
fun InfoCard(title: String, value: String) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(16.dp), CardDefaults.cardColors(containerColor = TestarioSurfaceLight)) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(title, color = TestarioMutedText, style = MaterialTheme.typography.bodyMedium)
            Text(value, color = TestarioText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SettingsLikeCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp), CardDefaults.cardColors(containerColor = TestarioSurface)) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun DifficultyChip(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(38.dp),
        selected = selected,
        border = BorderStroke(1.4.dp, TestarioPrimary),
        contentPadding = PaddingValues(0.dp)
    ) { Text(text, style = MaterialTheme.typography.bodySmall, color = if (selected) color else TestarioText) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopicDropdown(title: String, topics: List<Topic>, selectedTopicIds: Set<String>, onTopicToggled: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = when (selectedTopicIds.size) {
        0 -> "Ninguno"
        1 -> topics.firstOrNull { it.id == selectedTopicIds.first() }?.name ?: "1 seleccionado"
        else -> "${selectedTopicIds.size} seleccionados"
    }

    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TestarioText)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = displayText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = testarioTextFieldColors(),
                trailingIcon = { Text("▼", color = TestarioMutedText) }
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = TestarioSurface) {
                topics.forEach { topic ->
                    val checked = topic.id in selectedTopicIds
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = checked, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = TestarioPrimary))
                                Spacer(Modifier.width(8.dp))
                                Text(topic.name, color = TestarioText)
                            }
                        },
                        onClick = { onTopicToggled(topic.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTestCard(easyPercent: Int, mediumPercent: Int, hardPercent: Int, onEasyChange: (Int) -> Unit, onMediumChange: (Int) -> Unit, onHardChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PercentSliderRow("Fáciles", easyPercent, TestarioGreen, onEasyChange)
        PercentSliderRow("Medias", mediumPercent, TestarioYellow, onMediumChange)
        PercentSliderRow("Difíciles", hardPercent, TestarioRed, onHardChange)
    }
}

@Composable
private fun PercentSliderRow(label: String, value: Int, color: Color, onValueChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.bodyMedium, color = TestarioText)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color, inactiveTrackColor = TestarioSurfaceLight)
        )
        Text("$value%", modifier = Modifier.width(45.dp), style = MaterialTheme.typography.bodyMedium, color = TestarioMutedText)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun testarioTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TestarioText,
    unfocusedTextColor = TestarioText,
    focusedBorderColor = TestarioPrimary,
    unfocusedBorderColor = TestarioSurfaceLight,
    cursorColor = TestarioPrimary,
    focusedContainerColor = TestarioSurface,
    unfocusedContainerColor = TestarioSurface
)
