package com.example.testario.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.domain.OcclusionDrawRules
import com.example.testario.domain.OcclusionHandle
import com.example.testario.domain.OcclusionResize
import com.example.testario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Large-screen touch alternative to tiny Word-style corner handles.
 * Draw a rectangle with one finger, move it by dragging, or pinch and pan
 * with two fingers. Mask IDs and normalized geometry are never converted to
 * pixels in storage. The parent question is saved by the existing editor.
 */
@Composable
internal fun OcclusionTouchEditor(
    imageUri: String,
    annotations: ImageAnnotations,
    masks: List<ImageOcclusionMask>,
    onMasksChanged: (List<ImageOcclusionMask>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    val latestMasks by rememberUpdatedState(masks)
    var selectedId by remember(imageUri) { mutableStateOf(masks.lastOrNull()?.id) }
    var drawing by remember(imageUri) { mutableStateOf(masks.isEmpty()) }
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var start by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var finish by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var interrupted by remember { mutableStateOf(false) }
    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }

    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(TestarioBackground)
            .systemBarsPadding().padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ocultar imagen", color = TestarioText,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = onDismiss) {
                    Text("Terminar", color = TestarioPrimary)
                }
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = drawing, onClick = { drawing = true },
                    label = { Text("Dibujar zona") })
                FilterChip(selected = !drawing, onClick = { drawing = false },
                    label = { Text("Seleccionar / mover") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val next = (scale / 1.4f).coerceAtLeast(1f)
                    scale = next
                    if (next == 1f) pan = Offset.Zero
                }) { Text("−", color = TestarioPrimary) }
                Text("${"%.1f".format(scale)}×", color = TestarioText)
                TextButton(onClick = { scale = (scale * 1.4f).coerceAtMost(8f) }) {
                    Text("+", color = TestarioPrimary)
                }
            }
            Text(if (drawing) "Arrastra un dedo sobre lo que quieres tapar."
                else "Toca o arrastra la zona. Usa dos dedos para ampliar y desplazar.",
                color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black).clipToBounds()
            ) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val fit = intrinsic?.let { fitImageToBox(it.first, it.second, boxW, boxH) }

                Box(Modifier.fillMaxSize().pointerInput(imageUri) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                interrupted = true
                                start = null
                                finish = null
                                val next = (scale * event.calculateZoom()).coerceIn(1f, 8f)
                                val delta = event.calculatePan()
                                val maxX = size.width * (next - 1f) / 2f
                                val maxY = size.height * (next - 1f) / 2f
                                scale = next
                                pan = if (next <= 1.01f) Offset.Zero else Offset(
                                    (pan.x + delta.x).coerceIn(-maxX, maxX),
                                    (pan.y + delta.y).coerceIn(-maxY, maxY)
                                )
                                event.changes.forEach { if (it.pressed) it.consume() }
                            }
                        } while (pressed.isNotEmpty())
                    }
                }) {
                    val drawGesture = if (drawing && fit != null) {
                        Modifier.pointerInput(fit, drawing) {
                            detectDragGestures(
                                onDragStart = { point ->
                                    interrupted = false
                                    val xy = fit.toImage(point.x, point.y)
                                    start = xy
                                    finish = xy
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (!interrupted && start != null) {
                                        // Dragging beyond the photo edge clips the rectangle.
                                        finish = (((change.position.x - fit.left) / fit.width)
                                            .toDouble().coerceIn(0.0, 1.0)) to
                                            (((change.position.y - fit.top) / fit.height)
                                                .toDouble().coerceIn(0.0, 1.0))
                                    }
                                },
                                onDragEnd = {
                                    val a = start
                                    val b = finish
                                    if (!interrupted && a != null && b != null &&
                                        latestMasks.size < 60) {
                                        OcclusionDrawRules.rectangle(
                                            a.first, a.second, b.first, b.second
                                        )?.let { mask ->
                                            onMasksChanged(latestMasks + mask)
                                            selectedId = mask.id
                                            drawing = false
                                        }
                                    }
                                    start = null
                                    finish = null
                                },
                                onDragCancel = {
                                    start = null
                                    finish = null
                                }
                            )
                        }
                    } else Modifier
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }.then(drawGesture)) {
                        StudyVisualImage(imageUri = imageUri, annotations = annotations,
                            modifier = Modifier.fillMaxSize(), enableFullscreen = false)
                        if (fit != null) {
                            masks.forEach { mask ->
                                val left = fit.left + fit.width * mask.x.toFloat()
                                val top = fit.top + fit.height * mask.y.toFloat()
                                val width = fit.width * mask.width.toFloat()
                                val height = fit.height * mask.height.toFloat()
                                val targetSize = with(density) { 34.dp.toPx() }
                                val targetW = if (drawing) width else maxOf(width, targetSize)
                                val targetH = if (drawing) height else maxOf(height, targetSize)
                                val selected = mask.id == selectedId
                                val interaction = if (!drawing) {
                                    Modifier.clickable { selectedId = mask.id }
                                        .pointerInput(mask.id, fit) {
                                            detectDragGestures(
                                                onDragStart = { selectedId = mask.id },
                                                onDrag = { change, drag ->
                                                    change.consume()
                                                    onMasksChanged(latestMasks.map { old ->
                                                        if (old.id == mask.id)
                                                            OcclusionResize.translate(old,
                                                                (drag.x / fit.width).toDouble(),
                                                                (drag.y / fit.height).toDouble())
                                                        else old
                                                    })
                                                }
                                            )
                                        }
                                } else Modifier
                                Box(
                                    Modifier.offset {
                                        IntOffset((left - (targetW - width) / 2f).roundToInt(),
                                            (top - (targetH - height) / 2f).roundToInt())
                                    }.size(with(density) { targetW.toDp() },
                                        with(density) { targetH.toDp() }).then(interaction),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(
                                        with(density) { width.coerceAtLeast(1f).toDp() },
                                        with(density) { height.coerceAtLeast(1f).toDp() })
                                        .background(Color.Black.copy(alpha = .78f))
                                        .border(if (selected) 2.dp else 1.dp,
                                            if (selected) TestarioPrimary else TestarioMutedText,
                                            RoundedCornerShape(2.dp)))
                                }
                            }
                            val a = start
                            val b = finish
                            if (drawing && a != null && b != null && !interrupted) {
                                val x = minOf(a.first, b.first)
                                val y = minOf(a.second, b.second)
                                val w = kotlin.math.abs(a.first - b.first)
                                val h = kotlin.math.abs(a.second - b.second)
                                Box(Modifier.offset {
                                    IntOffset((fit.left + fit.width * x).roundToInt(),
                                        (fit.top + fit.height * y).roundToInt())
                                }.size(with(density) { (fit.width * w).toDp() },
                                    with(density) { (fit.height * h).toDp() })
                                    .background(Color.Black.copy(alpha = .5f))
                                    .border(2.dp, TestarioPrimary))
                            }
                        }
                    }
                }
            }

            if (masks.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    masks.forEachIndexed { index, mask ->
                        FilterChip(selected = selectedId == mask.id,
                            onClick = { selectedId = mask.id; drawing = false },
                            label = { Text("Zona ${index + 1}") })
                    }
                }
                masks.firstOrNull { it.id == selectedId }?.let { selected ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(
                            Triple("←", -.005, .0), Triple("→", .005, .0),
                            Triple("↑", .0, -.005), Triple("↓", .0, .005)
                        ).forEach { (symbol, x, y) ->
                            TextButton(onClick = {
                                onMasksChanged(latestMasks.map { old ->
                                    if (old.id == selected.id)
                                        OcclusionResize.translate(old, x, y) else old
                                })
                            }) { Text(symbol, color = TestarioPrimary) }
                        }
                        TextButton(onClick = {
                            onMasksChanged(latestMasks.map { old ->
                                if (old.id == selected.id) OcclusionResize.apply(old,
                                    OcclusionHandle.BOTTOM_RIGHT, -.006, -.006) else old
                            })
                        }) { Text("− tamaño", color = TestarioPrimary) }
                        TextButton(onClick = {
                            onMasksChanged(latestMasks.map { old ->
                                if (old.id == selected.id) OcclusionResize.apply(old,
                                    OcclusionHandle.BOTTOM_RIGHT, .006, .006) else old
                            })
                        }) { Text("+ tamaño", color = TestarioPrimary) }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Zona seleccionada", color = TestarioMutedText,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            onMasksChanged(latestMasks.filterNot { it.id == selected.id })
                            selectedId = latestMasks.firstOrNull { it.id != selected.id }?.id
                        }) { Text("Eliminar", color = TestarioRed) }
                    }
                }
            }
        }
    }
}
