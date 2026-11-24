package com.example.testario.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.testario.ui.theme.TestarioNeonSurface
import com.example.testario.ui.theme.TestarioNeonSelected
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioOutline
import com.example.testario.ui.theme.TestarioNavBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.planario.app.PlanarioDependencies
import com.example.testario.ui.theme.TestarioText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestarioTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TestarioText
            )
        },
        navigationIcon = {
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(40.dp)
                    .background(TestarioSurfaceLight, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Atrás" }
                ) {
                    EstudarioIcon(EstudarioIconKind.BACK, TestarioText, Modifier.size(24.dp))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TestarioBackground,
            scrolledContainerColor = TestarioBackground
        )
    )
}

@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    thumbColor: Color = TestarioPrimary
) {
    val coroutineScope = rememberCoroutineScope()
    var containerHeight by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val isScrolling = listState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else if (isScrolling) 0.8f else 0.3f,
        animationSpec = tween(durationMillis = 300),
        label = "scrollbarAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(vertical = 8.dp)
            .onGloballyPositioned { containerHeight = it.size.height.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    val totalItems = listState.layoutInfo.totalItemsCount
                    if (totalItems > 0 && containerHeight > 0) {
                        val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                        val thumbHeightPx = (visibleItems.toFloat() / totalItems * containerHeight).coerceIn(40f, containerHeight)
                        val maxScrollableHeight = containerHeight - thumbHeightPx

                        if (maxScrollableHeight > 0) {
                            dragOffset = (dragOffset + dragAmount.y).coerceIn(0f, maxScrollableHeight)
                            val scrollProportion = dragOffset / maxScrollableHeight
                            val targetItem = (scrollProportion * (totalItems - visibleItems)).roundToInt().coerceIn(0, totalItems - 1)

                            coroutineScope.launch {
                                listState.scrollToItem(targetItem)
                            }
                        }
                    }
                }
            }
    ) {
        val totalItems = listState.layoutInfo.totalItemsCount
        val firstVisibleItem = listState.firstVisibleItemIndex

        if (totalItems > 0 && containerHeight > 0) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo.size
            val thumbHeightPx = (visibleItems.toFloat() / totalItems * containerHeight).coerceIn(40f, containerHeight)

            val maxOffset = containerHeight - thumbHeightPx
            val maxItemsScroll = (totalItems - visibleItems).coerceAtLeast(1)

            if (!isDragging) {
                val scrollProportion = firstVisibleItem.toFloat() / maxItemsScroll
                dragOffset = (scrollProportion * maxOffset).coerceIn(0f, maxOffset)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(0, dragOffset.roundToInt()) }
                    .padding(end = 4.dp)
                    .width(6.dp)
                    .height(with(LocalDensity.current) { thumbHeightPx.toDp() })
                    .alpha(alpha)
                    .background(thumbColor, RoundedCornerShape(50))
            )
        }
    }
}


@Composable
fun CompactScrollableColumn(
    height: Dp,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp)
                .verticalScroll(scrollState),
            content = content
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(14.dp)
                .padding(vertical = 4.dp)
                .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
                .pointerInput(scrollState.maxValue) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false }
                    ) { change, dragAmount ->
                        change.consume()
                        val maxScroll = scrollState.maxValue
                        if (maxScroll > 0 && trackHeightPx > 0f) {
                            val viewport = trackHeightPx
                            val contentHeight = viewport + maxScroll
                            val thumbHeight = (viewport * (viewport / contentHeight))
                                .coerceIn(36f, viewport)
                            val maxOffset = (viewport - thumbHeight).coerceAtLeast(1f)
                            dragOffsetPx = (dragOffsetPx + dragAmount.y).coerceIn(0f, maxOffset)
                            val target = ((dragOffsetPx / maxOffset) * maxScroll).roundToInt()
                            coroutineScope.launch { scrollState.scrollTo(target) }
                        }
                    }
                }
        ) {
            val maxScroll = scrollState.maxValue
            if (maxScroll > 0 && trackHeightPx > 0f) {
                val viewport = trackHeightPx
                val contentHeight = viewport + maxScroll
                val thumbHeight = (viewport * (viewport / contentHeight)).coerceIn(36f, viewport)
                val maxOffset = (viewport - thumbHeight).coerceAtLeast(1f)
                if (!isDragging) {
                    dragOffsetPx = (scrollState.value.toFloat() / maxScroll.toFloat() * maxOffset)
                        .coerceIn(0f, maxOffset)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(0, dragOffsetPx.roundToInt()) }
                        .width(5.dp)
                        .height(with(LocalDensity.current) { thumbHeight.toDp() })
                        .background(TestarioPrimary.copy(alpha = if (isDragging) 0.9f else 0.45f), RoundedCornerShape(50))
                )
            }
        }
    }
}

// NUEVO: Algoritmo de ordenación natural (alfanumérica)
fun String.naturalCompareTo(other: String): Int {
    val a = this
    val b = other
    var i = 0
    var j = 0
    while (i < a.length && j < b.length) {
        val charA = a[i]
        val charB = b[j]
        if (charA.isDigit() && charB.isDigit()) {
            var startA = i
            while (i < a.length && a[i].isDigit()) i++
            var startB = j
            while (j < b.length && b[j].isDigit()) j++

            val numA = a.substring(startA, i).toLongOrNull() ?: 0L
            val numB = b.substring(startB, j).toLongOrNull() ?: 0L

            val cmp = numA.compareTo(numB)
            if (cmp != 0) return cmp
        } else {
            val cmp = charA.lowercaseChar().compareTo(charB.lowercaseChar())
            if (cmp != 0) return cmp
            i++
            j++
        }
    }
    return a.length.compareTo(b.length)
}
enum class MainSection { HOME, LIBRARY, TEST, PLAN, STATS }

/** Verdadero cuando TestarioApp muestra el carril lateral: la cápsula inferior se oculta. */
val LocalNexumRail = androidx.compose.runtime.compositionLocalOf { false }

@Composable
fun MainBottomNavigation(
    selected: MainSection,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onTest: () -> Unit,
    onPlan: () -> Unit,
    onStats: () -> Unit
) {
    if (LocalNexumRail.current) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TestarioBackground)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TestarioNavBackground, RoundedCornerShape(26.dp))
                .padding(6.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CapsuleNavItem(EstudarioIconKind.HOME, "Inicio", selected == MainSection.HOME, onHome)
            CapsuleNavItem(EstudarioIconKind.LIBRARY, "Biblioteca", selected == MainSection.LIBRARY, onLibrary)
            CapsuleNavItem(EstudarioIconKind.TEST, "Test", selected == MainSection.TEST, onTest)
            CapsuleNavItem(EstudarioIconKind.PLAN, "Plan", selected == MainSection.PLAN, onPlan)
        }
    }
}

/** Solo el destino activo muestra su nombre; los demás son iconos de 56 x 52 dp. */
@Composable
private fun CapsuleNavItem(
    icon: EstudarioIconKind,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pill by animateColorAsState(
        if (selected) TestarioNeonSelected else TestarioNeonSelected.copy(alpha = 0f),
        tween(180), label = "capsulePill"
    )
    val tint by animateColorAsState(
        if (selected) TestarioPrimary else TestarioMutedText, tween(180), label = "capsuleTint"
    )
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        EstudarioIcon(icon, tint = tint, modifier = Modifier.size(24.dp))
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(tween(180)) + fadeIn(tween(180)),
            exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120))
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TestarioPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Botón tonal sin borde: acento latón sobre superficie seleccionada, o neutro. Alto 52 dp. */
@Composable
fun NexumTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true
) {
    val container = if (primary) TestarioNeonSelected else TestarioSurfaceLight
    val content = if (primary) TestarioPrimary else TestarioText
    Box(
        modifier = modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** Navegación de pantallas anchas (tablet): sustituye a la cápsula inferior. */
@Composable
fun NexumNavRail(
    selected: MainSection,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onTest: () -> Unit,
    onPlan: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(88.dp)
            .background(TestarioNavBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 8.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        NexumLogoMark(modifier = Modifier.size(36.dp))
        Box(Modifier.height(18.dp))
        RailNavItem(EstudarioIconKind.HOME, "Inicio", selected == MainSection.HOME, onHome)
        RailNavItem(EstudarioIconKind.LIBRARY, "Biblioteca", selected == MainSection.LIBRARY, onLibrary)
        RailNavItem(EstudarioIconKind.TEST, "Test", selected == MainSection.TEST, onTest)
        RailNavItem(EstudarioIconKind.PLAN, "Plan", selected == MainSection.PLAN, onPlan)
        RailNavItem(EstudarioIconKind.STATS, "Estadísticas", selected == MainSection.STATS, onStats)
        Box(Modifier.weight(1f))
        RailNavItem(EstudarioIconKind.SETTINGS, "Ajustes", false, onSettings)
    }
}

@Composable
private fun RailNavItem(
    icon: EstudarioIconKind,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pill by animateColorAsState(
        if (selected) TestarioNeonSelected else TestarioNeonSelected.copy(alpha = 0f),
        tween(180), label = "railPill"
    )
    val tint by animateColorAsState(
        if (selected) TestarioPrimary else TestarioMutedText, tween(180), label = "railTint"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(pill)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        EstudarioIcon(icon, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
fun LibrarySectionTabs(
    topicsSelected: Boolean,
    onTopics: () -> Unit,
    onGroups: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        com.example.testario.ui.theme.NeonButton(
            onClick = onTopics,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            selected = topicsSelected
        ) { Text("Temas") }
        com.example.testario.ui.theme.NeonButton(
            onClick = onGroups,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            selected = !topicsSelected
        ) { Text("Grupos") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestarioMainTopBar(
    title: String,
    onSettings: () -> Unit
) {
    val studySettings by PlanarioDependencies.studyRepository.settings.collectAsState()
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(TestarioPrimaryDark, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    EstudarioIcon(EstudarioIconKind.LOGO, TestarioPrimary, Modifier.size(23.dp))
                }
                Column(Modifier.padding(start = 10.dp)) {
                    Text(
                        text = "NEXUM",
                        style = MaterialTheme.typography.titleMedium,
                        color = TestarioText
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodySmall,
                        color = TestarioMutedText
                    )
                }
            }
        },
        actions = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(TestarioSurfaceLight, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    modifier = Modifier.semantics {
                        contentDescription = if (studySettings.notificationsEnabled)
                            "Desactivar recordatorios" else "Activar recordatorios"
                    },
                    onClick = {
                        val enabled = !studySettings.notificationsEnabled
                        PlanarioDependencies.studyRepository.setNotificationsEnabled(enabled)
                        PlanarioDependencies.schedulePlanarioNotifications()
                    }
                ) {
                    EstudarioIcon(
                        EstudarioIconKind.NOTIFICATIONS,
                        tint = if (studySettings.notificationsEnabled) TestarioPrimary else TestarioMutedText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Box(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(TestarioSurfaceLight, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.semantics { contentDescription = "Ajustes" }
                ) {
                    EstudarioIcon(EstudarioIconKind.SETTINGS, TestarioText, Modifier.size(22.dp))
                }
            }
            Box(Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = TestarioBackground,
            scrolledContainerColor = TestarioBackground
        )
    )
}

