package com.example.testario.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.testario.BuildConfig
import com.example.testario.ui.theme.NeonButton
import com.example.testario.ui.theme.NeonOutlinedButton
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioSurface
import com.example.testario.update.NexumDownloadStatus
import com.example.testario.update.NexumRelease
import com.example.testario.update.NexumUpdateManager
import com.example.testario.update.NexumUpdateResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Explicit update controls with download progress and user-approved Android installation. */
@Composable
internal fun NexumUpdateSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<NexumRelease?>(null) }
    var message by remember { mutableStateOf("Consultando versiones oficiales…") }
    var isChecking by remember { mutableStateOf(true) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    var downloadReady by remember { mutableStateOf(false) }
    var integrityOk by remember { mutableStateOf(false) }

    suspend fun refresh(force: Boolean) {
        isChecking = true
        val result = NexumUpdateManager.check(context, force)
        when (result) {
            is NexumUpdateResult.Available -> {
                release = result.release
                message = "Disponible NEXUM ${result.release.version}"
            }
            NexumUpdateResult.UpToDate -> {
                release = null
                message = "Tienes la última versión publicada."
            }
            NexumUpdateResult.NotPublished -> {
                release = null
                message = "Aún no hay una nueva APK oficial publicada en GitHub Releases."
            }
            is NexumUpdateResult.Error -> {
                release = null
                message = result.explanation
            }
        }
        isChecking = false
    }

    LaunchedEffect(Unit) { refresh(force = false) }
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        while (true) {
            val status = runCatching { NexumUpdateManager.downloadStatus(context, id) }
                .getOrDefault(NexumDownloadStatus.Failed)
            when (status) {
                NexumDownloadStatus.Complete -> {
                    val requested = release
                    integrityOk = requested != null && runCatching {
                        NexumUpdateManager.verifyDownloadedApk(context, id, requested)
                    }.getOrDefault(false)
                    downloadReady = true
                    message = if (integrityOk) {
                        "APK verificada. Pulsa Instalar para confirmar la actualización en Android."
                    } else {
                        "La descarga no superó la verificación de integridad. No se instalará."
                    }
                    break
                }
                NexumDownloadStatus.Failed -> {
                    message = "La descarga falló. Puedes volver a intentarlo."
                    downloadId = null
                    break
                }
                NexumDownloadStatus.Pending -> message = "Descarga en cola…"
                NexumDownloadStatus.Downloading -> message = "Descargando APK oficial…"
            }
            delay(950)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = TestarioSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Información de NEXUM", style = MaterialTheme.typography.titleLarge)
            Text("Versión instalada: ${BuildConfig.VERSION_NAME}", color = TestarioMutedText)
            Text(message, color = TestarioMutedText, style = MaterialTheme.typography.bodySmall)
            if (release != null) {
                val r = release!!
                if (r.notes.isNotBlank()) {
                    Text(r.notes.take(350), style = MaterialTheme.typography.bodySmall)
                }
                if (downloadReady && integrityOk && downloadId != null) {
                    NeonButton(
                        onClick = {
                            val opened = runCatching {
                                NexumUpdateManager.openInstaller(context, downloadId!!)
                            }.getOrDefault(false)
                            message = if (opened) {
                                "Confirma la instalación en Android. Tus datos se conservarán."
                            } else {
                                "Activa «Permitir desde esta fuente» en Android y vuelve a pulsar Instalar."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Instalar NEXUM ${r.version}") }
                } else if (downloadId == null || downloadReady) {
                    NeonButton(
                        onClick = {
                            downloadReady = false
                            integrityOk = false
                            message = "Preparando descarga…"
                            val id = runCatching { NexumUpdateManager.startDownload(context, r) }
                            id.onSuccess { downloadId = it }
                                .onFailure { message = "No se pudo iniciar la descarga." }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Descargar actualización") }
                }
            }
            NeonOutlinedButton(
                onClick = { scope.launch { refresh(force = true) } },
                enabled = !isChecking && downloadId == null,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isChecking) "Comprobando…" else "Buscar actualizaciones") }
        }
    }
}
