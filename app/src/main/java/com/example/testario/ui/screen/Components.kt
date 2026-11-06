package com.example.testario.ui.screen

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
import com.example.testario.ui.theme.NeonIconButton as IconButton
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
                IconButton(onClick = onBack) {
                    Text(
                        text = "‹",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TestarioText
                    )
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

@Composable
fun MainBottomNavigation(
    selected: MainSection,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onTest: () -> Unit,
    onPlan: () -> Unit,
    onStats: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TestarioBackground)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(TestarioNavBackground, RoundedCornerShape(22.dp))
            .border(1.dp, TestarioOutline, RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
    ) {
        MainBottomNavigationItem(EstudarioIconKind.HOME, "Inicio", selected == MainSection.HOME, onHome, Modifier.weight(1f))
        MainBottomNavigationItem(EstudarioIconKind.LIBRARY, "Biblioteca", selected == MainSection.LIBRARY, onLibrary, Modifier.weight(1f))
        MainBottomNavigationItem(EstudarioIconKind.TEST, "Test", selected == MainSection.TEST, onTest, Modifier.weight(1f))
        MainBottomNavigationItem(EstudarioIconKind.PLAN, "Plan", selected == MainSection.PLAN, onPlan, Modifier.weight(1f))
        MainBottomNavigationItem(EstudarioIconKind.STATS, "Estadísticas", selected == MainSection.STATS, onStats, Modifier.weight(1f))
    }
}

@Composable
private fun MainBottomNavigationItem(
    icon: EstudarioIconKind,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (selected) TestarioPrimary else TestarioMutedText
    Column(
        modifier = modifier
            .background(
                color = if (selected) TestarioNeonSelected else TestarioNeonSurface,
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.2.dp, TestarioPrimary, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EstudarioIcon(icon, tint = tint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) TestarioText else TestarioMutedText,
            maxLines = 1
        )
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
                IconButton(onClick = onSettings) {
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

