package com.example.testario.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.planario.app.PlanarioDependencies
import com.example.testario.domain.NexumDeviceSyncManager
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.ui.theme.TestarioSurfaceLight
import com.example.testario.ui.theme.TestarioText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit per-device authority: publishing and restoring both require a user
 * confirmation. No automatic merging or background writes to the other phone.
 */
@Composable
fun NexumDeviceSyncPanel() {
    val context = LocalContext.current
    val manager = remember(context) { NexumDeviceSyncManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(manager.connectedFolder) }
    var name by remember { mutableStateOf(manager.deviceName) }
    var copies by remember { mutableStateOf<List<NexumDeviceSyncManager.Snapshot>>(emptyList()) }
    var selected by remember { mutableStateOf<NexumDeviceSyncManager.Snapshot?>(null) }
    var showPublishConfirmation by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var showRecoveryConfirmation by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var lastRecovery by remember { mutableStateOf(manager.recoveryFiles().firstOrNull()) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES")) }

    fun refresh() {
        if (folder == null) { copies = emptyList(); return }
        scope.launch {
            busy = true
            try {
                copies = withContext(Dispatchers.IO) { manager.snapshots() }
                selected = selected?.let { old -> copies.firstOrNull { it.uri == old.uri } }
                lastRecovery = manager.recoveryFiles().firstOrNull()
            } catch (e: Exception) {
                status = "No se pudieron leer las copias: ${e.message ?: "sin acceso a la carpeta"}"
            } finally {
                busy = false
            }
        }
    }

    val chooseTree = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                manager.connectFolder(uri)
                folder = manager.connectedFolder
                status = "Carpeta seleccionada. Usa la misma carpeta compartida en los demás dispositivos."
                refresh()
            } catch (e: Exception) {
                status = "No se pudo vincular la carpeta: ${e.message ?: "comprueba los permisos"}"
            }
        }
    }

    LaunchedEffect(folder) {
        if (folder != null && copies.isEmpty()) refresh()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Sincronización entre dispositivos", style = MaterialTheme.typography.titleLarge, color = TestarioText)
        Text(
            "Vincula en cada Android la MISMA carpeta compartida (por ejemplo, " +
                "una carpeta de nube disponible en Archivos). Sin cuentas ni contraseñas dentro de NEXUM.",
            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall
        )
        Text(
            "La sincronización es deliberada: eliges qué NEXUM prevalece y lo confirmas. " +
                "No se mezclan cambios hechos en varios móviles. No se suben datos automáticamente.",
            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it.take(35) },
            label = { Text("Nombre de este dispositivo") },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )
        OutlinedButton(
            onClick = {
                if (name.isNotBlank()) {
                    manager.deviceName = name
                    status = "Dispositivo identificado como ${manager.deviceName}."
                } else {
                    status = "Escribe un nombre para este dispositivo."
                }
            },
            modifier = Modifier.fillMaxWidth(), enabled = !busy
        ) { Text("Guardar nombre del dispositivo") }

        OutlinedButton(
            onClick = { chooseTree.launch(folder) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy
        ) {
            Text(if (folder == null) "Seleccionar carpeta compartida" else "Cambiar carpeta compartida")
        }
        Text(
            if (folder == null) "Sin carpeta vinculada."
            else "Carpeta vinculada en este dispositivo.",
            color = if (folder == null) TestarioMutedText else TestarioPrimary,
            style = MaterialTheme.typography.bodySmall
        )

        if (folder != null) {
            Button(
                onClick = { showPublishConfirmation = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !busy
            ) { Text("Este dispositivo prevalece · Enviar") }

            OutlinedButton(
                onClick = { refresh() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            ) { Text("Buscar copias de otros dispositivos") }

            Text("Elegir otro NEXUM para recibir sus datos", color = TestarioText, style = MaterialTheme.typography.titleMedium)
            if (copies.isEmpty()) {
                Text("No hay copias NEXUM válidas visibles en esta carpeta.", color = TestarioMutedText)
            } else {
                copies.take(12).forEach { copy ->
                    val isOwn = copy.sourceId == manager.deviceId
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { selected = copy },
                        colors = CardDefaults.cardColors(containerColor = TestarioSurface),
                        border = BorderStroke(1.dp, if (selected?.uri == copy.uri) TestarioPrimary else TestarioSurfaceLight),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = selected?.uri == copy.uri,
                                onClick = { selected = copy },
                                enabled = !busy
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(copy.sourceName + (if (isOwn) " (este dispositivo)" else ""), color = TestarioText)
                                Text(
                                    dateFormat.format(Date(copy.dateUtc)) + " · ID ${copy.sourceId}",
                                    color = TestarioMutedText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { showRestoreConfirmation = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !busy && selected != null
            ) { Text("Usar el NEXUM seleccionado · Recibir") }

            Text(
                "Seguridad: cada envío crea una copia nueva. Antes de recibir, NEXUM crea " +
                    "una copia local de recuperación. Se conservan hasta 3 copias por " +
                    "dispositivo en la carpeta compartida. Tamaño máximo: 128 MB por copia.",
                color = TestarioMutedText, style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Las copias contienen tus datos sin cifrado adicional de NEXUM. " +
                    "El acceso depende de la privacidad y sincronización del proveedor elegido.",
                color = TestarioMutedText, style = MaterialTheme.typography.bodySmall
            )
        }
        if (lastRecovery != null) {
            OutlinedButton(
                onClick = { showRecoveryConfirmation = true },
                modifier = Modifier.fillMaxWidth(), enabled = !busy
            ) { Text("Restaurar la última copia de recuperación") }
        }

        if (busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(22.dp), strokeWidth = 2.dp)
                Text("Sincronizando y verificando datos…", color = TestarioMutedText)
            }
        }
        if (status != null) {
            Text(status.orEmpty(), color = TestarioPrimary, style = MaterialTheme.typography.bodySmall)
        }
    }

    if (showPublishConfirmation) {
        AlertDialog(
            onDismissRequest = { showPublishConfirmation = false },
            title = { Text("Usar los datos de este NEXUM") },
            text = {
                Text(
                    "Se enviará una copia COMPLETA de ${manager.deviceName} a la carpeta " +
                        "compartida: biblioteca, respuestas, exámenes, planificación, objetivos, " +
                        "imágenes y ajustes. Los otros móviles no se modificarán hasta " +
                        "que confirmes recibir esta copia en ellos. ¿Continuar?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPublishConfirmation = false
                    scope.launch {
                        busy = true
                        try {
                            val sent = withContext(Dispatchers.IO) { manager.publishThisDevice() }
                            status = "Copia publicada: ${sent.sourceName} · ${dateFormat.format(Date(sent.dateUtc))}. Abre NEXUM en el otro móvil y elige Recibir."
                            copies = withContext(Dispatchers.IO) { manager.snapshots() }
                        } catch (e: Exception) {
                            status = "Error al enviar: ${e.message ?: "no se ha verificado la copia"}"
                        } finally { busy = false }
                    }
                }) { Text("Sí, enviar mis datos") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showPublishConfirmation = false }) { Text("Cancelar") }
            }
        )
    }

    val receiving = selected
    if (showRestoreConfirmation && receiving != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmation = false },
            title = { Text("¿Sustituir todos los datos de este NEXUM?") },
            text = {
                Text(
                    "Prevalecerá ${receiving.sourceName} (ID ${receiving.sourceId}), " +
                        "copia del ${dateFormat.format(Date(receiving.dateUtc))}. " +
                        "Se reemplazarán tus temas, preguntas, estadísticas, exámenes, " +
                        "planificación, objetivos, ajustes y tests pendientes. " +
                        "Antes guardaremos una copia de recuperación local. ¿Confirmas?"
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRestoreConfirmation = false
                    scope.launch {
                        busy = true
                        try {
                            val recovery = withContext(Dispatchers.IO) { manager.useRemoteDevice(receiving) }
                            PlanarioDependencies.schedulePlanarioNotifications()
                            status = "Datos recibidos de ${receiving.sourceName}. Copia anterior: $recovery. Cierra y vuelve a abrir NEXUM para refrescar todos los ajustes."
                            lastRecovery = manager.recoveryFiles().firstOrNull()
                        } catch (e: Exception) {
                            status = "Error al recibir: ${e.message ?: "se ha conservado la copia local"}"
                        } finally { busy = false }
                    }
                }) { Text("Sí, sustituir mis datos") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRestoreConfirmation = false }) { Text("Cancelar") }
            }
        )
    }

    val recovery = lastRecovery
    if (showRecoveryConfirmation && recovery != null) {
        AlertDialog(
            onDismissRequest = { showRecoveryConfirmation = false },
            title = { Text("Recuperar el estado anterior") },
            text = { Text("Se sustituirán todos los datos por la copia local creada antes de la última sincronización. ¿Continuar?") },
            confirmButton = {
                Button(onClick = {
                    showRecoveryConfirmation = false
                    scope.launch {
                        busy = true
                        try {
                            val ok = withContext(Dispatchers.IO) { manager.restoreRecoveryArchive(recovery) }
                            if (ok) PlanarioDependencies.schedulePlanarioNotifications()
                            status = if (ok) "Copia recuperada. Reinicia NEXUM para refrescar todos los ajustes."
                            else "No se pudo recuperar la copia. El archivo de recuperación se conserva."
                        } catch (e: Exception) {
                            status = "Error al recuperar: ${e.message ?: "copia no válida"}"
                        } finally { busy = false }
                    }
                }) { Text("Sí, restaurar copia") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRecoveryConfirmation = false }) { Text("Cancelar") }
            }
        )
    }
}
