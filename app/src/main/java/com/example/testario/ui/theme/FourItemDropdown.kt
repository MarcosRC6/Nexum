package com.example.testario.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Expandable section body: up to four full rows visible; extra rows scroll within
 * the section. Actual measured row heights, not estimates, determine the cap,
 * including when text wraps or users change font/UI scale.
 *
 * The caller controls expanded/collapsed state and should put the toggle next
 * to the section title (and Add action when present).
 */
@Composable
fun <T> FourItemDropdown(
    items: List<T>,
    modifier: Modifier = Modifier,
    rowSpacing: Dp = 8.dp,
    content: @Composable (index: Int, item: T) -> Unit
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val heights = remember { mutableStateMapOf<Int, Int>() }
    val scroll = rememberScrollState()
    val visibleCount = minOf(4, items.size)
    val known = (0 until visibleCount).all { heights[it] != null }
    val contentHeight = if (known) {
        with(density) { (0 until visibleCount).sumOf { heights[it] ?: 0 }.toDp() } +
            rowSpacing * (visibleCount - 1)
    } else {
        // A temporary ceiling until the first four rows have been measured.
        800.dp
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = contentHeight)
            .clipToBounds()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(rowSpacing)
        ) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (index < 4) Modifier.onSizeChanged { size ->
                                if (heights[index] != size.height) heights[index] = size.height
                            } else Modifier
                        )
                ) {
                    content(index, item)
                }
            }
        }

        if (items.size > 4) {
            val rail = TestarioPrimary
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 1.dp)
                    .width(4.dp)
                    .fillMaxHeight()
            ) {
                val viewport = size.height
                if (viewport <= 0f) return@Canvas
                val track = rail.copy(alpha = 0.15f)
                drawRoundRect(
                    color = track,
                    cornerRadius = CornerRadius(size.width / 2f)
                )
                val fullContent = viewport + scroll.maxValue.toFloat()
                val thumb = (viewport * viewport / fullContent)
                    .coerceIn(20.dp.toPx(), viewport)
                val y = if (scroll.maxValue > 0) {
                    (scroll.value.toFloat() / scroll.maxValue) * (viewport - thumb)
                } else 0f
                drawRoundRect(
                    color = rail.copy(alpha = 0.9f),
                    topLeft = Offset(0f, y),
                    size = androidx.compose.ui.geometry.Size(size.width, thumb),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
            }
        }
    }
}
