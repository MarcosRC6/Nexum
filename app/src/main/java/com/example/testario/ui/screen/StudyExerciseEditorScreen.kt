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
    onBack: () -> Unit
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
                imageUri = uri.toString()
                sketchEnabled = false
            }
            "front" -> frontImageUri = uri.toString()
            "back" -> backImageUri = uri.toString()
        }
    }
    fun chooseImage(target: String) {
        if (target == "written" && solutions.any { it.points.isNotEmpty() }) {
            error = "Antes de reemplazar el esquema, elimina sus marcadores; no se pueden trasladar automáticamente a una fotografía."
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
            gaps = stableGaps, masks = masks,
            acceptedAnswers = aliasesText.split(",").map(String::trim).filter(String::isNotBlank)
        ) else null
        return StudyExercise(
            id = original?.id ?: UUID.randomUUID().toString(),
            kind = exerciseKind, prompt = if (exerciseKind == StudyExerciseKind.ANKI_CARD) markup.text else prompt,
            solutions = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) solutions else emptyList(),
            flashcard = card, baseImageUri = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) imageUri else null,
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
        topBar = { TestarioTopBar("Editor de ejercicios", onBack = onBack) }
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
            Text("Tipo de ejercicio", color = TestarioText)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    StudyExerciseKind.WRITTEN_MULTI to "Respuesta escrita",
                    StudyExerciseKind.ANKI_CARD to "Tarjeta Anki"
                ).forEach { (kind, name) ->
                    FilterChip(selected = exerciseKind == kind,
                        onClick = { exerciseKind = kind; error = null }, label = { Text(name) })
                }
            }

            if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) {
                OutlinedTextField(
                    value = prompt, onValueChange = { prompt = it },
                    label = { Text("Enunciado") }, modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                Text("Respuestas correctas", color = TestarioText)
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
                            RadioButton(selected = selectedSolutionId == solution.id, onClick = {
                                selectedSolutionId = solution.id
                            })
                            Text("Solución ${index + 1}", color = TestarioText, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
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
                            Text("${solution.points.size} puntos asociados", color = TestarioMutedText)
                            TextButton(onClick = { solutions = solutions.map {
                                if (it.id == solution.id) it.copy(points = it.points.dropLast(1)) else it
                            } }) { Text("Deshacer último punto") }
                        }
                    }
                }
                OutlinedButton(onClick = {
                    val s = CanonicalSolution(text = "")
                    solutions = solutions + s
                    selectedSolutionId = s.id
                }, modifier = Modifier.fillMaxWidth()) { Text("+ Añadir solución") }

                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    if (!sketchEnabled && solutions.any { it.points.isNotEmpty() }) {
                        error = "Borra los marcadores actuales antes de cambiar de fotografía a dibujo."
                    } else {
                        sketchEnabled = !sketchEnabled
                        if (sketchEnabled) {
                            imageUri = null
                            sketchMode = SketchMode.DRAW
                        }
                    }
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (sketchEnabled) "Cerrar editor de imagen" else "Crear imagen")
                }
                if (sketchEnabled) {
                    Text("Dibuja con el dedo o lápiz táctil y después pasa a «Poner números».",
                        color = TestarioMutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf(
                            SketchMode.DRAW to "Dibujar",
                            SketchMode.ERASE to "Borrar",
                            SketchMode.MARK to "Números"
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
                } else if (imageUri != null) {
                    Text("Toca un punto existente para borrarlo; toca otra zona para añadir un número.",
                        color = TestarioMutedText)
                    StudyVisualImage(
                        imageUri = requireNotNull(imageUri),
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        markers = solutions.flatMap { it.points },
                        showCorrection = true,
                        onTapImage = ::placeOrSelectMarker
                    )
                    TextButton(onClick = {
                        if (solutions.any { it.points.isNotEmpty() }) error =
                            "Retira primero los marcadores antes de cambiar de imagen."
                        else imageUri = null
                    }) { Text("Quitar imagen base") }
                }
                OutlinedButton(onClick = { chooseImage("written") }, modifier = Modifier.fillMaxWidth()) {
                    Text("Importar imagen base")
                }
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
                OutlinedTextField(
                    value = front, onValueChange = { front = it; error = null },
                    label = { Text("Pregunta o texto de la tarjeta") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3
                )
                if (simpleAnki) {
                    Text("Selecciona una palabra o frase en el texto y márcala. Sin número se ocultará siempre; los turnos 1, 2, 3… se alternan cada vez que reveles la tarjeta.",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        runCatching {
                            ClozeMarkupEngine.markSelection(
                                front.text, front.selection.min, front.selection.max, null
                            )
                        }.onSuccess {
                            front = TextFieldValue(it)
                            error = null
                        }.onFailure { error = it.message }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ocultar siempre la selección") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = clozeTurnInput,
                            onValueChange = { clozeTurnInput = it.filter(Char::isDigit).take(3) },
                            label = { Text("Turno nº") },
                            modifier = Modifier.width(120.dp), singleLine = true
                        )
                        OutlinedButton(onClick = {
                            val number = clozeTurnInput.toIntOrNull()
                            if (number == null || number !in 1..999) {
                                error = "Elige un número entre 1 y 999."
                            } else runCatching {
                                ClozeMarkupEngine.markSelection(
                                    front.text, front.selection.min, front.selection.max, number
                                )
                            }.onSuccess {
                                front = TextFieldValue(it)
                                error = null
                            }.onFailure { error = it.message }
                        }, modifier = Modifier.weight(1f)) { Text("Ocultar en este turno") }
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
                    Text("También puedes escribir {{palabra}}, {{1:palabra}}, {{2:frase}} o [[1:palabra]].",
                        color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
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
                if (flashKind == FlashcardKind.IMAGE_OCCLUSION) {
                    Text("Importa una imagen y pulsa las zonas que quieras ocultar.",
                        color = TestarioMutedText)
                    frontImageUri?.let { image ->
                        StudyVisualImage(
                            imageUri = image,
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            masks = masks,
                            onTapImage = { x, y ->
                                masks = masks + ImageOcclusionMask(
                                    x = x.coerceAtMost(0.82), y = y.coerceAtMost(0.82),
                                    width = 0.18, height = 0.18
                                )
                            }
                        )
                    }
                    masks.forEach { mask ->
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { chooseImage("front") }) { Text("Imagen pregunta") }
                    OutlinedButton(onClick = { chooseImage("back") }) { Text("Imagen respuesta") }
                }
                if (frontImageUri != null || backImageUri != null)
                    Text("Imágenes asociadas: ${listOfNotNull(frontImageUri, backImageUri).size}",
                        color = TestarioMutedText)
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
                                    StudyVisualImage(image, modifier = Modifier.fillMaxWidth().height(190.dp))
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
