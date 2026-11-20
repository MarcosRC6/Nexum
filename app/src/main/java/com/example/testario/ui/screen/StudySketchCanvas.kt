package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import kotlin.math.hypot
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
    val latestColor by rememberUpdatedState(penArgb)
    val latestWidth by rememberUpdatedState(penWidthFraction)
    val latestOpacity by rememberUpdatedState(penOpacity)
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = latestZoom.coerceIn(1f, 5f)
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }
    fun eraseAt(point: SketchPoint, width: Int, height: Int) {
        val tolerance = (23.dp.value * 2.5 / latestZoom.coerceAtLeast(1f)).toDouble()
        val nearest = latestSketch.strokes.mapNotNull { stroke ->
            val distance = stroke.points.zipWithNext().minOfOrNull { (a, c) ->
                val sx = a.x * width; val sy = a.y * height
                val ex = c.x * width; val ey = c.y * height
                val px = point.x * width; val py = point.y * height
                val dx = ex - sx; val dy = ey - sy
                val t = if (dx * dx + dy * dy < 0.001) 0.0 else
                    (((px - sx) * dx + (py - sy) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
                hypot(px - (sx + t * dx), py - (sy + t * dy))
            }
            distance?.let { stroke.id to it }
        }.minByOrNull { it.second }
        if (nearest != null && nearest.second <= tolerance) latestErase(nearest.first)
    }

    // A second touch immediately cancels painting/erasing; the same gesture
    // becomes pinch zoom and pan. Do not restart the pointer handler while zoom changes.
    val gestures = if (mode == SketchMode.PREVIEW) Modifier else Modifier.pointerInput(mode) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var path = if (mode == SketchMode.DRAW)
                listOf(coordinates(down.position.x, down.position.y, size.width, size.height))
                else emptyList()
            if (mode == SketchMode.ERASE) eraseAt(
                coordinates(down.position.x, down.position.y, size.width, size.height),
                size.width, size.height
            )
            var multitouch = false
            var moved = false
            var last = down.position
            val first = down.position
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    multitouch = true
                    path = emptyList()
                    val before = latestZoom.coerceIn(1f,5f)
                    val after = (before * event.calculateZoom()).coerceIn(1f,5f)
                    val pan = event.calculatePan()
                    panX += pan.x
                    panY += pan.y
                    latestZoomChange(after)
                } else if (!multitouch && pressed.size == 1) {
                    val change = pressed[0]
                    val pos = change.position
                    if (last != androidx.compose.ui.geometry.Offset.Unspecified) {
                        if ((pos - last).getDistance() >= 1f) moved = true
                    }
                    val point = coordinates(pos.x, pos.y, size.width, size.height)
                    when (mode) {
                        SketchMode.DRAW -> if (path.isEmpty() ||
                            hypot(point.x - path.last().x, point.y - path.last().y) > .0006)
                            path = (path + point).take(8000)
                        SketchMode.ERASE -> eraseAt(point, size.width, size.height)
                        SketchMode.PAN -> if (last != androidx.compose.ui.geometry.Offset.Unspecified) {
                            panX += pos.x - last.x
                            panY += pos.y - last.y
                        }
                        else -> {}
                    }
                    last = pos
                }
                if (pressed.isEmpty()) break
                event.changes.forEach { if (it.pressed) it.consume() }
            }
            if (!multitouch) {
                if (mode == SketchMode.DRAW && path.size >= 2) {
                    latestDraw(SketchStroke(
                        points = path, colorArgb = latestColor,
                        widthFraction = latestWidth, opacity = latestOpacity
                    ))
                } else if (mode == SketchMode.MARK && !moved &&
                    first != androidx.compose.ui.geometry.Offset.Unspecified) {
                    val p = coordinates(first.x, first.y, size.width, size.height)
                    latestMark(p.x, p.y)
                }
            }
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
