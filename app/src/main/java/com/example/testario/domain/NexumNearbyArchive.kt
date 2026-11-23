package com.example.testario.domain

import android.content.Context
import com.example.testario.data.local.TestarioLocalStore
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Never applies data during transfer. Validate the complete ZIP first; before
 * importing, create a separate verified recovery snapshot in private storage.
 */
class NexumNearbyArchive(private val context: Context) {
    private val unified = UnifiedDataManager(context.applicationContext)
    private val prefix = "NEXUM_RECUPERACION_DISPOSITIVOS_"
    private val staging = File(context.cacheDir, "nexum-nearby-sync").apply { mkdirs() }

    data class Prepared(val file: File, val sha256: String, val size: Long)

    fun prepareHostArchive(): Prepared {
        val file = File(staging, "host-${UUID.randomUUID()}.zip")
        return try {
            file.outputStream().use { unified.exportGlobalBackup(BoundedOutputStream(it)) }
            requireValid(file)
            Prepared(file, digest(file), file.length())
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun stageReceiver(input: InputStream, expectedSha256: String, expectedSize: Long): Prepared {
        require(expectedSize in 1..MAX_COMPRESSED_BYTES)
        require(Regex("[a-f0-9]{64}").matches(expectedSha256))
        val file = File(staging, "received-${UUID.randomUUID()}.zip")
        return try {
            input.use { source ->
                file.outputStream().use { target ->
                    val md = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(32768)
                    var size = 0L
                    while (true) {
                        val n = source.read(buffer)
                        if (n < 0) break
                        size += n
                        require(size <= MAX_COMPRESSED_BYTES) { "La copia es demasiado grande" }
                        md.update(buffer, 0, n)
                        target.write(buffer, 0, n)
                    }
                    require(size == expectedSize) { "Tamaño recibido diferente del enviado" }
                    require(md.digest().hex() == expectedSha256) { "La integridad SHA-256 no coincide" }
                }
            }
            requireValid(file)
            Prepared(file, expectedSha256, expectedSize)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    /**
     * Manual global restore uses the same verified, reversible transaction
     * coordinator as Nearby reception. A failure retains a durable recovery
     * ZIP in private storage instead of silently leaving mixed data.
     */
    /** Legacy Testario-only restore receives the SAME pre-operation rescue
     * snapshot as manual global restore. Planario is left unchanged on success.
     * If the legacy reader fails after copying images, replay the full verified
     * backup to recover the old Library and every image reference.
     */
    suspend fun applyUserSelectedTestarioBackup(bytes: ByteArray): String =
        withContext(Dispatchers.IO + NonCancellable) {
            require(PlanarioDependencies.studyTimerController.state.value.studyTimerState ==
                StudyTimerState.STOPPED) {
                "Guarda el cronómetro antes de restaurar la biblioteca."
            }
            val recovery = makeVerifiedRecovery()
            val applied = runCatching {
                val ok = unified.restoreTestarioBackup(bytes)
                if (ok) TestarioLocalStore.get(context).flushCurrentForSync()
                ok
            }.getOrDefault(false)
            if (!applied) {
                val restored = runCatching {
                    val ok = unified.restoreGlobalBackup(readLimited(recovery))
                    if (ok) TestarioLocalStore.get(context).flushCurrentForSync()
                    ok
                }.getOrDefault(false)
                error(if (restored) "La restauración falló; se recuperaron los datos previos."
                    else "La restauración falló; se conserva la copia preventiva. No elimines datos.")
            }
            recoveryFiles().drop(3).forEach { runCatching { it.delete() } }
            recovery.name
        }

    suspend fun applyUserSelectedGlobalBackup(bytes: ByteArray): String {
        val expected = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
        val prepared = stageReceiver(ByteArrayInputStream(bytes), expected, bytes.size.toLong())
        return try {
            applyToReceiver(prepared).also { toast("Copia restaurada. Reinicia NEXUM para ver todos los cambios.") }
        } catch (error: Exception) {
            toast(error.message ?: "La restauración ha fallado.")
            throw error
        } finally {
            cleanup(prepared)
        }
    }

    // El diálogo de Ajustes se pierde si la pantalla se recompone durante la restauración.
    private fun toast(text: String) = android.os.Handler(android.os.Looper.getMainLooper()).post {
        android.widget.Toast.makeText(context.applicationContext, text, android.widget.Toast.LENGTH_LONG).show()
    }

    suspend fun applyToReceiver(archive: Prepared): String = withContext(Dispatchers.IO + NonCancellable) {
        require(PlanarioDependencies.studyTimerController.state.value.studyTimerState ==
            StudyTimerState.STOPPED) {
            "Guarda y termina el cronómetro (también si está pausado) antes de sustituir los datos."
        }
        require(archive.file.exists() && archive.file.length() == archive.size)
        require(digest(archive.file) == archive.sha256)
        requireValid(archive.file)
        val recovery = makeVerifiedRecovery()
        UnifiedDataManager.lastRestoreFailure = null
        val result = runCatching {
            val bytes = readLimited(archive.file)
            val ok = unified.restoreGlobalBackup(bytes)
            if (ok) TestarioLocalStore.get(context).flushCurrentForSync()
            ok
        }.onFailure {
            UnifiedDataManager.lastRestoreFailure = "${it.javaClass.simpleName}: ${it.message}"
        }.getOrDefault(false)
        if (!result) {
            val reason = UnifiedDataManager.lastRestoreFailure ?: "motivo desconocido"
            val undone = runCatching {
                val ok = unified.restoreGlobalBackup(readLimited(recovery))
                if (ok) TestarioLocalStore.get(context).flushCurrentForSync()
                ok
            }.getOrDefault(false)
            error((if (undone) "Falló la restauración ($reason): recuperado el estado anterior."
                else "Falló la restauración ($reason). Se conserva una copia de recuperación; no borres los datos."))
        }
        recoveryFiles().drop(3).forEach { runCatching { it.delete() } }
        recovery.name
    }

    suspend fun restoreLastRecovery(): Boolean = withContext(Dispatchers.IO + NonCancellable) {
        require(PlanarioDependencies.studyTimerController.state.value.studyTimerState ==
            StudyTimerState.STOPPED) {
            "Guarda y termina el cronómetro antes de recuperar otra copia."
        }
        val target = recoveryFiles().firstOrNull() ?: return@withContext false
        requireValid(target)
        // Undo is destructive too: keep a separate previous-state snapshot.
        val safety = makeVerifiedRecovery()
        val ok = runCatching {
            val done = unified.restoreGlobalBackup(readLimited(target))
            if (done) TestarioLocalStore.get(context).flushCurrentForSync()
            done
        }.getOrDefault(false)
        if (!ok) {
            runCatching {
                if (unified.restoreGlobalBackup(readLimited(safety)))
                    TestarioLocalStore.get(context).flushCurrentForSync()
            }
        }
        ok
    }

    fun recoveryFiles(): List<File> =
        context.filesDir.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith(prefix) && it.name.endsWith(".zip") }
            .sortedByDescending { it.lastModified() }

    fun cleanup(prepared: Prepared?) {
        prepared?.file?.takeIf { it.parentFile == staging }?.delete()
    }

    private fun makeVerifiedRecovery(): File {
        val target = File(context.filesDir, "${prefix}${System.currentTimeMillis()}_${UUID.randomUUID()}.zip")
        val partial = File(context.filesDir, "${target.name}.part")
        try {
            partial.outputStream().use {
                unified.exportGlobalBackup(
                    BoundedOutputStream(it), privateRescueOnly = true
                )
            }
            requireValid(partial)
            require(partial.renameTo(target)) { "No se pudo finalizar la copia preventiva" }
            return target
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    private fun readLimited(file: File): ByteArray {
        require(file.length() in 1..MAX_COMPRESSED_BYTES)
        return file.readBytes()
    }

    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val bytes = ByteArray(32768)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                md.update(bytes, 0, n)
            }
        }
        return md.digest().hex()
    }

    /** CRC verification and presence of all required parts; never uses ZIP entry paths. */
    private fun requireValid(file: File) {
        require(file.length() in 1..MAX_COMPRESSED_BYTES) { "Copia incompleta o demasiado grande" }
        var manifest: JSONObject? = null
        var database = false
        var plan = false
        var total = 0L
        val seen = hashSetOf<String>()
        val buffer = ByteArray(32768)
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    require(seen.add(entry.name)) { "ZIP con entradas duplicadas" }
                    var size = 0L
                    val body = if (entry.name == "manifest.json") ByteArrayOutputStream() else null
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        total += n
                        size += n
                        require(total <= MAX_UNCOMPRESSED_BYTES) { "ZIP descomprimido demasiado grande" }
                        if (body != null) {
                            require(size <= 32768L) { "Manifiesto excesivo" }
                            body.write(buffer, 0, n)
                        }
                    }
                    if (entry.name == "manifest.json")
                        manifest = JSONObject(requireNotNull(body).toString(Charsets.UTF_8.name()))
                    if (entry.name == "testario_database.json") database = true
                    if (entry.name == "planario_backup.json") plan = true
                }
                zip.closeEntry()
            }
        }
        require(database && plan && manifest?.optString("format") == UnifiedDataManager.GLOBAL_FORMAT &&
            manifest?.optInt("version") == 1) { "No es una copia completa válida de NEXUM" }
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class BoundedOutputStream(target: OutputStream): FilterOutputStream(target) {
        private var size = 0L
        override fun write(b: Int) {
            require(++size <= MAX_COMPRESSED_BYTES) { "Copia mayor de 128 MB" }
            out.write(b)
        }
        override fun write(bytes: ByteArray, off: Int, len: Int) {
            size += len
            require(size <= MAX_COMPRESSED_BYTES) { "Copia mayor de 128 MB" }
            out.write(bytes, off, len)
        }
    }

    companion object {
        const val MAX_COMPRESSED_BYTES = 128L * 1024L * 1024L
        private const val MAX_UNCOMPRESSED_BYTES = 768L * 1024L * 1024L
    }
}
