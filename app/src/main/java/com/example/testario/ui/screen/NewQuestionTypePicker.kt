package com.example.testario.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One creation entry in Biblioteca. Existing records cannot silently change kind. */
internal enum class NewQuestionType { TEST, WRITTEN, ANKI }

@Composable
internal fun NewQuestionTypePicker(
    selected: NewQuestionType,
    onSelect: (NewQuestionType) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        listOf(
            NewQuestionType.TEST to "Test",
            NewQuestionType.WRITTEN to "Respuesta escrita",
            NewQuestionType.ANKI to "Anki"
        ).forEach { (kind, text) ->
            FilterChip(
                modifier = Modifier.weight(if (kind == NewQuestionType.WRITTEN) 1.7f else 1f),
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(text, maxLines = 1) }
            )
        }
    }
}
