package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import com.example.testario.data.model.*
import com.example.testario.domain.OcclusionGeometry
import com.example.testario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Editable image occlusion in normalized coordinates. Drag the dark rectangle
 * to move it, or the four violet corner handles to resize it like a Word shape.
 * The physical photo is never modified.
 */
@Composable
internal fun OcclusionMaskEditor(
    imageUri: String,
    annotations: ImageAnnotations,
    masks: List<ImageOcclusionMask>,
    onAddMask: (Double, Double) -> Unit,
    onMaskChanged: (ImageOcclusionMask) -> Unit,
    onAnnotationsChanged: (ImageAnnotations) -> Unit,
    onReplace: () -> Unit,
    onDeleteImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var dimensions by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(imageUri) {
        dimensions = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }
    BoxWithConstraints(modifier) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val transform = dimensions?.let { fitImageToBox(it.first, it.second, widthPx, heightPx) }

        EditableAnnotationImage(
            imageUri = imageUri,
            annotations = annotations,
            modifier = Modifier.fillMaxSize(),
            onMarkerTap = onAddMask,
            onAnnotationsChanged = onAnnotationsChanged,
            onReplace = onReplace,
            onDelete = onDeleteImage
        )
        if (transform != null) {
            masks.forEachIndexed { index, mask ->
                key(mask.id) {
                    MaskWithHandles(mask, index + 1, transform, onMaskChanged)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MaskWithHandles(
    mask: ImageOcclusionMask,
    number: Int,
    fit: FitImageTransform,
    onMaskChanged: (ImageOcclusionMask) -> Unit
) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(mask)
    val latestChange by rememberUpdatedState(onMaskChanged)
    val origin = fit.locate(mask.x, mask.y)
    val width = with(density) { (fit.width * mask.width.toFloat()).toDp() }
    val height = with(density) { (fit.height * mask.height.toFloat()).toDp() }
    val radius = RoundedCornerShape(6.dp)

    Box(
        Modifier.offset { IntOffset(origin.x.roundToInt(), origin.y.roundToInt()) }
            .size(width, height)
            .border(2.dp, TestarioPrimary, radius)
            .background(Color.Black.copy(alpha = .88f), radius)
            .pointerInput(mask.id, fit) {
                detectDragGestures { change, delta ->
                    change.consume()
                    latestChange(OcclusionGeometry.transform(
                        latest, OcclusionGeometry.Handle.MOVE,
                        delta.x.toDouble() / fit.width,
                        delta.y.toDouble() / fit.height
                    ))
                }
            }
    ) {
        Text("● ${number}", modifier = Modifier.align(Alignment.Center),
            color = Color.White, style = MaterialTheme.typography.titleMedium)
        val handles = listOf(
            OcclusionGeometry.Handle.TOP_LEFT to Alignment.TopStart,
            OcclusionGeometry.Handle.TOP_RIGHT to Alignment.TopEnd,
            OcclusionGeometry.Handle.BOTTOM_LEFT to Alignment.BottomStart,
            OcclusionGeometry.Handle.BOTTOM_RIGHT to Alignment.BottomEnd
        )
        handles.forEach { (handle, alignment) ->
            Box(
                Modifier.align(alignment).offset(
                    x = if (alignment == Alignment.TopStart || alignment == Alignment.BottomStart)
                        (-9).dp else 9.dp,
                    y = if (alignment == Alignment.TopStart || alignment == Alignment.TopEnd)
                        (-9).dp else 9.dp
                ).size(22.dp).border(2.dp, Color.White, RoundedCornerShape(4.dp))
                    .background(TestarioPrimary, RoundedCornerShape(4.dp))
                    .pointerInput(mask.id, handle, fit) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            latestChange(OcclusionGeometry.transform(
                                latest, handle,
                                delta.x.toDouble() / fit.width,
                                delta.y.toDouble() / fit.height
                            ))
                        }
                    }
            )
        }
    }
}
