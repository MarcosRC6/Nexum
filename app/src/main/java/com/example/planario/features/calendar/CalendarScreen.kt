package com.example.planario.features.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.testario.ui.theme.NeonTonalButton as FilledTonalButton
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planario.core.model.DayStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    CalendarContent(
        uiState = uiState,
        onPreviousMonth = viewModel::goToPreviousMonth,
        onNextMonth = viewModel::goToNextMonth,
        onTodayClick = viewModel::goToCurrentMonth,
        onDateClick = viewModel::selectDate,
        onToggleRestDay = viewModel::toggleSelectedDateRestDay
    )
}

@Composable
private fun CalendarContent(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onToggleRestDay: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CalendarHeader()

                MonthCard(
                    uiState = uiState,
                    onPreviousMonth = onPreviousMonth,
                    onNextMonth = onNextMonth,
                    onTodayClick = onTodayClick,
                    onDateClick = onDateClick
                )

                SelectedDayCard(
                    uiState = uiState,
                    onToggleRestDay = {
                        val wasFreeDay = uiState.selectedDay?.isFreeDay == true

                        onToggleRestDay()

                        showMessage(
                            if (wasFreeDay) {
                                "Día libre cancelado."
                            } else {
                                "Día marcado como descanso."
                            }
                        )
                    }
                )

                LegendCard()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun CalendarHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Calendario",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Registro visual del estudio, sin planificación rígida.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthCard(
    uiState: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onDateClick: (LocalDate) -> Unit
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
            MonthNavigationRow(
                monthTitle = uiState.monthTitle,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onTodayClick = onTodayClick
            )

            WeekDaysRow(weekDays = uiState.weekDays)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.weeks.forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        week.forEach { day ->
                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                if (day == null) {
                                    EmptyDayCell()
                                } else {
                                    CalendarDayCell(
                                        day = day,
                                        onClick = {
                                            onDateClick(day.date)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavigationRow(
    monthTitle: String,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onPreviousMonth) {
                Text(text = "‹")
            }

            Text(
                text = monthTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            FilledTonalButton(onClick = onNextMonth) {
                Text(text = "›")
            }
        }

        OutlinedButton(
            onClick = onTodayClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Ir a hoy")
        }
    }
}

@Composable
private fun WeekDaysRow(
    weekDays: List<String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        weekDays.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyDayCell() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    )
}

@Composable
private fun CalendarDayCell(
    day: CalendarDayUi,
    onClick: () -> Unit
) {
    val backgroundColor = day.status.toCalendarBackgroundColor()
    val contentColor = day.status.toCalendarContentColor()

    val borderColor = when {
        day.isSelected -> MaterialTheme.colorScheme.primary
        day.status == DayStatus.REST -> day.status.toCalendarBorderColor()
        else -> Color.Transparent
    }

    val borderWidth = when {
        day.isSelected -> 2.dp
        day.status == DayStatus.REST -> 1.dp
        else -> 0.dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .border(
                width = borderWidth,
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = day.dayNumber.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )

            if (day.isToday) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "Hoy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    uiState: CalendarUiState,
    onToggleRestDay: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedDay = uiState.selectedDay

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandableCardHeader(
                title = uiState.selectedDateText,
                subtitle = selectedDay?.let { day ->
                    "${day.status.toReadableText()} · ${day.studiedMinutes.toHourMinuteText()} estudiados"
                } ?: "Sin información para este día",
                expanded = expanded,
                onClick = {
                    expanded = !expanded
                }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (selectedDay == null) {
                        Text(
                            text = "No hay información para este día.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        SelectedDayStatusCard(selectedDay = selectedDay)

                        SelectedDayInfoRow(
                            label = "Horas estudiadas",
                            value = selectedDay.studiedMinutes.toHourMinuteText()
                        )

                        SelectedDayInfoRow(
                            label = "Objetivo diario",
                            value = selectedDay.dailyGoalMinutes.toHourMinuteText()
                        )

                        SelectedDayInfoRow(
                            label = "Tema activo",
                            value = selectedDay.activeTopicNameSnapshot ?: "Sin tema activo"
                        )

                        SelectedDayObjectivesBlock(
                            completedFixedObjectiveCount = selectedDay.completedFixedObjectiveIds.size
                        )

                        if (selectedDay.isFreeDay) {
                            OutlinedButton(
                                onClick = onToggleRestDay,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFB91C1C)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancelar día libre")
                            }
                        } else {
                            Button(
                                onClick = onToggleRestDay,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Marcar como descanso")
                            }
                        }

                        ExpandableExplanation(
                            title = "💡 ¿Qué es?",
                            text = "Este bloque resume el día seleccionado. Los días libres y descansos no exigen objetivo, no acumulan nada y no rompen el plan."
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayStatusCard(
    selectedDay: SelectedCalendarDayUi
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = selectedDay.status.toCalendarBackgroundColor(),
        border = BorderStroke(
            width = 1.dp,
            color = selectedDay.status.toCalendarBorderColor()
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Estado",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF172554)
                )

                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.75f),
                    border = BorderStroke(
                        width = 1.dp,
                        color = selectedDay.status.toCalendarBorderColor()
                    )
                ) {
                    Text(
                        text = selectedDay.status.toReadableText(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = selectedDay.status.toCalendarContentColor()
                    )
                }
            }

            Text(
                text = selectedDay.freeDayDescription,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF475569)
            )
        }
    }
}

@Composable
private fun SelectedDayObjectivesBlock(
    completedFixedObjectiveCount: Int
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Objetivos diarios completados",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (completedFixedObjectiveCount > 0) {
                Color(0xFFDCFCE7)
            } else {
                Color(0xFFE5E7EB)
            },
            border = BorderStroke(
                width = 1.dp,
                color = if (completedFixedObjectiveCount > 0) {
                    Color(0xFF86EFAC)
                } else {
                    Color(0xFFCBD5E1)
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (completedFixedObjectiveCount == 0) {
                    "Ningún objetivo fijo marcado este día."
                } else {
                    "$completedFixedObjectiveCount objetivo(s) fijo(s) marcado(s) este día."
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = if (completedFixedObjectiveCount > 0) {
                    Color(0xFF166534)
                } else {
                    Color(0xFF475569)
                }
            )
        }
    }
}

@Composable
private fun SelectedDayInfoRow(
    label: String,
    value: String
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Color(0xFFE5E7EB),
        border = BorderStroke(
            width = 1.dp,
            color = Color(0xFFCBD5E1)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF475569)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = Color(0xFF172554)
            )
        }
    }
}

@Composable
private fun LegendCard() {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExpandableCardHeader(
                title = "Leyenda",
                subtitle = "Colores del calendario",
                expanded = expanded,
                onClick = {
                    expanded = !expanded
                }
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegendItem(
                        status = DayStatus.COMPLETED,
                        text = "Completado"
                    )

                    LegendItem(
                        status = DayStatus.IN_PROGRESS,
                        text = "En curso"
                    )

                    LegendItem(
                        status = DayStatus.REST,
                        text = "Descanso / día libre"
                    )

                    LegendItem(
                        status = DayStatus.NOT_COMPLETED,
                        text = "No completado"
                    )

                    LegendItem(
                        status = DayStatus.EMPTY,
                        text = "Sin comenzar / vacío"
                    )

                    ExpandableExplanation(
                        title = "💡 ¿Qué es?",
                        text = "La leyenda explica los colores del calendario. Solo sirve como ayuda visual: no crea deuda ni cambia el plan."
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(
    status: DayStatus,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .height(18.dp)
                .width(28.dp)
                .clip(MaterialTheme.shapes.small)
                .background(status.toCalendarBackgroundColor())
                .border(
                    width = 1.dp,
                    color = status.toCalendarBorderColor(),
                    shape = MaterialTheme.shapes.small
                )
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ExpandableCardHeader(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            // Extra inset avoids glyph clipping on narrow displays and large fonts.
            .padding(start = 9.dp, end = 3.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = if (expanded) "⌃" else "⌄",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ExpandableExplanation(
    title: String,
    text: String
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (expanded) "$title  ▲" else "$title  ▼",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    expanded = !expanded
                }
                .padding(vertical = 4.dp)
        )

        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayStatus.toCalendarBackgroundColor(): Color {
    return when (this) {
        DayStatus.COMPLETED -> Color(0xFFDCFCE7)
        DayStatus.IN_PROGRESS -> Color(0xFFDBEAFE)
        DayStatus.REST -> Color(0xFFFFFFFF)
        DayStatus.NOT_COMPLETED -> Color(0xFFFEE2E2)
        DayStatus.EMPTY -> Color(0xFFE5E7EB)
    }
}

@Composable
private fun DayStatus.toCalendarContentColor(): Color {
    return when (this) {
        DayStatus.COMPLETED -> Color(0xFF166534)
        DayStatus.IN_PROGRESS -> Color(0xFF1D4ED8)
        DayStatus.REST -> Color(0xFF475569)
        DayStatus.NOT_COMPLETED -> Color(0xFFB91C1C)
        DayStatus.EMPTY -> Color(0xFF64748B)
    }
}

@Composable
private fun DayStatus.toCalendarBorderColor(): Color {
    return when (this) {
        DayStatus.COMPLETED -> Color(0xFF86EFAC)
        DayStatus.IN_PROGRESS -> Color(0xFF93C5FD)
        DayStatus.REST -> Color(0xFFCBD5E1)
        DayStatus.NOT_COMPLETED -> Color(0xFFFCA5A5)
        DayStatus.EMPTY -> Color(0xFFCBD5E1)
    }
}