package com.example.testario.ui.screen

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planario.features.home.HomeViewModel
import com.example.planario.domain.timer.StudyTimerState
import com.example.testario.data.local.QuickTestLinkManager
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioOutline
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.HomeSettingsViewModel
import com.example.testario.viewmodel.LibraryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel,
    homeSettingsViewModel: HomeSettingsViewModel,
    onLibrary: () -> Unit,
    onGroups: () -> Unit,
    onCreateTest: () -> Unit,
    onPlan: () -> Unit,
    onOpenTimer: () -> Unit,
    onStartQuickTest: (List<String>, Int) -> Unit,
    onOpenTemplate: (String) -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
    onCustomizeHome: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val studyViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory())
    val studyState by studyViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val quickTestLinks = remember { QuickTestLinkManager(context.applicationContext) }
    var isReorderMode by remember { mutableStateOf(false) }

    val allQuestions = database.topics.flatMap { it.questions }
    val failures = allQuestions.count { it.stats.inFailures }
    val answered = allQuestions.sumOf { it.stats.timesAnswered }
    val correct = allQuestions.sumOf { it.stats.timesCorrect }
    val accuracy = if (answered == 0) 0 else ((correct.toDouble() / answered) * 100).toInt()
    val activeStudyTopic = studyState.activeTopic
    val activeQuickLink = activeStudyTopic?.let { quickTestLinks.get(it.id) }
    val activeLinkedTopics = activeQuickLink?.libraryTopicIds.orEmpty()
        .filter { id -> database.topics.any { it.id == id } }

    fun isWidgetVisible(widgetId: String): Boolean = when (widgetId) {
        HomeSettingsViewModel.WIDGET_TODAY -> homeSettingsViewModel.showStudyToday
        HomeSettingsViewModel.WIDGET_TOPIC -> homeSettingsViewModel.showStudyTopic && activeStudyTopic != null
        HomeSettingsViewModel.WIDGET_TIMER -> homeSettingsViewModel.showStudyTimer
        HomeSettingsViewModel.WIDGET_CREATE_TEST -> homeSettingsViewModel.showCreateTest
        HomeSettingsViewModel.WIDGET_SUMMARY -> homeSettingsViewModel.showSummary
        HomeSettingsViewModel.WIDGET_FAILURES -> homeSettingsViewModel.showFailures && failures > 0
        HomeSettingsViewModel.WIDGET_TEMPLATES -> homeSettingsViewModel.showTemplates
        HomeSettingsViewModel.WIDGET_QUICK_ACCESS ->
            homeSettingsViewModel.showLibraryShortcut ||
                homeSettingsViewModel.showTestShortcut ||
                homeSettingsViewModel.showPlanShortcut ||
                homeSettingsViewModel.showStatsShortcut
        else -> false
    }

    val visibleWidgetIds = homeSettingsViewModel.widgetOrder.filter(::isWidgetVisible)
    val homeListState = rememberLazyListState()
    var draggedWidgetId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioMainTopBar("Inicio", onSettings) },
        bottomBar = {
            MainBottomNavigation(
                selected = MainSection.HOME,
                onHome = {},
                onLibrary = onLibrary,
                onTest = onCreateTest,
                onPlan = onPlan,
                onStats = onStats
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(
                state = homeListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (visibleWidgetIds.isEmpty()) {
                    item(key = "empty_home") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clickable(onClick = onCustomizeHome),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = TestarioSurface.copy(alpha = 0.55f)),
                            border = BorderStroke(1.dp, TestarioPrimary.copy(alpha = 0.28f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("+", style = MaterialTheme.typography.displayLarge, color = TestarioPrimary.copy(alpha = 0.50f))
                                Text("Personalizar inicio", style = MaterialTheme.typography.bodyMedium, color = TestarioMutedText.copy(alpha = 0.80f))
                            }
                        }
                    }
                }

                items(
                    items = visibleWidgetIds,
                    key = { it }
                ) { widgetId ->
                    ReorderableHomeBlock(
                        modifier = if (draggedWidgetId == widgetId) Modifier else Modifier.animateItem(),
                        widgetId = widgetId,
                        reorderKey = visibleWidgetIds.joinToString("|"),
                        reorderMode = isReorderMode,
                        listState = homeListState,
                        visibleWidgetIds = visibleWidgetIds,
                        isDragging = draggedWidgetId == widgetId,
                        onDragStateChange = { dragging ->
                            if (!dragging && draggedWidgetId == widgetId) {
                                homeSettingsViewModel.persistWidgetOrder()
                            }
                            draggedWidgetId = if (dragging) widgetId else null
                        },
                        onEnterReorderMode = { isReorderMode = true },
                        onMove = { direction ->
                            homeSettingsViewModel.moveWidgetRelative(
                                widgetId = widgetId,
                                direction = direction,
                                visibleIds = visibleWidgetIds,
                                persistImmediately = false
                            )
                        }
                    ) {
                    when (widgetId) {
                        HomeSettingsViewModel.WIDGET_TODAY -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Hoy", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                            Text(studyState.dateText, color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(studyState.statusText, color = TestarioPrimary)
                                    }

                                    Text(
                                        "${studyState.studiedTimeText} / ${studyState.dailyGoalText}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TestarioText
                                    )
                                    LinearProgressIndicator(
                                        progress = { studyState.progress },
                                        modifier = Modifier.fillMaxWidth().height(8.dp),
                                        color = TestarioPrimary,
                                        trackColor = TestarioSurfaceLight
                                    )
                                    Text(
                                        if (studyState.isFreeDay) "Día libre" else "Restante: ${studyState.remainingTimeText}",
                                        color = TestarioMutedText,
                                        style = MaterialTheme.typography.bodySmall
                                    )

                                    Text("Acciones del día", style = MaterialTheme.typography.labelLarge, color = TestarioMutedText)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val primaryLabel = if (studyState.studyTimerState == StudyTimerState.STOPPED) {
                                            "Iniciar estudio"
                                        } else {
                                            "Reiniciar"
                                        }
                                        Button(
                                            onClick = {
                                                if (studyState.studyTimerState == StudyTimerState.STOPPED) {
                                                    studyViewModel.startStudyFromHome()
                                                } else {
                                                    studyViewModel.restartStudyFromHome()
                                                }
                                            },
                                            enabled = !studyState.isFreeDay,
                                            modifier = Modifier.weight(1f)
                                        ) { Text(primaryLabel) }

                                        val secondaryLabel = if (studyState.studyTimerState == StudyTimerState.PAUSED) {
                                            "Reanudar"
                                        } else {
                                            "Pausar"
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                when (studyState.studyTimerState) {
                                                    StudyTimerState.RUNNING -> studyViewModel.pauseStudyFromHome()
                                                    StudyTimerState.PAUSED -> studyViewModel.resumeStudyFromHome()
                                                    StudyTimerState.STOPPED -> Unit
                                                }
                                            },
                                            enabled = studyState.studyTimerState != StudyTimerState.STOPPED && !studyState.isFreeDay,
                                            modifier = Modifier.weight(1f)
                                        ) { Text(secondaryLabel) }
                                    }

                                    if (homeSettingsViewModel.showStudyObjectives && studyState.fixedObjectives.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text("Objetivos diarios", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        studyState.fixedObjectives.forEach { objective ->
                                            val checked = studyState.isFixedObjectiveCompleted(objective.id)
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable { studyViewModel.toggleFixedObjectiveCompleted(objective.id) }
                                                    .padding(vertical = 6.dp, horizontal = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = checked,
                                                    onCheckedChange = null
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = objective.name,
                                                    color = TestarioText,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = "${objective.targetMinutes} min",
                                                    color = TestarioMutedText,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_TOPIC -> {
                            val topic = activeStudyTopic
                            if (topic != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(22.dp),
                                    colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                                    border = BorderStroke(1.dp, TestarioPrimary.copy(alpha = 0.35f))
                                ) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Tema activo", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        Text(topic.name, style = MaterialTheme.typography.headlineSmall, color = TestarioText)
                                        Text("Vuelta ${topic.round}", color = TestarioMutedText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(onClick = onPlan, modifier = Modifier.weight(1f)) { Text("Ver plan") }
                                            Button(
                                                onClick = {
                                                    val link = activeQuickLink
                                                    if (link != null && activeLinkedTopics.isNotEmpty()) {
                                                        onStartQuickTest(activeLinkedTopics, link.questionCount)
                                                    } else {
                                                        onPlan()
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(if (activeLinkedTopics.isEmpty()) "Vincular test" else "Test rápido")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_TIMER -> {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTimer),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Reloj", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        Text(
                                            if (studyState.isStudyTimerActive) "Sesión activa · ${studyState.currentTimerText}" else "Cronómetro de estudio",
                                            color = TestarioMutedText
                                        )
                                    }
                                    Text("›", style = MaterialTheme.typography.headlineMedium, color = TestarioPrimary)
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_CREATE_TEST -> {
                            Button(
                                onClick = onCreateTest,
                                modifier = Modifier.fillMaxWidth().height(58.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) { Text("Crear test", style = MaterialTheme.typography.titleMedium) }
                        }

                        HomeSettingsViewModel.WIDGET_SUMMARY -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("Resumen", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DashboardMetric(database.topics.size.toString(), "Temas", Modifier.weight(1f))
                                        DashboardMetric(allQuestions.size.toString(), "Preguntas", Modifier.weight(1f))
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        DashboardMetric(failures.toString(), "En fallos", Modifier.weight(1f))
                                        DashboardMetric("$accuracy%", "Acierto", Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_FAILURES -> {
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable(onClick = onCreateTest),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                                border = BorderStroke(1.dp, TestarioPrimary)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Preguntas en Fallos", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        Text("Tienes $failures pendientes de repaso.", color = TestarioMutedText)
                                    }
                                    Text("›", style = MaterialTheme.typography.headlineMedium, color = TestarioPrimary)
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_TEMPLATES -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Plantillas", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                    if (database.testTemplates.isEmpty()) {
                                        Text("Aún no tienes plantillas", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                                        Text("Configura un test y guárdalo como plantilla para reutilizarlo desde aquí.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                                        OutlinedButton(onClick = onCreateTest, modifier = Modifier.fillMaxWidth()) { Text("Configurar un test") }
                                    } else {
                                        database.testTemplates.take(3).forEach { template ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth().clickable { onOpenTemplate(template.id) },
                                                shape = RoundedCornerShape(16.dp),
                                                colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)
                                            ) {
                                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(template.name, color = TestarioText, style = MaterialTheme.typography.titleMedium)
                                                        Text("${template.questionCount} preguntas · ${template.mode.name.lowercase().replaceFirstChar { it.uppercase() }}", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Text("›", style = MaterialTheme.typography.headlineMedium, color = TestarioPrimary)
                                                }
                                            }
                                        }
                                        if (database.testTemplates.size > 3) {
                                            OutlinedButton(onClick = onCreateTest, modifier = Modifier.fillMaxWidth()) { Text("Ver todas las plantillas") }
                                        }
                                    }
                                }
                            }
                        }

                        HomeSettingsViewModel.WIDGET_QUICK_ACCESS -> {
                            val quickAccesses = buildList<Triple<String, EstudarioIconKind, () -> Unit>> {
                                if (homeSettingsViewModel.showLibraryShortcut) add(Triple("Biblioteca", EstudarioIconKind.LIBRARY, onLibrary))
                                if (homeSettingsViewModel.showTestShortcut) add(Triple("Test", EstudarioIconKind.TEST, onCreateTest))
                                if (homeSettingsViewModel.showPlanShortcut) add(Triple("Plan", EstudarioIconKind.PLAN, onPlan))
                                if (homeSettingsViewModel.showStatsShortcut) add(Triple("Estadísticas", EstudarioIconKind.STATS, onStats))
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                                border = BorderStroke(1.dp, TestarioOutline)
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Accesos rápidos", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        quickAccesses.forEach { item ->
                                            Card(
                                                modifier = Modifier.weight(1f).clickable(onClick = item.third),
                                                shape = RoundedCornerShape(15.dp),
                                                colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight),
                                                border = BorderStroke(1.dp, TestarioOutline)
                                            ) {
                                                Column(
                                                    Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    EstudarioIcon(item.second, TestarioPrimary, Modifier.width(24.dp).height(24.dp))
                                                    Text(item.first, color = TestarioText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

                item(key = "home_bottom_spacer") {
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (isReorderMode && visibleWidgetIds.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .zIndex(30f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = TestarioSurface.copy(alpha = 0.96f)),
                    border = BorderStroke(1.dp, TestarioPrimary.copy(alpha = 0.48f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Organizar Inicio", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                            Text("Arrastra los bloques. Al acercarte a un borde, la pantalla se desplazará sola.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(
                            onClick = {
                                draggedWidgetId = null
                                isReorderMode = false
                            }
                        ) { Text("Listo") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderableHomeBlock(
    modifier: Modifier = Modifier,
    widgetId: String,
    reorderKey: String,
    reorderMode: Boolean,
    listState: LazyListState,
    visibleWidgetIds: List<String>,
    isDragging: Boolean,
    onDragStateChange: (Boolean) -> Unit,
    onEnterReorderMode: () -> Unit,
    onMove: (Int) -> Unit,
    content: @Composable () -> Unit
) {
    var dragOffset by remember(widgetId) { mutableStateOf(0f) }
    var autoScrollSpeedPx by remember(widgetId) { mutableStateOf(0f) }
    var waitingForReorderFrame by remember(widgetId) { mutableStateOf(false) }
    val latestVisibleIds by rememberUpdatedState(visibleWidgetIds)
    val latestOnMove by rememberUpdatedState(onMove)
    val latestReorderMode by rememberUpdatedState(reorderMode)
    val density = LocalDensity.current
    val spacingPx = with(density) { 14.dp.toPx() }
    val edgeZonePx = with(density) { 112.dp.toPx() }
    val minAutoScrollPx = with(density) { 18.dp.toPx() }
    val maxAutoScrollPx = with(density) { 36.dp.toPx() }
    val shadowPx = with(density) { 18.dp.toPx() }

    val displayedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = if (isDragging) snap() else tween(durationMillis = 135),
        label = "homeDragTranslation"
    )
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.022f else 1f,
        animationSpec = tween(durationMillis = 105),
        label = "homeDragScale"
    )

    LaunchedEffect(reorderKey) {
        waitingForReorderFrame = false
    }

    fun maybeReorder() {
        if (waitingForReorderFrame) return
        val ids = latestVisibleIds
        val currentIndex = ids.indexOf(widgetId)
        if (currentIndex < 0) return
        val info = listState.layoutInfo.visibleItemsInfo
        val draggedInfo = info.firstOrNull { it.key == widgetId } ?: return
        val draggedCenter = draggedInfo.offset + dragOffset + (draggedInfo.size / 2f)

        val nextId = ids.getOrNull(currentIndex + 1)
        val nextInfo = nextId?.let { id -> info.firstOrNull { it.key == id } }
        if (nextInfo != null && draggedCenter > nextInfo.offset + (nextInfo.size / 2f)) {
            dragOffset -= nextInfo.size + spacingPx
            waitingForReorderFrame = true
            latestOnMove(1)
            return
        }

        val previousId = ids.getOrNull(currentIndex - 1)
        val previousInfo = previousId?.let { id -> info.firstOrNull { it.key == id } }
        if (previousInfo != null && draggedCenter < previousInfo.offset + (previousInfo.size / 2f)) {
            dragOffset += previousInfo.size + spacingPx
            waitingForReorderFrame = true
            latestOnMove(-1)
        }
    }

    fun updateAutoScroll() {
        val layout = listState.layoutInfo
        val draggedInfo = layout.visibleItemsInfo.firstOrNull { it.key == widgetId } ?: run {
            autoScrollSpeedPx = 0f
            return
        }
        val draggedCenter = draggedInfo.offset + dragOffset + (draggedInfo.size / 2f)
        val topEdge = layout.viewportStartOffset.toFloat()
        val bottomEdge = layout.viewportEndOffset.toFloat()
        val topTrigger = topEdge + edgeZonePx
        val bottomTrigger = bottomEdge - edgeZonePx

        autoScrollSpeedPx = when {
            draggedCenter < topTrigger -> {
                val ratio = ((topTrigger - draggedCenter) / edgeZonePx).coerceIn(0f, 1f)
                -(minAutoScrollPx + (maxAutoScrollPx - minAutoScrollPx) * ratio)
            }
            draggedCenter > bottomTrigger -> {
                val ratio = ((draggedCenter - bottomTrigger) / edgeZonePx).coerceIn(0f, 1f)
                minAutoScrollPx + (maxAutoScrollPx - minAutoScrollPx) * ratio
            }
            else -> 0f
        }
    }

    LaunchedEffect(isDragging, autoScrollSpeedPx, reorderKey) {
        while (isDragging && autoScrollSpeedPx != 0f) {
            val consumed = listState.scrollBy(autoScrollSpeedPx)
            if (kotlin.math.abs(consumed) < 0.5f) {
                autoScrollSpeedPx = 0f
                break
            }
            // Compensa el movimiento de la lista para que la tarjeta siga pegada al dedo.
            dragOffset += consumed
            maybeReorder()
            updateAutoScroll()
            delay(16L)
        }
    }

    LaunchedEffect(isDragging) {
        if (!isDragging) {
            autoScrollSpeedPx = 0f
            delay(150L)
            dragOffset = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 40f else 0f)
            .graphicsLayer {
                translationY = displayedOffset
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = if (isDragging) shadowPx else 0f
                alpha = if (isDragging) 0.985f else 1f
            }
            .pointerInput(widgetId, reorderMode) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    val pointerId = down.id
                    val start = down.position
                    var last = start
                    val holdMillis = if (latestReorderMode) 90L else 520L

                    val interrupted = withTimeoutOrNull(holdMillis) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == pointerId }
                                ?: return@withTimeoutOrNull true
                            if (!change.pressed) return@withTimeoutOrNull true
                            last = change.position
                            if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                // Movimiento rápido = scroll normal, no reorganización.
                                return@withTimeoutOrNull true
                            }
                        }
                        @Suppress("UNREACHABLE_CODE")
                        false
                    }

                    // null significa que se cumplió el tiempo de pulsación sin cancelar.
                    if (interrupted == null) {
                        dragOffset = 0f
                        autoScrollSpeedPx = 0f
                        onEnterReorderMode()
                        onDragStateChange(true)

                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                val current = change.position
                                val deltaY = current.y - last.y
                                change.consume()
                                if (!change.pressed) break
                                if (deltaY != 0f) {
                                    dragOffset += deltaY
                                    maybeReorder()
                                    updateAutoScroll()
                                }
                                last = current
                            }
                        } finally {
                            autoScrollSpeedPx = 0f
                            onDragStateChange(false)
                        }
                    }
                }
            }
    ) {
        content()
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        color = TestarioPrimary.copy(alpha = 0.13f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .border(
                        width = 1.5.dp,
                        color = TestarioPrimary.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(22.dp)
                    )
            )
        }
    }
}

@Composable
private fun DashboardMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = TestarioText)
            Text(label, style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)
        }
    }
}
