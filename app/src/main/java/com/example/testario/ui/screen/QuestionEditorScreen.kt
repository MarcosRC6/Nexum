package com.example.testario.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.testario.ui.theme.NeonIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import com.example.testario.data.model.QuestionStats
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioRedDark
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.ui.theme.TestarioYellow
import com.example.testario.viewmodel.LibraryViewModel
import java.io.File
import java.util.UUID

@Composable
fun QuestionEditorScreen(
    topicId: String,
    questionId: String?,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()
    val topic = database.topics.firstOrNull { it.id == topicId }
    val existingQuestion = topic?.questions?.firstOrNull { it.id == questionId }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    var questionText by remember { mutableStateOf(existingQuestion?.text ?: "") }
    var answers by remember {
        mutableStateOf(
            existingQuestion?.answers ?: listOf(
                Answer(id = "a", text = ""),
                Answer(id = "b", text = ""),
                Answer(id = "c", text = "")
            )
        )
    }

    var correctAnswerId by remember { mutableStateOf(existingQuestion?.correctAnswerId ?: "") }
    var difficulty by remember { mutableStateOf(existingQuestion?.difficulty) }
    var explanation by remember { mutableStateOf(existingQuestion?.explanation ?: "") }
    // El campo histórico explanationImageUri se conserva como imagen del enunciado por compatibilidad.
    var statementImageUri by remember { mutableStateOf(existingQuestion?.explanationImageUri) }
    var correctionImageUri by remember { mutableStateOf(existingQuestion?.correctionImageUri) }
    var flagged by remember { mutableStateOf(existingQuestion?.flagged ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    // NUEVO: Estado para el diálogo de confirmación de borrado
    var showDeleteDialog by remember { mutableStateOf(false) }

    val statementImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) statementImageUri = uri.toString()
    }

    val correctionImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) correctionImageUri = uri.toString()
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = {
            TestarioTopBar(
                title = if (existingQuestion == null) "Nueva pregunta" else "Editar pregunta",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                EditorSectionCard {
                    Text(text = "Pregunta", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = questionText,
                        onValueChange = { questionText = it; error = null },
                        label = { Text("Texto de la pregunta") },
                        modifier = Modifier.fillMaxWidth().physicalKeyboardBehavior(focusManager),
                        minLines = 4,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }

            item { Text(text = "Respuestas", style = MaterialTheme.typography.titleMedium, color = TestarioMutedText) }

            itemsIndexed(answers) { index, answer ->
                AnswerEditorRow(
                    letter = ('A' + index).toString(),
                    answer = answer,
                    isCorrect = correctAnswerId == answer.id,
                    canDelete = answers.size > 3,
                    focusManager = focusManager,
                    onTextChange = { newText ->
                        answers = answers.map { if (it.id == answer.id) it.copy(text = newText) else it }
                        error = null
                    },
                    onCorrectSelected = { correctAnswerId = answer.id; error = null },
                    onDelete = {
                        if (answers.size > 3) {
                            answers = answers.filterNot { it.id == answer.id }
                            if (correctAnswerId == answer.id) correctAnswerId = ""
                        }
                    }
                )
            }

            item {
                OutlinedButton(
                    onClick = { answers = answers + Answer(id = UUID.randomUUID().toString(), text = "") },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, TestarioPrimary)
                ) { Text("+ Añadir respuesta") }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marcar pregunta (🚩)", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                        Switch(checked = flagged, onCheckedChange = { flagged = it }, colors = SwitchDefaults.colors(checkedThumbColor = TestarioPrimary))
                    }
                }
            }

            item {
                EditorSectionCard {
                    Text(text = "Dificultad", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DifficultyButton(text = "Nada", selected = difficulty == null, color = TestarioMutedText, modifier = Modifier.weight(1f)) { difficulty = null }
                        DifficultyButton(text = "Fácil", selected = difficulty == 1, color = TestarioGreen, modifier = Modifier.weight(1f)) { difficulty = 1 }
                        DifficultyButton(text = "Medio", selected = difficulty == 2, color = TestarioYellow, modifier = Modifier.weight(1f)) { difficulty = 2 }
                        DifficultyButton(text = "Difícil", selected = difficulty == 3, color = TestarioRed, modifier = Modifier.weight(1f)) { difficulty = 3 }
                    }
                }
            }

            item {
                EditorSectionCard {
                    Text(text = "Imagen del enunciado", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Se muestra mientras respondes la pregunta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TestarioMutedText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { statementImagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, TestarioPrimary)
                    ) {
                        Text(if (statementImageUri == null) "Adjuntar imagen del enunciado" else "Cambiar imagen del enunciado")
                    }
                    if (statementImageUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (statementImageUri!!.startsWith("content://")) Uri.parse(statementImageUri) else File(statementImageUri!!))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Imagen del enunciado",
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✓ Imagen lista", color = TestarioGreen)
                            TextButton(onClick = { statementImageUri = null }) { Text("Eliminar", color = TestarioRed) }
                        }
                    }
                }
            }

            item {
                EditorSectionCard {
                    Text(text = "Explicación", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text("Texto de explicación (opcional)") },
                        modifier = Modifier.fillMaxWidth().physicalKeyboardBehavior(focusManager),
                        minLines = 3,
                        shape = RoundedCornerShape(18.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(text = "Imagen de la explicación", style = MaterialTheme.typography.titleSmall, color = TestarioText)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Solo aparece al corregir: al responder en Entrenamiento y en la revisión del Examen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TestarioMutedText
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { correctionImagePicker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, TestarioPrimary)
                    ) {
                        Text(if (correctionImageUri == null) "Adjuntar imagen de explicación" else "Cambiar imagen de explicación")
                    }
                    if (correctionImageUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(if (correctionImageUri!!.startsWith("content://")) Uri.parse(correctionImageUri) else File(correctionImageUri!!))
                                .crossfade(true)
                                .build(),
                            contentDescription = "Imagen de la explicación",
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "✓ Imagen lista", color = TestarioGreen)
                            TextButton(onClick = { correctionImageUri = null }) { Text("Eliminar", color = TestarioRed) }
                        }
                    }
                }
            }

            if (error != null) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = TestarioRedDark), border = BorderStroke(1.dp, TestarioRed), shape = RoundedCornerShape(18.dp)) {
                        Text(text = error ?: "", color = TestarioText, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val validationError = validateQuestion(questionText, answers, correctAnswerId)
                        if (validationError != null) { error = validationError; return@Button }

                        val finalQuestion = Question(
                            id = existingQuestion?.id ?: UUID.randomUUID().toString(),
                            text = questionText.trim(),
                            answers = answers.map { it.copy(text = it.text.trim()) },
                            correctAnswerId = correctAnswerId,
                            difficulty = difficulty,
                            explanation = explanation.trim(),
                            explanationImageUri = statementImageUri,
                            correctionImageUri = correctionImageUri,
                            originTopicName = existingQuestion?.originTopicName,
                            favorite = existingQuestion?.favorite ?: false,
                            flagged = flagged,
                            isFlashcard = existingQuestion?.isFlashcard ?: false,
                            stats = existingQuestion?.stats ?: QuestionStats()
                        )

                        if (existingQuestion == null) { libraryViewModel.addQuestion(topicId, finalQuestion) }
                        else { libraryViewModel.updateQuestion(topicId, finalQuestion) }
                        onBack()
                    },
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Guardar pregunta") }
            }

            // NUEVO: Botón de eliminar (solo si la pregunta existe)
            if (existingQuestion != null) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.fillMaxWidth().height(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TestarioRed),
                        border = BorderStroke(1.dp, TestarioRed)
                    ) { Text("Eliminar pregunta") }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // NUEVO: Diálogo de confirmación para eliminar
    if (showDeleteDialog && existingQuestion != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = TestarioSurface,
            titleContentColor = TestarioText,
            textContentColor = TestarioMutedText,
            title = { Text("Eliminar pregunta") },
            text = { Text("¿Seguro que quieres eliminar esta pregunta? Se borrarán sus datos y sus imágenes (si tiene). Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        libraryViewModel.deleteQuestion(topicId, existingQuestion.id)
                        onBack() // Regresa a la pantalla anterior (TopicDetail o TestScreen)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar", color = TestarioText)
                }
            }
        )
    }
}

@Composable
private fun EditorSectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = TestarioSurface)) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun AnswerEditorRow(
    letter: String, answer: Answer, isCorrect: Boolean, canDelete: Boolean,
    focusManager: FocusManager, onTextChange: (String) -> Unit, onCorrectSelected: () -> Unit, onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (isCorrect) TestarioPrimaryDark else TestarioSurface),
        border = BorderStroke(width = 1.dp, color = if (isCorrect) TestarioPrimary else TestarioSurfaceLight)
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = TestarioSurfaceLight, modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(text = letter, color = TestarioText) }
            }
            Spacer(modifier = Modifier.width(10.dp))
            OutlinedTextField(value = answer.text, onValueChange = onTextChange, label = { Text("Respuesta") }, modifier = Modifier.weight(1f).physicalKeyboardBehavior(focusManager), shape = RoundedCornerShape(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            RadioButton(selected = isCorrect, onClick = onCorrectSelected)
            if (canDelete) { IconButton(onClick = onDelete) { Text("🗑") } }
        }
    }
}

@Composable
private fun DifficultyButton(text: String, selected: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(46.dp), shape = RoundedCornerShape(16.dp),
        selected = selected,
        border = BorderStroke(1.4.dp, TestarioPrimary),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) { Text(text, color = if (selected) TestarioPrimary else TestarioText) }
}

private fun validateQuestion(questionText: String, answers: List<Answer>, correctAnswerId: String): String? {
    if (questionText.trim().isBlank()) return "La pregunta no puede estar vacía."
    if (answers.size < 3) return "Debe haber mínimo 3 respuestas."
    if (answers.any { it.text.trim().isBlank() }) return "No puede haber respuestas vacías."
    if (correctAnswerId.isBlank()) return "Debes marcar una respuesta correcta."
    if (answers.none { it.id == correctAnswerId }) return "La respuesta correcta no es válida."
    return null
}

private fun Modifier.physicalKeyboardBehavior(focusManager: FocusManager): Modifier {
    return this
        .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel; up = FocusRequester.Cancel; down = FocusRequester.Cancel }
        .onPreviewKeyEvent { event ->
            if (event.key == Key.Tab && event.type == KeyEventType.KeyDown) {
                if (event.isShiftPressed) { focusManager.moveFocus(FocusDirection.Previous) }
                else { focusManager.moveFocus(FocusDirection.Next) }
                true
            } else { false }
        }
}