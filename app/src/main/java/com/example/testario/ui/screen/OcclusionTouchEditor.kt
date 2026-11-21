package com.example.testario.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.positionChange
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
import com.example.testario.domain.OcclusionTouchPolicy
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
    var scale by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var start by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var finish by remember { mutableStateOf<Pair<Double, Double>?>(null) }
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
            // One touch mode: tap a region to select it, drag to move,
            // drag a corner to resize, tap elsewhere to deselect and tap
            // again to create the next region. No Draw/Move mode switch.
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
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

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f).background(Color.Black).clipToBounds()
            ) {
                val density = LocalDensity.current
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                val fit = intrinsic?.let { fitImageToBox(it.first, it.second, boxW, boxH) }

                Box(Modifier.fillMaxSize().pointerInput(imageUri, fit) {
                    if (fit == null) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        // The displayed photo is zoomed/panned in a graphicsLayer.
                        // Convert screen touches back to image-local FIT coords.
                        fun photoPoint(point: Offset): Pair<Double, Double>? {
                            val localX = (point.x - size.width / 2f - pan.x) / scale +
                                size.width / 2f
                            val localY = (point.y - size.height / 2f - pan.y) / scale +
                                size.height / 2f
                            return fit.toImage(localX, localY)
                        }
                        val initial = photoPoint(first.position)
                        val previousSelection = selectedId
                        val densityTolerance = with(density) { 20.dp.toPx() }
                        val hit = if (initial != null) OcclusionTouchPolicy.hitTest(
                            latestMasks, selectedId, initial.first, initial.second,
                            densityTolerance / (fit.width * scale).toDouble(),
                            densityTolerance / (fit.height * scale).toDouble()
                        ) else OcclusionTouchPolicy.Hit.Background
                        when (hit) {
                            is OcclusionTouchPolicy.Hit.Body -> selectedId = hit.id
                            is OcclusionTouchPolicy.Hit.Corner -> selectedId = hit.id
                            OcclusionTouchPolicy.Hit.Background -> {
                                if (previousSelection != null) selectedId = null
                                else if (initial != null) {
                                    start = initial
                                    finish = initial
                                }
                            }
                        }
                        var moved = false
                        var multiTouch = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.size >= 2) {
                                multiTouch = true
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
                                event.changes.forEach { it.consume() }
                            } else if (!multiTouch && event.changes.isNotEmpty()) {
                                val change = event.changes.first()
                                if ((change.position - first.position).getDistance() >
                                    viewConfiguration.touchSlop) moved = true
                                if (moved) {
                                    when (hit) {
                                        is OcclusionTouchPolicy.Hit.Body ->
                                            onMasksChanged(latestMasks.map { old ->
                                                if (old.id == hit.id)
                                                    OcclusionResize.translate(
                                                        old,
                                                        (change.positionChange().x /
                                                            (fit.width * scale)).toDouble(),
                                                        (change.positionChange().y /
                                                            (fit.height * scale)).toDouble()
                                                    ) else old
                                            })
                                        is OcclusionTouchPolicy.Hit.Corner ->
                                            onMasksChanged(latestMasks.map { old ->
                                                if (old.id == hit.id)
                                                    OcclusionResize.apply(
                                                        old, hit.handle,
                                                        (change.positionChange().x /
                                                            (fit.width * scale)).toDouble(),
                                                        (change.positionChange().y /
                                                            (fit.height * scale)).toDouble()
                                                    ) else old
                                            })
                                        OcclusionTouchPolicy.Hit.Background -> {
                                            if (previousSelection == null && initial != null)
                                                finish = photoPoint(change.position)
                                        }
                                    }
                                    change.consume()
                                }
                            }
                            if (pressed.isEmpty()) break
                        }
                        if (!multiTouch && hit == OcclusionTouchPolicy.Hit.Background &&
                            previousSelection == null && initial != null &&
                            latestMasks.size < 60) {
                            val added = if (moved) {
                                finish?.let { end ->
                                    OcclusionDrawRules.rectangle(
                                        initial.first, initial.second, end.first, end.second)
                                }
                            } else {
                                OcclusionTouchPolicy.createAt(initial.first, initial.second)
                            }
                            if (added != null) {
                                onMasksChanged(latestMasks + added)
                                selectedId = added.id
                            }
                        }
                        start = null
                        finish = null
                    }
                }) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    }) {
                        StudyVisualImage(imageUri = imageUri, annotations = annotations,
                            modifier = Modifier.fillMaxSize(), enableFullscreen = false)
                        if (fit != null) {
                            masks.forEach { mask ->
                                val left = fit.left + fit.width * mask.x.toFloat()
                                val top = fit.top + fit.height * mask.y.toFloat()
                                val width = fit.width * mask.width.toFloat()
                                val height = fit.height * mask.height.toFloat()
                                // On small masks, fan touch targets outward so all
                                // four corners remain usable on a phone without
                                // changing the mask's actual stored dimensions.
                                val targetSize = with(density) {
                                    (if (mask.id == selectedId) 88.dp else 36.dp).toPx()
                                }
                                val targetW = maxOf(width, targetSize)
                                val targetH = maxOf(height, targetSize)
                                val selected = mask.id == selectedId
                                Box(
                                    Modifier.offset {
                                        IntOffset((left - (targetW - width) / 2f).roundToInt(),
                                            (top - (targetH - height) / 2f).roundToInt())
                                    }.size(with(density) { targetW.toDp() },
                                        with(density) { targetH.toDp() }),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(Modifier.size(
                                        with(density) { width.coerceAtLeast(1f).toDp() },
                                        with(density) { height.coerceAtLeast(1f).toDp() })
                                        .background(Color.Black.copy(alpha = .78f))
                                        .border(if (selected) 2.dp else 1.dp,
                                            if (selected) TestarioPrimary else TestarioMutedText,
                                            RoundedCornerShape(2.dp)))
                                    if (selected) {
                                        listOf(
                                            OcclusionHandle.TOP_LEFT to Alignment.TopStart,
                                            OcclusionHandle.TOP_RIGHT to Alignment.TopEnd,
                                            OcclusionHandle.BOTTOM_LEFT to Alignment.BottomStart,
                                            OcclusionHandle.BOTTOM_RIGHT to Alignment.BottomEnd
                                        ).forEach { (handle, alignment) ->
                                            // The hit area is 36 dp; only the center 12 dp
                                            // is visible, avoiding bulky handles on tiny masks.
                                            Box(Modifier.align(alignment).size(36.dp),
                                                contentAlignment = Alignment.Center) {
                                                Box(Modifier.size(13.dp)
                                                    .background(TestarioPrimary, CircleShape)
                                                    .border(2.dp, Color.White, CircleShape))
                                            }
                                        }
                                    }
                                }
                            }
                            val a = start
                            val b = finish
                            if (a != null && b != null) {
                                val x = minOf(a.first, b.first)
                                val y = minOf(a.second, b.second)
                                val w = kotlin.math.abs(a.first - b.first)
                                val h = kotlin.math.abs(a.second - b.second)
                                Box(Modifier.offset {
                                    IntOffset((fit.left + fit.width * x.toFloat()).roundToInt(),
                                        (fit.top + fit.height * y.toFloat()).roundToInt())
                                }.size(with(density) { (fit.width * w.toFloat()).toDp() },
                                    with(density) { (fit.height * h.toFloat()).toDp() })
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
                            onClick = { selectedId = mask.id },
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
                    }
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
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
