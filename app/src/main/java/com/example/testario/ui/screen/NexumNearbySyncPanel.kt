package com.example.testario.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import com.example.testario.domain.NexumNearbyManager
import com.example.testario.domain.NexumNearbyMode
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioRed
import com.example.testario.ui.theme.TestarioText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun nearbyPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 33 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.NEARBY_WIFI_DEVICES
    )
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    Build.VERSION.SDK_INT >= 29 -> arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    else -> arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
}

/** Keep the session on this screen only; closing it stops advertising and scanning. */
@Composable
fun NexumNearbySyncPanel() {
    val context = LocalContext.current
    val manager = remember(context) { NexumNearbyManager(context.applicationContext) }
    val state by manager.state.collectAsState()
    val scope = rememberCoroutineScope()
    val timer by PlanarioDependencies.studyTimerController.state.collectAsState()
    var feedback by remember { mutableStateOf<String?>(null) }
    var hostConfirmation by remember { mutableStateOf(false) }
    var restoreConfirmation by remember { mutableStateOf(false) }
    var recoveryConfirmation by remember { mutableStateOf(false) }
    var awaitingPermission by remember { mutableStateOf<NexumNearbyMode?>(null) }

    DisposableEffect(manager) {
        onDispose { manager.close() }
    }

    fun start(mode: NexumNearbyMode) {
        feedback = null
        try {
            if (mode == NexumNearbyMode.HOST) manager.host() else manager.join()
        } catch (error: Exception) {
            feedback = error.localizedMessage ?: "No fue posible iniciar la conexión"
        }
    }

    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val mode = awaitingPermission
        awaitingPermission = null
        if (mode != null && nearbyPermissions().all { permission ->
                result[permission] == true ||
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }) {
            start(mode)
        } else {
            feedback = "Debes conceder los permisos de dispositivos cercanos para transferir datos."
        }
    }

    fun startIfPermitted(mode: NexumNearbyMode) {
        val missing = nearbyPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            awaitingPermission = mode
            requestPermissions.launch(missing.toTypedArray())
        } else start(mode)
    }

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Transferencia entre dispositivos", style = MaterialTheme.typography.titleMedium,
            color = TestarioText)
        Text("Sincroniza dos Android cercanos sin archivos ni cuentas: conecta ambos, compara " +
            "su contenido y decide cuál prevalece. Nunca se sustituyen datos sin confirmar " +
            "en los dos dispositivos.", style = MaterialTheme.typography.bodySmall,
            color = TestarioMutedText)
        Text("1. En un móvil pulsa CREAR CONEXIÓN; en el otro, UNIRSE.\n" +
            "2. Comprueba que el código de seguridad sea idéntico.\n" +
            "3. Elige Prevalece ESTE móvil o Prevalece el OTRO.\n" +
            "4. El dueño del origen confirma el envío. El receptor confirma la sustitución.\n" +
            "5. NEXUM guarda automáticamente una copia previa recuperable.",
            style = MaterialTheme.typography.bodySmall, color = TestarioMutedText)

        if (state.mode == NexumNearbyMode.NONE) {
            Button(modifier = Modifier.fillMaxWidth().height(48.dp),
                onClick = { startIfPermitted(NexumNearbyMode.HOST) }) {
                Text("CREAR CONEXIÓN")
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth().height(48.dp),
                onClick = { startIfPermitted(NexumNearbyMode.JOIN) }) {
                Text("UNIRSE A CONEXIÓN")
            }
        } else {
            Text(if (state.mode == NexumNearbyMode.HOST) "Modo: creó conexión" else "Modo: se unió",
                color = TestarioPrimary, style = MaterialTheme.typography.titleMedium)
            Text(state.status, color = TestarioText)
            if (state.mode == NexumNearbyMode.JOIN && !state.connected &&
                state.authenticationDigits == null) {
                if (state.peers.isEmpty()) {
                    Text("No se han encontrado otros NEXUM cercanos todavía.", color = TestarioMutedText)
                } else {
                    state.peers.forEach { peer ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(),
                            onClick = { manager.connect(peer.id) }) {
                            Text("Conectar con ${peer.name}")
                        }
                    }
                }
            }
            if (state.connected && state.incomingPeer != null) {
                Text("Dispositivo: ${state.incomingPeer?.name.orEmpty()}",
                    color = TestarioMutedText)
            }
            if (state.connected) {
                state.localSummary?.let { summary ->
                    Text("ESTE dispositivo: ${summary.describe()}",
                        color = TestarioText, style = MaterialTheme.typography.bodySmall)
                }
                state.peerSummary?.let { summary ->
                    Text("OTRO dispositivo: ${summary.describe()}",
                        color = TestarioText, style = MaterialTheme.typography.bodySmall)
                }
                Text("La comparación de cantidades es orientativa; no garantiza " +
                    "que las preguntas o los planes sean idénticos.",
                    color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
            }
            if (state.connected && !state.processing && !state.completed &&
                !state.readyToRestore && !state.awaitingPeerToSend &&
                !state.remoteRequestedToSend) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { hostConfirmation = true }) {
                    Text("Prevalecen los datos de ESTE móvil")
                }
                OutlinedButton(modifier = Modifier.fillMaxWidth(),
                    onClick = { manager.requestPeerToSend() }) {
                    Text("Prevalecen los datos del OTRO móvil")
                }
            }
            if (state.remoteRequestedToSend) {
                AlertDialog(
                    onDismissRequest = { manager.rejectPeerRequest() },
                    title = { Text("El otro NEXUM solicita tus datos") },
                    text = { Text("El otro móvil ha elegido que prevalezca ESTE dispositivo. " +
                        "¿Confirmas enviar una copia completa? No se modificará tu información. " +
                        "El receptor tendrá que aceptar el reemplazo por separado.") },
                    confirmButton = {
                        Button(onClick = { manager.acceptPeerRequestAndSend() }) {
                            Text("Sí, prevalece este NEXUM")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { manager.rejectPeerRequest() }) {
                            Text("Rechazar")
                        }
                    }
                )
            }
            if (state.progressPercent > 0) {
                LinearProgressIndicator(progress = { state.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth())
                Text("Transferencia: ${state.progressPercent} %", color = TestarioMutedText)
            }
            if (state.readyToRestore && !state.processing) {
                Button(modifier = Modifier.fillMaxWidth(), onClick = { restoreConfirmation = true }) {
                    Text("Confirmar recepción de datos")
                }
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(),
                enabled = !state.processing,
                onClick = { manager.stop() }) {
                Text("Desconectar / cancelar")
            }
        }
        if (state.error != null) Text(state.error.orEmpty(), color = TestarioRed)
        if (feedback != null) Text(feedback.orEmpty(), color = TestarioPrimary)
        if (manager.hasRecovery()) {
            OutlinedButton(modifier = Modifier.fillMaxWidth(),
                onClick = { recoveryConfirmation = true }, enabled = !state.processing) {
                Text("Recuperar los datos anteriores")
            }
        }
        Text("Sincronización local bajo demanda (ambos móviles próximos), sin nube ni mezcla " +
            "automática de cambios. Máximo 128 MB por copia. Si se interrumpe el envío, " +
            "no se reemplazan datos.",
            color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
    }

    val pairingCode = state.authenticationDigits
    if (pairingCode != null) {
        AlertDialog(
            onDismissRequest = { manager.rejectCode() },
            title = { Text("Verificar conexión") },
            text = {
                Text("Compara este código con el que aparece en ${state.incomingPeer?.name ?: "el otro móvil"}:\n\n" +
                    pairingCode + "\n\nAcepta únicamente si ambos muestran exactamente el mismo código.")
            },
            confirmButton = { Button(onClick = { manager.confirmCode() }) { Text("Los códigos coinciden") } },
            dismissButton = { OutlinedButton(onClick = { manager.rejectCode() }) { Text("Rechazar") } }
        )
    }

    if (hostConfirmation) AlertDialog(
        onDismissRequest = { hostConfirmation = false },
        title = { Text("¿Prevalecerá ESTE NEXUM?") },
        text = { Text("Se enviarán biblioteca, preguntas e imágenes, estadísticas, " +
            "historial, planificación, objetivos y ajustes. El receptor deberá confirmar " +
            "antes de reemplazar sus datos. Este dispositivo no se modificará.") },
        confirmButton = { Button(onClick = {
            hostConfirmation = false
            manager.send()
        }) { Text("Confirmar origen y enviar") } },
        dismissButton = { OutlinedButton(onClick = { hostConfirmation = false }) { Text("Cancelar") } }
    )

    if (restoreConfirmation) AlertDialog(
        onDismissRequest = { restoreConfirmation = false },
        title = { Text("¿Sustituir TODOS los datos de este NEXUM?") },
        text = { Text("La información del emisor (${state.incomingPeer?.name ?: "otro móvil"}) " +
            "prevalecerá. Se reemplazarán TODOS los temas, imágenes, pruebas, estadísticas, " +
            "plantillas, planificación, objetivos y ajustes. No se mezclarán cambios. " +
            "Antes guardaremos una copia local recuperable." +
            if (timer.studyTimerState != StudyTimerState.STOPPED)
                "\n\nATENCIÓN: guarda y termina antes el cronómetro de estudio, incluso si está pausado." else "") },
        confirmButton = {
            Button(
                enabled = timer.studyTimerState == StudyTimerState.STOPPED,
                onClick = {
                    restoreConfirmation = false
                    scope.launch {
                        try {
                            val backup = withContext(Dispatchers.IO) { manager.applyReceived() }
                            PlanarioDependencies.schedulePlanarioNotifications()
                            feedback = "Datos recibidos. Copia anterior: $backup. Cierra y abre NEXUM " +
                                "para refrescar los ajustes de todas las pantallas."
                        } catch (error: Exception) {
                            feedback = error.localizedMessage ?: "No se pudo importar la copia"
                        }
                    }
                }
            ) { Text("Sí, sustituir y guardar recuperación") }
        },
        dismissButton = {
            OutlinedButton(onClick = { restoreConfirmation = false }) { Text("Cancelar") }
        }
    )

    if (recoveryConfirmation) AlertDialog(
        onDismissRequest = { recoveryConfirmation = false },
        title = { Text("Restaurar estado anterior") },
        text = { Text("Se sustituirá el estado de NEXUM por el que tenía antes de la última " +
            "recepción. Antes se guardará otra copia de seguridad. ¿Continuar?") },
        confirmButton = {
            Button(enabled = timer.studyTimerState == StudyTimerState.STOPPED, onClick = {
                recoveryConfirmation = false
                scope.launch {
                    try {
                        val ok = withContext(Dispatchers.IO) { manager.restoreLastRecovery() }
                        if (ok) PlanarioDependencies.schedulePlanarioNotifications()
                        feedback = if (ok) "Copia anterior recuperada. Reinicia NEXUM."
                            else "No se pudo recuperar la copia anterior."
                    } catch (error: Exception) {
                        feedback = error.localizedMessage ?: "Error al recuperar datos"
                    }
                }
            }) { Text("Sí, restaurar copia") }
        },
        dismissButton = {
            OutlinedButton(onClick = { recoveryConfirmation = false }) { Text("Cancelar") }
        }
    )
}
