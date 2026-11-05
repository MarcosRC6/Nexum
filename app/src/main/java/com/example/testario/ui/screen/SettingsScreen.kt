package com.example.testario.ui.screen

import android.Manifest
import android.app.DatePickerDialog
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.planario.app.PlanarioDependencies
import com.example.testario.data.model.DuplicateTopicAction
import com.example.testario.data.model.ImportPreview
import com.example.testario.data.model.Topic
import com.example.testario.domain.UnifiedDataManager
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioGreen
import com.example.testario.ui.theme.TestarioGreenDark
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioPrimaryDark
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioRedDark
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import com.example.testario.viewmodel.BehaviorSettingsViewModel
import com.example.testario.viewmodel.HomeSettingsViewModel
import com.example.testario.viewmodel.ImportExportViewModel
import com.example.testario.viewmodel.LibraryViewModel
import com.example.testario.viewmodel.StatsViewModel
import com.example.testario.viewmodel.ThemeViewModel
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class ExportMode { COMPLETE, SIMPLE }
private enum class ExportScope { ALL, SINGLE, MULTIPLE }
private enum class ExportTarget { LIBRARY, PLAN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeViewModel: ThemeViewModel,
    behaviorSettingsViewModel: BehaviorSettingsViewModel,
    homeSettingsViewModel: HomeSettingsViewModel,
    importExportViewModel: ImportExportViewModel,
    statsViewModel: StatsViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    openHomeSettingsInitially: Boolean = false
) {
    val context = LocalContext.current
    val database by libraryViewModel.database.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    var exportMode by remember { mutableStateOf(ExportMode.COMPLETE) }
    var exportScope by remember { mutableStateOf(ExportScope.ALL) }
    var selectedSingleTopicId by remember { mutableStateOf<String?>(null) }
    var selectedMultipleTopicIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var singleDropdownExpanded by remember { mutableStateOf(false) }
    var multiDropdownExpanded by remember { mutableStateOf(false) }

    var pendingExportTopicIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingExportText by remember { mutableStateOf<String?>(null) }

    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportFileName by remember { mutableStateOf<String?>(null) }
    var pendingImportPreview by remember { mutableStateOf(null as ImportPreview?) }
    var pendingRestoreText by remember { mutableStateOf<String?>(null) }

    var importedTopicsQueue by remember { mutableStateOf<List<Topic>>(emptyList()) }
    var currentImportTopic by remember { mutableStateOf<Topic?>(null) }

    // Estados para los desplegables de ayuda y secciones configurables
    var showHomeSettings by remember(openHomeSettingsInitially) { mutableStateOf(openHomeSettingsInitially) }
    var showBehaviorSettings by remember { mutableStateOf(false) }
    var showBehaviorInstructions by remember { mutableStateOf(false) }

    var showPreviewDialog by remember { mutableStateOf(false) }
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showClearPlanningDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var customTopicName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val unifiedDataManager = remember { UnifiedDataManager(context.applicationContext) }
    val studySettings by PlanarioDependencies.studyRepository.settings.collectAsState()
    var showStudySettings by remember { mutableStateOf(false) }
    var showDataOptions by remember { mutableStateOf(false) }
    var showExportOptions by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<ExportTarget?>(null) }
    var pendingUnifiedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingTestarioBackupBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingPlanJson by remember { mutableStateOf<String?>(null) }
    var showUnifiedRestoreConfirm by remember { mutableStateOf(false) }
    var showTestarioBackupRestoreConfirm by remember { mutableStateOf(false) }
    var showPlanImportConfirm by remember { mutableStateOf(false) }
    var pendingDayOverrideDate by remember { mutableStateOf<LocalDate?>(null) }
    val planningDateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }

    LaunchedEffect(database.topics) {
        if (selectedSingleTopicId == null && database.topics.isNotEmpty()) {
            selectedSingleTopicId = database.topics.first().id
        }
    }

    val sortedTopicsForExport = remember(database.topics) {
        database.topics.sortedWith(Comparator { t1, t2 -> t1.name.naturalCompareTo(t2.name) })
    }

    val startImportWorkflow: (List<Topic>) -> Unit = { topics ->
        coroutineScope.launch {
            var currentQueue = topics
            val dbTopics = libraryViewModel.database.value.topics.toMutableList()
            var conflictFound = false

            while (currentQueue.isNotEmpty() && !conflictFound) {
                val next = currentQueue.first()
                val conflict = dbTopics.any { it.name.equals(next.name, true) }
                if (conflict) {
                    currentImportTopic = next
                    importedTopicsQueue = currentQueue.drop(1)
                    showDuplicateDialog = true
                    conflictFound = true
                } else {
                    importExportViewModel.importSingleTopicProcessed(next, DuplicateTopicAction.RENAME_AUTO)
                    dbTopics.add(next)
                    currentQueue = currentQueue.drop(1)
                }
            }
            if (currentQueue.isEmpty() && !conflictFound) {
                message = "Temas importados correctamente."
                pendingImportText = null
                pendingImportFileName = null
                pendingImportPreview = null
                currentImportTopic = null
                importedTopicsQueue = emptyList()
            }
        }
    }

    val importTopicsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                val fileName = uri.lastPathSegment
                val rawText = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importExportViewModel.extractPayload(stream, fileName)
                } ?: ""

                val preview = importExportViewModel.previewImport(rawText, fileName)

                if (!preview.canImport) {
                    message = "Error al leer el archivo. Revisa el formato."
                } else {
                    pendingImportText = rawText
                    pendingImportFileName = fileName
                    pendingImportPreview = preview
                    showPreviewDialog = true
                    message = null
                }
            } catch (_: Exception) {
                message = "Error al importar temas."
            } finally {
                isLoading = false
            }
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExportText ?: "") }
                message = "Temas exportados correctamente."
                pendingExportText = null
            } catch (_: Exception) {
                message = "Error al exportar temas."
            } finally {
                isLoading = false
            }
        }
    }

    val exportZipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    importExportViewModel.exportCompleteZip(stream, pendingExportTopicIds)
                }
                message = "Temas y recursos exportados correctamente."
            } catch (_: Exception) {
                message = "Error al exportar temas."
            } finally {
                isLoading = false
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    importExportViewModel.exportBackupZip(stream)
                }
                message = "Copia de seguridad guardada correctamente."
            } catch (_: Exception) {
                message = "Error al exportar backup."
            } finally {
                isLoading = false
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                val fileName = uri.lastPathSegment
                val text = context.contentResolver.openInputStream(uri)?.use { stream ->
                    importExportViewModel.extractPayload(stream, fileName)
                } ?: ""

                pendingRestoreText = text
                showRestoreConfirmDialog = true
            } catch (_: Exception) {
                message = "Error al leer el archivo de backup."
            } finally {
                isLoading = false
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Permiso de notificaciones concedido." else "Permiso de notificaciones no concedido."
    }

    val unifiedBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    unifiedDataManager.exportGlobalBackup(stream)
                }
                message = "Copia completa guardada correctamente."
            } catch (_: Exception) {
                message = "Error al crear la copia completa."
            } finally {
                isLoading = false
            }
        }
    }

    val exportPlanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                val raw = PlanarioDependencies.studyRepository.exportBackupJson()
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(raw) }
                message = "Plan exportado correctamente."
            } catch (_: Exception) {
                message = "Error al exportar el plan."
            } finally {
                isLoading = false
            }
        }
    }

    val unifiedImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoading = true
        coroutineScope.launch {
            try {
                val fileName = uri.lastPathSegment
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                val detection = unifiedDataManager.detect(bytes, fileName)
                when (detection.kind) {
                        UnifiedDataManager.ImportKind.GLOBAL_BACKUP -> {
                            pendingUnifiedBytes = bytes
                            showUnifiedRestoreConfirm = true
                        }
                        UnifiedDataManager.ImportKind.TESTARIO_BACKUP -> {
                            pendingTestarioBackupBytes = bytes
                            showTestarioBackupRestoreConfirm = true
                        }
                        UnifiedDataManager.ImportKind.PLAN_BACKUP -> {
                            pendingPlanJson = detection.text ?: bytes.toString(Charsets.UTF_8)
                            showPlanImportConfirm = true
                        }
                        UnifiedDataManager.ImportKind.LIBRARY_DATA -> {
                            val rawText = if (fileName?.endsWith(".zip", true) == true) {
                                importExportViewModel.extractPayload(ByteArrayInputStream(bytes), fileName)
                            } else {
                                detection.text ?: bytes.toString(Charsets.UTF_8)
                            }
                            val preview = importExportViewModel.previewImport(rawText, fileName)
                            if (!preview.canImport) {
                                message = "Error al leer el archivo de Biblioteca."
                            } else {
                                pendingImportText = rawText
                                pendingImportFileName = fileName
                                pendingImportPreview = preview
                                showPreviewDialog = true
                                message = null
                            }
                        }
                        UnifiedDataManager.ImportKind.UNKNOWN -> {
                            message = "Error: formato no reconocido."
                        }
                }
            } catch (_: Exception) {
                message = "Error al importar el archivo."
            } finally {
                isLoading = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(containerColor = TestarioBackground, topBar = {
            TestarioTopBar("Ajustes", onBack)
        }) { padding ->
            Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = TestarioSurface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Apariencia", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeOption("Claro", themeViewModel.themeMode == ThemeViewModel.ThemeMode.LIGHT, Modifier.weight(1f)) { themeViewModel.changeThemeMode(ThemeViewModel.ThemeMode.LIGHT) }
                            ThemeOption("Oscuro", themeViewModel.themeMode == ThemeViewModel.ThemeMode.DARK, Modifier.weight(1f)) { themeViewModel.changeThemeMode(ThemeViewModel.ThemeMode.DARK) }
                            ThemeOption("Sistema", themeViewModel.themeMode == ThemeViewModel.ThemeMode.SYSTEM, Modifier.weight(1f)) { themeViewModel.changeThemeMode(ThemeViewModel.ThemeMode.SYSTEM) }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("Tamaño de la interfaz", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))

                        val percentage = (themeViewModel.uiScale * 100).roundToInt()
                        Text("Escala actual: $percentage%", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("A-", color = TestarioText, style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = themeViewModel.uiScale,
                                onValueChange = { themeViewModel.changeUiScale(it) },
                                valueRange = 0.65f..1.35f,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = TestarioPrimary,
                                    activeTrackColor = TestarioPrimary,
                                    inactiveTrackColor = TestarioSurfaceLight
                                )
                            )
                            Text("A+", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                        }

                        if (themeViewModel.uiScale != 1.0f) {
                            TextButton(
                                onClick = { themeViewModel.changeUiScale(1.0f) },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Restaurar (100%)", color = TestarioPrimary)
                            }
                        }
                    }
                }

                Card(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(24.dp),
                    CardDefaults.cardColors(containerColor = TestarioSurface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showHomeSettings = !showHomeSettings }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Pantalla de inicio", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                Text("Elige los bloques y accesos que aparecen en Inicio.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (showHomeSettings) "▲" else "▼", color = TestarioPrimary, style = MaterialTheme.typography.titleMedium)
                        }

                        AnimatedVisibility(visible = showHomeSettings) {
                            CompactScrollableColumn(height = 310.dp) {
                                Spacer(Modifier.height(6.dp))
                                HomeSettingToggle(
                                    title = "Hoy",
                                    subtitle = "Progreso diario, reloj y acciones principales del día.",
                                    checked = homeSettingsViewModel.showStudyToday,
                                    onCheckedChange = homeSettingsViewModel::changeShowStudyToday
                                )
                                HomeSettingToggle(
                                    title = "Objetivos en Hoy",
                                    subtitle = "Muestra los objetivos diarios debajo de Iniciar estudio / Parar reloj.",
                                    checked = homeSettingsViewModel.showStudyObjectives,
                                    onCheckedChange = homeSettingsViewModel::changeShowStudyObjectives
                                )
                                HomeSettingToggle(
                                    title = "Tema activo",
                                    subtitle = "Tema actual del plan y acceso al test rápido vinculado.",
                                    checked = homeSettingsViewModel.showStudyTopic,
                                    onCheckedChange = homeSettingsViewModel::changeShowStudyTopic
                                )
                                HomeSettingToggle(
                                    title = "Reloj",
                                    subtitle = "Resumen del cronómetro y acceso directo al reloj.",
                                    checked = homeSettingsViewModel.showStudyTimer,
                                    onCheckedChange = homeSettingsViewModel::changeShowStudyTimer
                                )
                                HomeSettingToggle(
                                    title = "Crear test",
                                    subtitle = "Acceso directo a la configuración de tests.",
                                    checked = homeSettingsViewModel.showCreateTest,
                                    onCheckedChange = homeSettingsViewModel::changeShowCreateTest
                                )
                                HomeSettingToggle(
                                    title = "Resumen",
                                    subtitle = "Temas, preguntas, Fallos y porcentaje de acierto.",
                                    checked = homeSettingsViewModel.showSummary,
                                    onCheckedChange = homeSettingsViewModel::changeShowSummary
                                )
                                HomeSettingToggle(
                                    title = "Fallos",
                                    subtitle = "Acceso al repaso de preguntas pendientes.",
                                    checked = homeSettingsViewModel.showFailures,
                                    onCheckedChange = homeSettingsViewModel::changeShowFailures
                                )
                                HomeSettingToggle(
                                    title = "Plantillas",
                                    subtitle = "Tus plantillas de test guardadas.",
                                    checked = homeSettingsViewModel.showTemplates,
                                    onCheckedChange = homeSettingsViewModel::changeShowTemplates
                                )

                                Spacer(Modifier.height(8.dp))
                                Text("Accesos rápidos", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                Text("Configura los cuatro accesos del bloque de Inicio.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                                HomeSettingToggle(
                                    title = "Biblioteca",
                                    checked = homeSettingsViewModel.showLibraryShortcut,
                                    onCheckedChange = homeSettingsViewModel::changeShowLibraryShortcut
                                )
                                HomeSettingToggle(
                                    title = "Test",
                                    checked = homeSettingsViewModel.showTestShortcut,
                                    onCheckedChange = homeSettingsViewModel::changeShowTestShortcut
                                )
                                HomeSettingToggle(
                                    title = "Plan",
                                    checked = homeSettingsViewModel.showPlanShortcut,
                                    onCheckedChange = homeSettingsViewModel::changeShowPlanShortcut
                                )
                                HomeSettingToggle(
                                    title = "Estadísticas",
                                    checked = homeSettingsViewModel.showStatsShortcut,
                                    onCheckedChange = homeSettingsViewModel::changeShowStatsShortcut
                                )

                                if (!homeSettingsViewModel.isDefaultLayout()) {
                                    Spacer(Modifier.height(6.dp))
                                    TextButton(
                                        onClick = { homeSettingsViewModel.restoreDefaults() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Restaurar distribución inicial", color = TestarioPrimary) }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = TestarioSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { showBehaviorSettings = !showBehaviorSettings }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Comportamiento de los tests", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                Text("Prioridad de Fallos y reglas de salida del repaso.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (showBehaviorSettings) "▲" else "▼", color = TestarioPrimary)
                        }

                        AnimatedVisibility(visible = showBehaviorSettings) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            behaviorSettingsViewModel.changePrioritizeFailuresInNormalTests(
                                                !behaviorSettingsViewModel.prioritizeFailuresInNormalTests
                                            )
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Priorizar preguntas falladas", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        Text(
                                            "Aumenta la prioridad de Fallos y de preguntas con peor porcentaje en Entrenamiento y Examen.",
                                            color = TestarioMutedText,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Checkbox(checked = behaviorSettingsViewModel.prioritizeFailuresInNormalTests, onCheckedChange = null)
                                }

                                Text("Salir de Fallos", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                val failureStreak = behaviorSettingsViewModel.failureExitCorrectStreak
                                Text(
                                    if (failureStreak == 1) "1 acierto consecutivo" else "$failureStreak aciertos consecutivos",
                                    color = TestarioPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Slider(
                                    value = failureStreak.toFloat(),
                                    onValueChange = { behaviorSettingsViewModel.changeFailureExitCorrectStreak(it.roundToInt()) },
                                    valueRange = 1f..10f,
                                    steps = 8,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = TestarioPrimary,
                                        activeTrackColor = TestarioPrimary,
                                        inactiveTrackColor = TestarioSurfaceLight
                                    )
                                )
                                Text(
                                    "Un fallo o una respuesta en blanco reinicia la racha de esa pregunta.",
                                    color = TestarioMutedText,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                if (!behaviorSettingsViewModel.prioritizeFailuresInNormalTests || failureStreak != 3) {
                                    TextButton(
                                        onClick = { behaviorSettingsViewModel.restoreDefaults() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Restaurar valores", color = TestarioPrimary) }
                                }

                                Text(
                                    text = if (showBehaviorInstructions) "💡 ¿Cómo funcionan estos ajustes? ▲" else "💡 ¿Cómo funcionan estos ajustes? ▼",
                                    color = TestarioPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showBehaviorInstructions = !showBehaviorInstructions }
                                        .padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                                AnimatedVisibility(visible = showBehaviorInstructions) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = TestarioSurfaceLight),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text(
                                            "Priorizar Fallos solo cambia la selección en tests normales; no borra ni altera Fallos. Salir de Fallos define cuántos aciertos consecutivos necesita cada pregunta para abandonar esa lista.",
                                            modifier = Modifier.padding(14.dp),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TestarioMutedText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = TestarioSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showStudySettings = !showStudySettings }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Planificación y notificaciones", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                Text("Objetivo diario, descansos y recordatorios.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (showStudySettings) "▲" else "▼", color = TestarioPrimary)
                        }
                        AnimatedVisibility(showStudySettings) {
                            CompactScrollableColumn(height = 350.dp) {
                                Spacer(Modifier.height(6.dp))
                                Text("Objetivo diario", color = TestarioText, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tiempo de estudio que quieres completar cada día: ${studySettings.dailyGoalMinutes / 60} h ${studySettings.dailyGoalMinutes % 60} min.",
                                    color = TestarioMutedText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Slider(
                                    value = studySettings.dailyGoalMinutes.toFloat(),
                                    onValueChange = { value ->
                                        val minutes = ((value / 15f).roundToInt() * 15).coerceIn(30, 720)
                                        PlanarioDependencies.studyRepository.setDailyGoalMinutes(minutes)
                                    },
                                    valueRange = 30f..720f,
                                    steps = 45
                                )

                                HomeSettingToggle(
                                    title = "Notificaciones",
                                    subtitle = "Control maestro para activar o silenciar todos los avisos programados.",
                                    checked = studySettings.notificationsEnabled,
                                    onCheckedChange = { enabled ->
                                        PlanarioDependencies.studyRepository.setNotificationsEnabled(enabled)
                                        PlanarioDependencies.schedulePlanarioNotifications()
                                    }
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    OutlinedButton(
                                        onClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Conceder permiso de notificaciones") }
                                }

                                HomeSettingToggle(
                                    title = "Recordatorio de estudio",
                                    subtitle = "Te recuerda comenzar la sesión de estudio a la hora seleccionada.",
                                    checked = studySettings.studyReminderEnabled,
                                    onCheckedChange = { enabled ->
                                        PlanarioDependencies.studyRepository.setStudyReminderEnabled(enabled)
                                        PlanarioDependencies.schedulePlanarioNotifications()
                                    }
                                )
                                NotificationTimeField("Hora de estudio", studySettings.studyReminderTime) {
                                    PlanarioDependencies.studyRepository.setStudyReminderTime(it)
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                }

                                HomeSettingToggle(
                                    title = "Objetivos fijos pendientes",
                                    subtitle = "Avisa cuando todavía quedan objetivos fijos por completar durante el día.",
                                    checked = studySettings.fixedObjectivesReminderEnabled,
                                    onCheckedChange = { enabled ->
                                        PlanarioDependencies.studyRepository.setFixedObjectivesReminderEnabled(enabled)
                                        PlanarioDependencies.schedulePlanarioNotifications()
                                    }
                                )
                                NotificationTimeField("Hora de objetivos", studySettings.fixedObjectivesReminderTime) {
                                    PlanarioDependencies.studyRepository.setFixedObjectivesReminderTime(it)
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                }

                                HomeSettingToggle(
                                    title = "Aviso de día libre",
                                    subtitle = "Te informa cuando el día está configurado como descanso y no exige objetivo.",
                                    checked = studySettings.restDayNotificationEnabled,
                                    onCheckedChange = { enabled ->
                                        PlanarioDependencies.studyRepository.setRestDayNotificationEnabled(enabled)
                                        PlanarioDependencies.schedulePlanarioNotifications()
                                    }
                                )
                                NotificationTimeField("Hora de día libre", studySettings.restDayNotificationTime) {
                                    PlanarioDependencies.studyRepository.setRestDayNotificationTime(it)
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                }

                                HomeSettingToggle(
                                    title = "Resumen diario",
                                    subtitle = "Al final del día resume el tiempo estudiado y el estado de tus objetivos.",
                                    checked = studySettings.dailySummaryEnabled,
                                    onCheckedChange = { enabled ->
                                        PlanarioDependencies.studyRepository.setDailySummaryEnabled(enabled)
                                        PlanarioDependencies.schedulePlanarioNotifications()
                                    }
                                )
                                NotificationTimeField("Hora del resumen", studySettings.dailySummaryTime) {
                                    PlanarioDependencies.studyRepository.setDailySummaryTime(it)
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                }

                                Spacer(Modifier.height(4.dp))
                                Text("Días libres por defecto", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                Text("Estos días no exigen completar el objetivo diario salvo que los cambies para una fecha concreta.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                                val dayLabels = listOf("L" to 1, "M" to 2, "X" to 3, "J" to 4, "V" to 5, "S" to 6, "D" to 7)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    dayLabels.forEach { (label, value) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Checkbox(
                                                checked = value in studySettings.defaultFreeWeekDays,
                                                onCheckedChange = {
                                                    PlanarioDependencies.studyRepository.toggleDefaultFreeWeekDay(value)
                                                    PlanarioDependencies.schedulePlanarioNotifications()
                                                }
                                            )
                                            Text(label, color = TestarioText, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        val initialDate = LocalDate.now()
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                pendingDayOverrideDate = LocalDate.of(year, month + 1, dayOfMonth)
                                            },
                                            initialDate.year,
                                            initialDate.monthValue - 1,
                                            initialDate.dayOfMonth
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Configurar día concreto") }

                                TextButton(
                                    onClick = { showClearPlanningDialog = true },
                                    modifier = Modifier.align(Alignment.End)
                                ) { Text("Borrar datos de planificación", color = TestarioRed) }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp), CardDefaults.cardColors(containerColor = TestarioSurface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { showDataOptions = !showDataOptions }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Datos", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                                Text("Copia completa, importación automática y exportación compatible.", color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(if (showDataOptions) "▲" else "▼", color = TestarioPrimary)
                        }

                        AnimatedVisibility(showDataOptions) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = { unifiedBackupLauncher.launch("nexum_copia_completa.zip") },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = TestarioGreenDark, contentColor = TestarioGreen)
                                ) { Text("Copia de seguridad") }

                                OutlinedButton(
                                    onClick = { unifiedImportLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "text/csv", "text/*", "*/*")) },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) { Text("Importar") }

                                OutlinedButton(
                                    onClick = {
                                        showExportOptions = !showExportOptions
                                        if (!showExportOptions) exportTarget = null
                                    },
                                    modifier = Modifier.fillMaxWidth().height(50.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, TestarioPrimary)
                                ) { Text(if (showExportOptions) "Cerrar exportación" else "Exportar", color = TestarioPrimary) }

                                AnimatedVisibility(showExportOptions) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("¿Qué quieres exportar?", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            JsonModeButton("Biblioteca", exportTarget == ExportTarget.LIBRARY, Modifier.weight(1f)) { exportTarget = ExportTarget.LIBRARY }
                                            JsonModeButton("Plan", exportTarget == ExportTarget.PLAN, Modifier.weight(1f)) { exportTarget = ExportTarget.PLAN }
                                        }

                                        if (exportTarget == ExportTarget.PLAN) {
                                            Text(
                                                "Exporta temas del plan, vueltas, días, tiempos, objetivos y ajustes en el formato compatible con Planario.",
                                                color = TestarioMutedText,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Button(
                                                onClick = { exportPlanLauncher.launch("planario_backup.json") },
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) { Text("Exportar plan") }
                                        }

                                        if (exportTarget == ExportTarget.LIBRARY) {
                                            Text("Formato", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                JsonModeButton("Completo (ZIP)", exportMode == ExportMode.COMPLETE, Modifier.weight(1f)) { exportMode = ExportMode.COMPLETE }
                                                JsonModeButton("Simple (JSON)", exportMode == ExportMode.SIMPLE, Modifier.weight(1f)) { exportMode = ExportMode.SIMPLE }
                                            }

                                            Text("Temas", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                ScopeButton("Todos", exportScope == ExportScope.ALL, Modifier.weight(1f)) { exportScope = ExportScope.ALL }
                                                ScopeButton("Uno", exportScope == ExportScope.SINGLE, Modifier.weight(1f)) { exportScope = ExportScope.SINGLE }
                                                ScopeButton("Varios", exportScope == ExportScope.MULTIPLE, Modifier.weight(1f)) { exportScope = ExportScope.MULTIPLE }
                                            }

                                            if (exportScope == ExportScope.SINGLE && database.topics.isNotEmpty()) {
                                                val sel = database.topics.firstOrNull { it.id == selectedSingleTopicId }
                                                ExposedDropdownMenuBox(expanded = singleDropdownExpanded, onExpandedChange = { singleDropdownExpanded = !singleDropdownExpanded }) {
                                                    OutlinedTextField(value = sel?.name ?: "Selecciona tema", onValueChange = {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = testarioTextFieldColors(), trailingIcon = { Text("▼") })
                                                    ExposedDropdownMenu(expanded = singleDropdownExpanded, onDismissRequest = { singleDropdownExpanded = false }, containerColor = TestarioSurface) {
                                                        sortedTopicsForExport.forEach { topic -> DropdownMenuItem(text = { Text(topic.name, color = TestarioText) }, onClick = { selectedSingleTopicId = topic.id; singleDropdownExpanded = false }) }
                                                    }
                                                }
                                            }

                                            if (exportScope == ExportScope.MULTIPLE && database.topics.isNotEmpty()) {
                                                val txt = "${selectedMultipleTopicIds.size} seleccionados"
                                                ExposedDropdownMenuBox(expanded = multiDropdownExpanded, onExpandedChange = { multiDropdownExpanded = !multiDropdownExpanded }) {
                                                    OutlinedTextField(value = txt, onValueChange = {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = testarioTextFieldColors(), trailingIcon = { Text("▼") })
                                                    ExposedDropdownMenu(expanded = multiDropdownExpanded, onDismissRequest = { multiDropdownExpanded = false }, containerColor = TestarioSurface) {
                                                        sortedTopicsForExport.forEach { topic ->
                                                            val active = selectedMultipleTopicIds.contains(topic.id)
                                                            DropdownMenuItem(
                                                                text = { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = active, onCheckedChange = null); Spacer(Modifier.width(6.dp)); Text(topic.name, color = TestarioText) } },
                                                                onClick = { selectedMultipleTopicIds = if (active) selectedMultipleTopicIds - topic.id else selectedMultipleTopicIds + topic.id }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val ids = when (exportScope) {
                                                        ExportScope.ALL -> null
                                                        ExportScope.SINGLE -> selectedSingleTopicId?.let { listOf(it) }
                                                        ExportScope.MULTIPLE -> selectedMultipleTopicIds.toList()
                                                    }
                                                    if ((exportScope == ExportScope.SINGLE && selectedSingleTopicId == null) ||
                                                        (exportScope == ExportScope.MULTIPLE && selectedMultipleTopicIds.isEmpty())) {
                                                        message = "Error: Configuración incompleta."
                                                        return@Button
                                                    }
                                                    pendingExportTopicIds = ids
                                                    if (exportMode == ExportMode.COMPLETE) {
                                                        exportZipLauncher.launch("testario_biblioteca.zip")
                                                    } else {
                                                        isLoading = true
                                                        coroutineScope.launch {
                                                            try {
                                                                pendingExportText = importExportViewModel.exportSimpleJson(ids)
                                                                isLoading = false
                                                                exportJsonLauncher.launch("testario_biblioteca.json")
                                                            } catch (_: Exception) {
                                                                message = "Error al exportar la Biblioteca."
                                                                isLoading = false
                                                            }
                                                        }
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) { Text("Exportar Biblioteca") }

                                        }
                                    }
                                }

                                Text(
                                    "Importar reconoce automáticamente copias completas, backups antiguos de Planario y archivos de Biblioteca de Testario.",
                                    color = TestarioMutedText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                message?.let { Card(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp), CardDefaults.cardColors(containerColor = if(it.startsWith("Error")) TestarioRedDark else TestarioGreenDark)) { Text(it, Modifier.padding(12.dp), color = TestarioText) } }
            }
        }

        if (isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                    elevation = CardDefaults.cardElevation(6.dp)
                ) {
                    Column(
                        Modifier
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                            .width(260.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Cargando datos...", style = MaterialTheme.typography.titleMedium, color = TestarioText)
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(50)),
                            color = TestarioPrimary,
                            trackColor = TestarioSurfaceLight
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Procesando archivos", style = MaterialTheme.typography.labelSmall, color = TestarioMutedText)
                    }
                }
            }
        }
    }

    pendingDayOverrideDate?.let { selectedDate ->
        val isCurrentlyFree = PlanarioDependencies.studyRepository.isFreeDay(selectedDate)
        AlertDialog(
            onDismissRequest = { pendingDayOverrideDate = null },
            title = { Text("Configurar ${planningDateFormatter.format(selectedDate)}") },
            text = {
                Text(
                    if (isCurrentlyFree)
                        "Actualmente es un día libre. ¿Quieres convertirlo en día de estudio?"
                    else
                        "Actualmente es un día de estudio. ¿Quieres marcarlo como día libre?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isCurrentlyFree) {
                        PlanarioDependencies.studyRepository.cancelFreeDay(selectedDate)
                        message = "${planningDateFormatter.format(selectedDate)} marcado como día de estudio."
                    } else {
                        PlanarioDependencies.studyRepository.setRestDay(selectedDate, true)
                        message = "${planningDateFormatter.format(selectedDate)} marcado como día libre."
                    }
                    PlanarioDependencies.schedulePlanarioNotifications()
                    pendingDayOverrideDate = null
                }) {
                    Text(if (isCurrentlyFree) "Día de estudio" else "Día libre")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDayOverrideDate = null }) { Text("Cancelar") }
            }
        )
    }

    if (showClearPlanningDialog) {
        AlertDialog(
            onDismissRequest = { showClearPlanningDialog = false },
            title = { Text("Borrar datos de planificación") },
            text = { Text("Se borrarán temas del Plan, vueltas, horas, calendario, objetivos y ajustes de planificación. La Biblioteca, preguntas y estadísticas de tests no se tocarán.") },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        PlanarioDependencies.studyTimerController.discardStudyTimer()
                        PlanarioDependencies.stopStudyTimerForegroundService()
                        PlanarioDependencies.cancelPlanarioScheduledNotifications()
                        PlanarioDependencies.studyRepository.clearAllData()
                        com.example.testario.data.local.QuickTestLinkManager(context.applicationContext).prune(
                            validPlanTopicIds = emptySet(),
                            validLibraryTopicIds = database.topics.mapTo(mutableSetOf()) { it.id }
                        )
                        showClearPlanningDialog = false
                        message = "Datos de planificación borrados."
                    },
                    border = BorderStroke(1.dp, TestarioRed)
                ) { Text("Borrar", color = TestarioRed) }
            },
            dismissButton = { TextButton(onClick = { showClearPlanningDialog = false }) { Text("Cancelar") } }
        )
    }


    if (showUnifiedRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showUnifiedRestoreConfirm = false; pendingUnifiedBytes = null },
            title = { Text("Restaurar copia completa") },
            text = { Text("Se sustituirán Biblioteca, estadísticas, planificación, objetivos, ajustes y vínculos de test rápido por los de la copia seleccionada.") },
            confirmButton = {
                Button(
                    onClick = {
                        val bytes = pendingUnifiedBytes
                        if (bytes != null) {
                            isLoading = true
                            coroutineScope.launch {
                                val ok = runCatching { unifiedDataManager.restoreGlobalBackup(bytes) }.getOrDefault(false)
                                if (ok) {
                                    PlanarioDependencies.schedulePlanarioNotifications()
                                    message = "Copia completa restaurada. Cierra y abre la app para recargar también todos los ajustes visuales."
                                } else {
                                    message = "Error: la copia completa no es válida."
                                }
                                pendingUnifiedBytes = null
                                showUnifiedRestoreConfirm = false
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)
                ) { Text("Restaurar") }
            },
            dismissButton = { OutlinedButton(onClick = { showUnifiedRestoreConfirm = false; pendingUnifiedBytes = null }) { Text("Cancelar") } }
        )
    }

    if (showTestarioBackupRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showTestarioBackupRestoreConfirm = false; pendingTestarioBackupBytes = null },
            title = { Text("Copia anterior de Biblioteca detectada") },
            text = { Text("El archivo corresponde a una copia completa de Testario. Restaurará Biblioteca, preguntas, imágenes, grupos, plantillas y estadísticas de test; tu Plan, tiempos y objetivos de estudio no se tocarán.") },
            confirmButton = {
                Button(
                    onClick = {
                        val bytes = pendingTestarioBackupBytes
                        if (bytes != null) {
                            isLoading = true
                            coroutineScope.launch {
                                val ok = runCatching { unifiedDataManager.restoreTestarioBackup(bytes) }.getOrDefault(false)
                                message = if (ok) "Copia de Biblioteca restaurada correctamente." else "Error: la copia de Biblioteca no es válida."
                                pendingTestarioBackupBytes = null
                                showTestarioBackupRestoreConfirm = false
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)
                ) { Text("Restaurar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTestarioBackupRestoreConfirm = false; pendingTestarioBackupBytes = null }) { Text("Cancelar") }
            }
        )
    }

    if (showPlanImportConfirm) {
        AlertDialog(
            onDismissRequest = { showPlanImportConfirm = false; pendingPlanJson = null },
            title = { Text("Datos de planificación detectados") },
            text = { Text("El archivo corresponde a una copia de la aplicación de planificación anterior. Al importarlo sustituirá el plan, calendario, tiempos, objetivos y sus ajustes actuales; la Biblioteca y tus tests no se tocarán.") },
            confirmButton = {
                Button(onClick = {
                    val raw = pendingPlanJson
                    val ok = raw != null && PlanarioDependencies.studyRepository.importBackupJson(raw)
                    if (ok) {
                        PlanarioDependencies.schedulePlanarioNotifications()
                        message = "Planificación importada correctamente."
                    } else {
                        message = "Error: copia de planificación no válida."
                    }
                    pendingPlanJson = null
                    showPlanImportConfirm = false
                }) { Text("Importar") }
            },
            dismissButton = { OutlinedButton(onClick = { showPlanImportConfirm = false; pendingPlanJson = null }) { Text("Cancelar") } }
        )
    }

    if (showRestoreConfirmDialog) {
        AlertDialog(onDismissRequest = { showRestoreConfirmDialog = false; pendingRestoreText = null }, title = { Text("Restaurar copia de seguridad") }, text = { Text("Se sobreescribirán todos tus datos y recursos actuales. Esta acción no se puede deshacer.") }, confirmButton = {
            Button(onClick = {
                isLoading = true
                coroutineScope.launch {
                    try {
                        importExportViewModel.restoreBackup(pendingRestoreText!!)
                        message = "Backup restaurado correctamente."
                    } catch(e: Exception) {
                        message = "Error: Archivo corrupto."
                    } finally {
                        isLoading = false
                        showRestoreConfirmDialog = false
                        pendingRestoreText = null
                    }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = TestarioRed)) { Text("Restaurar") }
        }, dismissButton = { OutlinedButton(onClick = { showRestoreConfirmDialog = false }) { Text("Cancelar") } })
    }

    if (showPreviewDialog) {
        val prev = pendingImportPreview
        AlertDialog(onDismissRequest = { showPreviewDialog = false }, title = { Text("Previsualizar importación") }, text = { Text("Temas hallados: ${prev?.topicCount}\nPreguntas válidas: ${prev?.questionCount}") }, confirmButton = {
            Button(onClick = {
                isLoading = true
                coroutineScope.launch {
                    val topics = importExportViewModel.getTopicsFromRawText(pendingImportText!!, pendingImportFileName)
                    showPreviewDialog = false
                    isLoading = false
                    startImportWorkflow(topics)
                }
            }) { Text("Aceptar") }
        }, dismissButton = { OutlinedButton(onClick = { showPreviewDialog = false }) { Text("Cancelar") } })
    }

    if (showDuplicateDialog) {
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("Tema duplicado") },
            text = { Text("Ya existe el tema \"${currentImportTopic?.name}\"") },
            confirmButton = {
                Button(onClick = {
                    customTopicName = currentImportTopic?.name.orEmpty()
                    showDuplicateDialog = false
                    showRenameDialog = true
                }) { Text("Renombrar") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        currentImportTopic?.let { importExportViewModel.importSingleTopicProcessed(it, DuplicateTopicAction.MERGE) }
                        showDuplicateDialog = false
                        startImportWorkflow(importedTopicsQueue)
                    }
                }) { Text("Fusionar") }
            }
        )
    }

    if (showRenameDialog) {
        AlertDialog(onDismissRequest = { showRenameDialog = false }, title = { Text("Renombrar") }, text = { OutlinedTextField(value = customTopicName, onValueChange = { customTopicName = it }, label = { Text("Nombre") }) }, confirmButton = {
            Button(onClick = {
                if (customTopicName.trim().isNotBlank()) {
                    coroutineScope.launch {
                        currentImportTopic?.let { importExportViewModel.importSingleTopicProcessed(it, DuplicateTopicAction.RENAME, customTopicName.trim()) }
                        showRenameDialog = false
                        customTopicName = ""
                        startImportWorkflow(importedTopicsQueue)
                    }
                }
            }) { Text("Guardar") }
        })
    }
}


@Composable
private fun HomeSettingToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TestarioText, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(10.dp))
        Checkbox(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
private fun NotificationTimeField(
    label: String,
    value: String,
    onValidTime: (String) -> Unit
) {
    var localValue by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = localValue,
        onValueChange = { raw ->
            val filtered = raw.filter { it.isDigit() || it == ':' }.take(5)
            localValue = filtered
            if (Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$").matches(filtered)) {
                onValidTime(filtered)
            }
        },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = testarioTextFieldColors()
    )
}

@Composable private fun ThemeOption(t: String, s: Boolean, m: Modifier, o: () -> Unit) = Button(o, m.height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (s) TestarioPrimaryDark else TestarioSurfaceLight, contentColor = TestarioText), border = if (s) BorderStroke(1.dp, TestarioPrimary) else null) { Text(t) }
@Composable private fun JsonModeButton(t: String, s: Boolean, m: Modifier, o: () -> Unit) = Button(o, m.height(46.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = if (s) TestarioPrimaryDark else TestarioSurfaceLight, contentColor = TestarioText), border = if (s) BorderStroke(1.dp, TestarioPrimary) else null) { Text(t) }
@Composable private fun ScopeButton(t: String, s: Boolean, m: Modifier, o: () -> Unit) = Button(o, shape = RoundedCornerShape(10.dp), modifier = m.height(38.dp), colors = ButtonDefaults.buttonColors(containerColor = if (s) TestarioPrimary else TestarioSurfaceLight, contentColor = TestarioText)) { Text(t, style = MaterialTheme.typography.bodySmall) }