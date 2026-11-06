package com.example.planario.features.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.planario.core.model.FixedObjective
import com.example.testario.ui.theme.FourItemDropdown
import com.example.planario.core.model.StudyTopic
import kotlinx.coroutines.launch

@Composable
fun PlanScreen(
    viewModel: PlanViewModel = viewModel(factory = PlanViewModel.factory())
) {
    val uiState by viewModel.uiState.collectAsState()

    PlanContent(
        uiState = uiState,
        onAddTopic = viewModel::addTopic,
        onUpdateTopic = viewModel::updateTopicName,
        onActivateTopic = viewModel::activateTopic,
        onMoveTopicUp = viewModel::moveTopicUp,
        onMoveTopicDown = viewModel::moveTopicDown,
        onArchiveTopic = viewModel::archiveTopic,
        onCompleteActiveTopic = viewModel::completeActiveTopic,
        onAddFixedObjective = viewModel::addFixedObjective,
        onUpdateFixedObjective = viewModel::updateFixedObjective,
        onSetFixedObjectiveEnabled = viewModel::setFixedObjectiveEnabled,
        onDeleteFixedObjective = viewModel::deleteFixedObjective
    )
}

@Composable
private fun PlanContent(
    uiState: PlanUiState,
    onAddTopic: (String) -> Unit,
    onUpdateTopic: (topicId: String, newName: String) -> Unit,
    onActivateTopic: (String) -> Unit,
    onMoveTopicUp: (String) -> Unit,
    onMoveTopicDown: (String) -> Unit,
    onArchiveTopic: (String) -> Unit,
    onCompleteActiveTopic: () -> Unit,
    onAddFixedObjective: (name: String, targetMinutes: Int) -> Unit,
    onUpdateFixedObjective: (objectiveId: String, name: String, targetMinutes: Int) -> Unit,
    onSetFixedObjectiveEnabled: (objectiveId: String, enabled: Boolean) -> Unit,
    onDeleteFixedObjective: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.showSnackbar(message)
        }
    }

    var showAddTopicDialog by remember { mutableStateOf(false) }
    var topicToEdit by remember { mutableStateOf<StudyTopic?>(null) }
    var topicToArchive by remember { mutableStateOf<StudyTopic?>(null) }
    var showCompleteTopicDialog by remember { mutableStateOf(false) }

    var showAddObjectiveDialog by remember { mutableStateOf(false) }
    var objectiveToEdit by remember { mutableStateOf<FixedObjective?>(null) }
    var objectiveToDelete by remember { mutableStateOf<FixedObjective?>(null) }

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
                PlanHeader()

                ActiveTopicSection(
                    activeTopic = uiState.activeTopic,
                    onAddTopicClick = { showAddTopicDialog = true },
                    onCompleteActiveTopicClick = {
                        if (uiState.activeTopic != null) {
                            showCompleteTopicDialog = true
                        }
                    }
                )

                TopicQueueSection(
                    topics = uiState.topics,
                    onAddTopicClick = { showAddTopicDialog = true },
                    onEditTopicClick = { topicToEdit = it },
                    onActivateTopicClick = { topic ->
                        onActivateTopic(topic.id)
                        showMessage("${topic.name} es ahora el tema activo.")
                    },
                    onMoveTopicUpClick = { topic ->
                        onMoveTopicUp(topic.id)
                    },
                    onMoveTopicDownClick = { topic ->
                        onMoveTopicDown(topic.id)
                    },
                    onArchiveTopicClick = { topicToArchive = it }
                )

                FixedObjectivesSection(
                    objectives = uiState.fixedObjectives,
                    onAddObjectiveClick = { showAddObjectiveDialog = true },
                    onEditObjectiveClick = { objectiveToEdit = it },
                    onDeleteObjectiveClick = { objectiveToDelete = it },
                    onSetObjectiveEnabled = { objectiveId, enabled ->
                        onSetFixedObjectiveEnabled(objectiveId, enabled)

                        showMessage(
                            if (enabled) {
                                "Objetivo fijo activado."
                            } else {
                                "Objetivo fijo desactivado."
                            }
                        )
                    }
                )

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

    if (showAddTopicDialog) {
        AddTopicDialog(
            onDismiss = { showAddTopicDialog = false },
            onConfirm = { name ->
                onAddTopic(name)
                showAddTopicDialog = false
                showMessage("Tema añadido.")
            }
        )
    }

    topicToEdit?.let { topic ->
        EditTopicDialog(
            topic = topic,
            onDismiss = { topicToEdit = null },
            onConfirm = { newName ->
                onUpdateTopic(topic.id, newName)
                topicToEdit = null
                showMessage("Tema actualizado.")
            }
        )
    }

    topicToArchive?.let { topic ->
        ArchiveTopicDialog(
            topicName = topic.name,
            onDismiss = { topicToArchive = null },
            onConfirm = {
                onArchiveTopic(topic.id)
                topicToArchive = null
                showMessage("Tema quitado de la cola.")
            }
        )
    }

    if (showCompleteTopicDialog) {
        CompleteActiveTopicDialog(
            topicName = uiState.activeTopic?.name.orEmpty(),
            onDismiss = { showCompleteTopicDialog = false },
            onConfirm = {
                onCompleteActiveTopic()
                showCompleteTopicDialog = false
                showMessage("Tema enviado al final de la cola.")
            }
        )
    }

    if (showAddObjectiveDialog) {
        AddOrEditFixedObjectiveDialog(
            title = "Crear objetivo fijo",
            initialName = "",
            initialMinutes = 60,
            onDismiss = { showAddObjectiveDialog = false },
            onConfirm = { name, minutes ->
                onAddFixedObjective(name, minutes)
                showAddObjectiveDialog = false
                showMessage("Objetivo fijo creado.")
            }
        )
    }

    objectiveToEdit?.let { objective ->
        AddOrEditFixedObjectiveDialog(
            title = "Editar objetivo fijo",
            initialName = objective.name,
            initialMinutes = objective.targetMinutes,
            onDismiss = { objectiveToEdit = null },
            onConfirm = { name, minutes ->
                onUpdateFixedObjective(
                    objective.id,
                    name,
                    minutes
                )
                objectiveToEdit = null
                showMessage("Objetivo fijo actualizado.")
            }
        )
    }

    objectiveToDelete?.let { objective ->
        DeleteFixedObjectiveDialog(
            objectiveName = objective.name,
            onDismiss = { objectiveToDelete = null },
            onConfirm = {
                onDeleteFixedObjective(objective.id)
                objectiveToDelete = null
                showMessage("Objetivo fijo eliminado.")
            }
        )
    }
}

@Composable
private fun PlanHeader() {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Plan",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "Organiza tu tema activo, la cola de vueltas y tus objetivos diarios.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActiveTopicSection(
    activeTopic: StudyTopic?,
    onAddTopicClick: () -> Unit,
    onCompleteActiveTopicClick: () -> Unit
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var topicExpanded by remember { mutableStateOf(true) }

    CompactCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tema activo",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { topicExpanded = !topicExpanded }) {
                    Text(if (topicExpanded) "Ocultar ▲" else "Ver ▼")
                }
            }

            if (!topicExpanded) {
                // Mantener visible la cabecera al plegar el tema activo.
            } else if (activeTopic == null) {
                Text(
                    text = "Todavía no hay ningún tema en la cola.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onAddTopicClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Añadir primer tema")
                }
            } else {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = activeTopic.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Text(
                            text = "Vuelta ${activeTopic.round}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = onCompleteActiveTopicClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Marcar como terminado")
                }
            }

            ExpandableHelp(
                title = "💡 ¿Qué es?",
                expanded = helpExpanded,
                onClick = { helpExpanded = !helpExpanded }
            ) {
                Text(
                    text = "El tema activo es lo principal que toca estudiar. Se mantiene día tras día hasta que lo marques como terminado. Al terminarlo, pasa al final de la cola y sube de vuelta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TopicQueueSection(
    topics: List<StudyTopic>,
    onAddTopicClick: () -> Unit,
    onEditTopicClick: (StudyTopic) -> Unit,
    onActivateTopicClick: (StudyTopic) -> Unit,
    onMoveTopicUpClick: (StudyTopic) -> Unit,
    onMoveTopicDownClick: (StudyTopic) -> Unit,
    onArchiveTopicClick: (StudyTopic) -> Unit
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(true) }

    CompactCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Temas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (topics.isEmpty()) {
                            "Sin temas añadidos"
                        } else {
                            "${topics.size} tema(s) en cola"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { listExpanded = !listExpanded }, enabled = topics.isNotEmpty()) {
                    Text(if (listExpanded) "Ocultar ▲" else "Ver ▼")
                }
                TextButton(onClick = onAddTopicClick) {
                    Text(text = "Añadir")
                }
            }

            if (topics.isEmpty()) {
                Text(
                    text = "Añade temas poco a poco. No hace falta cargar todo el temario desde el principio.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (listExpanded) {
                FourItemDropdown(items = topics) { index, topic ->
                    TopicQueueRow(
                        position = index + 1,
                        topic = topic,
                        isActive = index == 0,
                        canMoveUp = index > 1,
                        canMoveDown = index > 0 && index < topics.lastIndex,
                        onEditClick = { onEditTopicClick(topic) },
                        onActivateClick = { onActivateTopicClick(topic) },
                        onMoveUpClick = { onMoveTopicUpClick(topic) },
                        onMoveDownClick = { onMoveTopicDownClick(topic) },
                        onArchiveClick = { onArchiveTopicClick(topic) }
                    )
                }
            }

            ExpandableHelp(
                title = "💡 ¿Qué es?",
                expanded = helpExpanded,
                onClick = { helpExpanded = !helpExpanded }
            ) {
                Text(
                    text = "La cola marca el orden de estudio. Solo el primer tema es el tema activo. Cuando lo terminas, se mueve al final y sube de vuelta. Si activas manualmente otro tema, el que estaba activo pasa al final para no alternar siempre entre los mismos dos. Los temas nuevos se colocan al final de lo pendiente, antes de volver a temas ya terminados. Usa las flechas ↑ y ↓ para reordenar manualmente los temas pendientes; el tema activo permanece fijo y se cambia con el botón Activar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TopicQueueRow(
    position: Int,
    topic: StudyTopic,
    isActive: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEditClick: () -> Unit,
    onActivateClick: () -> Unit,
    onMoveUpClick: () -> Unit,
    onMoveDownClick: () -> Unit,
    onArchiveClick: () -> Unit
) {
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        border = BorderStroke(
            width = if (isActive) 1.dp else 0.dp,
            color = borderColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isActive) {
                        Color.White.copy(alpha = 0.75f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = position.toString(),
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = topic.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (isActive) {
                            "Tema activo · Vuelta ${topic.round}"
                        } else {
                            "Vuelta ${topic.round}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isActive) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onMoveUpClick,
                            enabled = canMoveUp
                        ) {
                            Text(
                                text = "↑",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(
                            onClick = onMoveDownClick,
                            enabled = canMoveDown
                        ) {
                            Text(
                                text = "↓",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Editar")
                }

                FilledTonalButton(
                    onClick = onActivateClick,
                    enabled = !isActive,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = if (isActive) {
                            "Activo"
                        } else {
                            "Activar"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onArchiveClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Quitar")
                }
            }
        }
    }
}

@Composable
private fun FixedObjectivesSection(
    objectives: List<FixedObjective>,
    onAddObjectiveClick: () -> Unit,
    onEditObjectiveClick: (FixedObjective) -> Unit,
    onDeleteObjectiveClick: (FixedObjective) -> Unit,
    onSetObjectiveEnabled: (objectiveId: String, enabled: Boolean) -> Unit
) {
    var helpExpanded by remember { mutableStateOf(false) }
    var listExpanded by remember { mutableStateOf(true) }

    CompactCard {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Objetivos diarios",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (objectives.isEmpty()) {
                            "Sin objetivos diarios"
                        } else {
                            "${objectives.count { it.isEnabled }}/${objectives.size} activos"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(onClick = { listExpanded = !listExpanded }, enabled = objectives.isNotEmpty()) {
                    Text(if (listExpanded) "Ocultar ▲" else "Ver ▼")
                }
                TextButton(onClick = onAddObjectiveClick) {
                    Text(text = "Añadir")
                }
            }

            if (objectives.isEmpty()) {
                Text(
                    text = "Crea cualquier objetivo fijo: callejero, test, legislación, academia o lo que necesites.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (listExpanded) {
                FourItemDropdown(items = objectives) { _, objective ->
                    FixedObjectiveRow(
                        objective = objective,
                        onEditClick = { onEditObjectiveClick(objective) },
                        onDeleteClick = { onDeleteObjectiveClick(objective) },
                        onEnabledChange = { enabled ->
                            onSetObjectiveEnabled(objective.id, enabled)
                        }
                    )
                }
            }

            ExpandableHelp(
                title = "💡 ¿Qué es?",
                expanded = helpExpanded,
                onClick = { helpExpanded = !helpExpanded }
            ) {
                Text(
                    text = "Los objetivos diarios aparecen cada día, pero no generan deuda. Si un día no marcas uno, mañana vuelve a aparecer normal, sin acumularse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FixedObjectiveRow(
    objective: FixedObjective,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    val backgroundColor = if (objective.isEnabled) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val statusText = if (objective.isEnabled) {
        "Activo"
    } else {
        "Desactivado"
    }

    val statusColor = if (objective.isEnabled) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = backgroundColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = objective.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (objective.isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )

                    Text(
                        text = "${objective.targetMinutes.toHourMinuteText()} · ${if (objective.isEnabled) "Activo" else "Inactivo"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (objective.isEnabled) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Switch(
                    checked = objective.isEnabled,
                    onCheckedChange = onEnabledChange
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onEditClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "Editar",
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    )
                ) {
                    Text(
                        text = "Eliminar",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun ExpandableHelp(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
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
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp)
        )

        AnimatedVisibility(visible = expanded) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun AddTopicDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val canSave = name.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Añadir tema")
        },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del tema") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onConfirm(name.trim())
                }
            ) {
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
private fun EditTopicDialog(
    topic: StudyTopic,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(topic.id) { mutableStateOf(topic.name) }
    val canSave = name.trim().isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Editar tema")
        },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del tema") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = {
                    onConfirm(name.trim())
                }
            ) {
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
private fun ArchiveTopicDialog(
    topicName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Quitar tema")
        },
        text = {
            Text(
                text = "El tema \"$topicName\" dejará de aparecer en la cola."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Quitar")
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
private fun CompleteActiveTopicDialog(
    topicName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Terminar tema")
        },
        text = {
            Text(
                text = "\"$topicName\" pasará al final de la cola para la siguiente vuelta."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Terminar")
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
private fun AddOrEditFixedObjectiveDialog(
    title: String,
    initialName: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (name: String, targetMinutes: Int) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var timeText by remember {
        mutableStateOf(initialMinutes.toHourMinuteInputText())
    }

    val totalMinutes = timeText.toMinutesFromHourMinuteInput()
    val canSave = name.trim().isNotBlank() && totalMinutes > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Introduce el tiempo en formato HH:MM.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = timeText,
                    onValueChange = { value ->
                        timeText = value.toHourMinuteInput()
                    },
                    label = { Text("Tiempo") },
                    placeholder = { Text("01:00") },
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
                    onConfirm(
                        name.trim(),
                        totalMinutes
                    )
                }
            ) {
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
private fun DeleteFixedObjectiveDialog(
    objectiveName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Eliminar objetivo fijo")
        },
        text = {
            Text(
                text = "El objetivo fijo \"$objectiveName\" dejará de aparecer cada día."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "Eliminar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancelar")
            }
        }
    )
}

private fun Int.toHourMinuteText(): String {
    val hours = this / 60
    val minutes = this % 60

    return when {
        hours > 0 && minutes > 0 -> "${hours} h ${minutes} min"
        hours > 0 -> "${hours} h"
        else -> "${minutes} min"
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