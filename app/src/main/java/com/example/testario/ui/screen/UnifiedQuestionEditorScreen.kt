package com.example.testario.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.StudyExerciseKind
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.LibraryViewModel

/** One entry point in a topic, retaining legacy edit routes for existing records. */
private enum class NewQuestionKind { TEST, WRITTEN, ANKI }

@Composable
fun UnifiedQuestionEditorScreen(
    topicId: String,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    var kind by remember { mutableStateOf(NewQuestionKind.TEST) }
    var requested by remember { mutableStateOf<NewQuestionKind?>(null) }
    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioTopBar("Añadir pregunta", onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    NewQuestionKind.TEST to "Test",
                    NewQuestionKind.WRITTEN to "Respuesta escrita",
                    NewQuestionKind.ANKI to "Anki"
                ).forEach { (choice, title) ->
                    FilterChip(
                        selected = kind == choice,
                        onClick = { if (kind != choice) requested = choice },
                        label = { Text(title, maxLines = 1) },
                        modifier = Modifier.weight(
                            if (choice == NewQuestionKind.WRITTEN) 1.6f else 1f
                        )
                    )
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (kind) {
                    NewQuestionKind.TEST -> QuestionEditorScreen(
                        topicId = topicId, questionId = null,
                        libraryViewModel = libraryViewModel,
                        onBack = onBack, showTopBar = false
                    )
                    NewQuestionKind.WRITTEN, NewQuestionKind.ANKI ->
                        StudyExerciseEditorScreen(
                            topicId = topicId, exerciseId = null,
                            initialKind = if (kind == NewQuestionKind.WRITTEN)
                                StudyExerciseKind.WRITTEN_MULTI else StudyExerciseKind.ANKI_CARD,
                            libraryViewModel = libraryViewModel,
                            onBack = onBack, showTopBar = false, showKindSelector = false
                        )
                }
            }
        }
    }
    requested?.let { destination ->
        AlertDialog(
            onDismissRequest = { requested = null },
            title = { Text("Cambiar tipo de pregunta") },
            text = {
                Text("Si has empezado a escribir, los cambios que no hayas guardado se perderán. " +
                    "Las preguntas y ejercicios ya guardados no se modifican.")
            },
            confirmButton = {
                TextButton(onClick = {
                    kind = destination
                    requested = null
                }) { Text("Cambiar") }
            },
            dismissButton = {
                TextButton(onClick = { requested = null }) { Text("Cancelar") }
            }
        )
    }
}
