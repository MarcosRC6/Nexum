package com.example.testario.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.testario.viewmodel.EditorDraftViewModel
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.StudyExerciseKind
import com.example.testario.ui.theme.*
import com.example.testario.viewmodel.LibraryViewModel

/** One creation entry point. Existing question/exercise IDs retain their original editors. */
private enum class AuthoringKind { TEST, WRITTEN, ANKI }

@Composable
fun UnifiedQuestionEditorScreen(
    topicId: String,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit
) {
    val drafts: EditorDraftViewModel = viewModel()
    var selected by drafts.state("unified:$topicId", "selectedKind") { AuthoringKind.TEST }
    Column(Modifier.fillMaxSize()) {
        TestarioTopBar(title = "Añadir pregunta", onBack = onBack)
        Text("Tipo de pregunta", modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp),
            color = TestarioMutedText)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                AuthoringKind.TEST to "Test",
                AuthoringKind.WRITTEN to "Respuesta escrita",
                AuthoringKind.ANKI to "Anki"
            ).forEach { (kind, label) ->
                FilterChip(
                    modifier = Modifier.weight(if (kind == AuthoringKind.WRITTEN) 1.5f else 1f),
                    selected = selected == kind,
                    onClick = { selected = kind },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }
        key(selected) {
            when (selected) {
                AuthoringKind.TEST -> QuestionEditorScreen(
                    topicId = topicId, questionId = null,
                    libraryViewModel = libraryViewModel, onBack = onBack, embedded = true
                )
                AuthoringKind.WRITTEN, AuthoringKind.ANKI -> StudyExerciseEditorScreen(
                    topicId = topicId, exerciseId = null,
                    initialKind = if (selected == AuthoringKind.WRITTEN)
                        StudyExerciseKind.WRITTEN_MULTI else StudyExerciseKind.ANKI_CARD,
                    libraryViewModel = libraryViewModel, onBack = onBack, embedded = true
                )
            }
        }
    }
}
