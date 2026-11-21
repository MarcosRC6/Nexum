package com.example.testario.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.domain.OcclusionHandle
import com.example.testario.domain.OcclusionResize
import com.example.testario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Word-like editable occlusion blocks. The original image is drawn beneath them.
 * A block can be moved by dragging its body and resized from eight edge/corner handles.
 * All coordinates stay normalized so changing screen size never moves hidden content.
 */
@Composable
internal fun EditableOcclusionImage(
    imageUri: String,
    annotations: ImageAnnotations,
    masks: List<ImageOcclusionMask>,
    onMasksChanged: (List<ImageOcclusionMask>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    var selectedId by remember(imageUri) { mutableStateOf<String?>(null) }
    var precisionExpanded by remember(imageUri) { mutableStateOf(false) }
    val latestMasks by rememberUpdatedState(masks)
    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Tapar partes de la imagen", color = TestarioText,
                modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val count = masks.size
                if (count < 60) {
                    val x = (.12 + (count % 4) * .12).coerceAtMost(.7)
                    val y = (.12 + (count % 4) * .10).coerceAtMost(.7)
                    val mask = ImageOcclusionMask(x = x, y = y, width = .24, height = .12)
                    onMasksChanged(masks + mask)
                    selectedId = mask.id
                }
            }, enabled = intrinsic != null && masks.size < 60) {
                Text("+ Añadir bloque")
            }
        }
        Text("Arrastra el centro para mover; ajusta las esquinas o usa «Ajuste fino» para zonas pequeñas.",
            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
        BoxWithConstraints(Modifier.fillMaxWidth().height(270.dp)) {
            val density = LocalDensity.current
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val fit = intrinsic?.let { fitImageToBox(it.first, it.second, boxW, boxH) }
            StudyVisualImage(imageUri = imageUri, annotations = annotations,
                modifier = Modifier.fillMaxSize())
            if (fit != null) {
                masks.forEach { mask ->
                    val left = (fit.left + fit.width * mask.x).roundToInt()
                    val top = (fit.top + fit.height * mask.y).roundToInt()
                    val widthPx = (fit.width * mask.width).coerceAtLeast(20.0).toFloat()
                    val heightPx = (fit.height * mask.height).coerceAtLeast(20.0).toFloat()
                    val selected = mask.id == selectedId
                    Box(
                        Modifier.offset { IntOffset(left, top) }
                            .size(with(density) { widthPx.toDp() },
                                with(density) { heightPx.toDp() })
                            .background(Color.Black.copy(alpha = .93f), RoundedCornerShape(4.dp))
                            .border(if (selected) 3.dp else 1.dp,
                                TestarioPrimary, RoundedCornerShape(4.dp))
                            .clickable { selectedId = mask.id }
                            .pointerInput(mask.id, fit) {
                                detectDragGestures(
                                    onDragStart = { selectedId = mask.id },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        onMasksChanged(latestMasks.map {
                                            if (it.id != mask.id) it else it.copy(
                                                x = (it.x + drag.x / fit.width).coerceIn(0.0, 1.0 - it.width),
                                                y = (it.y + drag.y / fit.height).coerceIn(0.0, 1.0 - it.height)
                                            )
                                        })
                                    }
                                )
                            }
                    ) {
                        Text("Oculto", color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.Center))
                        if (selected) {
                            val compact = widthPx < 90f || heightPx < 90f
                            val verySmall = widthPx < 45f || heightPx < 45f
                            val handles = when {
                                verySmall -> listOf(Triple(
                                    OcclusionHandle.BOTTOM_RIGHT, Alignment.BottomEnd, "↘"))
                                compact -> listOf(
                                    Triple(OcclusionHandle.TOP_LEFT, Alignment.TopStart, "↖"),
                                    Triple(OcclusionHandle.TOP_RIGHT, Alignment.TopEnd, "↗"),
                                    Triple(OcclusionHandle.BOTTOM_LEFT, Alignment.BottomStart, "↙"),
                                    Triple(OcclusionHandle.BOTTOM_RIGHT, Alignment.BottomEnd, "↘"))
                                else -> listOf(
                                    Triple(OcclusionHandle.TOP_LEFT, Alignment.TopStart, "↖"),
                                    Triple(OcclusionHandle.TOP, Alignment.TopCenter, "↑"),
                                    Triple(OcclusionHandle.TOP_RIGHT, Alignment.TopEnd, "↗"),
                                    Triple(OcclusionHandle.LEFT, Alignment.CenterStart, "←"),
                                    Triple(OcclusionHandle.RIGHT, Alignment.CenterEnd, "→"),
                                    Triple(OcclusionHandle.BOTTOM_LEFT, Alignment.BottomStart, "↙"),
                                    Triple(OcclusionHandle.BOTTOM, Alignment.BottomCenter, "↓"),
                                    Triple(OcclusionHandle.BOTTOM_RIGHT, Alignment.BottomEnd, "↘"))
                            }
                            handles.forEach { (handle, alignment, symbol) ->
                                Box(
                                    Modifier.align(alignment).size(if (compact) 12.dp else 14.dp)
                                        .background(TestarioPrimary, RoundedCornerShape(5.dp))
                                        .pointerInput(mask.id, fit, handle) {
                                            detectDragGestures(onDrag = { change, drag ->
                                                change.consume()
                                                onMasksChanged(latestMasks.map {
                                                    if (it.id != mask.id) it else
                                                        OcclusionResize.apply(
                                                            it, handle,
                                                            (drag.x / fit.width).toDouble(),
                                                            (drag.y / fit.height).toDouble()
                                                        )
                                                })
                                            })
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(symbol, color = TestarioBackground,
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
        val selectedMask = masks.firstOrNull { it.id == selectedId }
        if (selectedMask != null) {
            TextButton(onClick = { precisionExpanded = !precisionExpanded }) {
                Text(if (precisionExpanded) "Ocultar ajuste fino" else "Ajuste fino del bloque seleccionado",
                    color = TestarioPrimary)
            }
            if (precisionExpanded) {
                // The existing geometry/IDs are preserved; only normalized bounds change.
                listOf("Horizontal", "Vertical", "Anchura", "Altura").forEach { axis ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(axis, modifier = Modifier.width(90.dp), color = TestarioText)
                        val value = when (axis) {
                            "Horizontal" -> selectedMask.x
                            "Vertical" -> selectedMask.y
                            "Anchura" -> selectedMask.width
                            else -> selectedMask.height
                        }
                        val upper = when (axis) {
                            "Horizontal" -> 1.0 - selectedMask.width
                            "Vertical" -> 1.0 - selectedMask.height
                            "Anchura" -> 1.0 - selectedMask.x
                            else -> 1.0 - selectedMask.y
                        }.coerceAtLeast(0.001)
                        Slider(
                            value = value.toFloat().coerceIn(0f, upper.toFloat()),
                            onValueChange = { v ->
                                onMasksChanged(latestMasks.map { mask ->
                                    if (mask.id != selectedMask.id) mask else when (axis) {
                                        "Horizontal" -> mask.copy(x = v.toDouble())
                                        "Vertical" -> mask.copy(y = v.toDouble())
                                        "Anchura" -> mask.copy(width = v.toDouble().coerceAtLeast(0.001))
                                        else -> mask.copy(height = v.toDouble().coerceAtLeast(0.001))
                                    }
                                })
                            },
                            valueRange = 0f..upper.toFloat(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (masks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${masks.size} ${if (masks.size == 1) "zona oculta" else "zonas ocultas"}",
                    color = TestarioMutedText, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val id = selectedId ?: masks.last().id
                    onMasksChanged(masks.filterNot { it.id == id })
                    selectedId = null
                }) { Text("Eliminar bloque seleccionado", color = TestarioRed) }
            }
        }
    }
}
