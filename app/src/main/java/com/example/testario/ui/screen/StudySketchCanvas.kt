package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.clip
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
    val latestSketch by rememberUpdatedState(sketch)
    val currentZoom by rememberUpdatedState(zoomFactor)
    val changeZoom by rememberUpdatedState(onZoomChange)
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = currentZoom.coerceIn(1f, 5f)
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }
    // One recognizer handles both drawing/continuous erasing and pinch zoom.
    // A second finger cancels the in-progress brush so zoom never creates ink.
    val gestures = Modifier.pointerInput(mode, penArgb, penWidthFraction, penOpacity) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var pinched = false
            var moved = false
            var points = mutableListOf(coordinates(down.position.x, down.position.y, size.width, size.height))
            val erased = mutableSetOf<String>()

            fun eraseAt(px: Float, py: Float) {
                val p = coordinates(px, py, size.width, size.height)
                val radius = 26.dp.toPx() / currentZoom.coerceAtLeast(1f)
                val radiusSq = radius * radius
                latestSketch.strokes.forEach { stroke ->
                    if (stroke.id !in erased && stroke.points.any {
                        val dx = (it.x - p.x) * size.width
                        val dy = (it.y - p.y) * size.height
                        dx * dx + dy * dy < radiusSq
                    }) erased += stroke.id
                }
            }
            if (mode == SketchMode.ERASE) eraseAt(down.position.x, down.position.y)

            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    pinched = true
                    points.clear()
                    erased.clear()
                    inProgress = emptyList()
                    val nextZoom = (currentZoom * event.calculateZoom()).coerceIn(1f, 5f)
                    changeZoom(nextZoom)
                    val pan = event.calculatePan()
                    panX += pan.x
                    panY += pan.y
                    val limitX = size.width * (nextZoom - 1f) / 2f
                    val limitY = size.height * (nextZoom - 1f) / 2f
                    panX = panX.coerceIn(-limitX, limitX)
                    panY = panY.coerceIn(-limitY, limitY)
                } else if (!pinched && pressed.size == 1) {
                    val c = pressed.first()
                    val delta = c.positionChange()
                    if (delta.x != 0f || delta.y != 0f) moved = true
                    when (mode) {
                        SketchMode.DRAW -> if (moved && points.size < 8000) {
                            val p = coordinates(c.position.x,c.position.y,size.width,size.height)
                            if (points.lastOrNull() != p) points.add(p)
                            inProgress = points.toList()
                        }
                        SketchMode.ERASE -> eraseAt(c.position.x,c.position.y)
                        SketchMode.PAN -> {
                            panX += delta.x
                            panY += delta.y
                            val limitX = size.width * (currentZoom - 1f) / 2f
                            val limitY = size.height * (currentZoom - 1f) / 2f
                            panX = panX.coerceIn(-limitX, limitX)
                            panY = panY.coerceIn(-limitY, limitY)
                        }
                        else -> {}
                    }
                }
                event.changes.forEach { if (it.pressed) it.consume() }
                if (pressed.isEmpty()) break
            }
            if (!pinched) when (mode) {
                SketchMode.DRAW -> if (points.size >= 2) latestDraw(SketchStroke(
                    points = points, colorArgb = penArgb,
                    widthFraction = penWidthFraction, opacity = penOpacity
                ))
                SketchMode.ERASE -> erased.forEach(latestErase)
                SketchMode.MARK -> if (!moved) latestMark(points.first().x, points.first().y)
                else -> {}
            }
            inProgress = emptyList()
        }
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(14.dp)).background(TestarioSurface).then(gestures)) {
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
