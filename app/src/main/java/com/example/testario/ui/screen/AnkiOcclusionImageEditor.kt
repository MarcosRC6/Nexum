package com.example.testario.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.testario.data.model.*
import com.example.testario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** WYSIWYG image occlusion: drag anywhere inside a cover to move it; resize
 * from the four corners, like an image in a document editor. All geometry
 * remains normalized and independent of the original bitmap.
 */
@Composable
internal fun AnkiOcclusionImageEditor(
    imageUri: String,
    annotations: ImageAnnotations,
    masks: List<ImageOcclusionMask>,
    onMasksChange: (List<ImageOcclusionMask>) -> Unit,
    onAnnotationsChanged: (ImageAnnotations) -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var size by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedId by remember(imageUri) { mutableStateOf<String?>(null) }
    val latestMasks by rememberUpdatedState(masks)
    val latestCallback by rememberUpdatedState(onMasksChange)

    LaunchedEffect(imageUri) {
        size = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().height(270.dp)) {
        val density = LocalDensity.current
        val areaWidth = with(density) { maxWidth.toPx() }
        val areaHeight = with(density) { maxHeight.toPx() }
        val fit = size?.let { fitImageToBox(it.first, it.second, areaWidth, areaHeight) }
        EditableAnnotationImage(
            imageUri = imageUri, annotations = annotations, masks = masks,
            modifier = Modifier.fillMaxSize(),
            onAnnotationsChanged = onAnnotationsChanged,
            onReplace = onReplace,
            onDelete = onDelete,
            onMarkerTap = { x, y ->
                val width = .16
                val height = .12
                val mask = ImageOcclusionMask(
                    x = (x - width / 2).coerceIn(0.0, 1.0 - width),
                    y = (y - height / 2).coerceIn(0.0, 1.0 - height),
                    width = width, height = height
                )
                latestCallback(latestMasks + mask)
                selectedId = mask.id
            }
        )
        if (fit != null) {
            masks.forEach { mask ->
                val selected = selectedId == mask.id
                val at = fit.locate(mask.x, mask.y)
                val left = with(density) { at.x.toDp() }
                val top = with(density) { at.y.toDp() }
                val w = with(density) { (fit.width * mask.width.toFloat()).toDp() }
                val h = with(density) { (fit.height * mask.height.toFloat()).toDp() }
                Box(
                    Modifier.offset(x = left, y = top)
                        .size(width = w, height = h)
                        .border(if (selected) 3.dp else 1.dp, TestarioPrimary,
                            RoundedCornerShape(3.dp))
                        .pointerInput(mask.id, fit) {
                            detectDragGestures { change, delta ->
                                change.consume()
                                val stepX = delta.x / fit.width
                                val stepY = delta.y / fit.height
                                latestCallback(latestMasks.map {
                                    if (it.id != mask.id) it else it.copy(
                                        x = (it.x + stepX).coerceIn(0.0, 1.0 - it.width),
                                        y = (it.y + stepY).coerceIn(0.0, 1.0 - it.height)
                                    )
                                })
                                selectedId = mask.id
                            }
                        }
                        .clickable { selectedId = mask.id }
                ) {
                    if (selected) {
                        listOf(
                            Alignment.TopStart to 0,
                            Alignment.TopEnd to 1,
                            Alignment.BottomStart to 2,
                            Alignment.BottomEnd to 3
                        ).forEach { (alignment, corner) ->
                            Box(
                                Modifier.align(alignment)
                                    .size(24.dp)
                                    .background(TestarioPrimary, RoundedCornerShape(4.dp))
                                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                                    .pointerInput(mask.id, corner, fit) {
                                        detectDragGestures { change, delta ->
                                            change.consume()
                                            val dx = delta.x / fit.width
                                            val dy = delta.y / fit.height
                                            latestCallback(latestMasks.map {
                                                if (it.id != mask.id) it
                                                else resizeOcclusionMask(it, corner, dx, dy)
                                            })
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
    Text(
        "Toca una zona libre para taparla. Selecciona el recuadro y arrastra su centro para moverlo o sus esquinas moradas para cambiar su tamaño.",
        color = TestarioMutedText,
        style = MaterialTheme.typography.bodySmall
    )
}

/** Corner order: top-left, top-right, bottom-left, bottom-right. */
internal fun resizeOcclusionMask(
    mask: ImageOcclusionMask, corner: Int, dx: Float, dy: Float
): ImageOcclusionMask {
    val minSize = .035
    val oldRight = mask.x + mask.width
    val oldBottom = mask.y + mask.height
    val left = if (corner == 0 || corner == 2)
        (mask.x + dx.toDouble()).coerceIn(0.0, oldRight - minSize) else mask.x
    val top = if (corner == 0 || corner == 1)
        (mask.y + dy.toDouble()).coerceIn(0.0, oldBottom - minSize) else mask.y
    val right = if (corner == 1 || corner == 3)
        (oldRight + dx.toDouble()).coerceIn(left + minSize, 1.0) else oldRight
    val bottom = if (corner == 2 || corner == 3)
        (oldBottom + dy.toDouble()).coerceIn(top + minSize, 1.0) else oldBottom
    return mask.copy(x = left, y = top,
        width = right - left, height = bottom - top)
}
