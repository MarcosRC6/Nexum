package com.example.testario.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.StudyExercise
import com.example.testario.domain.StudyExerciseRules
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioRed

/** Compact correction: do not repeat 'Correcta' and 'Faltaban' for one answer. */
@Composable
internal fun WrittenCorrectionRows(
    exercise: StudyExercise,
    answers: List<String>,
    grade: StudyExerciseRules.WrittenResult
) {
    val numbered = exercise.solutions.any { it.points.isNotEmpty() }
    if (numbered) {
        StudyExerciseRules.positionalSolutions(exercise).forEachIndexed { index, solution ->
            val entry = answers.getOrNull(index).orEmpty()
            val accepted = solution.id in grade.correctIds
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${index + 1}. ${entry.ifBlank { "Sin responder" }} ${if (accepted) "✓" else "✗"}",
                    color = if (accepted) TestarioGreen else TestarioRed,
                    style = MaterialTheme.typography.bodyLarge)
                if (!accepted) {
                    Text("${solution.text} ✓", color = TestarioGreen,
                        style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        // Enumeration is order-independent: never claim response 2 must match
        // canonical solution 2 when no numbered photograph exists.
        val entries = answers.filter { it.isNotBlank() }
        val seen = mutableSetOf<String>()
        entries.forEachIndexed { index, entry ->
            val matched = StudyExerciseRules.findAcceptedSolution(exercise, entry)?.id
            val isNew = matched != null && seen.add(matched)
            Text("${index + 1}. $entry ${if (isNew) "✓" else if (matched != null) "·" else "✗"}",
                color = if (isNew) TestarioGreen else if (matched != null)
                    TestarioMutedText else TestarioRed,
                style = MaterialTheme.typography.bodyLarge)
        }
        val missing = exercise.solutions.filterNot { it.id in grade.correctIds }
        missing.forEach { solution ->
            Text("${solution.text} ✓", color = TestarioGreen,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}
