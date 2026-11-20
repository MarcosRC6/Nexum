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
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErase by rememberUpdatedState(onEraseStroke)
    val latestMark by rememberUpdatedState(onMarkTap)
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
    val gestures = when (mode) {
        SketchMode.DRAW -> Modifier.pointerInput(mode, zoomFactor, penArgb, penWidthFraction, penOpacity) {
            detectDragGestures(
                onDragStart = { start ->
                    inProgress = listOf(coordinates(start.x, start.y, size.width, size.height))
                },
                onDrag = { change, _ ->
                    change.consume()
                    val next = coordinates(
                        change.position.x, change.position.y, size.width, size.height)
                    val previous = inProgress.lastOrNull()
                    if (inProgress.size < 8000 && (previous == null ||
                        (next.x - previous.x) * (next.x - previous.x) +
                            (next.y - previous.y) * (next.y - previous.y) > 0.000003)) {
                        inProgress = inProgress + next
                    }
                },
                onDragEnd = {
                    val points = inProgress
                    if (points.size >= 2) latestDraw(SketchStroke(
                        points = points, colorArgb = penArgb, widthFraction = penWidthFraction,
                        opacity = penOpacity
                    ))
                    inProgress = emptyList()
                },
                onDragCancel = { inProgress = emptyList() }
            )
        }
        SketchMode.ERASE, SketchMode.MARK -> Modifier.pointerInput(mode, sketch, markers, zoomFactor) {
            detectTapGestures { pos ->
                val p = coordinates(pos.x, pos.y, size.width, size.height)
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
        SketchMode.PAN -> Modifier.pointerInput(mode, zoomFactor) {
            detectDragGestures { change, drag ->
                change.consume()
                panX = (panX + drag.x).coerceIn(-size.width * (zoomFactor - 1f) / 2f,
                    size.width * (zoomFactor - 1f) / 2f)
                panY = (panY + drag.y).coerceIn(-size.height * (zoomFactor - 1f) / 2f,
                    size.height * (zoomFactor - 1f) / 2f)
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
