package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
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
    onZoomChange: (Float) -> Unit = {},
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
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErase by rememberUpdatedState(onEraseStroke)
    val latestMark by rememberUpdatedState(onMarkTap)
    val latestZoom by rememberUpdatedState(zoomFactor)
    val latestZoomChange by rememberUpdatedState(onZoomChange)
    val latestSketch by rememberUpdatedState(sketch)
    val latestPenArgb by rememberUpdatedState(penArgb)
    val latestPenWidth by rememberUpdatedState(penWidthFraction)
    val latestPenOpacity by rememberUpdatedState(penOpacity)
    LaunchedEffect(mode) { inProgress = emptyList() }
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = zoomFactor.coerceIn(1f, 5f)
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }
    // One recognizer for one-finger painting/erasing and two-finger zoom/pan.
    // The second finger cancels an unfinished stroke so pinching never draws.
    val gestures = when (mode) {
        SketchMode.DRAW, SketchMode.ERASE, SketchMode.PAN ->
            Modifier.pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val points = mutableListOf<SketchPoint>()
                    val erased = hashSetOf<String>()
                    var multiTouch = false
                    var gestureZoom = latestZoom

                    fun canvasPoint(px: Float, py: Float): SketchPoint {
                        val z = latestZoom.coerceIn(1f, 5f)
                        val x = (px - size.width / 2f - panX) / z + size.width / 2f
                        val y = (py - size.height / 2f - panY) / z + size.height / 2f
                        return SketchPoint((x / size.width).toDouble().coerceIn(0.0, 1.0),
                            (y / size.height).toDouble().coerceIn(0.0, 1.0))
                    }
                    fun eraseAt(p: SketchPoint) {
                        val z = latestZoom.coerceIn(1f, 5f)
                        val thresholdSquared = (24.dp.toPx() / z).let { it * it }.toDouble()
                        val hit = latestSketch.strokes.filterNot { it.id in erased }
                            .mapNotNull { stroke ->
                                stroke.points.minOfOrNull { pt ->
                                    val dx = (pt.x - p.x) * size.width
                                    val dy = (pt.y - p.y) * size.height
                                    dx * dx + dy * dy
                                }?.let { stroke.id to it }
                            }.minByOrNull { it.second }
                        if (hit != null && hit.second <= thresholdSquared) {
                            erased.add(hit.first)
                            latestErase(hit.first)
                        }
                    }
                    if (mode == SketchMode.DRAW) {
                        points += canvasPoint(down.position.x, down.position.y)
                        inProgress = points.toList()
                    }
                    if (mode == SketchMode.ERASE) eraseAt(canvasPoint(
                        down.position.x, down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                            multiTouch = true
                            points.clear()
                            inProgress = emptyList()
                            val oldZoom = gestureZoom
                            val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, 5f)
                            gestureZoom = newZoom
                            val pan = event.calculatePan()
                            val maxX = size.width * (newZoom - 1f) / 2f
                            val maxY = size.height * (newZoom - 1f) / 2f
                            panX = (panX + pan.x).coerceIn(-maxX, maxX)
                            panY = (panY + pan.y).coerceIn(-maxY, maxY)
                            if (newZoom != oldZoom) latestZoomChange(newZoom)
                        } else if (pressed.size == 1 && !multiTouch) {
                            val change = pressed.first()
                            val pos = canvasPoint(change.position.x, change.position.y)
                            when (mode) {
                                SketchMode.DRAW -> {
                                    if (points.size < 8000 && points.lastOrNull() != pos) {
                                        points.add(pos)
                                        inProgress = points.toList()
                                    }
                                }
                                SketchMode.ERASE -> eraseAt(pos)
                                SketchMode.PAN -> {
                                    val step = change.position - change.previousPosition
                                    val z = latestZoom
                                    val maxX = size.width * (z - 1f) / 2f
                                    val maxY = size.height * (z - 1f) / 2f
                                    panX = (panX + step.x).coerceIn(-maxX, maxX)
                                    panY = (panY + step.y).coerceIn(-maxY, maxY)
                                }
                                else -> Unit
                            }
                        }
                        event.changes.forEach { if (it.pressed) it.consume() }
                        if (pressed.isEmpty()) break
                    }
                    if (mode == SketchMode.DRAW && !multiTouch && points.size >= 2) {
                        latestDraw(SketchStroke(
                            points = points,
                            colorArgb = latestPenArgb,
                            widthFraction = latestPenWidth,
                            opacity = latestPenOpacity
                        ))
                    }
                    inProgress = emptyList()
                }
            }
        SketchMode.MARK -> Modifier.pointerInput(mode, zoomFactor, markers) {
            detectTapGestures { pos ->
                val p = coordinates(pos.x, pos.y, size.width, size.height)
                latestMark(p.x, p.y)
            }
        }
        SketchMode.PREVIEW -> Modifier
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
                SketchStroke(points = inProgress, colorArgb = penArgb,
                    widthFraction = penWidthFraction, opacity = penOpacity)) else emptyList()
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
