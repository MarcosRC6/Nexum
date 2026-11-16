package com.example.testario.domain

import android.content.Context
import android.os.Build
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class NexumNearbyPeer(val id: String, val name: String)

enum class NexumNearbyMode { NONE, HOST, JOIN }

data class NexumNearbyState(
    val mode: NexumNearbyMode = NexumNearbyMode.NONE,
    val status: String = "Selecciona Enviar o Recibir.",
    val peers: List<NexumNearbyPeer> = emptyList(),
    val incomingPeer: NexumNearbyPeer? = null,
    val authenticationDigits: String? = null,
    val connected: Boolean = false,
    val progressPercent: Int = 0,
    val processing: Boolean = false,
    val readyToRestore: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null
)

/** Direct phone-to-phone communication. No cloud service, files chooser or background sync. */
class NexumNearbyManager(context: Context) {
    private val app = context.applicationContext
    private val client = Nearby.getConnectionsClient(app)
    private val archives = NexumNearbyArchive(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serviceId = app.packageName + ".nexum_transfer_v1"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    val localName: String = "NEXUM · ${Build.MODEL}".take(64)
    private val mutable = MutableStateFlow(NexumNearbyState())
    val state = mutable.asStateFlow()

    private var endpoint: String? = null
    private var hostFile: NexumNearbyArchive.Prepared? = null
    private var outgoingFileId: Long? = null
    private var receiverFile: NexumNearbyArchive.Prepared? = null
    private var filePayload: Payload? = null
    private var fileCompleted = false
    private var metadata: JSONObject? = null
    private var stagingStarted = false

    private fun update(change: (NexumNearbyState) -> NexumNearbyState) {
        mutable.value = change(mutable.value)
    }
    private fun failure(reason: String) {
        update { it.copy(status = "No se pudo completar la conexión o transferencia.",
            processing = false, error = reason) }
    }

    fun host() {
        stop()
        update { NexumNearbyState(mode = NexumNearbyMode.HOST,
            status = "Preparando dispositivo emisor…") }
        client.startAdvertising(localName, serviceId, connections,
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener {
                update { it.copy(status = "Emisor activo. En el otro móvil pulsa Recibir.") }
            }
            .addOnFailureListener { failure(it.localizedMessage ?: "No se pudo anunciar el dispositivo") }
    }

    fun join() {
        stop()
        update { NexumNearbyState(mode = NexumNearbyMode.JOIN,
            status = "Buscando dispositivos NEXUM cercanos…") }
        client.startDiscovery(serviceId, discovery,
            DiscoveryOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener {
                update { it.copy(status = "Buscando… En el otro móvil pulsa Enviar.") }
            }
            .addOnFailureListener { failure(it.localizedMessage ?: "No se pudo buscar dispositivos") }
    }

    fun connect(peerId: String) {
        require(state.value.mode == NexumNearbyMode.JOIN && !state.value.connected)
        val peer = state.value.peers.firstOrNull { it.id == peerId } ?: return
        endpoint = peerId
        update { it.copy(status = "Conectando con ${peer.name}…", incomingPeer = peer) }
        client.requestConnection(localName, peerId, connections)
            .addOnFailureListener { failure(it.localizedMessage ?: "Solicitud de conexión fallida") }
    }

    private val discovery = object: EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) {
            update {
                val other = it.peers.filterNot { peer -> peer.id == id } +
                    NexumNearbyPeer(id, info.endpointName)
                it.copy(peers = other)
            }
        }
        override fun onEndpointLost(id: String) {
            update { it.copy(peers = it.peers.filterNot { p -> p.id == id }) }
        }
    }

    private val connections = object: ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
            if (endpoint != null && endpoint != id) {
                client.rejectConnection(id)
                return
            }
            endpoint = id
            update { it.copy(
                incomingPeer = NexumNearbyPeer(id, info.endpointName),
                authenticationDigits = info.authenticationDigits,
                status = "Compara el código en ambos móviles. Acepta solamente si es idéntico.",
                error = null
            ) }
        }
        override fun onConnectionResult(id: String, result: ConnectionResolution) {
            if (id != endpoint) return
            if (result.status.statusCode == CommonStatusCodes.SUCCESS) {
                client.stopAdvertising()
                client.stopDiscovery()
                update { it.copy(connected = true, authenticationDigits = null,
                    status = if (it.mode == NexumNearbyMode.HOST)
                        "Conectados. Confirma el envío de todos los datos."
                    else "Conectados. Esperando el envío desde el dispositivo emisor.") }
            } else {
                failure("Conexión rechazada o interrumpida")
                endpoint = null
                update { it.copy(authenticationDigits = null) }
            }
        }
        override fun onDisconnected(id: String) {
            if (id == endpoint) {
                update { it.copy(connected = false, authenticationDigits = null,
                    status = if (it.readyToRestore)
                        "Datos recibidos y verificados. Puedes confirmar su importación."
                    else "El otro dispositivo se ha desconectado.") }
            }
        }
    }

    fun confirmCode() {
        val id = endpoint ?: return
        require(state.value.authenticationDigits != null)
        client.acceptConnection(id, payloads)
            .addOnFailureListener { failure(it.localizedMessage ?: "No se pudo aceptar la conexión") }
        update { it.copy(authenticationDigits = null, status = "Esperando confirmación del otro móvil…") }
    }

    fun rejectCode() {
        endpoint?.let { client.rejectConnection(it) }
        stop()
    }

    /** Only the HOST can initiate a transfer after the pairing token is accepted. */
    fun send() {
        val id = endpoint ?: return
        if (state.value.mode != NexumNearbyMode.HOST || !state.value.connected ||
            state.value.processing) return
        update { it.copy(processing = true, error = null, progressPercent = 0,
            status = "Preparando y verificando copia completa…") }
        scope.launch {
            try {
                val prepared = archives.prepareHostArchive()
                hostFile = prepared
                val file = Payload.fromFile(prepared.file)
                // Keep the received archive out of public Downloads.
                file.setSensitive(true)
                outgoingFileId = file.id
                val control = JSONObject()
                    .put("type", "NEXUM_BACKUP_V1")
                    .put("payloadId", file.id)
                    .put("sha256", prepared.sha256)
                    .put("size", prepared.size)
                    .put("source", localName)
                client.sendPayload(id, Payload.fromBytes(control.toString().toByteArray(Charsets.UTF_8)))
                    .addOnSuccessListener {
                        client.sendPayload(id, file)
                            .addOnSuccessListener {
                                update { it.copy(status = "Enviando copia completa…") }
                            }
                            .addOnFailureListener { failure(it.localizedMessage ?: "Error al enviar archivo") }
                    }
                    .addOnFailureListener { failure(it.localizedMessage ?: "Error al enviar información") }
            } catch (error: Exception) {
                failure(error.localizedMessage ?: "No se pudo preparar la copia")
            }
        }
    }

    private val payloads = object: PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (id != endpoint || state.value.mode != NexumNearbyMode.JOIN ||
                !state.value.connected) return
            when (payload.type) {
                Payload.Type.BYTES -> {
                    try {
                        val body = payload.asBytes() ?: error("Faltan metadatos")
                        require(body.size <= 4096) { "Datos de control demasiado largos" }
                        val json = JSONObject(String(body, Charsets.UTF_8))
                        require(json.optString("type") == "NEXUM_BACKUP_V1")
                        require(json.optLong("size") in 1..NexumNearbyArchive.MAX_COMPRESSED_BYTES)
                        require(Regex("[a-f0-9]{64}").matches(json.optString("sha256")))
                        require(metadata == null && !state.value.readyToRestore)
                        metadata = json
                        checkStaging()
                    } catch (error: Exception) {
                        failure(error.message ?: "Datos de control no válidos")
                    }
                }
                Payload.Type.FILE -> {
                    if (filePayload != null) { failure("Se ha recibido más de un archivo"); return }
                    filePayload = payload
                    checkStaging()
                }
                else -> failure("Tipo de transferencia no compatible")
            }
        }
        override fun onPayloadTransferUpdate(id: String, transfer: PayloadTransferUpdate) {
            if (id != endpoint) return
            val payload = filePayload
            if (state.value.mode == NexumNearbyMode.HOST &&
                transfer.payloadId != outgoingFileId) return
            if (state.value.mode == NexumNearbyMode.JOIN &&
                (payload == null || transfer.payloadId != payload.id)) return
            if (transfer.totalBytes > 0L) {
                val percent = (transfer.bytesTransferred * 100L / transfer.totalBytes).toInt()
                    .coerceIn(0, 100)
                update { it.copy(progressPercent = percent,
                    status = if (state.value.mode == NexumNearbyMode.HOST)
                        "Enviando copia…" else "Recibiendo copia…") }
            }
            when (transfer.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (state.value.mode == NexumNearbyMode.HOST) {
                        update { it.copy(progressPercent = 100, processing = false,
                            completed = true, status = "Envío terminado. El otro móvil debe confirmar la importación.") }
                    } else {
                        fileCompleted = true
                        checkStaging()
                    }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED ->
                    failure("Transferencia interrumpida. No se han reemplazado datos.")
                else -> Unit
            }
        }
    }

    /** File and BYTES payloads are NOT guaranteed to arrive in the same order. */
    private fun checkStaging() {
        val file = filePayload ?: return
        val meta = metadata ?: return
        if (!fileCompleted || stagingStarted || state.value.mode != NexumNearbyMode.JOIN) return
        if (meta.optLong("payloadId") != file.id) {
            failure("Los metadatos no corresponden al archivo recibido")
            return
        }
        val uri = file.asFile()?.asUri() ?: run {
            failure("No se puede abrir el archivo recibido")
            return
        }
        stagingStarted = true
        update { it.copy(processing = true, status = "Comprobando integridad y copia recibida…") }
        scope.launch {
            try {
                val input = requireNotNull(app.contentResolver.openInputStream(uri))
                receiverFile = archives.stageReceiver(input, meta.getString("sha256"),
                    meta.getLong("size"))
                update { it.copy(readyToRestore = true, processing = false, progressPercent = 100,
                    status = "Copia validada. Confirma si quieres reemplazar todos tus datos.") }
            } catch (error: Exception) {
                failure(error.localizedMessage ?: "La copia recibida no es válida")
            } finally {
                runCatching { app.contentResolver.delete(uri, null, null) }
            }
        }
    }

    suspend fun applyReceived(): String {
        require(state.value.mode == NexumNearbyMode.JOIN && state.value.readyToRestore)
        val prepared = requireNotNull(receiverFile)
        update { it.copy(processing = true, status = "Guardando copia preventiva y restaurando…") }
        return try {
            val recovery = archives.applyToReceiver(prepared)
            update { it.copy(processing = false, readyToRestore = false, completed = true,
                status = "Importación completa. Reinicia NEXUM para refrescar todos los ajustes.") }
            recovery
        } catch (error: Exception) {
            failure(error.localizedMessage ?: "No se pudieron restaurar los datos")
            throw error
        }
    }

    suspend fun restoreLastRecovery(): Boolean = archives.restoreLastRecovery()

    fun hasRecovery() = archives.recoveryFiles().isNotEmpty()

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        archives.cleanup(hostFile)
        archives.cleanup(receiverFile)
        hostFile = null
        receiverFile = null
        outgoingFileId = null
        endpoint = null
        metadata = null
        filePayload = null
        fileCompleted = false
        stagingStarted = false
        mutable.value = NexumNearbyState()
    }

    fun close() {
        stop()
        scope.cancel()
    }
}
