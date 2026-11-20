package com.example.testario.ui.screen

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
import com.example.testario.domain.StudyImageStore
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    creationType: NewQuestionType? = null,
    onCreationTypeChange: (NewQuestionType) -> Unit = {}
) {
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
    var clozeTurnInput by remember(exerciseId) { mutableStateOf("1") }
    var previewRevealed by remember(exerciseId) { mutableStateOf(false) }
    var masks by remember(exerciseId) { mutableStateOf(original?.flashcard?.masks.orEmpty()) }
    var aliasesText by remember(exerciseId) { mutableStateOf(original?.flashcard?.acceptedAnswers?.joinToString(", ").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var imageUri by remember(exerciseId) { mutableStateOf(original?.baseImageUri) }
    var frontImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.frontImageUri) }
    var backImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.backImageUri) }
    var baseAnnotations by remember(exerciseId) { mutableStateOf(original?.baseAnnotations ?: ImageAnnotations()) }
    var frontAnnotations by remember(exerciseId) { mutableStateOf(original?.flashcard?.frontAnnotations ?: ImageAnnotations()) }
    var backAnnotations by remember(exerciseId) { mutableStateOf(original?.flashcard?.backAnnotations ?: ImageAnnotations()) }
    var photoMarkerMode by remember(exerciseId) { mutableStateOf(false) }
    var occlusionMarkMode by remember(exerciseId) { mutableStateOf(true) }
    var pickerTarget by remember { mutableStateOf("written") }
    val context = LocalContext.current
    val editorScope = rememberCoroutineScope()
    val imageStore = remember(context) { StudyImageStore(context.applicationContext) }
    var showImageAddMenu by remember(exerciseId) { mutableStateOf(false) }
    var justCreatedImage by remember(exerciseId) { mutableStateOf<String?>(null) }
    var showAnkiImageMenu by remember(exerciseId) { mutableStateOf(false) }
    var pendingAnkiImageTarget by remember(exerciseId) { mutableStateOf<String?>(null) }
    var showAnkiOcclusions by remember(exerciseId) {
        mutableStateOf(original?.flashcard?.kind == FlashcardKind.IMAGE_OCCLUSION)
    }

    var showHowItWorks by remember(exerciseId) { mutableStateOf(false) }
    var showSelectionTurnEditor by remember(exerciseId) { mutableStateOf<Int?>(null) }
    var turnMenuGap by remember(exerciseId) { mutableStateOf<Int?>(null) }
    var customTurnInput by remember(exerciseId) { mutableStateOf("1") }
    var showCustomTurnInput by remember(exerciseId) { mutableStateOf(false) }

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
        val nextNumber = (solutions.flatMap { it.points }.maxOfOrNull { it.order } ?: 0) + 1
        solutions = solutions.map { item ->
            if (item.id == id) item.copy(points = item.points +
                ImagePoint(x = x, y = y, order = nextNumber)) else item
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
    fun createBlankImage(target: String) {
        if (target == "front" && masks.isNotEmpty()) {
            error = "Quita las máscaras existentes antes de sustituir su imagen."
            return
        }
        editorScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { imageStore.createBlankCanvas() }
            }.onSuccess { path ->
                when (target) {
                    "written" -> {
                        if (solutions.any { it.points.isNotEmpty() }) {
                            error = "Retira antes las soluciones colocadas en la imagen actual."
                        } else {
                            imageUri = path
                            justCreatedImage = path
                            baseAnnotations = ImageAnnotations()
                            sketchEnabled = false
                            photoMarkerMode = false
                        }
                    }
                    "front" -> {
                        frontImageUri = path
                        justCreatedImage = path
                        frontAnnotations = ImageAnnotations()
                        masks = emptyList()
                    }
                    "back" -> {
                        backImageUri = path
                        justCreatedImage = path
                        backAnnotations = ImageAnnotations()
                    }
                }
            }.onFailure { error = it.message ?: "No se pudo crear la imagen." }
        }
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
        val effectiveKind = when {
            flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE) &&
                masks.isNotEmpty() && markup.gaps.isEmpty() -> FlashcardKind.IMAGE_OCCLUSION
            flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE) ->
                if (markup.gaps.isEmpty()) FlashcardKind.BASIC else FlashcardKind.CLOZE
            else -> flashKind
        }
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

    pendingAnkiImageTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingAnkiImageTarget = null },
            title = { Text(if (target == "front") "Imagen de pregunta" else "Imagen de respuesta") },
            text = { Text("Puedes crear un lienzo con rotulador y subrayador o importar una fotografía.") },
            confirmButton = {
                Button(onClick = {
                    pendingAnkiImageTarget = null
                    createBlankImage(target)
                }) { Text("Crear imagen") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingAnkiImageTarget = null }) {
                        Text("Cancelar")
                    }
                    OutlinedButton(onClick = {
                        pendingAnkiImageTarget = null
                        chooseImage(target)
                    }) { Text("Importar imagen") }
                }
            }
        )
    }

    showSelectionTurnEditor?.let { gapIndex ->
        AlertDialog(
            onDismissRequest = { showSelectionTurnEditor = null },
            title = { Text("Número de turno") },
            text = {
                OutlinedTextField(
                    value = customTurnInput,
                    onValueChange = { customTurnInput = it.filter(Char::isDigit).take(3) },
                    label = { Text("Turno (1–999)") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    val number = customTurnInput.toIntOrNull()
                    if (number !in 1..999) error = "Número entre 1 y 999."
                    else runCatching {
                        val parsed = ClozeMarkupEngine.parse(front.text)
                        val updated = parsed.gaps.toMutableList()
                        updated[gapIndex] = updated[gapIndex].copy(groupNumber = number)
                        ClozeMarkupEngine.encode(parsed.text, updated)
                    }.onSuccess {
                        front = TextFieldValue(it)
                        showSelectionTurnEditor = null
                        error = null
                    }.onFailure { error = it.message }
                }) { Text("Aplicar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSelectionTurnEditor = null }) { Text("Cancelar") }
            }
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
        topBar = { TestarioTopBar(
            if (original == null) "Nueva pregunta" else
                if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) "Editar respuesta escrita" else "Editar tarjeta Anki",
            onBack = onBack
        ) }
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
            if (original == null && creationType != null)
                NewQuestionTypePicker(creationType, onCreationTypeChange)
            // An existing exercise keeps its immutable saved type.
            if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) {
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Enunciado") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                // Both paths share the same photo annotation editor. Historical
                // freehand sketches remain editable without changing their data.
                if (imageUri == null && !sketchEnabled) {
                    Box {
                        OutlinedButton(onClick = { showImageAddMenu = true },
                            modifier = Modifier.fillMaxWidth()) { Text("Añadir imagen") }
                        DropdownMenu(expanded = showImageAddMenu,
                            onDismissRequest = { showImageAddMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Crear imagen") },
                                onClick = { showImageAddMenu = false; createBlankImage("written") }
                            )
                            DropdownMenuItem(
                                text = { Text("Importar imagen") },
                                onClick = { showImageAddMenu = false; chooseImage("written") }
                            )
                        }
                    }
                }
                if (sketchEnabled) {
                    Text("Dibuja con el dedo o lápiz táctil y después pasa a «Colocar soluciones».",
                        color = TestarioMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            SketchMode.DRAW to "Dibujar",
                            SketchMode.ERASE to "Borrar",
                            SketchMode.MARK to "Colocar soluciones"
                        ).forEach { (mode, title) ->
                            FilterChip(selected = sketchMode == mode,
                                onClick = { sketchMode = mode },
                                label = { Text(title) })
                        }
                    }
                    StudySketchCanvas(
                        sketch = SketchDrawing(strokes = sketchStrokes),
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        markers = solutions.flatMap { it.points },
                        showMarkers = sketchMode == SketchMode.MARK,
                        mode = sketchMode,
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
                }
                if (imageUri != null && !sketchEnabled) {
                    EditableAnnotationImage(
                        imageUri = requireNotNull(imageUri),
                        annotations = baseAnnotations,
                        modifier = Modifier.fillMaxWidth().height(250.dp),
                        markers = solutions.flatMap { it.points },
                        showCorrection = true,
                        onMarkerTap = if (photoMarkerMode) ::placeOrSelectMarker else null,
                        autoEdit = justCreatedImage == imageUri,
                        onAutoEditConsumed = { justCreatedImage = null },
                        onAnnotationsChanged = { baseAnnotations = it },
                        onReplace = {
                            if (solutions.any { it.points.isNotEmpty() })
                                error = "Quita las soluciones colocadas antes de cambiar la fotografía."
                            else chooseImage("written")
                        },
                        onDelete = {
                            if (solutions.any { it.points.isNotEmpty() })
                                error = "Quita las soluciones colocadas antes de eliminar la fotografía."
                            else {
                                imageUri = null
                                baseAnnotations = ImageAnnotations()
                                justCreatedImage = null
                            }
                        }
                    )
                    FilterChip(
                        selected = photoMarkerMode,
                        onClick = { photoMarkerMode = !photoMarkerMode },
                        label = { Text("Colocar soluciones" +
                            if (photoMarkerMode) " · activo" else "") }
                    )
                    if (photoMarkerMode) {
                        Text("Elige la solución y toca su posición en la imagen.",
                            color = TestarioMutedText)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            solutions.forEachIndexed { index, solution ->
                                FilterChip(
                                    selected = selectedSolutionId == solution.id,
                                    onClick = { selectedSolutionId = solution.id },
                                    label = { Text("Solución ${index + 1}") }
                                )
                            }
                        }
                    }
                }
                Text("Soluciones", color = TestarioText)
                solutions.forEach { solution ->
                    val index = solutions.indexOfFirst { it.id == solution.id }
                    Column(Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp)).padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Solución ${index + 1}", color = TestarioText, modifier = Modifier.weight(1f))
                            TextButton(enabled = solutions.size > 1, onClick = {
                                solutions = solutions.filterNot { it.id == solution.id }
                                if (selectedSolutionId == solution.id)
                                    selectedSolutionId = solutions.firstOrNull()?.id
                            }) { Text("Eliminar") }
                        }
                        OutlinedTextField(
                            value = solution.text,
                            onValueChange = { text -> solutions = solutions.map {
                                if (it.id == solution.id) it.copy(text = text) else it
                            } },
                            label = { Text("Escribe la solución ${index + 1}") }, modifier = Modifier.fillMaxWidth()
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
                TextButton(onClick = { showOptionalAliases = !showOptionalAliases }) {
                    Text(if (showOptionalAliases) "Ocultar variantes especiales  ▴"
                        else "Variantes especiales (opcional)  ▾")
                }
                if (showOptionalAliases) {
                    Text("NEXUM reconoce variantes y abreviaturas inequívocas automáticamente. Añade aquí solo excepciones específicas.",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }

            } else {
                val simpleAnki = flashKind in setOf(FlashcardKind.BASIC, FlashcardKind.CLOZE)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (simpleAnki) "Tarjeta Anki" else "Tarjeta heredada · ${flashKind.name}",
                        color = TestarioText, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (frontImageUri == null || backImageUri == null) Box {
                        TextButton(onClick = { showAnkiImageMenu = true }) {
                            Text("Añadir imagen +", color = TestarioPrimary)
                        }
                        DropdownMenu(
                            expanded = showAnkiImageMenu,
                            onDismissRequest = { showAnkiImageMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Imagen de pregunta") },
                                enabled = frontImageUri == null,
                                onClick = {
                                    showAnkiImageMenu = false
                                    pendingAnkiImageTarget = "front"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Imagen de respuesta") },
                                enabled = backImageUri == null,
                                onClick = {
                                    showAnkiImageMenu = false
                                    pendingAnkiImageTarget = "back"
                                }
                            )
                        }
                    }
                }
                if (!simpleAnki) {
                    Text("El formato anterior se conserva para no alterar tarjetas guardadas.",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = front, onValueChange = { front = it; error = null },
                    label = { Text("Pregunta / enunciado") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                TextButton(onClick = { showHowItWorks = !showHowItWorks }) {
                    Text(if (showHowItWorks) "Cómo funciona  ▴" else "Cómo funciona  ▾",
                        color = TestarioPrimary)
                }
                if (showHowItWorks) {
                    Column(
                        Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text("Escribe el enunciado y selecciona una palabra o frase. Pulsa Marcar selección: aparecerá en la lista y podrás asignarle Siempre o un turno.",
                            color = TestarioText)
                        Text("{{palabra}} se oculta cada vez. {{1:palabra}} y {{2:frase}} se alternan en turnos 1 y 2. Repetir el mismo número oculta los fragmentos juntos.",
                            color = TestarioMutedText)
                        Text("También sirven [[palabra]] y [[1:palabra]]. Puedes escribir los marcadores directamente.",
                            color = TestarioMutedText)
                        Text("En imágenes de pregunta, activa Colocar oclusiones y toca la foto para añadir bloques que tapan zonas; al corregir se descubre la respuesta. El rotulador y el subrayador se editan con el lápiz.",
                            color = TestarioMutedText)
                    }
                }
                frontImageUri?.let { image ->
                    Text("Imagen de pregunta", color = TestarioMutedText)
                    val textHasGaps = runCatching {
                        ClozeMarkupEngine.parse(front.text).gaps.isNotEmpty()
                    }.getOrDefault(false)
                    FilterChip(
                        selected = showAnkiOcclusions,
                        onClick = {
                            if (textHasGaps && !showAnkiOcclusions) {
                                error = "Esta tarjeta ya tiene fragmentos de texto: para evitar perder turnos, utiliza otra tarjeta para oclusiones de imagen."
                            } else {
                                showAnkiOcclusions = !showAnkiOcclusions
                                occlusionMarkMode = showAnkiOcclusions
                            }
                        },
                        label = { Text(
                            if (showAnkiOcclusions) "Colocar oclusiones · activo"
                            else "Colocar oclusiones"
                        ) }
                    )
                    EditableAnnotationImage(
                        imageUri = image,
                        annotations = frontAnnotations,
                        modifier = Modifier.fillMaxWidth().height(265.dp),
                        masks = masks,
                        autoEdit = justCreatedImage == image,
                        onAutoEditConsumed = { justCreatedImage = null },
                        onMarkerTap = if (showAnkiOcclusions && occlusionMarkMode) { x, y ->
                            masks = masks + ImageOcclusionMask(
                                x = x.coerceIn(0.0, 0.82),
                                y = y.coerceIn(0.0, 0.88),
                                width = 0.18, height = 0.12
                            )
                        } else null,
                        onAnnotationsChanged = { frontAnnotations = it },
                        onReplace = { chooseImage("front") },
                        onDelete = {
                            if (masks.isNotEmpty()) error =
                                "Elimina primero las oclusiones antes de quitar esta foto."
                            else {
                                frontImageUri = null
                                frontAnnotations = ImageAnnotations()
                            }
                        }
                    )
                    if (showAnkiOcclusions || masks.isNotEmpty()) {
                        Text("Las zonas negras ocultarán partes de la fotografía. Se descubren al responder.",
                            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    masks.forEachIndexed { index, mask ->
                        Column(
                            Modifier.fillMaxWidth()
                                .background(TestarioSurface, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Zona ${index + 1}", color = TestarioText,
                                    modifier = Modifier.weight(1f))
                                TextButton(onClick = { masks = masks.filterNot { it.id == mask.id } }) {
                                    Text("Quitar")
                                }
                            }
                            OutlinedTextField(
                                value = mask.answer,
                                onValueChange = { answer -> masks = masks.map {
                                    if (it.id == mask.id) it.copy(answer = answer) else it
                                } },
                                label = { Text("Qué se oculta (opcional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("Anchura de la oclusión", color = TestarioMutedText)
                            Slider(
                                value = mask.width.toFloat(),
                                onValueChange = { width ->
                                    masks = masks.map {
                                        if (it.id == mask.id) it.copy(width = width.toDouble()) else it
                                    }
                                },
                                valueRange = .03f..(1.0 - mask.x).toFloat()
                            )
                            Text("Altura de la oclusión", color = TestarioMutedText)
                            Slider(
                                value = mask.height.toFloat(),
                                onValueChange = { height ->
                                    masks = masks.map {
                                        if (it.id == mask.id) it.copy(height = height.toDouble()) else it
                                    }
                                },
                                valueRange = .03f..(1.0 - mask.y).toFloat()
                            )
                        }
                    }
                }
                backImageUri?.let { image ->
                    Text("Imagen de respuesta", color = TestarioMutedText)
                    EditableAnnotationImage(
                        imageUri = image,
                        annotations = backAnnotations,
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        autoEdit = justCreatedImage == image,
                        onAutoEditConsumed = { justCreatedImage = null },
                        onAnnotationsChanged = { backAnnotations = it },
                        onReplace = { chooseImage("back") },
                        onDelete = {
                            backImageUri = null
                            backAnnotations = ImageAnnotations()
                        }
                    )
                }
                if (simpleAnki) {
                    if (front.selection.min < front.selection.max) {
                        Button(onClick = {
                            runCatching {
                                ClozeMarkupEngine.markSelection(
                                    front.text, front.selection.min, front.selection.max, null
                                )
                            }.onSuccess {
                                front = TextFieldValue(it)
                                error = null
                            }.onFailure { error = it.message }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text("Marcar el texto seleccionado")
                        }
                    }
                    val candidateMarkup = runCatching { ClozeMarkupEngine.parse(front.text) }
                    candidateMarkup.onSuccess { parsed ->
                        if (parsed.gaps.isNotEmpty()) {
                            Text("Fragmentos ocultos", color = TestarioText,
                                style = MaterialTheme.typography.titleMedium)
                            val setTurn: (Int, Int?) -> Unit = { idx, group ->
                                runCatching {
                                    val changed = parsed.gaps.toMutableList()
                                    changed[idx] = changed[idx].copy(groupNumber = group)
                                    ClozeMarkupEngine.encode(parsed.text, changed)
                                }.onSuccess {
                                    front = TextFieldValue(it)
                                    error = null
                                }.onFailure { error = it.message }
                            }
                            parsed.gaps.forEachIndexed { idx, gap ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        parsed.text.substring(gap.start, gap.end),
                                        modifier = Modifier.weight(1f),
                                        maxLines = 2, color = TestarioText
                                    )
                                    Box {
                                        TextButton(onClick = { turnMenuGap = idx }) {
                                            Text(gap.groupNumber?.let { "Turno $it ▾" }
                                                ?: "Siempre ▾")
                                        }
                                        DropdownMenu(
                                            expanded = turnMenuGap == idx,
                                            onDismissRequest = { turnMenuGap = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Siempre") },
                                                onClick = { turnMenuGap = null; setTurn(idx, null) }
                                            )
                                            (1..3).forEach { number ->
                                                DropdownMenuItem(
                                                    text = { Text("Turno $number") },
                                                    onClick = { turnMenuGap = null; setTurn(idx, number) }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text("Otro turno…") },
                                                onClick = {
                                                    turnMenuGap = null
                                                    customTurnInput = (gap.groupNumber ?: 4).toString()
                                                    showSelectionTurnEditor = idx
                                                }
                                            )
                                        }
                                    }
                                    TextButton(onClick = {
                                        runCatching { ClozeMarkupEngine.unmark(front.text, idx) }
                                            .onSuccess { front = TextFieldValue(it) }
                                            .onFailure { error = it.message }
                                    }) { Text("Quitar") }
                                }
                            }
                        }
                    }.onFailure {
                        Text(it.message ?: "Comprueba los marcadores.",
                            color = TestarioRed, style = MaterialTheme.typography.bodySmall)
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
                if (flashKind == FlashcardKind.REVERSED) {
                    Text("Se estudiarán ambas direcciones; la pregunta se mantiene encima de la solución.",
                        color = TestarioMutedText)
                }
            }

            if (showPreview) {
                val candidate = runCatching { construct(draft = false) }
                Column(Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("Vista previa", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    candidate.onSuccess { ex ->
                        if (ex.kind == StudyExerciseKind.WRITTEN_MULTI) {
                            Text(ex.prompt, color = TestarioText)
                            ex.sketch?.let { drawing ->
                                StudySketchCanvas(
                                    sketch = drawing,
                                    modifier = Modifier.fillMaxWidth().height(220.dp),
                                    markers = ex.solutions.flatMap { it.points },
                                    mode = SketchMode.PREVIEW,
                                    showMarkers = previewRevealed
                                )
                            }
                            ex.baseImageUri?.let { image ->
                                StudyVisualImage(image, modifier = Modifier.fillMaxWidth().height(220.dp),
                                    annotations = ex.baseAnnotations,
                                    markers = ex.solutions.flatMap { it.points },
                                    showCorrection = previewRevealed)
                            }
                            if (previewRevealed) ex.solutions.forEach {
                                Text(it.text, color = TestarioGreen)
                            }
                        } else {
                            val card = ex.flashcard
                            if (card != null) {
                                Text(if (previewRevealed) card.front else
                                    ClozeMarkupEngine.mask(card.front, card.gaps, 0),
                                    color = TestarioText)
                                card.frontImageUri?.let { image ->
                                    StudyVisualImage(image, modifier = Modifier.fillMaxWidth().height(190.dp),
                                        annotations = card.frontAnnotations)
                                }
                                if (previewRevealed) {
                                    Divider()
                                    Text("Respuesta", color = TestarioGreen)
                                    Text(card.back.ifBlank { card.gaps.joinToString(", ") { g ->
                                        card.front.substring(g.start, g.end) } }, color = TestarioText)
                                }
                            }
                        }
                        val reasons = StudyExerciseRules.errors(ex)
                        if (reasons.isNotEmpty()) Text(reasons.joinToString("\n"), color = TestarioRed)
                        else Text("Contenido válido", color = TestarioGreen)
                    }.onFailure {
                        Text(it.message ?: "Formato incompleto", color = TestarioRed)
                    }
                    OutlinedButton(onClick = { previewRevealed = !previewRevealed }) {
                        Text(if (previewRevealed) "Ver como alumno" else "Mostrar corrección")
                    }
                }
            }
            OutlinedButton(onClick = { showPreview = !showPreview }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showPreview) "Ocultar vista previa" else "Vista previa y comprobaciones")
            }
            if (error != null) Text(error.orEmpty(), color = TestarioRed)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    runCatching { libraryViewModel.saveStudyExercise(topicId, construct(draft = true), original?.revision) }
                        .onSuccess { if (it) onBack() else error = "Este ejercicio se ha modificado en otro lugar." }
                        .onFailure { error = it.message ?: "No se pudo guardar el borrador." }
                }, modifier = Modifier.weight(1f)) { Text("Borrador") }
                Button(onClick = {
                    val candidate = construct(draft = false)
                    val errors = StudyExerciseRules.errors(candidate)
                    if (errors.isNotEmpty()) error = errors.joinToString("\n")
                    else runCatching {
                        libraryViewModel.saveStudyExercise(topicId, candidate, original?.revision)
                    }.onSuccess {
                        if (it) onBack() else error = "Conflicto de edición; recarga el ejercicio."
                    }.onFailure { error = it.message ?: "No se pudo guardar." }
                }, modifier = Modifier.weight(1f)) { Text("Guardar") }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}
