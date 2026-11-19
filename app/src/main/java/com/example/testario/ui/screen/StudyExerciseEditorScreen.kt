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
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.firstOrNull { it.id == topicId }
    val original = topic?.exercises?.firstOrNull { it.id == exerciseId }
    var exerciseKind by remember(exerciseId) { mutableStateOf(original?.kind ?: StudyExerciseKind.WRITTEN_MULTI) }
    var prompt by remember(exerciseId) { mutableStateOf(original?.prompt.orEmpty()) }
    var solutions by remember(exerciseId) {
        mutableStateOf(original?.solutions ?: listOf(CanonicalSolution(text = "")))
    }
    var selectedSolutionId by remember(exerciseId) {
        mutableStateOf(original?.solutions?.firstOrNull()?.id)
    }
    var diagramEnabled by remember(exerciseId) { mutableStateOf(original?.diagram != null) }
    var directionArrow by remember(exerciseId) { mutableStateOf(original?.diagram?.directionArrow == true) }
    var front by remember(exerciseId) {
        mutableStateOf(TextFieldValue(original?.flashcard?.front.orEmpty()))
    }
    var back by remember(exerciseId) { mutableStateOf(original?.flashcard?.back.orEmpty()) }
    var flashKind by remember(exerciseId) { mutableStateOf(original?.flashcard?.kind ?: FlashcardKind.BASIC) }
    var gaps by remember(exerciseId) { mutableStateOf(original?.flashcard?.gaps.orEmpty()) }
    var masks by remember(exerciseId) { mutableStateOf(original?.flashcard?.masks.orEmpty()) }
    var aliasesText by remember(exerciseId) { mutableStateOf(original?.flashcard?.acceptedAnswers?.joinToString(", ").orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf(false) }
    var imageUri by remember(exerciseId) { mutableStateOf(original?.baseImageUri) }
    var frontImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.frontImageUri) }
    var backImageUri by remember(exerciseId) { mutableStateOf(original?.flashcard?.backImageUri) }
    var pickerTarget by remember { mutableStateOf("written") }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) when (pickerTarget) {
            "written" -> {
                imageUri = uri.toString()
                diagramEnabled = false
            }
            "front" -> frontImageUri = uri.toString()
            "back" -> backImageUri = uri.toString()
        }
    }
    fun chooseImage(target: String) {
        if (target == "written" && diagramEnabled && solutions.any { it.points.isNotEmpty() }) {
            error = "Antes de reemplazar el esquema, elimina sus marcadores; no se pueden trasladar automáticamente a una fotografía."
            return
        }
        pickerTarget = target
        pickImage.launch("image/*")
    }
    fun construct(draft: Boolean): StudyExercise {
        val card = if (exerciseKind == StudyExerciseKind.ANKI_CARD) FlashcardContent(
            kind = flashKind, front = front.text, back = back,
            frontImageUri = frontImageUri, backImageUri = backImageUri,
            gaps = gaps, masks = masks,
            acceptedAnswers = aliasesText.split(",").map(String::trim).filter(String::isNotBlank)
        ) else null
        return StudyExercise(
            id = original?.id ?: UUID.randomUUID().toString(),
            kind = exerciseKind, prompt = if (exerciseKind == StudyExerciseKind.ANKI_CARD) front.text else prompt,
            solutions = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) solutions else emptyList(),
            flashcard = card, baseImageUri = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) imageUri else null,
            diagram = if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI && diagramEnabled)
                LineDiagram(directionArrow = directionArrow) else null,
            revision = original?.revision ?: 1, draft = draft
        )
    }

    val schematicLineColor = TestarioPrimary

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
                Text("Soluciones correctas y alias", color = TestarioText)
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
                        OutlinedTextField(
                            value = solution.aliases.joinToString(", "),
                            onValueChange = { text -> solutions = solutions.map {
                                if (it.id == solution.id) it.copy(aliases = text.split(",")
                                    .map(String::trim).filter(String::isNotBlank)) else it
                            } },
                            label = { Text("Alias separados por comas") }, modifier = Modifier.fillMaxWidth()
                        )
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = diagramEnabled, onCheckedChange = { enabled ->
                        if (!enabled && solutions.any { it.points.isNotEmpty() }) {
                            error = "Borra los marcadores antes de cambiar de esquema a imagen."
                        } else {
                            diagramEnabled = enabled
                            if (enabled) imageUri = null
                        }
                    })
                    Text("Crear esquema lineal", color = TestarioText)
                }
                if (diagramEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = directionArrow, onCheckedChange = { directionArrow = it })
                        Text("Flecha de orientación", color = TestarioText)
                    }
                    Text("Selecciona una solución y toca la línea donde quieras añadir un número.", color = TestarioMutedText)
                    BoxWithConstraints(
                        Modifier.fillMaxWidth().height(170.dp).background(TestarioSurface, RoundedCornerShape(12.dp))
                            .pointerInput(selectedSolutionId, solutions) {
                                detectTapGestures { offset ->
                                    val id = selectedSolutionId ?: return@detectTapGestures
                                    val x = (offset.x / size.width).toDouble().coerceIn(0.0, 1.0)
                                    val y = (offset.y / size.height).toDouble().coerceIn(0.0, 1.0)
                                    val number = (solutions.flatMap { it.points }.maxOfOrNull { it.order } ?: 0) + 1
                                    solutions = solutions.map { s ->
                                        if (s.id == id) s.copy(points = s.points + ImagePoint(x = x, y = y, order = number))
                                        else s
                                    }
                                }
                            }
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLine(schematicLineColor,
                                Offset(size.width * 0.1f, size.height * 0.5f),
                                Offset(size.width * 0.9f, size.height * 0.5f), strokeWidth = 8f)
                        }
                        solutions.flatMap { it.points }.forEach { pt ->
                            Text(
                                pt.order.toString(),
                                modifier = Modifier.offset(
                                    x = maxWidth * pt.x.toFloat() - 8.dp,
                                    y = maxHeight * pt.y.toFloat() - 12.dp
                                ),
                                color = TestarioGreen
                            )
                        }
                    }
                }
                if (!diagramEnabled && imageUri != null) {
                    Text("Toca la fotografía para añadir un marcador a la solución seleccionada.",
                        color = TestarioMutedText)
                    StudyVisualImage(
                        imageUri = requireNotNull(imageUri),
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        markers = solutions.flatMap { it.points },
                        showCorrection = true,
                        onTapImage = { x, y ->
                            val id = selectedSolutionId
                            val number = (solutions.flatMap { it.points }.maxOfOrNull { it.order } ?: 0) + 1
                            solutions = solutions.map { solution ->
                                if (solution.id == id) solution.copy(points = solution.points +
                                    ImagePoint(x = x, y = y, order = number)) else solution
                            }
                        }
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
                Text("Tipo de tarjeta", color = TestarioText)
                val supported = listOf(
                    FlashcardKind.BASIC to "Básica",
                    FlashcardKind.REVERSED to "Bidireccional",
                    FlashcardKind.CLOZE to "Completar huecos",
                    FlashcardKind.TYPE_ANSWER to "Respuesta escrita",
                    FlashcardKind.IMAGE_OCCLUSION to "Ocultación de imagen"
                )
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) {
                        Text(supported.firstOrNull { it.first == flashKind }?.second ?: "Tipo avanzado")
                    }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        supported.forEach { (kind, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                flashKind = kind; expanded = false
                            })
                        }
                    }
                }
                OutlinedTextField(
                    value = front, onValueChange = {
                        if (it.text != front.text) gaps = emptyList() // avoid stale character offsets
                        front = it
                    }, label = { Text("Pregunta / texto") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                if (flashKind == FlashcardKind.CLOZE) {
                    OutlinedButton(onClick = {
                        val lo = front.selection.min
                        val hi = front.selection.max
                        if (lo < hi && front.text.substring(lo, hi).isNotBlank()) {
                            val newGap = ClozeGap(start = lo, end = hi)
                            gaps = gaps + newGap
                            error = null
                        } else error = "Selecciona primero la palabra o frase que quieras ocultar."
                    }) { Text("Ocultar selección") }
                    Text("${gaps.size} huecos definidos", color = TestarioMutedText)
                    if (gaps.isNotEmpty()) TextButton(onClick = { gaps = gaps.dropLast(1) }) {
                        Text("Deshacer último hueco")
                    }
                }
                OutlinedTextField(
                    value = back, onValueChange = { back = it },
                    label = { Text(if (flashKind == FlashcardKind.CLOZE) "Explicación adicional (opcional)" else "Respuesta") },
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
                val candidate = construct(draft = false)
                val reasons = StudyExerciseRules.errors(candidate)
                Column(Modifier.fillMaxWidth().background(TestarioSurface, RoundedCornerShape(12.dp)).padding(12.dp)) {
                    Text("Vista previa", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    Text(if (exerciseKind == StudyExerciseKind.WRITTEN_MULTI) prompt else front.text,
                        color = TestarioText)
                    if (reasons.isNotEmpty()) Text(reasons.joinToString("\n"),
                        color = TestarioRed, style = MaterialTheme.typography.bodySmall)
                    else Text("Validación del contenido: correcta", color = TestarioGreen)
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
