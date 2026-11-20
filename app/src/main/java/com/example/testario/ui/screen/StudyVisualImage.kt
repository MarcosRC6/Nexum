package com.example.testario.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
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
    markerStatuses: Map<String, Boolean> = emptyMap(),
    onTapImage: ((Double, Double) -> Unit)? = null
) {
    val context = LocalContext.current
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
        val touch = if (onTapImage != null && fit != null) {
            Modifier.pointerInput(imageUri, fit, onTapImage) {
                detectTapGestures { p ->
                    fit.toImage(p.x, p.y)?.let { (x,y) -> onTapImage(x,y) }
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
                            val topLeft = fit.locate(m.x, m.y)
                            drawRect(
                                color = Color.Black.copy(alpha = .94f),
                                topLeft = topLeft,
                                size = Size(fit.width * m.width.toFloat(), fit.height * m.height.toFloat())
                            )
                        }
                    }
                    // Written-answer locations are QUESTIONS, not answer reveals.
                    // Show 1/2/3 before typing, then color each location on correction.
                    markers.forEach { marker ->
                        drawCircle(
                            color = when (markerStatuses[marker.id]) {
                                true -> Color(0xFF21784D)
                                false -> Color(0xFF9D263A)
                                null -> Color(0xFF9347D9)
                            }, radius = 16.dp.toPx(), center = fit.locate(marker.x, marker.y)
                        )
                    }
                }
                markers.forEach { p ->
                    val at = fit.locate(p.x, p.y)
                    Text(
                        text = p.order.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.offset(
                            x = with(density) { at.x.toDp() } - 8.dp,
                            y = with(density) { at.y.toDp() } - 15.dp
                        )
                    )
                }
            }
        }
    }
}
