package com.example.testario.ui.screen

import androidx.compose.ui.platform.LocalContext
import com.example.testario.domain.ImageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.*
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.domain.ClozeMarkupEngine
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.LibraryViewModel
import java.util.UUID

/**
 * Scoped editor: legacy tests retain their existing editor. New written and Anki
 * records are saved in Topic.exercises only after validation succeeds.
 */
@Composable
fun StudyExerciseEditorScreen(
    topicId: String,
    exerciseId: String?,
    initialKind: StudyExerciseKind = StudyExerciseKind.WRITTEN_MULTI,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    embedded: Boolean = false
) {
    val appContext = LocalContext.current
    val imageManager = remember(appContext) { ImageManager(appContext.applicationContext) }
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.firstOrNull { it.id == topicId }
    val original = topic?.exercises?.firstOrNull { it.id == exerciseId }
    var exerciseKind by remember(exerciseId, initialKind) { mutableStateOf(original?.kind ?: initialKind) }
    var prompt by remember(exerciseId) { mutableStateOf(original?.prompt.orEmpty()) }
    var solutions by remember(exerciseId) {
        mutableStateOf(original?.solutions ?: listOf(CanonicalSolution(text = "")))
    }
    var selectedSolutionId by remember(exerciseId) {
        mutableStateOf(original?.solutions?.firstOrNull()?.id)
    }
    var sketchEnabled by remember(exerciseId) {
        mutableStateOf(original?.sketch != null || original?.diagram != null)
    }
    var sketchStrokes by remember(exerciseId) {
        mutableStateOf(original?.sketch?.strokes ?: original?.diagram?.let { d ->
            listOf(SketchStroke(points = listOf(
                SketchPoint(d.startX, d.startY), SketchPoint(d.endX, d.endY))))
        }.orEmpty())
    }
    var sketchColor by remember(exerciseId) { mutableLongStateOf(0xFFA855F7L) }
    var sketchToolHighlighter by remember(exerciseId) { mutableStateOf(false) }
    var sketchWidthMultiplier by remember(exerciseId) { mutableFloatStateOf(1f) }
    var sketchOpacity by remember(exerciseId) { mutableFloatStateOf(.34f) }
    var sketchZoom by remember(exerciseId) { mutableFloatStateOf(1f) }
    var sketchMode by remember(exerciseId) { mutableStateOf(SketchMode.DRAW) }
    var sketchUndo by remember(exerciseId) { mutableStateOf<List<List<SketchStroke>>>(emptyList()) }
    var sketchRedo by remember(exerciseId) { mutableStateOf<List<List<SketchStroke>>>(emptyList()) }
    var confirmClearSketch by remember(exerciseId) { mutableStateOf(false) }
    var pendingMarkerId by remember(exerciseId) { mutableStateOf<String?>(null) }
    var expandedMarkersSolutionId by remember(exerciseId) { mutableStateOf<String?>(null) }
    var showOptionalAliases by remember(exerciseId) { mutableStateOf(false) }
    var front by remember(exerciseId) {
        mutableStateOf(TextFieldValue(
            if (original?.flashcard?.kind == FlashcardKind.CLOZE)
                runCatching { ClozeMarkupEngine.encode(
                    original.flashcard.front, original.flashcard.gaps
                ) }.getOrDefault(original.flashcard.front)
            else original?.flashcard?.front.orEmpty()
        ))
    }
    var back by remember(exerciseId) { mutableStateOf(original?.flashcard?.back.orEmpty()) }
    var flashKind by remember(exerciseId) { mutableStateOf(original?.flashcard?.kind ?: FlashcardKind.BASIC) }
    var gaps by remember(exerciseId) { mutableStateOf(original?.flashcard?.gaps.orEmpty()) }
    var ankiHelpExpanded by remember(exerciseId) { mutableStateOf(false) }
    var showClozeMarkerDialog by remember(exerciseId) { mutableStateOf(false) }
    var clozeAlways by remember(exerciseId) { mutableStateOf(true) }
    var showAnkiImageOptions by remember(exerciseId) { mutableStateOf(false) }
    var showAnkiImageSourceOptions by remember(exerciseId) { mutableStateOf(false) }
    var ankiImageSlot by remember(exerciseId) { mutableStateOf("front") }

    var clozeTurnInput by remember(exerciseId) { mutableStateOf("1") }
    var masks by remember(exerciseId) { mutableStateOf(original?.flashcard?.masks.orEmpty()) }
    var aliasesText by remember(exerciseId) { mutableStateOf(original?.flashcard?.acceptedAnswers?.joinToString(", ").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var imageUri by remember(exerciseId) { mutableStateOf(original?.baseImageUri) }
    var frontImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.frontImageUri) }
    var backImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.backImageUri) }
    var baseAnnotations by remember(exerciseId) { mutableStateOf(original?.baseAnnotations ?: ImageAnnotations()) }
    var frontAnnotations by remember(exerciseId) { mutableStateOf(original?.flashcard?.frontAnnotations ?: ImageAnnotations()) }
    var backAnnotations by remember(exerciseId) { mutableStateOf(original?.flashcard?.backAnnotations ?: ImageAnnotations()) }
    var photoMarkerMode by remember(exerciseId) { mutableStateOf(false) }
    var showImageOptions by remember { mutableStateOf(false) }
    var occlusionMarkMode by remember(exerciseId) { mutableStateOf(true) }
    var pickerTarget by remember { mutableStateOf("written") }
    LaunchedEffect(exerciseId, solutions.size) {
        if (selectedSolutionId == null || solutions.none { it.id == selectedSolutionId }) {
            selectedSolutionId = solutions.firstOrNull()?.id
        }
    }
    fun editSketch(next: List<SketchStroke>) {
        sketchUndo = (sketchUndo + listOf(sketchStrokes)).takeLast(30)
        sketchStrokes = next
        sketchRedo = emptyList()
    }
    fun placeOrSelectMarker(x: Double, y: Double) {
        val nearby = solutions.flatMap { it.points }.minByOrNull { point ->
            (point.x - x) * (point.x - x) + (point.y - y) * (point.y - y)
        }
        if (nearby != null && (nearby.x - x) * (nearby.x - x) +
            (nearby.y - y) * (nearby.y - y) <= 0.0025) {
            pendingMarkerId = nearby.id
            return
        }
        val id = selectedSolutionId ?: return
        val selected = solutions.firstOrNull { it.id == id } ?: return
        // Exactly one point per written solution. Selecting an existing solution
        // moves its point instead of creating surplus numbers.
        val ordinal = solutions.indexOfFirst { it.id == id } + 1
        solutions = solutions.map { item ->
            if (item.id == id) item.copy(points = listOf(
                selected.points.firstOrNull()?.copy(x = x, y = y, order = ordinal)
                    ?: ImagePoint(x = x, y = y, order = ordinal)
            )) else item
        }
    }
    fun removeMarker(markerId: String) {
        val remaining = solutions.flatMap { it.points }
            .filterNot { it.id == markerId }.sortedBy { it.order }
        val orders = remaining.mapIndexed { index, p -> p.id to (index + 1) }.toMap()
        solutions = solutions.map { item ->
            item.copy(points = item.points.filterNot { it.id == markerId }
                .map { it.copy(order = orders[it.id] ?: it.order) })
        }
        pendingMarkerId = null
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) when (pickerTarget) {
            "written" -> {
                if (imageUri != uri.toString()) baseAnnotations = ImageAnnotations()
                imageUri = uri.toString()
                sketchEnabled = false
            }
            "front" -> {
                if (frontImageUri != uri.toString()) frontAnnotations = ImageAnnotations()
                frontImageUri = uri.toString()
            }
            "back" -> {
                if (backImageUri != uri.toString()) backAnnotations = ImageAnnotations()
                backImageUri = uri.toString()
            }
        }
    }
    fun chooseImage(target: String) {
        if (target == "written" && solutions.any { it.points.isNotEmpty() }) {
            error = "Antes de reemplazar el esquema, elimina sus marcadores; no se pueden trasladar automáticamente a una fotografía."
            return
        }
        if (target == "front" && masks.isNotEmpty()) {
            error = "Elimina primero las máscaras de ocultación antes de cambiar de imagen."
            return
        }
        pickerTarget = target
        pickImage.launch("image/*")
    }
    fun construct(draft: Boolean): StudyExercise {
        val markup = if (exerciseKind == StudyExerciseKind.ANKI_CARD &&
            flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE)) {
            runCatching { ClozeMarkupEngine.parse(front.text) }.getOrElse { failure ->
                if (draft) ClozeMarkupEngine.Parsed(front.text, emptyList()) else throw failure
            }
        } else ClozeMarkupEngine.Parsed(front.text, gaps)
        // Preserve stable gap IDs and hints while editing existing beta cards.
        // First match unchanged position/text, then unique moved span with same group.
        val oldCard = original?.flashcard
        val originalGaps = oldCard?.gaps.orEmpty()
        val keptGapIds = hashSetOf<String>()
        val stableGaps = markup.gaps.map { gap ->
            val value = markup.text.substring(gap.start, gap.end)
            fun sameText(old: ClozeGap): Boolean =
                old.start >= 0 && old.end <= (oldCard?.front?.length ?: 0) &&
                    old.start < old.end &&
                    oldCard?.front?.substring(old.start, old.end) == value &&
                    old.groupNumber == gap.groupNumber && old.id !in keptGapIds
            val matched = originalGaps.firstOrNull { old ->
                sameText(old) && old.start == gap.start && old.end == gap.end
            } ?: originalGaps.firstOrNull(::sameText)
            if (matched != null) {
                keptGapIds += matched.id
                gap.copy(id = matched.id, hint = matched.hint)
            } else gap
        }
        val effectiveKind = if (flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE)) {
            if (markup.gaps.isEmpty()) FlashcardKind.BASIC else FlashcardKind.CLOZE
        } else flashKind
        val card = if (exerciseKind == StudyExerciseKind.ANKI_CARD) FlashcardContent(
            kind = effectiveKind, front = markup.text, back = back,
            frontImageUri = frontImageUri, backImageUri = backImageUri,
            frontAnnotations = frontAnnotations, backAnnotations = backAnnotations,
            gaps = stableGaps, masks = masks,
            acceptedAnswers = aliasesText.split(",").map(String::trim).filter(String::isNotBlank)
        ) else null
        return StudyExercise(
            id = original?.id ?: UUID.randomUUID().toString(),
            kind = exerciseKind, prompt = if (exerciseKind == StudyExerciseKind.ANKI_CARD) markup.text else prompt,
            solutions = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) solutions else emptyList(),
            flashcard = card, baseImageUri = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) imageUri else null,
            baseAnnotations = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI)
                baseAnnotations else ImageAnnotations(),
            diagram = null,
            sketch = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI && sketchEnabled)
                SketchDrawing(strokes = sketchStrokes) else null,
            revision = original?.revision ?: 1, draft = draft
        )
    }

    val schematicLineColor = TestarioPrimary

    if (pendingMarkerId != null) {
        val marker = solutions.flatMap { it.points }.find { it.id == pendingMarkerId }
        if (marker != null) AlertDialog(
            onDismissRequest = { pendingMarkerId = null },
            title = { Text("Marcador ${marker.order}") },
            text = { Text("¿Eliminar este número de la imagen? La respuesta escrita se conservará.") },
            confirmButton = {
                TextButton(onClick = { removeMarker(marker.id) }) { Text("Eliminar número") }
            },
            dismissButton = {
                TextButton(onClick = { pendingMarkerId = null }) { Text("Cancelar") }
            }
        )
    }
    if (showClozeMarkerDialog) {
        AlertDialog(
            onDismissRequest = { showClozeMarkerDialog = false },
            title = { Text("Ocultar texto seleccionado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Selecciona cuándo se oculta esta palabra o frase.", color = TestarioMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = clozeAlways, onClick = { clozeAlways = true },
                            label = { Text("Siempre") })
                        FilterChip(selected = !clozeAlways, onClick = { clozeAlways = false },
                            label = { Text("Por turnos") })
                    }
                    if (!clozeAlways) OutlinedTextField(
                        value = clozeTurnInput,
                        onValueChange = { clozeTurnInput = it.filter(Char::isDigit).take(3) },
                        label = { Text("Número de turno") }, singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val n = if (clozeAlways) null else clozeTurnInput.toIntOrNull()
                    if (!clozeAlways && (n == null || n !in 1..999)) {
                        error = "Escribe un número de turno entre 1 y 999."
                    } else runCatching {
                        ClozeMarkupEngine.markSelection(
                            front.text, front.selection.min, front.selection.max, n
                        )
                    }.onSuccess {
                        front = TextFieldValue(it)
                        error = null
                        showClozeMarkerDialog = false
                    }.onFailure { error = it.message }
                }) { Text("Añadir ocultación") }
            },
            dismissButton = {
                TextButton(onClick = { showClozeMarkerDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (confirmClearSketch) AlertDialog(
        onDismissRequest = { confirmClearSketch = false },
        title = { Text("Limpiar imagen") },
        text = { Text("Se quitarán los trazos del dibujo. Las respuestas y marcadores no se borrarán.") },
        confirmButton = { TextButton(onClick = {
            editSketch(emptyList())
            confirmClearSketch = false
        }) { Text("Limpiar") } },
        dismissButton = { TextButton(onClick = { confirmClearSketch = false }) {
            Text("Cancelar")
        } }
    )

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { if (!embedded) TestarioTopBar("Editor de ejercicios", onBack = onBack) }
    ) { pad ->
        if (topic == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tema no encontrado", color = TestarioText)
            }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tema: ${topic.name}", color = TestarioMutedText)
            if (!embedded) {
                Text("Tipo de contenido", color = TestarioText)
                Text(if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) "Respuesta escrita" else "Tarjeta Anki",
                    color = TestarioMutedText)
            }

            if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) {
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Enunciado") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Box {
                    OutlinedButton(onClick = { showImageOptions = true },
                        modifier = Modifier.fillMaxWidth()) { Text("Añadir imagen") }
                    DropdownMenu(expanded = showImageOptions,
                        onDismissRequest = { showImageOptions = false }) {
                        DropdownMenuItem(text = { Text("Crear imagen") }, onClick = {
                            showImageOptions = false
                            if (imageUri != null && solutions.any { it.points.isNotEmpty() }) {
                                error = "Borra los marcadores existentes antes de cambiar a un dibujo."
                            } else {
                                sketchEnabled = true; imageUri = null; sketchMode = SketchMode.DRAW
                            }
                        })
                        DropdownMenuItem(text = { Text("Importar imagen") }, onClick = {
                            showImageOptions = false; chooseImage("written")
                        })
                    }
                }
                if (sketchEnabled) {
                    OutlinedButton(onClick = { sketchEnabled = false }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cerrar creador de imagen")
                    }
                }
                if ((sketchEnabled && sketchMode == SketchMode.MARK) ||
                    (imageUri != null && photoMarkerMode)) {
                    Text("Solución que vas a colocar", color = TestarioMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        solutions.forEachIndexed { index, solution ->
                            FilterChip(selected = selectedSolutionId == solution.id,
                                onClick = { selectedSolutionId = solution.id },
                                label = { Text("${index + 1}") })
                        }
                    }
                }
                if (sketchEnabled) {
                    Text("Dibuja con rotulador o subrayador y después utiliza «Colocar soluciones».",
                        color = TestarioMutedText)
                    if (sketchMode == SketchMode.DRAW) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = !sketchToolHighlighter,
                                onClick = { sketchToolHighlighter = false },
                                label = { Text("Rotulador fino") })
                            FilterChip(selected = sketchToolHighlighter,
                                onClick = { sketchToolHighlighter = true },
                                label = { Text("Subrayador") })
                        }
                        AnnotationColorPalette(selected = sketchColor,
                            onSelect = { sketchColor = it })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Grosor", color = TestarioMutedText)
                            Slider(value = sketchWidthMultiplier,
                                onValueChange = { sketchWidthMultiplier = it },
                                valueRange = .5f..2f, modifier = Modifier.weight(1f))
                        }
                        if (sketchToolHighlighter) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Opacidad", color = TestarioMutedText)
                            Slider(value = sketchOpacity,
                                onValueChange = { sketchOpacity = it },
                                valueRange = .1f..1f, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            SketchMode.DRAW to "Dibujar",
                            SketchMode.ERASE to "Borrar",
                            SketchMode.MARK to "Colocar soluciones",
                            SketchMode.PAN to "Mover"
                        ).forEach { (mode, title) ->
                            FilterChip(selected = sketchMode == mode,
                                onClick = { sketchMode = mode },
                                label = { Text(title) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zoom", color = TestarioMutedText)
                        Slider(value = sketchZoom, onValueChange = { sketchZoom = it },
                            valueRange = 1f..5f, modifier = Modifier.weight(1f))
                        Text("${"%.1f".format(sketchZoom)}×", color = TestarioText)
                    }
                    if (sketchZoom > 1f) Text(
                        "Para desplazar la imagen ampliada, elige Mover.",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                    StudySketchCanvas(
                        sketch = SketchDrawing(strokes = sketchStrokes),
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        markers = solutions.flatMap { it.points },
                        showMarkers = sketchMode == SketchMode.MARK,
                        mode = sketchMode,
                        zoomFactor = sketchZoom,
                        penArgb = sketchColor,
                        penWidthFraction = (if (sketchToolHighlighter) .045 else .005) *
                            sketchWidthMultiplier.toDouble(),
                        penOpacity = if (sketchToolHighlighter) sketchOpacity.toDouble() else 1.0,
                        onDrawStroke = { stroke -> editSketch(sketchStrokes + stroke) },
                        onEraseStroke = { id -> editSketch(sketchStrokes.filterNot { it.id == id }) },
                        onMarkTap = ::placeOrSelectMarker
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            if (sketchUndo.isNotEmpty()) {
                                sketchRedo = sketchRedo + listOf(sketchStrokes)
                                sketchStrokes = sketchUndo.last()
                                sketchUndo = sketchUndo.dropLast(1)
                            }
                        }, enabled = sketchUndo.isNotEmpty()) { Text("Deshacer") }
                        TextButton(onClick = {
                            if (sketchRedo.isNotEmpty()) {
                                sketchUndo = sketchUndo + listOf(sketchStrokes)
                                sketchStrokes = sketchRedo.last()
                                sketchRedo = sketchRedo.dropLast(1)
                            }
                        }, enabled = sketchRedo.isNotEmpty()) { Text("Rehacer") }
                        TextButton(onClick = { confirmClearSketch = true },
                            enabled = sketchStrokes.isNotEmpty()) { Text("Limpiar dibujo") }
                    }
                } else if (imageUri != null) {
                    Text(
                        if (photoMarkerMode) "Colocar soluciones: toca la fotografía para vincular cada solución a un punto."
                        else "Las anotaciones y las soluciones se editan por separado.",
                        color = TestarioMutedText
                    )
                    FilterChip(
                        selected = photoMarkerMode,
                        onClick = { photoMarkerMode = !photoMarkerMode },
                        label = { Text(if (photoMarkerMode) "Colocar soluciones · activo" else "Colocar soluciones") }
                    )
                    EditableAnnotationImage(
                        imageUri = requireNotNull(imageUri),
                        annotations = baseAnnotations,
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        markers = solutions.flatMap { it.points },
                        showCorrection = true,
                        onMarkerTap = if (photoMarkerMode) ::placeOrSelectMarker else null,
                        onAnnotationsChanged = { baseAnnotations = it },
                        onReplace = { chooseImage("written") },
                        onDelete = {
                            if (solutions.any { it.points.isNotEmpty() }) {
                                error = "Retira primero los números vinculados a esta fotografía."
                            } else {
                                imageUri = null
                                baseAnnotations = ImageAnnotations()
                            }
                        }
                    )
                }

                Text("Soluciones", color = TestarioText)
                Text("NEXUM reconoce automáticamente variantes y abreviaturas inequívocas.",
                    color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { showOptionalAliases = !showOptionalAliases }) {
                    Text(if (showOptionalAliases) "Ocultar excepciones manuales"
                        else "Variantes especiales (opcional)")
                }
                solutions.forEach { solution ->
                    val index = solutions.indexOfFirst { it.id == solution.id }
                    Column(Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Solución ${index + 1}", color = TestarioText, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                val retained = solutions.filterNot { it.id == solution.id }
                                solutions = retained.mapIndexed { ordinal, item ->
                                    item.copy(points = item.points.take(1).map {
                                        it.copy(order = ordinal + 1)
                                    })
                                }
                                if (selectedSolutionId == solution.id)
                                    selectedSolutionId = solutions.firstOrNull()?.id
                            }) { Text("Eliminar") }
                        }
                        OutlinedTextField(
                            value = solution.text,
                            onValueChange = { text -> solutions = solutions.map {
                                if (it.id == solution.id) it.copy(text = text) else it
                            } },
                            label = { Text("Respuesta") }, modifier = Modifier.fillMaxWidth()
                        )
                        if (showOptionalAliases || solution.aliases.isNotEmpty()) {
                            OutlinedTextField(
                                value = solution.aliases.joinToString(", "),
                                onValueChange = { text -> solutions = solutions.map {
                                    if (it.id == solution.id) it.copy(aliases = text.split(",")
                                        .map(String::trim).filter(String::isNotBlank)) else it
                                } },
                                label = { Text("Alias separados por comas") }, modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (solution.points.isNotEmpty()) {
                            TextButton(onClick = {
                                expandedMarkersSolutionId =
                                    if (expandedMarkersSolutionId == solution.id) null else solution.id
                            }) {
                                Text("${solution.points.size} marcadores · " +
                                    if (expandedMarkersSolutionId == solution.id) "Ocultar" else "Gestionar")
                            }
                            if (expandedMarkersSolutionId == solution.id) {
                                solution.points.sortedBy { it.order }.forEach { marker ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Número ${marker.order}", modifier = Modifier.weight(1f),
                                            color = TestarioText)
                                        TextButton(onClick = { removeMarker(marker.id) }) {
                                            Text("Eliminar")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val s = CanonicalSolution(text = "")
                    solutions = solutions + s
                    selectedSolutionId = s.id
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Añadir solución") }


            } else {
                val simpleAnki = flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE)
                Text(
                    if (simpleAnki) "Tarjeta · pregunta y respuestas ocultables"
                    else "Tarjeta heredada · ${flashKind.name}",
                    color = TestarioText, style = MaterialTheme.typography.titleMedium
                )
                if (!simpleAnki) {
                    Text("Este formato antiguo se conserva por compatibilidad. Las tarjetas nuevas usan un editor simplificado.",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box {
                        TextButton(onClick = { showAnkiImageOptions = true }) {
                            Text("Añadir imagen", color = TestarioPrimary)
                        }
                        DropdownMenu(expanded = showAnkiImageOptions,
                            onDismissRequest = { showAnkiImageOptions = false }) {
                            DropdownMenuItem(text = { Text("Imagen de pregunta") }, onClick = {
                                showAnkiImageOptions = false
                                ankiImageSlot = "front"
                                showAnkiImageSourceOptions = true
                            })
                            DropdownMenuItem(text = { Text("Imagen de respuesta") }, onClick = {
                                showAnkiImageOptions = false
                                ankiImageSlot = "back"
                                showAnkiImageSourceOptions = true
                            })
                        }
                        DropdownMenu(expanded = showAnkiImageSourceOptions,
                            onDismissRequest = { showAnkiImageSourceOptions = false }) {
                            DropdownMenuItem(text = { Text("Crear imagen") }, onClick = {
                                showAnkiImageSourceOptions = false
                                if (ankiImageSlot == "front" && masks.isNotEmpty()) {
                                    error = "Elimina las máscaras actuales antes de cambiar la imagen."
                                } else runCatching {
                                    imageManager.createBlankImage()
                                }.onSuccess { path ->
                                    if (ankiImageSlot == "front") {
                                        frontImageUri = path
                                        frontAnnotations = ImageAnnotations()
                                    } else {
                                        backImageUri = path
                                        backAnnotations = ImageAnnotations()
                                    }
                                }.onFailure { error = it.message }
                            })
                            DropdownMenuItem(text = { Text("Importar imagen") }, onClick = {
                                showAnkiImageSourceOptions = false
                                chooseImage(ankiImageSlot)
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = front, onValueChange = { front = it; error = null },
                    label = { Text("Pregunta o texto de la tarjeta") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                if (simpleAnki) {
                    TextButton(onClick = { ankiHelpExpanded = !ankiHelpExpanded }) {
                        Text(if (ankiHelpExpanded) "Cómo funciona  ▴" else "Cómo funciona  ▾")
                    }
                    if (ankiHelpExpanded) {
                        Column(
                            Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Escribe el enunciado y selecciona una palabra o frase para ocultarla.",
                                color = TestarioText)
                            Text("Siempre: {{palabra}} desaparece en todos los repasos.",
                                color = TestarioMutedText)
                            Text("Turnos: {{1:palabra}}, {{2:frase}} alternan lo oculto al volver a estudiar. Dos fragmentos con el mismo número se ocultan juntos.",
                                color = TestarioMutedText)
                            Text("También se admite [[1:palabra]]. Los fragmentos sin número se ocultan siempre.",
                                color = TestarioMutedText)
                            Text("Las imágenes permiten tapar zonas con máscaras que desaparecen al revelar la solución.",
                                color = TestarioMutedText)
                        }
                    }
                    if (!front.selection.collapsed) {
                        Button(onClick = { clozeAlways = true; showClozeMarkerDialog = true },
                            modifier = Modifier.fillMaxWidth()) { Text("Marcar texto seleccionado") }
                    }
                    val candidateMarkup = runCatching { ClozeMarkupEngine.parse(front.text) }
                    candidateMarkup.onSuccess { parsed ->
                        if (parsed.gaps.isNotEmpty()) {
                            Text("Fragmentos ocultos", color = TestarioText)
                            parsed.gaps.forEachIndexed { idx, gap ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${parsed.text.substring(gap.start, gap.end)} · " +
                                            (gap.groupNumber?.let { "Turno $it" } ?: "Siempre"),
                                        modifier = Modifier.weight(1f), color = TestarioText
                                    )
                                    TextButton(onClick = {
                                        runCatching { ClozeMarkupEngine.unmark(front.text, idx) }
                                            .onSuccess { front = TextFieldValue(it) }
                                            .onFailure { error = it.message }
                                    }) { Text("Quitar") }
                                }
                            }
                        }
                    }.onFailure {
                        Text(it.message ?: "Marcador incompleto", color = TestarioRed,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedTextField(
                    value = back, onValueChange = { back = it },
                    label = { Text(if (flashKind == FlashcardKind.CLOZE ||
                        (flashKind == FlashcardKind.BASIC &&
                            runCatching { ClozeMarkupEngine.parse(front.text).gaps.isNotEmpty() }
                                .getOrDefault(false))) "Explicación (opcional)"
                        else "Respuesta") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                if (flashKind == FlashcardKind.TYPE_ANSWER) {
                    OutlinedTextField(
                        value = aliasesText, onValueChange = { aliasesText = it },
                        label = { Text("Otras respuestas aceptadas (separadas por coma)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (frontImageUri != null &&
                    (flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE, FlashcardKind.IMAGE_OCCLUSION))) {
                    FilterChip(
                        selected = flashKind == FlashcardKind.IMAGE_OCCLUSION,
                        onClick = {
                            if (flashKind == FlashcardKind.IMAGE_OCCLUSION) {
                                if (masks.isNotEmpty()) error =
                                    "Elimina primero las zonas ocultas para desactivar este modo."
                                else flashKind = FlashcardKind.BASIC
                            } else if (runCatching {
                                    ClozeMarkupEngine.parse(front.text).gaps.isNotEmpty()
                                }.getOrDefault(false)) {
                                error = "Para ocultar zonas en la imagen, elimina primero los huecos de texto. Puedes conservar esta tarjeta y crear otra para la imagen."
                            } else {
                                flashKind = FlashcardKind.IMAGE_OCCLUSION
                                occlusionMarkMode = true
                            }
                        },
                        label = { Text("Ocultar partes de la imagen") }
                    )
                }
                if (flashKind == FlashcardKind.IMAGE_OCCLUSION) {
                    Text("Toca las zonas de la imagen que quieras tapar. Al revelar la tarjeta los bloques desaparecen.",
                        color = TestarioMutedText)
                    frontImageUri?.let { image ->
                        FilterChip(
                            selected = occlusionMarkMode,
                            onClick = { occlusionMarkMode = !occlusionMarkMode },
                            label = { Text(if (occlusionMarkMode) "Ocultar zonas · activo"
                                else "Ocultar zonas") }
                        )
                        EditableAnnotationImage(
                            imageUri = image,
                            annotations = frontAnnotations,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            masks = masks,
                            onMarkerTap = if (occlusionMarkMode) { x, y ->
                                masks = masks + ImageOcclusionMask(
                                    x = x.coerceAtMost(0.82), y = y.coerceAtMost(0.82),
                                    width = 0.18, height = 0.18
                                )
                            } else null,
                            onAnnotationsChanged = { frontAnnotations = it },
                            onReplace = { chooseImage("front") },
                            onDelete = {
                                if (masks.isNotEmpty()) error =
                                    "Elimina primero las máscaras asociadas a esta fotografía."
                                else {
                                    frontImageUri = null
                                    frontAnnotations = ImageAnnotations()
                                }
                            }
                        )
                    }
                    masks.forEach { mask ->
                        Text("Zona oculta ${masks.indexOf(mask) + 1}", color = TestarioMutedText)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ancho", color = TestarioMutedText)
                            Slider(
                                value = mask.width.toFloat().coerceIn(.04f,.7f),
                                onValueChange = { v ->
                                    masks = masks.map { if (it.id == mask.id)
                                        it.copy(width = v.toDouble(), x = it.x.coerceAtMost(1.0 - v))
                                        else it }
                                },
                                valueRange = .04f.. .7f, modifier = Modifier.weight(1f)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Alto", color = TestarioMutedText)
                            Slider(
                                value = mask.height.toFloat().coerceIn(.04f,.7f),
                                onValueChange = { v ->
                                    masks = masks.map { if (it.id == mask.id)
                                        it.copy(height = v.toDouble(), y = it.y.coerceAtMost(1.0 - v))
                                        else it }
                                },
                                valueRange = .04f.. .7f, modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = mask.answer,
                            onValueChange = { newText -> masks = masks.map {
                                if (it.id == mask.id) it.copy(answer = newText) else it
                            } },
                            label = { Text("Respuesta de la zona ${masks.indexOf(mask) + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { masks = masks.filterNot { it.id == mask.id } }) {
                                    Text("Quitar")
                                }
                            }
                        )
                    }
                }
                if (flashKind != FlashcardKind.IMAGE_OCCLUSION) {
                    frontImageUri?.let { image ->
                        Text("Imagen delantera", color = TestarioMutedText)
                        EditableAnnotationImage(
                            imageUri = image,
                            annotations = frontAnnotations,
                            modifier = Modifier.fillMaxWidth().height(220.dp),
                            onAnnotationsChanged = { frontAnnotations = it },
                            onReplace = { chooseImage("front") },
                            onDelete = {
                                frontImageUri = null
                                frontAnnotations = ImageAnnotations()
                            }
                        )
                    }
                }
                backImageUri?.let { image ->
                    Text("Imagen trasera", color = TestarioMutedText)
                    EditableAnnotationImage(
                        imageUri = image,
                        annotations = backAnnotations,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        onAnnotationsChanged = { backAnnotations = it },
                        onReplace = { chooseImage("back") },
                        onDelete = {
                            backImageUri = null
                            backAnnotations = ImageAnnotations()
                        }
                    )
                }
                if (frontImageUri != null || backImageUri != null)
                    Text("Imágenes asociadas: ${listOfNotNull(frontImageUri, backImageUri).size}",
                        color = TestarioMutedText)
                if (flashKind == FlashcardKind.REVERSED) {
                    Text("Se estudiarán ambas direcciones; la pregunta se mantiene encima de la solución.",
                        color = TestarioMutedText)
                }
            }

            if (error != null) Text(error.orEmpty(), color = TestarioRed)
                Button(onClick = {
                    runCatching { construct(draft = false) }.onSuccess { candidate ->
                        val errors = StudyExerciseRules.errors(candidate)
                        if (errors.isNotEmpty()) error = errors.joinToString("\n")
                        else runCatching {
                            libraryViewModel.saveStudyExercise(topicId, candidate, original?.revision)
                        }.onSuccess {
                            if (it) onBack() else error = "Conflicto de edición; recarga el ejercicio."
                        }.onFailure { error = it.message ?: "No se pudo guardar." }
                    }.onFailure {
                        error = it.message ?: "Hay una ocultación o un campo incompleto."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
            Spacer(Modifier.height(18.dp))
        }
    }
}
