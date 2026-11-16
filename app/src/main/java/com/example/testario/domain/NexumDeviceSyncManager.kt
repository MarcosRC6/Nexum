package com.example.testario.domain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import com.example.testario.data.local.TestarioLocalStore
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Explicit, conflict-safe shared-folder synchronization using Android's Storage
 * Access Framework. The selected folder may be backed by a cloud provider.
 * No background overwrite, plaintext passwords or server credentials.
 */
class NexumDeviceSyncManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("nexum_shared_folder_sync_v1", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val unified = UnifiedDataManager(context.applicationContext)

    val deviceId: String
        get() {
            val previous = prefs.getString("device_id", null)
            if (previous != null) return previous
            val newId = UUID.randomUUID().toString().replace("-", "").take(8)
            prefs.edit().putString("device_id", newId).apply()
            return newId
        }
    var deviceName: String
        get() = prefs.getString("device_name", null)
            ?: "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
        set(value) {
            require(value.isNotBlank())
            prefs.edit().putString("device_name", value.trim().take(35)).apply()
        }

    val connectedFolder: Uri?
        get() = prefs.getString("folder_uri", null)?.let(Uri::parse)

    fun connectFolder(uri: Uri) {
        resolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val dir = requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
            "No se pudo abrir la carpeta seleccionada"
        }
        require(dir.isDirectory && dir.canRead() && dir.canWrite()) {
            "La carpeta debe permitir lectura y escritura"
        }
        prefs.edit().putString("folder_uri", uri.toString()).apply()
    }

    fun disconnectFolder() { prefs.edit().remove("folder_uri").apply() }

    data class Snapshot(
        val uri: Uri,
        val filename: String,
        val sourceName: String,
        val sourceId: String,
        val dateUtc: Long,
        val size: Long
    )

    private fun directory(): DocumentFile {
        val uri = requireNotNull(connectedFolder) { "Selecciona una carpeta compartida primero" }
        return requireNotNull(DocumentFile.fromTreeUri(context, uri)) {
            "La carpeta compartida ya no está disponible"
        }.also { require(it.canRead() && it.isDirectory) { "Sin acceso a la carpeta compartida" } }
    }

    fun snapshots(): List<Snapshot> = directory().listFiles().mapNotNull { doc ->
        if (!doc.isFile) return@mapNotNull null
        val name = doc.name ?: return@mapNotNull null
        val parsed = NexumSyncFiles.parse(name) ?: return@mapNotNull null
        Snapshot(doc.uri, name, parsed.deviceName, parsed.deviceId, parsed.createdAt, doc.length())
    }.sortedWith(compareByDescending<Snapshot> { it.dateUtc }.thenByDescending { it.filename })

    /** Read remote copies only after an explicit selection; verify CRCs before returning them. */
    private fun verifyZip(stream: InputStream, expectedDevice: String?): Boolean {
        var manifest: JSONObject? = null
        var foundDb = false
        var foundPlan = false
        val seen = hashSetOf<String>()
        val buffer = ByteArray(32768)
        var consumed = 0L
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) { zip.closeEntry(); continue }
                require(seen.add(entry.name)) { "Entradas ZIP duplicadas" }
                val body = if (entry.name == "manifest.json") ByteArrayOutputStream() else null
                var entryBytes = 0L
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    consumed += n
                    entryBytes += n
                    require(consumed <= MAX_UNCOMPRESSED_BYTES) { "La copia excede el límite de tamaño" }
                    if (body != null) {
                        require(entryBytes <= 32768) { "Manifiesto demasiado grande" }
                        body.write(buffer, 0, n)
                    }
                }
                if (entry.name == "manifest.json") {
                    manifest = JSONObject(requireNotNull(body).toString(Charsets.UTF_8.name()))
                }
                if (entry.name == "testario_database.json") foundDb = true
                if (entry.name == "planario_backup.json") foundPlan = true
                zip.closeEntry()
            }
        }
        val meta = manifest ?: return false
        return foundDb && foundPlan &&
            meta.optString("format") == UnifiedDataManager.GLOBAL_FORMAT &&
            meta.optInt("version") == 1 &&
            (expectedDevice == null || meta.optString("syncDeviceId") == expectedDevice)
    }

    private fun requireFolderWrite(): DocumentFile = directory().also {
        require(it.canWrite()) { "No hay permiso para escribir en la carpeta" }
    }

    /** Publishes a NEW immutable snapshot; never replaces another device's file. */
    fun publishThisDevice(): Snapshot {
        val dir = requireFolderWrite()
        val id = deviceId
        val filename = NexumSyncFiles.name(
            id, deviceName, LocalDateTime.now(ZoneOffset.UTC)
        )
        require(dir.findFile(filename) == null) {
            "Ya existe una copia de ese segundo; inténtalo nuevamente"
        }
        val file = requireNotNull(dir.createFile("application/zip", filename)) {
            "No se pudo crear la copia en la carpeta"
        }
        try {
            val output = requireNotNull(resolver.openOutputStream(file.uri, "w")) {
                "No se pudo abrir la copia para escritura"
            }
            output.use { stream ->
                unified.exportGlobalBackup(LimitedOutput(stream), syncDeviceId = id)
            }
            val verified = requireNotNull(resolver.openInputStream(file.uri))
                .use { verifyZip(it, id) }
            require(verified) { "La copia compartida no pudo verificarse" }
        } catch (t: Exception) {
            runCatching { file.delete() }
            throw t
        }
        val snapshot = requireNotNull(snapshots().firstOrNull { it.uri == file.uri }) {
            "La copia creada no aparece en la carpeta. Comprueba la sincronización del proveedor"
        }
        // Keep at most three complete copies from THIS device.
        snapshots().filter { it.sourceId == id }.drop(3).forEach { old ->
            runCatching {
                if (old.uri != snapshot.uri) DocumentFile.fromSingleUri(context, old.uri)?.delete()
            }
        }
        return snapshot
    }

    /** The caller MUST display a confirmation identifying the source and destructive scope. */
    suspend fun useRemoteDevice(snapshot: Snapshot): String {
        val dir = directory()
        require(dir.listFiles().any { it.uri == snapshot.uri && it.name == snapshot.filename }) {
            "La copia compartida ha cambiado; vuelve a actualizar la lista"
        }
        val read = requireNotNull(resolver.openInputStream(snapshot.uri)) {
            "No se pudo abrir la copia del otro dispositivo"
        }
        val bytes = read.use { limitedBytes(it, MAX_TRANSFER_BYTES) }
        require(verifyZip(ByteArrayInputStream(bytes), snapshot.sourceId)) {
            "La copia seleccionada no es válida o está incompleta"
        }
        val recovery = createVerifiedRecovery()
        val success = runCatching {
            val restored = unified.restoreGlobalBackup(bytes)
            if (restored) TestarioLocalStore.get(context).flushCurrentForSync()
            restored
        }.getOrDefault(false)
        if (!success) {
            val restored = runCatching {
                val ok = unified.restoreGlobalBackup(recovery.readBytes())
                if (ok) TestarioLocalStore.get(context).flushCurrentForSync()
                ok
            }.getOrDefault(false)
            error(
                if (restored) "Error al recibir los datos; se ha restaurado la copia previa."
                else "Error al recibir los datos; guarda la copia de recuperación antes de continuar."
            )
        }
        // Keep two recent recovery archives in app-private storage.
        recoveryFiles().drop(2).forEach { runCatching { it.delete() } }
        return recovery.name
    }

    /** Never expose a partial recovery as an eligible restore point. */
    private fun createVerifiedRecovery(): File {
        val dest = File(
            context.filesDir, "NEXUM_RECUPERACION_SINCRONIZACION_${System.currentTimeMillis()}.zip"
        )
        val temporary = File(context.filesDir, "${dest.name}.partial")
        try {
            temporary.outputStream().use { unified.exportGlobalBackup(LimitedOutput(it)) }
            require(temporary.inputStream().use { verifyZip(it, null) }) {
                "La copia de seguridad local es incompleta o inválida"
            }
            require(temporary.renameTo(dest)) {
                "No se pudo guardar la copia de recuperación verificada"
            }
            return dest
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    fun recoveryFiles(): List<File> = context.filesDir.listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("NEXUM_RECUPERACION_SINCRONIZACION_") && it.name.endsWith(".zip") }
        .sortedByDescending { it.lastModified() }

    suspend fun restoreRecoveryArchive(file: File): Boolean {
        require(recoveryFiles().any { it.absolutePath == file.absolutePath })
        val bytes = file.inputStream().use { limitedBytes(it, MAX_TRANSFER_BYTES) }
        require(verifyZip(ByteArrayInputStream(bytes), null)) { "La copia de recuperación no es válida" }

        // Undoing a synchronization is also destructive: protect CURRENT data first.
        val before = createVerifiedRecovery()
        val ok = runCatching {
            val result = unified.restoreGlobalBackup(bytes)
            if (result) TestarioLocalStore.get(context).flushCurrentForSync()
            result
        }.getOrDefault(false)
        if (!ok) {
            runCatching {
                if (unified.restoreGlobalBackup(before.readBytes()))
                    TestarioLocalStore.get(context).flushCurrentForSync()
            }
        }
        recoveryFiles().drop(2).forEach { runCatching { it.delete() } }
        return ok
    }

    private class LimitedOutput(target: OutputStream) : FilterOutputStream(target) {
        private var count = 0L
        override fun write(b: Int) {
            require(++count <= MAX_TRANSFER_BYTES) { "La copia supera los 128 MB permitidos" }
            out.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            count += len
            require(count <= MAX_TRANSFER_BYTES) { "La copia supera los 128 MB permitidos" }
            out.write(b, off, len)
        }
    }

    private fun limitedBytes(input: InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(32768)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            require(total <= max) { "Archivo demasiado grande para importarlo de forma segura" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        // The legacy restore API consumes a byte array; avoid an unbounded heap allocation.
        const val MAX_TRANSFER_BYTES = 128L * 1024L * 1024L
        private const val MAX_UNCOMPRESSED_BYTES = 768L * 1024L * 1024L
    }
}
