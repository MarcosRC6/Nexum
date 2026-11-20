package com.example.testario.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageAnnotationTool
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.data.model.ImagePoint
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/** Immutable content-to-screen transform. Does not confuse letterboxing with image pixels. */
internal data class FitImageTransform(
    val left: Float, val top: Float, val width: Float, val height: Float
) {
    fun locate(x: Double, y: Double): Offset =
        Offset(left + width * x.toFloat(), top + height * y.toFloat())

    fun toImage(px: Float, py: Float): Pair<Double, Double>? {
        if (width <= 0f || height <= 0f ||
            px < left || py < top || px > left + width || py > top + height) return null
        return ((px - left) / width).toDouble().coerceIn(0.0, 1.0) to
            ((py - top) / height).toDouble().coerceIn(0.0, 1.0)
    }
}

internal fun fitImageToBox(imageW: Int, imageH: Int, boxW: Float, boxH: Float): FitImageTransform? {
    if (imageW <= 0 || imageH <= 0 || boxW <= 0f || boxH <= 0f) return null
    val factor = min(boxW / imageW, boxH / imageH)
    val w = imageW * factor
    val h = imageH * factor
    return FitImageTransform((boxW - w) / 2f, (boxH - h) / 2f, w, h)
}


/** Reused by all image viewers and the annotation editor. */
internal fun DrawScope.drawImageAnnotations(
    annotations: ImageAnnotations, fit: FitImageTransform
) {
    val shorter = min(fit.width, fit.height)
    annotations.strokes.take(500).forEach { stroke ->
        val color = Color(stroke.colorArgb.toInt()).copy(
            alpha = stroke.opacity.toFloat().coerceIn(0f, 1f)
        )
        val brush = (shorter * stroke.widthFraction.toFloat()).coerceAtLeast(1f)
        val points = stroke.points.take(8000)
        if (points.size == 1) {
            drawCircle(color, radius = brush / 2f, center = fit.locate(points[0].x, points[0].y))
        } else if (points.size >= 2) {
            val shape = Path()
            val first = fit.locate(points[0].x, points[0].y)
            shape.moveTo(first.x, first.y)
            points.drop(1).forEach { point ->
                val pos = fit.locate(point.x, point.y)
                shape.lineTo(pos.x, pos.y)
            }
            drawPath(shape, color, style = Stroke(width = brush,
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/**
 * FIT media renderer for both editor and study. Anki masks are drawn on the
 * original image without baking answers into it. Markers are only visible when
 * showCorrection=true; pointer coordinates are normalized against actual pixels.
 */
@Composable
internal fun StudyVisualImage(
    imageUri: String,
    modifier: Modifier = Modifier,
    masks: List<ImageOcclusionMask> = emptyList(),
    markers: List<ImagePoint> = emptyList(),
    annotations: ImageAnnotations = ImageAnnotations(),
    showCorrection: Boolean = false,
    showMarkerNumbersBeforeReveal: Boolean = false,
    markerStatuses: Map<String, Boolean> = emptyMap(),
    onTapImage: ((Double, Double) -> Unit)? = null,
    onMaskUpdate: ((ImageOcclusionMask) -> Unit)? = null,
    selectedMaskId: String? = null,
    onMaskSelect: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val latestMasks by rememberUpdatedState(masks)
    val latestTap by rememberUpdatedState(onTapImage)
    val latestMaskUpdate by rememberUpdatedState(onMaskUpdate)
    val latestMaskSelect by rememberUpdatedState(onMaskSelect)
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) {
            orientedImageSize(context, imageUri)
        }
    }
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val boxW = with(density) { maxWidth.toPx() }
        val boxH = with(density) { maxHeight.toPx() }
        val fit = intrinsic?.let { (w,h) -> fitImageToBox(w,h,boxW,boxH) }
        val touch = if (onMaskUpdate != null && fit != null) {
            Modifier.pointerInput(imageUri, fit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val origin = fit.toImage(down.position.x, down.position.y)
                    val originX = origin?.first ?: -1.0
                    val originY = origin?.second ?: -1.0
                    val tolerance = (16.dp.toPx() / min(fit.width, fit.height))
                        .toDouble().coerceIn(.012, .065)
                    val mask = latestMasks.asReversed().firstOrNull { m ->
                        originX >= m.x - tolerance && originX <= m.x + m.width + tolerance &&
                            originY >= m.y - tolerance && originY <= m.y + m.height + tolerance
                    }
                    val handle = if (mask == null) "" else {
                        val left = kotlin.math.abs(originX - mask.x) <= tolerance
                        val right = kotlin.math.abs(originX - mask.x - mask.width) <= tolerance
                        val top = kotlin.math.abs(originY - mask.y) <= tolerance
                        val bottom = kotlin.math.abs(originY - mask.y - mask.height) <= tolerance
                        when {
                            left && top -> "LT"
                            right && top -> "RT"
                            left && bottom -> "LB"
                            right && bottom -> "RB"
                            left -> "L"
                            right -> "R"
                            top -> "T"
                            bottom -> "B"
                            else -> "MOVE"
                        }
                    }
                    if (mask != null) latestMaskSelect?.invoke(mask.id)
                    var dragged = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size == 1 && mask != null) {
                            val position = pressed.first().position
                            val current = fit.toImage(position.x, position.y)
                            if (current != null) {
                                val dx = current.first - originX
                                val dy = current.second - originY
                                if (kotlin.math.abs(dx) > .002 || kotlin.math.abs(dy) > .002) {
                                    dragged = true
                                    var x = mask.x
                                    var y = mask.y
                                    var w = mask.width
                                    var h = mask.height
                                    if (handle == "MOVE") {
                                        x = (mask.x + dx).coerceIn(0.0, 1.0 - w)
                                        y = (mask.y + dy).coerceIn(0.0, 1.0 - h)
                                    } else {
                                        if (handle.contains("L")) {
                                            x = (mask.x + dx).coerceIn(0.0, mask.x + mask.width - .04)
                                            w = mask.width + mask.x - x
                                        }
                                        if (handle.contains("R"))
                                            w = (mask.width + dx).coerceIn(.04, 1.0 - x)
                                        if (handle.contains("T")) {
                                            y = (mask.y + dy).coerceIn(0.0, mask.y + mask.height - .04)
                                            h = mask.height + mask.y - y
                                        }
                                        if (handle.contains("B"))
                                            h = (mask.height + dy).coerceIn(.04, 1.0 - y)
                                    }
                                    latestMaskUpdate?.invoke(mask.copy(x = x, y = y, width = w, height = h))
                                }
                            }
                        } else if (pressed.size >= 2) {
                            // Two-finger gestures are reserved for zoom in the image editor.
                            dragged = true
                        }
                        event.changes.forEach { if (it.pressed) it.consume() }
                        if (pressed.isEmpty()) break
                    }
                    if (!dragged && mask == null && origin != null)
                        latestTap?.invoke(originX, originY)
                }
            }
        } else if (onTapImage != null && fit != null) {
            Modifier.pointerInput(imageUri, fit, onTapImage) {
                detectTapGestures { p ->
                    fit.toImage(p.x, p.y)?.let { (x, y) -> onTapImage(x, y) }
                }
            }
        } else Modifier
        Box(Modifier.fillMaxSize().then(touch)) {
            AsyncImage(
                model = if (imageUri.startsWith("/")) File(imageUri) else imageUri,
                contentDescription = "Imagen base del ejercicio",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            if (fit != null) {
                Canvas(Modifier.fillMaxSize()) {
                    drawImageAnnotations(annotations, fit)
                    if (!showCorrection) {
                        masks.forEach { m ->
                            val topLeft = fit.locate(m.x,m.y)
                            drawRect(
                                color = Color.Black.copy(alpha = 0.94f),
                                topLeft = topLeft,
                                size = Size((fit.width*m.width).toFloat(),
                                    (fit.height*m.height).toFloat())
                            )
                        }
                    }
                    if (onMaskUpdate != null && !showCorrection) {
                        val selected = masks.firstOrNull { it.id == selectedMaskId }
                        if (selected != null) {
                            val left = selected.x
                            val top = selected.y
                            val right = selected.x + selected.width
                            val bottom = selected.y + selected.height
                            drawRect(color = Color(0xFFBD83FA),
                                topLeft = fit.locate(left, top),
                                size = Size(fit.width * selected.width.toFloat(),
                                    fit.height * selected.height.toFloat()),
                                style = Stroke(width = 2.dp.toPx()))
                            listOf(
                                left to top,
                                (left + right) / 2 to top,
                                right to top,
                                left to (top + bottom) / 2,
                                right to (top + bottom) / 2,
                                left to bottom,
                                (left + right) / 2 to bottom,
                                right to bottom
                            ).forEach { (x,y) ->
                                drawCircle(Color.White, radius = 6.dp.toPx(), center = fit.locate(x,y))
                                drawCircle(Color(0xFFBD83FA), radius = 3.5.dp.toPx(),
                                    center = fit.locate(x,y))
                            }
                        }
                    }
                    if (showCorrection || showMarkerNumbersBeforeReveal) {
                        markers.forEach { p ->
                            drawCircle(color = when (markerStatuses[p.id]) {
                                true -> Color(0xFF51D79D)
                                false -> Color(0xFFEA6877)
                                null -> Color(0xFFA85AFF)
                            }, radius = 11.dp.toPx(), center = fit.locate(p.x,p.y))
                        }
                    }
                }
                if (showCorrection || showMarkerNumbersBeforeReveal) markers.forEach { p ->
                    val at = fit.locate(p.x, p.y)
                    Text(
                        text = p.order.toString(),
                        color = when (markerStatuses[p.id]) {
                            true -> Color(0xFF51D79D)
                            false -> Color(0xFFEA6877)
                            null -> Color.White
                        },
                        modifier = Modifier.offset(
                            x = with(density) { at.x.toDp() } - 6.dp,
                            y = with(density) { at.y.toDp() } - 12.dp
                        )
                    )
                }
            }
        }
    }
}
