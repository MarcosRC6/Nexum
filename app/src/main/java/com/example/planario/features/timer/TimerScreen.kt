package com.example.planario.features.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planario.domain.timer.PomodoroTimerState
import com.example.planario.domain.timer.StudyTimerState

@Composable
fun TimerScreen(
    viewModel: TimerViewModel = viewModel(factory = TimerViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    TimerContent(
        uiState = uiState,
        onStartStudy = viewModel::startStudyTimer,
        onPauseStudy = viewModel::pauseStudyTimer,
        onResumeStudy = viewModel::resumeStudyTimer,
        onFinishStudy = viewModel::finishStudyTimer,
        onDiscardStudy = viewModel::discardStudyTimer,
        onSetPomodoroDuration = viewModel::setPomodoroDuration,
        onStartPomodoro = viewModel::startPomodoro,
        onPausePomodoro = viewModel::pausePomodoro,
        onResumePomodoro = viewModel::resumePomodoro,
        onResetPomodoro = viewModel::resetPomodoro
    )
}

@Composable
private fun TimerContent(
    uiState: TimerUiState,
    onStartStudy: () -> Unit,
    onPauseStudy: () -> Unit,
    onResumeStudy: () -> Unit,
    onFinishStudy: () -> Unit,
    onDiscardStudy: () -> Unit,
    onSetPomodoroDuration: (Int) -> Unit,
    onStartPomodoro: () -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onResetPomodoro: () -> Unit
) {
    var showFinishDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showPomodoroDurationDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TimerHeader()

            TimeGoalCard(uiState = uiState)

            StudyTimerCard(
                uiState = uiState,
                onStartStudy = onStartStudy,
                onPauseStudy = onPauseStudy,
                onResumeStudy = onResumeStudy,
                onFinishStudyClick = { showFinishDialog = true },
                onDiscardStudyClick = { showDiscardDialog = true }
            )

            PomodoroCard(
                uiState = uiState,
                onChoosePomodoroDurationClick = {
                    showPomodoroDurationDialog = true
                },
                onStartPomodoro = onStartPomodoro,
                onPausePomodoro = onPausePomodoro,
                onResumePomodoro = onResumePomodoro,
                onResetPomodoro = onResetPomodoro
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showFinishDialog) {
        FinishStudyDialog(
            elapsedText = uiState.studyElapsedText,
            onDismiss = { showFinishDialog = false },
            onConfirm = {
                onFinishStudy()
                showFinishDialog = false
            }
        )
    }

    if (showDiscardDialog) {
        DiscardStudyDialog(
            onDismiss = { showDiscardDialog = false },
            onConfirm = {
                onDiscardStudy()
                showDiscardDialog = false
            }
        )
    }

    if (showPomodoroDurationDialog) {
        PomodoroDurationDialog(
            initialMinutes = uiState.lastPomodoroMinutes,
            onDismiss = { showPomodoroDurationDialog = false },
            onConfirm = { minutes ->
                onSetPomodoroDuration(minutes)
                showPomodoroDurationDialog = false
            }
        )
    }
}

@Composable
private fun TimerHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Reloj",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Controla el estudio real y usa el pomodoro como apoyo.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeGoalCard(
    uiState: TimerUiState
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Objetivo de hoy",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = uiState.activeTopicName ?: "Sin tema activo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = uiState.totalTodayIncludingCurrentText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            LinearProgressIndicator(
                progress = { uiState.todayProgress },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimerSummaryItem(
                    label = "Llevas",
                    value = uiState.totalTodayIncludingCurrentText
                )

                TimerSummaryItem(
                    label = "Objetivo",
                    value = uiState.dailyGoalText
                )

                TimerSummaryItem(
                    label = "Restante",
                    value = uiState.remainingTodayText
                )
            }

            if (uiState.studyTimerState != StudyTimerState.STOPPED) {
                Text(
                    text = "Cronómetro actual: ${uiState.studyElapsedText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StudyTimerCard(
    uiState: TimerUiState,
    onStartStudy: () -> Unit,
    onPauseStudy: () -> Unit,
    onResumeStudy: () -> Unit,
    onFinishStudyClick: () -> Unit,
    onDiscardStudyClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Estudio total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = uiState.studyElapsedText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = uiState.studyTimerState.toReadableText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (uiState.studyTimerState) {
                StudyTimerState.STOPPED -> {
                    Button(
                        onClick = onStartStudy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                    ) {
                        Text(text = "Empezar estudio")
                    }
                }

                StudyTimerState.RUNNING -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onPauseStudy,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Text(text = "Pausar")
                        }

                        Button(
                            onClick = onFinishStudyClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Text(text = "Guardar")
                        }
                    }
                }

                StudyTimerState.PAUSED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onResumeStudy,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Text(text = "Reanudar")
                        }

                        FilledTonalButton(
                            onClick = onFinishStudyClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                        ) {
                            Text(text = "Guardar")
                        }
                    }

                    OutlinedButton(
                        onClick = onDiscardStudyClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Descartar cronómetro")
                    }
                }
            }
        }
    }
}

@Composable
private fun PomodoroCard(
    uiState: TimerUiState,
    onChoosePomodoroDurationClick: () -> Unit,
    onStartPomodoro: () -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onResetPomodoro: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Pomodoro",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Duración elegida: ${uiState.selectedPomodoroText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "El pomodoro no suma horas. Solo sirve como ayuda para estudiar por bloques.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = uiState.pomodoroRemainingText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            LinearProgressIndicator(
                progress = { uiState.pomodoroProgress },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = uiState.pomodoroTimerState.toReadableText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when (uiState.pomodoroTimerState) {
                PomodoroTimerState.STOPPED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onChoosePomodoroDurationClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Elegir duración")
                        }

                        Button(
                            enabled = uiState.canStartPomodoro,
                            onClick = onStartPomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Empezar")
                        }
                    }
                }

                PomodoroTimerState.RUNNING -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onPausePomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Pausar")
                        }

                        OutlinedButton(
                            onClick = onResetPomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Reiniciar")
                        }
                    }
                }

                PomodoroTimerState.PAUSED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onResumePomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Reanudar")
                        }

                        OutlinedButton(
                            onClick = onResetPomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Reiniciar")
                        }
                    }
                }

                PomodoroTimerState.FINISHED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = onChoosePomodoroDurationClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Elegir duración")
                        }

                        Button(
                            enabled = uiState.canStartPomodoro,
                            onClick = onStartPomodoro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Empezar")
                        }
                    }

                    OutlinedButton(
                        onClick = onResetPomodoro,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Reiniciar último pomodoro")
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerSummaryItem(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FinishStudyDialog(
    elapsedText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Guardar estudio")
        },
        text = {
            Text(
                text = "Se guardará el tiempo estudiado: $elapsedText. Después el contador volverá a 0."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}

@Composable
private fun DiscardStudyDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Descartar cronómetro")
        },
        text = {
            Text(
                text = "Se perderá el tiempo actual y no se sumará al día."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Descartar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}

@Composable
private fun PomodoroDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (minutes: Int) -> Unit
) {
    var timeText by remember {
        mutableStateOf(
            initialMinutes.toHourMinuteInputText()
        )
    }

    val totalMinutes = timeText.toMinutesFromHourMinuteInput()
    val canSave = totalMinutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Duración del pomodoro")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Introduce la duración en formato hh:mm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextField(
                    value = timeText,
                    onValueChange = { value ->
                        timeText = value.toHourMinuteInput()
                    },
                    label = { Text("hh:mm") },
                    placeholder = { Text("00:25") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onConfirm(totalMinutes)
                }
            ) {
                Text(text = "Guardar duración")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}

private fun StudyTimerState.toReadableText(): String {
    return when (this) {
        StudyTimerState.STOPPED -> "Preparado para empezar"
        StudyTimerState.RUNNING -> "Estudiando"
        StudyTimerState.PAUSED -> "Pausado"
    }
}

private fun PomodoroTimerState.toReadableText(): String {
    return when (this) {
        PomodoroTimerState.STOPPED -> "Sin iniciar"
        PomodoroTimerState.RUNNING -> "Pomodoro en marcha"
        PomodoroTimerState.PAUSED -> "Pomodoro pausado"
        PomodoroTimerState.FINISHED -> "Pomodoro terminado"
    }
}

private fun Int.toHourMinuteInputText(): String {
    val hours = this / 60
    val minutes = this % 60

    return "%02d:%02d".format(hours, minutes)
}

private fun String.toHourMinuteInput(): String {
    val digits = filter { it.isDigit() }.takeLast(4)
    val padded = digits.padStart(4, '0')
    val hours = padded.take(2)
    val minutes = padded.takeLast(2)

    return "$hours:$minutes"
}

private fun String.toMinutesFromHourMinuteInput(): Int {
    val parts = split(":")
    if (parts.size != 2) return 0

    val hours = parts[0].toIntOrNull() ?: 0
    val minutes = parts[1].toIntOrNull() ?: 0

    if (minutes > 59) return 0

    return (hours * 60) + minutes
}