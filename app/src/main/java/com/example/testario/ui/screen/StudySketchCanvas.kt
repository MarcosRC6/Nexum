package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.ui.theme.*

internal enum class SketchMode { DRAW, ERASE, MARK, PAN, PREVIEW }

/** Raw vector strokes and answer markers stay distinct and use normalized coordinates. */
@Composable
internal fun StudySketchCanvas(
    sketch: SketchDrawing,
    modifier: Modifier = Modifier,
    markers: List<ImagePoint> = emptyList(),
    mode: SketchMode = SketchMode.PREVIEW,
    showMarkers: Boolean = false,
    markerStatuses: Map<String, Boolean> = emptyMap(),
    zoomFactor: Float = 1f,
    penArgb: Long = 0xFFA855F7L,
    penWidthFraction: Double = .007,
    penOpacity: Double = 1.0,
    onZoomChange: (Float) -> Unit = {},
    onDrawStroke: (SketchStroke) -> Unit = {},
    onEraseStroke: (String) -> Unit = {},
    onMarkTap: (Double, Double) -> Unit = { _, _ -> }
) {
    var inProgress by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErase by rememberUpdatedState(onEraseStroke)
    val latestMark by rememberUpdatedState(onMarkTap)
    val liveZoom by rememberUpdatedState(zoomFactor)
    val latestZoomChange by rememberUpdatedState(onZoomChange)
    val livePenColor by rememberUpdatedState(penArgb)
    val livePenWidth by rememberUpdatedState(penWidthFraction)
    val livePenOpacity by rememberUpdatedState(penOpacity)
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = liveZoom.coerceIn(1f, 5f)
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }
    // Detect the full gesture before drawing: two fingers must NEVER leave paint.
    // Eraser behaves like a continuous brush while dragging over existing strokes.
    val gestures = if (mode == SketchMode.PREVIEW) Modifier else Modifier.pointerInput(mode, sketch.strokes) {
        awaitEachGesture {
            val first = awaitFirstDown(requireUnconsumed = false)
            val pointStart = coordinates(first.position.x, first.position.y, size.width, size.height)
            val path = mutableListOf<SketchPoint>()
            if (mode == SketchMode.DRAW) path += pointStart
            val alreadyErased = mutableSetOf<String>()
            var multiTouch = false
            var moved = false

            fun eraseAt(p: SketchPoint) {
                val nearest = sketch.strokes.filterNot { it.id in alreadyErased }.mapNotNull { stroke ->
                    stroke.points.minOfOrNull { candidate ->
                        val dx = (candidate.x - p.x) * size.width
                        val dy = (candidate.y - p.y) * size.height
                        dx * dx + dy * dy
                    }?.let { stroke.id to it }
                }.minByOrNull { it.second }
                if (nearest != null && nearest.second <=
                    (22.dp.toPx() / liveZoom).let { it * it }) {
                    alreadyErased += nearest.first
                    latestErase(nearest.first)
                }
            }
            if (mode == SketchMode.ERASE) eraseAt(pointStart)
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    multiTouch = true
                    path.clear()
                    val current = liveZoom
                    val next = (current * event.calculateZoom()).coerceIn(1f, 5f)
                    val drift = event.calculatePan()
                    panX = (panX + drift.x).coerceIn(
                        -size.width * (next - 1f) / 2f, size.width * (next - 1f) / 2f)
                    panY = (panY + drift.y).coerceIn(
                        -size.height * (next - 1f) / 2f, size.height * (next - 1f) / 2f)
                    latestZoomChange(next)
                } else if (!multiTouch && pressed.size == 1) {
                    val change = pressed.first()
                    val p = coordinates(change.position.x, change.position.y, size.width, size.height)
                    val traveled = change.position - first.position
                    if (traveled.getDistance() > 9.dp.toPx()) moved = true
                    when (mode) {
                        SketchMode.DRAW -> {
                            val last = path.lastOrNull()
                            if (path.size < 8000 && last != p) path += p
                            inProgress = path.toList()
                        }
                        SketchMode.ERASE -> eraseAt(p)
                        SketchMode.PAN -> {
                            val movement = change.position - change.previousPosition
                            panX = (panX + movement.x).coerceIn(
                                -size.width * (liveZoom - 1f) / 2f,
                                size.width * (liveZoom - 1f) / 2f)
                            panY = (panY + movement.y).coerceIn(
                                -size.height * (liveZoom - 1f) / 2f,
                                size.height * (liveZoom - 1f) / 2f)
                        }
                        else -> Unit
                    }
                }
                event.changes.forEach { if (it.pressed) it.consume() }
                if (pressed.isEmpty()) break
            }
            if (!multiTouch && mode == SketchMode.DRAW && path.size >= 2)
                latestDraw(SketchStroke(points = path,
                    colorArgb = livePenColor, widthFraction = livePenWidth,
                    opacity = livePenOpacity))
            if (!multiTouch && mode == SketchMode.MARK && !moved)
                latestMark(pointStart.x, pointStart.y)
            inProgress = emptyList()
        }
    }
    BoxWithConstraints(modifier.background(TestarioSurface, RoundedCornerShape(14.dp)).then(gestures)) {
        val drawingWidth = maxWidth
        val drawingHeight = maxHeight
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = zoomFactor
            scaleY = zoomFactor
            translationX = panX
            translationY = panY
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val allStrokes = sketch.strokes + if (inProgress.size > 1) listOf(
                SketchStroke(points = inProgress,
                    colorArgb = penArgb, widthFraction = penWidthFraction, opacity = penOpacity)) else emptyList()
            for (stroke in allStrokes) {
                if (stroke.points.size < 2) continue
                val shape = Path()
                val start = stroke.points.first()
                shape.moveTo(size.width * start.x.toFloat(), size.height * start.y.toFloat())
                stroke.points.drop(1).forEach {
                    shape.lineTo(size.width * it.x.toFloat(), size.height * it.y.toFloat())
                }
                val oldColor = when (stroke.brush) {
                    "white" -> label
                    "green" -> green
                    "red" -> red
                    else -> purple
                }
                val brushColor = stroke.colorArgb?.let { Color(it.toInt()) } ?: oldColor
                val color = brushColor.copy(alpha = (stroke.opacity ?: 1.0).toFloat().coerceIn(0f,1f))
                val thickness = stroke.widthFraction?.let {
                    (minOf(size.width,size.height) * it.toFloat()).coerceAtLeast(1f)
                } ?: 5.dp.toPx()
                drawPath(shape, color, style = Stroke(
                    width = thickness, cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                ))
            }
        }
        if (showMarkers) markers.forEach { marker ->
            val statusColor = when (markerStatuses[marker.id]) {
                true -> green
                false -> red
                null -> purple
            }
            Text(marker.order.toString(), color = statusColor,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                modifier = Modifier.offset(
                    x = drawingWidth * marker.x.toFloat() - 8.dp,
                    y = drawingHeight * marker.y.toFloat() - 12.dp
                ))
        }
        }
    }
}
