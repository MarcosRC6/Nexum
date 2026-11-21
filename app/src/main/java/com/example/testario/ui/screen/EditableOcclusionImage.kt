package com.example.testario.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.ImageAnnotations
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.ui.theme.*

/**
 * Compact preview: edit in a dedicated full-screen canvas instead of fighting
 * microscopic resize handles and horizontally overflowing controls.
 * The parent's existing save action persists the unchanged mask schema.
 */
@Composable
internal fun EditableOcclusionImage(
    imageUri: String,
    annotations: ImageAnnotations,
    masks: List<ImageOcclusionMask>,
    onMasksChanged: (List<ImageOcclusionMask>) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by remember(imageUri) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Zonas ocultas", color = TestarioText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { editing = true }) {
                Text("Editar a pantalla completa", color = TestarioPrimary)
            }
        }
        StudyVisualImage(imageUri = imageUri, annotations = annotations,
            masks = masks, modifier = Modifier.fillMaxWidth().height(240.dp),
            enableFullscreen = false)
        Text("${masks.size} ${if (masks.size == 1) "zona" else "zonas"} · " +
            "Dibuja directamente los rectángulos en el editor ampliado.",
            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
    }
    if (editing) {
        OcclusionTouchEditor(
            imageUri = imageUri, annotations = annotations, masks = masks,
            onMasksChanged = onMasksChanged, onDismiss = { editing = false }
        )
    }
}
