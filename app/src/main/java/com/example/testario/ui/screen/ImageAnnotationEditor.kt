package com.example.testario.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.testario.data.model.*
import com.example.testario.domain.ImageAnnotationRules
import com.example.testario.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class AnnotationSwatch(val name: String, val argb: Long)
private val annotationPalette = listOf(
    AnnotationSwatch("Morado", 0xFFA855F7L),
    AnnotationSwatch("Amarillo", 0xFFFFD54FL),
    AnnotationSwatch("Rojo", 0xFFEF5350L),
    AnnotationSwatch("Azul", 0xFF42A5F5L),
    AnnotationSwatch("Verde", 0xFF4CAF50L),
    AnnotationSwatch("Blanco", 0xFFFFFFFFL),
    AnnotationSwatch("Negro", 0xFF171717L)
)

/**
 * Reusable contextual menu for all authoring-image slots.
 * Image taps edit the current object's overlay, never the physical photo.
 */
@Composable
internal fun EditableAnnotationImage(
    imageUri: String,
    annotations: ImageAnnotations,
    modifier: Modifier = Modifier,
    markers: List<ImagePoint> = emptyList(),
    masks: List<ImageOcclusionMask> = emptyList(),
    showCorrection: Boolean = false,
    onAnnotationsChanged: (ImageAnnotations) -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    autoEdit: Boolean = false,
    onAutoEditConsumed: () -> Unit = {},
    /** When marking numbered answers, image taps must remain marker gestures. */
    onMarkerTap: ((Double, Double) -> Unit)? = null
) {
    var showActions by remember(imageUri) { mutableStateOf(false) }
    var editing by remember(imageUri) { mutableStateOf(false) }
    var viewing by remember(imageUri) { mutableStateOf(false) }
    var pendingReplace by remember(imageUri) { mutableStateOf(false) }
    var pendingDelete by remember(imageUri) { mutableStateOf(false) }

    LaunchedEffect(imageUri, autoEdit) {
        if (autoEdit) {
            editing = true
            onAutoEditConsumed()
        }
    }

    // Pencil always stays visible and never steals the tap used to place a
    // numbered solution or an Anki occlusion. Plain taps open the larger view.
    Box(modifier = modifier) {
        StudyVisualImage(
            imageUri = imageUri,
            annotations = annotations,
            markers = markers, masks = masks, showCorrection = showCorrection,
            modifier = Modifier.fillMaxSize(),
            onTapImage = onMarkerTap ?: { _, _ -> viewing = true }
        )
        Box(
            Modifier.align(Alignment.TopEnd).padding(8.dp)
                .background(Color(0xF2131B2B), CircleShape)
                .border(1.dp, TestarioPrimary, CircleShape)
                .size(44.dp)
                .semantics { contentDescription = "Editar imagen" }
                .clickable { showActions = true },
            contentAlignment = Alignment.Center
        ) {
            Text("✎", color = TestarioPrimary, fontSize = 25.sp)
        }
    }
    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("Imagen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick = { showActions = false; viewing = true }) { Text("Ver imagen") }
                    TextButton(onClick = { showActions = false; editing = true }) { Text("Editar anotaciones") }
                    TextButton(onClick = {
                        showActions = false
                        if (annotations.isEmpty) onReplace() else pendingReplace = true
                    }) { Text("Cambiar fotografía") }
                    TextButton(onClick = { showActions = false; pendingDelete = true }) {
                        Text("Eliminar fotografía", color = TestarioRed)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showActions = false }) { Text("Cerrar") } }
        )
    }
    if (viewing) {
        Dialog(
            onDismissRequest = { viewing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(
                Modifier.fillMaxSize().background(Color(0xFF0A0F1C)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { viewing = false }) { Text("Volver") }
                StudyVisualImage(
                    imageUri = imageUri, annotations = annotations,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
    if (editing) {
        ImageAnnotationEditorDialog(
            imageUri = imageUri,
            initial = annotations,
            onSave = { onAnnotationsChanged(it); editing = false },
            onCancel = { editing = false }
        )
    }
    if (pendingReplace || pendingDelete) {
        val replacing = pendingReplace
        AlertDialog(
            onDismissRequest = { pendingReplace = false; pendingDelete = false },
            title = { Text(if (replacing) "Cambiar fotografía" else "Eliminar fotografía") },
            text = { Text(
                if (replacing)
                    "Se perderán las anotaciones asociadas a esta fotografía si eliges otra. La imagen original permanece sin cambios."
                else "Se quitará esta imagen y sus anotaciones de este contenido. No se modificarán otras preguntas que usen el mismo archivo."
            ) },
            confirmButton = {
                TextButton(onClick = {
                    pendingReplace = false
                    pendingDelete = false
                    if (replacing) onReplace() else onDelete()
                }) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingReplace = false; pendingDelete = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}


/** Viewport math shared by pen and touch, keeping normalized strokes stable at any zoom. */
internal fun inverseImageViewport(
    point: Offset, zoom: Float, pan: Offset, width: Float, height: Float
): Offset {
    val z = zoom.coerceAtLeast(1f)
    return Offset(
        (point.x - width / 2f - pan.x) / z + width / 2f,
        (point.y - height / 2f - pan.y) / z + height / 2f
    )
}

internal fun clampImagePan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val x = ((zoom - 1f) * width / 2f).coerceAtLeast(0f)
    val y = ((zoom - 1f) * height / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-x, x), pan.y.coerceIn(-y, y))
}

/** Full screen draft editor; Cancel cannot mutate original images or parent model. */
@Composable
internal fun ImageAnnotationEditorDialog(
    imageUri: String,
    initial: ImageAnnotations,
    onSave: (ImageAnnotations) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    var strokes by remember(imageUri) { mutableStateOf(initial.strokes) }
    var undo by remember(imageUri) { mutableStateOf<List<List<ImageAnnotationStroke>>>(emptyList()) }
    var redo by remember(imageUri) { mutableStateOf<List<List<ImageAnnotationStroke>>>(emptyList()) }
    var drawing by remember(imageUri) { mutableStateOf<List<ImageAnnotationPoint>>(emptyList()) }
    var tool by remember { mutableStateOf(ImageAnnotationTool.PEN) }
    var colorArgb by remember { mutableLongStateOf(0xFFFFD54FL) }
    var sizeScale by remember { mutableFloatStateOf(1f) }
    var highlighterOpacity by remember { mutableFloatStateOf(.34f) }
    var zoom by remember(imageUri) { mutableFloatStateOf(1f) }
    var pan by remember(imageUri) { mutableStateOf(Offset.Zero) }
    var moveCanvas by remember(imageUri) { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (imageUri.startsWith("/")) BitmapFactory.decodeFile(imageUri, options)
                else context.contentResolver.openInputStream(Uri.parse(imageUri))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                if (options.outWidth > 0 && options.outHeight > 0 &&
                    options.outWidth.toLong() * options.outHeight <= 100_000_000L)
                    options.outWidth to options.outHeight else null
            }.getOrNull()
        }
    }

    fun commit(next: List<ImageAnnotationStroke>) {
        undo = (undo + listOf(strokes)).takeLast(40)
        strokes = next
        redo = emptyList()
        error = null
    }
    val widthFraction = (if (tool == ImageAnnotationTool.PEN) .005 else .045) * sizeScale
    val opacity = if (tool == ImageAnnotationTool.PEN) 1.0 else highlighterOpacity.toDouble()

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0A0F1C)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Anotar imagen", color = TestarioText,
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = {
                    val layer = ImageAnnotations(strokes)
                    runCatching { ImageAnnotationRules.requireValid(layer) }
                        .onSuccess { onSave(layer) }
                        .onFailure { error = it.message }
                }) { Text("Guardar") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tool == ImageAnnotationTool.PEN,
                    onClick = { tool = ImageAnnotationTool.PEN },
                    label = { Text("Rotulador fino") }
                )
                FilterChip(
                    selected = tool == ImageAnnotationTool.HIGHLIGHTER,
                    onClick = { tool = ImageAnnotationTool.HIGHLIGHTER },
                    label = { Text("Subrayador") }
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
                annotationPalette.forEach { swatch ->
                    Box(
                        Modifier.weight(1f).height(34.dp)
                            .padding(1.dp).clip(CircleShape)
                            .background(Color(swatch.argb.toInt()))
                            .border(
                                if (colorArgb == swatch.argb) 3.dp else 1.dp,
                                if (colorArgb == swatch.argb) TestarioPrimary else TestarioMutedText,
                                CircleShape
                            )
                            .semantics { contentDescription = "Color ${swatch.name}" }
                            .clickable { colorArgb = swatch.argb }
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Grosor", color = TestarioMutedText)
                Slider(
                    value = sizeScale,
                    onValueChange = { sizeScale = it },
                    valueRange = .5f..2f,
                    modifier = Modifier.weight(1f)
                )
            }

            if (tool == ImageAnnotationTool.HIGHLIGHTER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Opacidad ${(highlighterOpacity * 100).toInt()}%", color = TestarioMutedText)
                    Slider(
                        value = highlighterOpacity,
                        onValueChange = { highlighterOpacity = it },
                        valueRange = .10f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Zoom ${(zoom * 100).toInt()}%", color = TestarioMutedText)
                Slider(
                    value = zoom,
                    onValueChange = { target ->
                        zoom = target
                        if (zoom <= 1.001f) pan = Offset.Zero
                    },
                    valueRange = 1f..5f,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = moveCanvas,
                    onClick = { moveCanvas = !moveCanvas },
                    label = { Text("Mover") }
                )
            }
            Text("Amplía con dos dedos. Para desplazar con uno, pulsa Mover.",
                style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)

            BoxWithConstraints(
                Modifier.fillMaxWidth().weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TestarioSurface)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val heightPx = with(density) { maxHeight.toPx() }
                val fit = intrinsic?.let { fitImageToBox(it.first, it.second, widthPx, heightPx) }
                val gestures = if (fit != null) Modifier
                    .pointerInput(imageUri, fit, tool, colorArgb, widthFraction, opacity, moveCanvas) {
                        // A single recognizer handles BOTH drawing and pinch gestures.
                        // A second finger cancels the unfinished stroke; pinch can
                        // never accidentally paint or create hidden answer markers.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var multipleFingers = false
                            if (!moveCanvas) {
                                val raw = inverseImageViewport(
                                    down.position, zoom, pan, widthPx, heightPx
                                )
                                drawing = fit.toImage(raw.x, raw.y)?.let {
                                    listOf(ImageAnnotationPoint(it.first, it.second))
                                }.orEmpty()
                            }
                            while (true) {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size > 1) {
                                    multipleFingers = true
                                    drawing = emptyList()
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                    pan = clampImagePan(
                                        pan + event.calculatePan(), zoom, widthPx, heightPx
                                    )
                                } else if (!multipleFingers && pressed.size == 1) {
                                    val pointer = pressed.first()
                                    if (moveCanvas) {
                                        pan = clampImagePan(
                                            pan + pointer.positionChange(), zoom, widthPx, heightPx
                                        )
                                    } else if (drawing.isNotEmpty() && drawing.size < 8000) {
                                        val raw = inverseImageViewport(
                                            pointer.position, zoom, pan, widthPx, heightPx
                                        )
                                        fit.toImage(raw.x, raw.y)?.let { (x, y) ->
                                            val point = ImageAnnotationPoint(x, y)
                                            if (drawing.lastOrNull() != point) drawing = drawing + point
                                        }
                                    }
                                }
                                event.changes.forEach { if (it.pressed) it.consume() }
                                if (event.changes.none { it.pressed }) break
                            }
                            if (!multipleFingers && !moveCanvas && drawing.isNotEmpty()) {
                                commit(strokes + ImageAnnotationStroke(
                                    tool = tool, colorArgb = colorArgb,
                                    widthFraction = widthFraction.toDouble(),
                                    opacity = opacity, points = drawing
                                ))
                            }
                            drawing = emptyList()
                        }
                    } else Modifier
                Box(Modifier.fillMaxSize().then(gestures)) {
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }) {
                        AsyncImage(
                            model = if (imageUri.startsWith("/")) File(imageUri) else imageUri,
                            contentDescription = "Imagen original sin modificar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        if (fit != null) {
                            Canvas(Modifier.fillMaxSize()) {
                                val current = if (drawing.isNotEmpty()) listOf(ImageAnnotationStroke(
                                    tool = tool, colorArgb = colorArgb,
                                    widthFraction = widthFraction.toDouble(),
                                    opacity = opacity, points = drawing
                                )) else emptyList()
                                drawImageAnnotations(ImageAnnotations(strokes + current), fit)
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (undo.isNotEmpty()) {
                            redo = (redo + listOf(strokes)).takeLast(40)
                            strokes = undo.last()
                            undo = undo.dropLast(1)
                        }
                    }, enabled = undo.isNotEmpty(), modifier = Modifier.weight(1f)
                ) { Text("Deshacer") }
                OutlinedButton(
                    onClick = {
                        if (redo.isNotEmpty()) {
                            undo = (undo + listOf(strokes)).takeLast(40)
                            strokes = redo.last()
                            redo = redo.dropLast(1)
                        }
                    }, enabled = redo.isNotEmpty(), modifier = Modifier.weight(1f)
                ) { Text("Rehacer") }
                OutlinedButton(
                    onClick = { clearConfirm = true },
                    enabled = strokes.isNotEmpty(), modifier = Modifier.weight(1f)
                ) { Text("Limpiar") }
            }
            if (intrinsic == null) Text("La imagen no puede leerse todavía.", color = TestarioRed)
            error?.let { Text(it, color = TestarioRed) }
        }
    }
    if (clearConfirm) AlertDialog(
        onDismissRequest = { clearConfirm = false },
        title = { Text("Limpiar anotaciones") },
        text = { Text("Se borrarán los trazos de este borrador, nunca la fotografía original.") },
        confirmButton = {
            TextButton(onClick = { commit(emptyList()); clearConfirm = false }) { Text("Limpiar") }
        },
        dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancelar") } }
    )
}
