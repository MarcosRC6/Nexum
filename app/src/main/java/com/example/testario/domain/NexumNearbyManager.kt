package com.example.testario.domain

import android.content.Context
import android.os.Build
import com.example.planario.app.PlanarioDependencies
import com.example.testario.data.local.TestarioLocalStore
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

/** Display-only inventory: identical counts DO NOT prove identical data. */
data class NexumNearbySummary(
    val topics: Int, val questions: Int, val groups: Int,
    val planTopics: Int, val objectives: Int
) {
    fun describe(): String =
        "$topics temas · $questions preguntas · $groups grupos · " +
        "$planTopics temas de Plan · $objectives objetivos"
}

data class NexumNearbyState(
    val mode: NexumNearbyMode = NexumNearbyMode.NONE,
    val status: String = "Selecciona Enviar o Recibir.",
    val peers: List<NexumNearbyPeer> = emptyList(),
    val incomingPeer: NexumNearbyPeer? = null,
    val localSummary: NexumNearbySummary? = null,
    val peerSummary: NexumNearbySummary? = null,
    val awaitingPeerToSend: Boolean = false,
    val remoteRequestedToSend: Boolean = false,
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
    // Version 2 changes the negotiation protocol; don't pair with an older client.
    private val serviceId = app.packageName + ".nexum_transfer_v2"
    private val strategy = Strategy.P2P_POINT_TO_POINT
    val localName: String = "NEXUM · ${Build.MODEL}".take(64)
    private val mutable = MutableStateFlow(NexumNearbyState())
    val state = mutable.asStateFlow()

    private var endpoint: String? = null
    private var hostFile: NexumNearbyArchive.Prepared? = null
    private var outgoingFileId: Long? = null
    @Volatile private var closed = false
    private var receiverFile: NexumNearbyArchive.Prepared? = null
    private var filePayload: Payload? = null
    private var fileCompleted = false
    private var metadata: JSONObject? = null
    private var stagingStarted = false
    @Volatile private var applyingReceived = false

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
            status = "Creando conexión de sincronización…") }
        client.startAdvertising(localName, serviceId, connections,
            AdvertisingOptions.Builder().setStrategy(strategy).build())
            .addOnSuccessListener {
                update { it.copy(status = "Conexión creada. En el otro móvil pulsa Unirse.") }
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
                    status = "Conectados. Elige en cuál de los móviles prevalecerán los datos.") }
                announceSummary(id)
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

    /** Ask the other phone to be authoritative, with confirmation ON THAT PHONE. */
    fun requestPeerToSend() {
        val id = endpoint ?: return
        if (!state.value.connected || state.value.processing ||
            state.value.completed || state.value.readyToRestore ||
            outgoingFileId != null || filePayload != null || metadata != null) return
        update { it.copy(awaitingPeerToSend = true,
            status = "Esperando a que el otro móvil confirme que prevalecen sus datos…") }
        client.sendPayload(id, Payload.fromBytes(
            JSONObject().put("type", "NEXUM_REQUEST_SEND_V1")
                .toString().toByteArray(Charsets.UTF_8)))
            .addOnFailureListener {
                update { it.copy(awaitingPeerToSend = false) }
                failure(it.localizedMessage ?: "No se pudo solicitar la sincronización")
            }
    }

    fun acceptPeerRequestAndSend() {
        if (!state.value.remoteRequestedToSend) return
        update { it.copy(remoteRequestedToSend = false, awaitingPeerToSend = false) }
        send()
    }

    fun rejectPeerRequest() {
        val id = endpoint ?: return
        update { it.copy(remoteRequestedToSend = false) }
        client.sendPayload(id, Payload.fromBytes(
            JSONObject().put("type", "NEXUM_REQUEST_REJECTED_V1")
                .toString().toByteArray(Charsets.UTF_8)))
    }

    /**
     * Either connected Android can be the source. HOST/JOIN are only roles for
     * discovery. The source confirms; the receiver verifies and confirms
     * replacement separately. No unattended or automatic overwrite.
     */
    fun send() {
        val id = endpoint ?: return
        if (!state.value.connected || state.value.processing ||
            state.value.awaitingPeerToSend ||
            state.value.readyToRestore || state.value.completed ||
            outgoingFileId != null || filePayload != null || metadata != null) return
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

    private fun localSummary(): NexumNearbySummary {
        val db = TestarioLocalStore.get(app).database.value
        val plan = PlanarioDependencies.studyRepository
        return NexumNearbySummary(
            topics = db.topics.size,
            questions = db.topics.sumOf { it.questions.size },
            groups = db.groups.size,
            planTopics = plan.topics.value.size,
            objectives = plan.fixedObjectives.value.size
        )
    }

    private fun announceSummary(peerId: String) {
        scope.launch {
            try {
                val summary = localSummary()
                update { it.copy(localSummary = summary) }
                val control = JSONObject()
                    .put("type", "NEXUM_SUMMARY_V1")
                    .put("topics", summary.topics)
                    .put("questions", summary.questions)
                    .put("groups", summary.groups)
                    .put("planTopics", summary.planTopics)
                    .put("objectives", summary.objectives)
                client.sendPayload(peerId,
                    Payload.fromBytes(control.toString().toByteArray(Charsets.UTF_8)))
            } catch (error: Exception) {
                failure("No se pudo preparar el resumen local: ${error.message}")
            }
        }
    }

    private val payloads = object: PayloadCallback() {
        override fun onPayloadReceived(id: String, payload: Payload) {
            if (id != endpoint || !state.value.connected) return
            when (payload.type) {
                Payload.Type.BYTES -> {
                    try {
                        val body = payload.asBytes() ?: error("Faltan metadatos")
                        require(body.size <= 4096) { "Datos de control demasiado largos" }
                        val json = JSONObject(String(body, Charsets.UTF_8))
                        when (json.optString("type")) {
                            "NEXUM_SUMMARY_V1" -> {
                                fun field(name: String): Int = json.getInt(name).also {
                                    require(it in 0..10_000_000) { "Resumen remoto no válido." }
                                }
                                val summary = NexumNearbySummary(
                                    field("topics"), field("questions"), field("groups"),
                                    field("planTopics"), field("objectives")
                                )
                                update { it.copy(peerSummary = summary) }
                            }
                            "NEXUM_REQUEST_SEND_V1" -> {
                                require(!state.value.processing && !state.value.completed &&
                                    !state.value.readyToRestore && !state.value.awaitingPeerToSend &&
                                    outgoingFileId == null &&
                                    filePayload == null && metadata == null) {
                                    "La sincronización ya está ocupada."
                                }
                                update { it.copy(remoteRequestedToSend = true,
                                    status = "El otro móvil solicita que prevalezcan TUS datos. Confirma antes de enviar.") }
                            }
                            "NEXUM_REQUEST_REJECTED_V1" -> {
                                update { it.copy(awaitingPeerToSend = false,
                                    status = "El otro dispositivo ha rechazado el envío.") }
                            }
                            "NEXUM_BACKUP_V1" -> {
                                require(outgoingFileId == null && !state.value.completed) {
                                    "Dos dispositivos intentaron enviar a la vez. Reconecta y elige uno."
                                }
                                require(json.optLong("size") in 1..NexumNearbyArchive.MAX_COMPRESSED_BYTES)
                                require(Regex("[a-f0-9]{64}").matches(json.optString("sha256")))
                                require(metadata == null && !state.value.readyToRestore)
                                metadata = json
                                update { it.copy(processing = true,
                                    awaitingPeerToSend = false,
                                    status = "Recibiendo datos del dispositivo que prevalecerá…") }
                                checkStaging()
                            }
                            else -> error("Mensaje de sincronización no reconocido")
                        }
                    } catch (error: Exception) {
                        failure(error.message ?: "Datos de control no válidos")
                    }
                }
                Payload.Type.FILE -> {
                    if (outgoingFileId != null || filePayload != null || state.value.completed) {
                        failure("Transferencias simultáneas no permitidas"); return
                    }
                    filePayload = payload
                    update { it.copy(processing = true,
                        awaitingPeerToSend = false, status = "Recibiendo copia…") }
                    checkStaging()
                }
                else -> failure("Tipo de transferencia no compatible")
            }
        }
        override fun onPayloadTransferUpdate(id: String, transfer: PayloadTransferUpdate) {
            if (id != endpoint) return
            val payload = filePayload
            val outgoing = outgoingFileId != null && transfer.payloadId == outgoingFileId
            val incoming = payload != null && transfer.payloadId == payload.id
            if (!outgoing && !incoming) return
            if (transfer.totalBytes > 0L) {
                val percent = (transfer.bytesTransferred * 100L / transfer.totalBytes).toInt()
                    .coerceIn(0, 100)
                update { it.copy(progressPercent = percent,
                    status = if (outgoing) "Enviando copia…" else "Recibiendo copia…") }
            }
            when (transfer.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    if (outgoing) {
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
        if (!fileCompleted || stagingStarted || outgoingFileId != null) return
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
        require(state.value.readyToRestore && outgoingFileId == null)
        val prepared = requireNotNull(receiverFile)
        update { it.copy(processing = true, status = "Guardando copia preventiva y restaurando…") }
        applyingReceived = true
        return try {
            val recovery = archives.applyToReceiver(prepared)
            update { it.copy(processing = false, readyToRestore = false, completed = true,
                status = "Importación completa. Reinicia NEXUM para refrescar todos los ajustes.") }
            recovery
        } catch (error: Exception) {
            failure(error.localizedMessage ?: "No se pudieron restaurar los datos")
            throw error
        } finally {
            applyingReceived = false
            if (closed) archives.cleanup(receiverFile)
        }
    }

    suspend fun restoreLastRecovery(): Boolean = archives.restoreLastRecovery()

    fun hasRecovery() = archives.recoveryFiles().isNotEmpty()

    fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        archives.cleanup(hostFile)
        if (!applyingReceived) archives.cleanup(receiverFile)
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
        closed = true
        stop()
        scope.cancel()
    }
}
