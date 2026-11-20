package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.ui.theme.*

internal enum class SketchMode { DRAW, ERASE, MARK, PREVIEW }

/** Raw vector strokes and answer markers stay distinct and use normalized coordinates. */
@Composable
internal fun StudySketchCanvas(
    sketch: SketchDrawing,
    modifier: Modifier = Modifier,
    markers: List<ImagePoint> = emptyList(),
    mode: SketchMode = SketchMode.PREVIEW,
    showMarkers: Boolean = false,
    markerStatuses: Map<String, Boolean> = emptyMap(),
    onDrawStroke: (SketchStroke) -> Unit = {},
    onEraseStroke: (String) -> Unit = {},
    onMarkTap: (Double, Double) -> Unit = { _, _ -> }
) {
    var inProgress by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErase by rememberUpdatedState(onEraseStroke)
    val latestMark by rememberUpdatedState(onMarkTap)
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint? {
        val fit = fitImageToBox(1200, 800, w.toFloat(), h.toFloat()) ?: return null
        val point = fit.toImage(px, py) ?: return null
        return SketchPoint(point.first, point.second)
    }
    val gestures = when (mode) {
        SketchMode.DRAW -> Modifier.pointerInput(mode) {
            detectDragGestures(
                onDragStart = { start ->
                    inProgress = listOfNotNull(
                        coordinates(start.x, start.y, size.width, size.height)
                    )
                },
                onDrag = { change, _ ->
                    change.consume()
                    coordinates(change.position.x, change.position.y,
                        size.width, size.height)?.let { next ->
                        val previous = inProgress.lastOrNull()
                        if (inProgress.isNotEmpty() && inProgress.size < 8000 &&
                            (previous == null ||
                                (next.x - previous.x) * (next.x - previous.x) +
                                (next.y - previous.y) * (next.y - previous.y) > .000003)) {
                            inProgress = inProgress + next
                        }
                    }
                },
                onDragEnd = {
                    val points = inProgress
                    if (points.size >= 2) latestDraw(SketchStroke(points = points))
                    inProgress = emptyList()
                },
                onDragCancel = { inProgress = emptyList() }
            )
        }
        SketchMode.ERASE, SketchMode.MARK -> Modifier.pointerInput(mode, sketch, markers) {
            detectTapGestures { pos ->
                val p = coordinates(pos.x, pos.y, size.width, size.height)
                    ?: return@detectTapGestures
                if (mode == SketchMode.MARK) latestMark(p.x, p.y)
                else {
                    val nearest = sketch.strokes.mapNotNull { stroke ->
                        stroke.points.minOfOrNull { a ->
                            val dx = (a.x - p.x) * size.width.toDouble()
                            val dy = (a.y - p.y) * size.height.toDouble()
                            dx * dx + dy * dy
                        }?.let { stroke.id to it }
                    }.minByOrNull { it.second }
                    if (nearest != null && nearest.second < (32.dp.toPx() * 32.dp.toPx()))
                        latestErase(nearest.first)
                }
            }
        }
        SketchMode.PREVIEW -> Modifier
    }
    BoxWithConstraints(modifier.background(TestarioSurface, RoundedCornerShape(14.dp)).then(gestures)) {
        Canvas(Modifier.fillMaxSize()) {
            val fit = fitImageToBox(1200, 800, size.width, size.height)
                ?: return@Canvas
            val allStrokes = sketch.strokes + if (inProgress.size > 1) listOf(
                SketchStroke(points = inProgress)) else emptyList()
            for (stroke in allStrokes) {
                if (stroke.points.size < 2) continue
                val shape = Path()
                val start = stroke.points.first()
                val origin = fit.locate(start.x, start.y)
                shape.moveTo(origin.x, origin.y)
                stroke.points.drop(1).forEach {
                    val mapped = fit.locate(it.x, it.y)
                    shape.lineTo(mapped.x, mapped.y)
                }
                val brushColor = when (stroke.brush) {
                    "white" -> label
                    "green" -> green
                    "red" -> red
                    else -> purple
                }
                val ink = stroke.colorArgb?.let { Color(it.toInt()) } ?: brushColor
                drawPath(
                    shape, ink.copy(alpha = stroke.opacity?.toFloat()?.coerceIn(.05f, 1f) ?: 1f),
                    style = Stroke(
                        width = stroke.widthFraction?.let {
                            (minOf(fit.width, fit.height) * it.toFloat()).coerceAtLeast(1f)
                        } ?: 5.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
        val density = LocalDensity.current
        val containerWidth = with(density) { maxWidth.toPx() }
        val containerHeight = with(density) { maxHeight.toPx() }
        val markerFit = fitImageToBox(1200, 800, containerWidth, containerHeight)
        if (showMarkers && markerFit != null) markers.forEach { marker ->
            val statusColor = when (markerStatuses[marker.id]) {
                true -> green
                false -> red
                null -> purple
            }
            val position = markerFit.locate(marker.x, marker.y)
            Text(
                marker.order.toString(),
                color = statusColor,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                modifier = Modifier.offset(
                    x = with(density) { position.x.toDp() } - 10.dp,
                    y = with(density) { position.y.toDp() } - 14.dp
                )
            )
        }
    }
}
