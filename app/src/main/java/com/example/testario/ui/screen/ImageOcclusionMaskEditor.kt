package com.example.testario.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.ImageAnnotationPoint
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.ui.theme.TestarioPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Word-like handles: drag the body to move a cover, drag the lower-right
 * corner to resize. All edits use normalized image coordinates, not screen
 * pixels, and preserve IDs for session/review compatibility.
 */
@Composable
internal fun ResizableOcclusionImage(
    imageUri: String,
    masks: List<ImageOcclusionMask>,
    annotations: ImageAnnotations,
    onMasksChanged: (List<ImageOcclusionMask>) -> Unit,
    onAnnotationsChanged: (ImageAnnotations) -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }
    val latestMasks by rememberUpdatedState(masks)
    val latestOnMasksChanged by rememberUpdatedState(onMasksChanged)
    var selectedId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(Modifier.fillMaxWidth().height(270.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val fit = intrinsic?.let { fitImageToBox(it.first, it.second, widthPx, heightPx) }

        EditableAnnotationImage(
            imageUri = imageUri, annotations = annotations,
            modifier = Modifier.fillMaxSize(), masks = masks,
            onMarkerTap = { x, y ->
                // New covers fit inside the photo and can be edited immediately.
                val width = .18
                val height = .12
                val mask = ImageOcclusionMask(
                    x = x.coerceIn(0.0, 1.0 - width),
                    y = y.coerceIn(0.0, 1.0 - height),
                    width = width, height = height
                )
                selectedId = mask.id
                onMasksChanged(masks + mask)
            },
            onAnnotationsChanged = onAnnotationsChanged,
            onReplace = onReplace, onDelete = onDelete
        )
        if (fit != null) masks.forEach { mask ->
            val left = with(density) { (fit.left + fit.width * mask.x.toFloat()).toDp() }
            val top = with(density) { (fit.top + fit.height * mask.y.toFloat()).toDp() }
            val boxWidth = with(density) { (fit.width * mask.width.toFloat()).toDp() }
            val boxHeight = with(density) { (fit.height * mask.height.toFloat()).toDp() }
            val chosen = selectedId == mask.id
            Box(
                Modifier.offset(x = left, y = top)
                    .size(boxWidth, boxHeight)
                    .border(if (chosen) 3.dp else 2.dp,
                        if (chosen) TestarioPrimary else Color.White,
                        RoundedCornerShape(4.dp))
                    .pointerInput(mask.id, fit.width, fit.height) {
                        detectDragGestures(
                            onDragStart = { selectedId = mask.id },
                            onDrag = { change, delta ->
                                change.consume()
                                val current = latestMasks.firstOrNull { it.id == mask.id }
                                    ?: return@detectDragGestures
                                val x = (current.x + delta.x / fit.width)
                                    .coerceIn(0.0, 1.0 - current.width)
                                val y = (current.y + delta.y / fit.height)
                                    .coerceIn(0.0, 1.0 - current.height)
                                latestOnMasksChanged(latestMasks.map {
                                    if (it.id == mask.id) it.copy(x = x, y = y) else it
                                })
                            }
                        )
                    }
            ) {
                // Resizing works independently of moving the mask.
                Box(
                    Modifier.align(Alignment.BottomEnd)
                        .size(28.dp)
                        .background(TestarioPrimary, RoundedCornerShape(5.dp))
                        .pointerInput(mask.id, fit.width, fit.height) {
                            detectDragGestures(
                                onDragStart = { selectedId = mask.id },
                                onDrag = { change, delta ->
                                    change.consume()
                                    val current = latestMasks.firstOrNull { it.id == mask.id }
                                        ?: return@detectDragGestures
                                    val w = (current.width + delta.x / fit.width)
                                        .coerceIn(.035, 1.0 - current.x)
                                    val h = (current.height + delta.y / fit.height)
                                        .coerceIn(.035, 1.0 - current.y)
                                    latestOnMasksChanged(latestMasks.map {
                                        if (it.id == mask.id) it.copy(width = w, height = h)
                                        else it
                                    })
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) { Text("↘", color = Color.Black) }
            }
        }
    }
    Text("Toca una zona para crear un bloque. Arrástralo para moverlo y usa su esquina ↘ para cambiar el tamaño.")
    masks.forEachIndexed { index, mask ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Zona ${index + 1}", modifier = Modifier.weight(1f))
            TextButton(onClick = {
                selectedId = if (selectedId == mask.id) null else mask.id
            }) { Text("Seleccionar") }
            TextButton(onClick = {
                selectedId = null
                onMasksChanged(masks.filterNot { it.id == mask.id })
            }) { Text("Quitar") }
        }
    }
}
