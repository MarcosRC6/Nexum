package com.example.testario.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.example.testario.ui.theme.NeonIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.example.testario.ui.theme.NeonTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.Topic
import com.example.testario.data.model.TopicGroup
import com.example.testario.domain.GroupHierarchy
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.LibraryViewModel

@Composable
fun GroupsScreen(
    libraryViewModel: LibraryViewModel,
    onHome: () -> Unit,
    onTopics: () -> Unit,
    onCreateTest: () -> Unit,
    onPlan: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit
) {
    val database by libraryViewModel.database.collectAsState()

    var expandedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var createParentId by remember { mutableStateOf<String?>(null) }
    var createName by remember { mutableStateOf("") }

    var renameTarget by remember { mutableStateOf<TopicGroup?>(null) }
    var renameText by remember { mutableStateOf("") }

    var topicsTarget by remember { mutableStateOf<TopicGroup?>(null) }
    var selectedTopicIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var moveTarget by remember { mutableStateOf<TopicGroup?>(null) }
    var moveParentId by remember { mutableStateOf<String?>(null) }

    var deleteTarget by remember { mutableStateOf<TopicGroup?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(database.groups.map { it.id }) {
        expandedIds = expandedIds.intersect(database.groups.mapTo(hashSetOf()) { it.id })
    }

    val roots = remember(database.groups) {
        val validIds = database.groups.mapTo(hashSetOf()) { it.id }
        database.groups.filter { it.parentGroupId == null || it.parentGroupId !in validIds }
            .sortedWith(Comparator { a, b -> a.name.naturalCompareTo(b.name) })
    }
    val topicsById = remember(database.topics) { database.topics.associateBy { it.id } }

    fun startCreate(parentId: String?) {
        createParentId = parentId
        createName = ""
        message = null
        showCreateDialog = true
    }

    Scaffold(
        containerColor = TestarioBackground,
        topBar = { TestarioMainTopBar("Biblioteca", onSettings) },
        bottomBar = {
            MainBottomNavigation(
                selected = MainSection.LIBRARY,
                onHome = onHome,
                onLibrary = onTopics,
                onTest = onCreateTest,
                onPlan = onPlan,
                onStats = onStats
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LibrarySectionTabs(
                topicsSelected = false,
                onTopics = onTopics,
                onGroups = {},
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { startCreate(null) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Nuevo grupo")
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Organiza tus temas sin mover preguntas", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Un tema puede estar en varios grupos. Los subgrupos son organizativos y al seleccionar un grupo en un test se incluyen todos sus descendientes.",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (message != null) {
                Text(message.orEmpty(), color = if (message!!.startsWith("Error")) TestarioRed else TestarioPrimary)
            }

            if (roots.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = TestarioSurface)
                ) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Todavía no hay grupos", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Usa “Nuevo grupo” para crear, por ejemplo, Legislación o Específico.", color = TestarioMutedText)
                    }
                }
            } else {
                roots.forEach { group ->
                    GroupTreeNode(
                        group = group,
                        allGroups = database.groups,
                        topicsById = topicsById,
                        expandedIds = expandedIds,
                        depth = 0,
                        onToggleExpanded = { id ->
                            expandedIds = if (id in expandedIds) expandedIds - id else expandedIds + id
                        },
                        onCreateSubgroup = { startCreate(it.id) },
                        onEditTopics = { target ->
                            topicsTarget = target
                            selectedTopicIds = target.topicIds.toSet()
                            message = null
                        },
                        onRename = { target ->
                            renameTarget = target
                            renameText = target.name
                            message = null
                        },
                        onMove = { target ->
                            moveTarget = target
                            moveParentId = target.parentGroupId
                            message = null
                        },
                        onDelete = { deleteTarget = it }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(if (createParentId == null) "Crear grupo" else "Crear subgrupo") },
            text = {
                Column {
                    if (createParentId != null) {
                        Text(
                            "Dentro de ${GroupHierarchy.path(database.groups, createParentId!!)}",
                            color = TestarioMutedText,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedTextField(
                        value = createName,
                        onValueChange = { createName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        colors = testarioTextFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val id = libraryViewModel.addGroup(createName, createParentId)
                    if (id == null) {
                        message = "Error: nombre vacío, repetido o grupo padre no válido."
                    } else {
                        if (createParentId != null) expandedIds = expandedIds + createParentId!!
                        message = "Grupo creado correctamente."
                    }
                    showCreateDialog = false
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") } }
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar grupo") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    colors = testarioTextFieldColors()
                )
            },
            confirmButton = {
                Button(onClick = {
                    message = if (libraryViewModel.renameGroup(target.id, renameText)) {
                        "Grupo renombrado."
                    } else {
                        "Error: ese nombre ya existe al mismo nivel o no es válido."
                    }
                    renameTarget = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
        )
    }

    topicsTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { topicsTarget = null },
            title = { Text("Temas de ${target.name}") },
            text = {
                Column(
                    Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (database.topics.isEmpty()) {
                        Text("No hay temas disponibles.", color = TestarioMutedText)
                    }
                    database.topics.sortedWith(Comparator { a, b -> a.name.naturalCompareTo(b.name) }).forEach { topic ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selectedTopicIds = if (topic.id in selectedTopicIds) selectedTopicIds - topic.id else selectedTopicIds + topic.id
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = topic.id in selectedTopicIds,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(checkedColor = TestarioPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(topic.name, color = TestarioText)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    libraryViewModel.setGroupTopics(target.id, selectedTopicIds)
                    topicsTarget = null
                    message = "Temas del grupo actualizados."
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { topicsTarget = null }) { Text("Cancelar") } }
        )
    }

    moveTarget?.let { target ->
        val forbidden = GroupHierarchy.descendantIds(database.groups, target.id) + target.id
        val possibleParents = database.groups
            .filterNot { it.id in forbidden }
            .sortedBy { GroupHierarchy.path(database.groups, it.id).lowercase() }

        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text("Mover ${target.name}") },
            text = {
                Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                    Row(
                        Modifier.fillMaxWidth().clickable { moveParentId = null }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = moveParentId == null, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nivel principal", color = TestarioText)
                    }
                    possibleParents.forEach { parent ->
                        Row(
                            Modifier.fillMaxWidth().clickable { moveParentId = parent.id }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = moveParentId == parent.id, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(GroupHierarchy.path(database.groups, parent.id), color = TestarioText)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    message = if (libraryViewModel.moveGroup(target.id, moveParentId)) {
                        "Grupo movido correctamente."
                    } else {
                        "Error: el movimiento crearía un ciclo o un nombre duplicado al mismo nivel."
                    }
                    moveTarget = null
                }) { Text("Mover") }
            },
            dismissButton = { TextButton(onClick = { moveTarget = null }) { Text("Cancelar") } }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar grupo") },
            text = {
                Text(
                    "Se eliminará solo la organización “${target.name}”. No se borrará ningún tema ni pregunta. Sus subgrupos subirán un nivel automáticamente."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        libraryViewModel.deleteGroup(target.id)
                        deleteTarget = null
                        message = "Grupo eliminado; temas y subgrupos se han conservado."
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun GroupTreeNode(
    group: TopicGroup,
    allGroups: List<TopicGroup>,
    topicsById: Map<String, Topic>,
    expandedIds: Set<String>,
    depth: Int,
    onToggleExpanded: (String) -> Unit,
    onCreateSubgroup: (TopicGroup) -> Unit,
    onEditTopics: (TopicGroup) -> Unit,
    onRename: (TopicGroup) -> Unit,
    onMove: (TopicGroup) -> Unit,
    onDelete: (TopicGroup) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val children = remember(allGroups, group.id) {
        allGroups.filter { it.parentGroupId == group.id }
            .sortedWith(Comparator { a, b -> a.name.naturalCompareTo(b.name) })
    }
    val recursiveTopicIds = remember(allGroups, group.id) { GroupHierarchy.resolveTopicIds(allGroups, group.id) }
    val questionCount = remember(recursiveTopicIds, topicsById) {
        recursiveTopicIds.asSequence()
            .mapNotNull(topicsById::get)
            .flatMap { it.questions.asSequence() }
            .distinctBy { it.id }
            .count()
    }
    val isExpanded = group.id in expandedIds
    val hasContents = group.topicIds.isNotEmpty() || children.isNotEmpty()

    Column(Modifier.padding(start = (depth * 14).dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = TestarioSurface),
            border = if (depth == 0) BorderStroke(1.dp, TestarioSurfaceLight) else null
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { if (hasContents) onToggleExpanded(group.id) },
                    enabled = hasContents
                ) {
                    Text(if (!hasContents) "•" else if (isExpanded) "▼" else "▶", color = TestarioPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(group.name, color = TestarioText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${recursiveTopicIds.size} temas · $questionCount preguntas",
                        color = TestarioMutedText,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                androidx.compose.material3.IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = TestarioText, style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Crear subgrupo") }, onClick = { menuExpanded = false; onCreateSubgroup(group) })
                    DropdownMenuItem(text = { Text("Seleccionar temas") }, onClick = { menuExpanded = false; onEditTopics(group) })
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menuExpanded = false; onRename(group) })
                    DropdownMenuItem(text = { Text("Mover a otro grupo") }, onClick = { menuExpanded = false; onMove(group) })
                    DropdownMenuItem(text = { Text("Eliminar grupo", color = TestarioRed) }, onClick = { menuExpanded = false; onDelete(group) })
                }
            }
        }

        if (isExpanded) {
            if (group.topicIds.isNotEmpty()) {
                Column(Modifier.padding(start = 18.dp, top = 4.dp, bottom = 4.dp)) {
                    group.topicIds.mapNotNull(topicsById::get)
                        .sortedWith(Comparator { a, b -> a.name.naturalCompareTo(b.name) })
                        .forEach { topic ->
                            Text("• ${topic.name}", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                        }
                }
            }
            children.forEach { child ->
                Spacer(Modifier.height(6.dp))
                GroupTreeNode(
                    group = child,
                    allGroups = allGroups,
                    topicsById = topicsById,
                    expandedIds = expandedIds,
                    depth = depth + 1,
                    onToggleExpanded = onToggleExpanded,
                    onCreateSubgroup = onCreateSubgroup,
                    onEditTopics = onEditTopics,
                    onRename = onRename,
                    onMove = onMove,
                    onDelete = onDelete
                )
            }
        }
    }
}
