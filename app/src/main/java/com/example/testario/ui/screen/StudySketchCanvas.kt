package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
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
    onErasePath: (List<SketchPoint>, Int, Int) -> Unit = { _, _, _ -> },
    onMarkTap: (Double, Double) -> Unit = { _, _ -> },
    enableFullscreen: Boolean = true
) {
    var fullScreen by remember { mutableStateOf(false) }
    var inProgress by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }
    var eraserPreview by remember { mutableStateOf<List<SketchPoint>>(emptyList()) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    val latestDraw by rememberUpdatedState(onDrawStroke)
    val latestErasePath by rememberUpdatedState(onErasePath)
    val latestMark by rememberUpdatedState(onMarkTap)
    val latestZoom by rememberUpdatedState(zoomFactor)
    val latestZoomChange by rememberUpdatedState(onZoomChange)
    val latestPen by rememberUpdatedState(Triple(penArgb, penWidthFraction, penOpacity))
    LaunchedEffect(zoomFactor) {
        if (zoomFactor <= 1.01f) {
            panX = 0f
            panY = 0f
        }
    }
    val purple = TestarioPrimary
    val green = TestarioGreen
    val red = TestarioRed
    val label = TestarioText
    val erasedPreviewColor = TestarioSurface
    fun coordinates(px: Float, py: Float, w: Int, h: Int): SketchPoint {
        val scale = latestZoom.coerceIn(1f, 5f)
        val x = (px - w / 2f - panX) / scale + w / 2f
        val y = (py - h / 2f - panY) / scale + h / 2f
        return SketchPoint((x / w).toDouble().coerceIn(0.0, 1.0),
            (y / h).toDouble().coerceIn(0.0, 1.0))
    }
    val gestures = when (mode) {
        SketchMode.DRAW, SketchMode.ERASE, SketchMode.PAN, SketchMode.MARK ->
            Modifier.pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var multiTouch = false
                    var tapCandidate = true
                    var tapDistance = 0f
                    var gesturePoints = if (mode == SketchMode.ERASE)
                        listOf(coordinates(down.position.x, down.position.y, size.width, size.height))
                        else emptyList()
                    while (true) {
                        val event = awaitPointerEvent()
                        val touches = event.changes.filter { it.pressed }
                        if (touches.size >= 2) {
                            multiTouch = true
                            gesturePoints = emptyList()
                            inProgress = emptyList()
                            eraserPreview = emptyList()
                            val oldZoom = latestZoom
                            val newZoom = (oldZoom * event.calculateZoom()).coerceIn(1f, 5f)
                            latestZoomChange(newZoom)
                            val shift = event.calculatePan()
                            val maxX = size.width * (newZoom - 1f) / 2f
                            val maxY = size.height * (newZoom - 1f) / 2f
                            panX = (panX + shift.x).coerceIn(-maxX, maxX)
                            panY = (panY + shift.y).coerceIn(-maxY, maxY)
                        } else if (!multiTouch && touches.size == 1) {
                            val change = touches.first()
                            val at = coordinates(change.position.x, change.position.y,
                                size.width, size.height)
                            when (mode) {
                                SketchMode.DRAW -> {
                                    if (gesturePoints.lastOrNull() != at && gesturePoints.size < 8000)
                                        gesturePoints = gesturePoints + at
                                    inProgress = gesturePoints
                                }
                                SketchMode.ERASE -> {
                                    if (gesturePoints.lastOrNull() != at && gesturePoints.size < 1200)
                                        gesturePoints = gesturePoints + at
                                    eraserPreview = gesturePoints
                                }
                                SketchMode.MARK -> {
                                    // Only a tap places a number; drags do not create points.
                                    tapDistance += change.positionChange().getDistance()
                                    if (tapDistance > viewConfiguration.touchSlop)
                                        tapCandidate = false
                                }
                                SketchMode.PAN -> {
                                    val displacement = change.positionChange()
                                    panX = (panX + displacement.x).coerceIn(
                                        -size.width * (latestZoom - 1f) / 2f,
                                        size.width * (latestZoom - 1f) / 2f
                                    )
                                    panY = (panY + displacement.y).coerceIn(
                                        -size.height * (latestZoom - 1f) / 2f,
                                        size.height * (latestZoom - 1f) / 2f
                                    )
                                }
                                else -> Unit
                            }
                        }
                        event.changes.forEach { if (it.pressed) it.consume() }
                        if (touches.isEmpty()) break
                    }
                    if (!multiTouch) {
                        if (mode == SketchMode.DRAW && gesturePoints.size >= 2) {
                            val (argb, thickness, alpha) = latestPen
                            latestDraw(SketchStroke(
                                points = gesturePoints, colorArgb = argb,
                                widthFraction = thickness, opacity = alpha
                            ))
                        } else if (mode == SketchMode.ERASE && gesturePoints.isNotEmpty()) {
                            latestErasePath(gesturePoints, size.width, size.height)
                        } else if (mode == SketchMode.MARK && tapCandidate) {
                            val position = coordinates(
                                down.position.x, down.position.y, size.width, size.height
                            )
                            latestMark(position.x, position.y)
                        }
                    }
                    inProgress = emptyList()
                    eraserPreview = emptyList()
                }
            }
        SketchMode.PREVIEW -> if (enableFullscreen) Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { fullScreen = true })
        } else Modifier
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
            if (eraserPreview.isNotEmpty() && mode == SketchMode.ERASE) {
                val eraser = Path()
                val first = eraserPreview.first()
                eraser.moveTo(size.width * first.x.toFloat(),
                    size.height * first.y.toFloat())
                eraserPreview.drop(1).forEach { point ->
                    eraser.lineTo(size.width * point.x.toFloat(),
                        size.height * point.y.toFloat())
                }
                val brush = 30.dp.toPx()
                if (eraserPreview.size == 1) {
                    drawCircle(erasedPreviewColor, radius = brush / 2f,
                        center = Offset(size.width * first.x.toFloat(),
                            size.height * first.y.toFloat()))
                } else {
                    drawPath(eraser, erasedPreviewColor, style = Stroke(width = brush,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        if (showMarkers) markers.forEach { marker ->
            val statusColor = when (markerStatuses[marker.id]) {
                true -> green
                false -> red
                null -> purple
            }
            Box(
                modifier = Modifier.offset(
                    x = drawingWidth * marker.x.toFloat() - 15.dp,
                    y = drawingHeight * marker.y.toFloat() - 15.dp
                ).size(30.dp).background(statusColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(marker.order.toString(), color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            }
        }
        }
    }
    if (fullScreen && enableFullscreen && mode == SketchMode.PREVIEW) {
        var previewZoom by remember { mutableFloatStateOf(1f) }
        var previewPan by remember { mutableStateOf(Offset.Zero) }
        Dialog(
            onDismissRequest = { fullScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF090C15))) {
                Box(Modifier.fillMaxSize().clipToBounds().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (previewZoom * zoom).coerceIn(1f, 6f)
                        val maxX = (size.width * (next - 1f) / 2f).coerceAtLeast(0f)
                        val maxY = (size.height * (next - 1f) / 2f).coerceAtLeast(0f)
                        previewZoom = next
                        previewPan = if (next <= 1.01f) Offset.Zero else Offset(
                            (previewPan.x + pan.x).coerceIn(-maxX, maxX),
                            (previewPan.y + pan.y).coerceIn(-maxY, maxY)
                        )
                    }
                }) {
                    StudySketchCanvas(
                        sketch = sketch, markers = markers, mode = SketchMode.PREVIEW,
                        showMarkers = showMarkers, markerStatuses = markerStatuses,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = previewZoom; scaleY = previewZoom
                            translationX = previewPan.x; translationY = previewPan.y
                        },
                        enableFullscreen = false
                    )
                }
                Box(Modifier.fillMaxWidth().padding(10.dp),
                    contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = { fullScreen = false },
                        modifier = Modifier.background(Color(0xD9090C15), CircleShape)) {
                        Text("✕", color = Color.White)
                    }
                }
            }
        }
    }

}
