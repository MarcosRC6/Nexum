package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
    onDrawStroke: (SketchStroke) -> Unit = {},
    onEraseStroke: (String) -> Unit = {},
    onMarkTap: (Double, Double) -> Unit = { _, _ -> }
) {
    var inProgress by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var pinchFactor by remember { mutableFloatStateOf(1f) }
    val effectiveZoom = (zoomFactor * pinchFactor).coerceIn(1f, 5f)
    val latestZoom by rememberUpdatedState(effectiveZoom)
    val latestSliderZoom by rememberUpdatedState(zoomFactor)
    val latestSketch by rememberUpdatedState(sketch)
    val latestBrushColor by rememberUpdatedState(penArgb)
    val latestBrushWidth by rememberUpdatedState(penWidthFraction)
    val latestBrushAlpha by rememberUpdatedState(penOpacity)
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErase by rememberUpdatedState(onEraseStroke)
    val latestMark by rememberUpdatedState(onMarkTap)
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = latestZoom
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }

    // A single recognizer handles both fingers without ever drawing/erasing
    // on a pinch. Erasing follows the finger continuously like the pen.
    val gestures = if (mode == SketchMode.PREVIEW) Modifier else
        Modifier.pointerInput(mode) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var multiTouch = false
                var moved = false
                var lastErased: String? = null
                var markerPoint = coordinates(down.position.x, down.position.y, size.width, size.height)
                if (mode == SketchMode.DRAW) inProgress = listOf(markerPoint)
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        multiTouch = true
                        inProgress = emptyList()
                        val factor = event.calculateZoom().coerceIn(.5f, 2f)
                        val sliderZoom = latestSliderZoom.coerceIn(1f, 5f)
                        pinchFactor = ((latestZoom * factor).coerceIn(1f,5f) / sliderZoom)
                        val pan = event.calculatePan()
                        panX = (panX + pan.x).coerceIn(
                            -size.width * (latestZoom - 1f) / 2f,
                            size.width * (latestZoom - 1f) / 2f
                        )
                        panY = (panY + pan.y).coerceIn(
                            -size.height * (latestZoom - 1f) / 2f,
                            size.height * (latestZoom - 1f) / 2f
                        )
                    } else if (pressed.size == 1 && !multiTouch) {
                        val point = pressed[0].position
                        val next = coordinates(point.x, point.y, size.width, size.height)
                        if (next != markerPoint) moved = true
                        when (mode) {
                            SketchMode.DRAW -> {
                                if (inProgress.size < 8000 && inProgress.lastOrNull() != next)
                                    inProgress = inProgress + next
                            }
                            SketchMode.ERASE -> {
                                val nearest = latestSketch.strokes.mapNotNull { stroke ->
                                    stroke.points.minOfOrNull { sp ->
                                        val dx = (sp.x - next.x) * size.width
                                        val dy = (sp.y - next.y) * size.height
                                        dx * dx + dy * dy
                                    }?.let { stroke.id to it }
                                }.minByOrNull { it.second }
                                if (nearest != null && nearest.second <
                                    (25.dp.toPx() / latestZoom).let { it * it } &&
                                    nearest.first != lastErased) {
                                    lastErased = nearest.first
                                    latestErase(nearest.first)
                                }
                            }
                            SketchMode.PAN -> {
                                val delta = pressed[0].positionChange()
                                panX = (panX + delta.x).coerceIn(
                                    -size.width * (latestZoom - 1f) / 2f,
                                    size.width * (latestZoom - 1f) / 2f
                                )
                                panY = (panY + delta.y).coerceIn(
                                    -size.height * (latestZoom - 1f) / 2f,
                                    size.height * (latestZoom - 1f) / 2f
                                )
                            }
                            SketchMode.MARK -> markerPoint = next
                            else -> Unit
                        }
                    }
                    event.changes.forEach { if (it.pressed) it.consume() }
                    if (pressed.isEmpty()) break
                }
                if (!multiTouch) {
                    when (mode) {
                        SketchMode.DRAW -> if (inProgress.size >= 2) latestDraw(
                            SketchStroke(points = inProgress,
                                colorArgb = latestBrushColor, widthFraction = latestBrushWidth,
                                opacity = latestBrushAlpha)
                        )
                        SketchMode.MARK -> latestMark(markerPoint.x, markerPoint.y)
                        else -> Unit
                    }
                }
                inProgress = emptyList()
            }
        }
    BoxWithConstraints(modifier.background(TestarioSurface, RoundedCornerShape(14.dp)).then(gestures)) {
        val drawingWidth = maxWidth
        val drawingHeight = maxHeight
        Box(Modifier.fillMaxSize().graphicsLayer {
            scaleX = effectiveZoom
            scaleY = effectiveZoom
            translationX = panX
            translationY = panY
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val allStrokes = sketch.strokes + if (inProgress.size > 1) listOf(
                SketchStroke(points = inProgress)) else emptyList()
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
