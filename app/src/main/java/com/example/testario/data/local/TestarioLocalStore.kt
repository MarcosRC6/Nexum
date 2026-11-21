package com.example.testario.data.local

import android.content.Context
import android.util.AtomicFile
import com.example.testario.data.model.TestarioDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File


class TestarioLocalStore private constructor(context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "testario_database.json")
    private val atomicFile = AtomicFile(file)

    /** Data load failure is a locked recovery state, never an editable empty library. */
    private val _recoveryError = MutableStateFlow<String?>(null)
    val recoveryError: StateFlow<String?> = _recoveryError

    // Aquí vive la verdad absoluta de tu app
    private val _database = MutableStateFlow(loadDatabase())
    val database: StateFlow<TestarioDatabase> = _database

    private fun loadDatabase(): TestarioDatabase {
        return try {
            if (!file.exists() && !File("${file.absolutePath}.bak").exists()) {
                TestarioDatabase()
            } else {
                // 1. Leemos el texto crudo guardado en el dispositivo
                val rawText = atomicFile.openRead().bufferedReader().use { it.readText() }

                // 2. Pasamos el texto por el filtro de migraciones (actualiza datos viejos)
                val migratedText = MigrationManager.migrate(rawText)

                // 3. Convertimos el JSON seguro y actualizado a las clases de Kotlin
                val db = json.decodeFromString<TestarioDatabase>(migratedText)

                // 4. Si el gestor tuvo que alterar el JSON antiguo, guardamos los cambios
                // para que no tenga que volver a migrarlo en el siguiente inicio.
                if (rawText != migratedText) {
                    // Create an immutable pre-v4 snapshot BEFORE migrating to a new
                    // schema. This is local recovery material, not a substitute for
                    // the user's complete ZIP backup (which also contains media).
                    val oldVersion = org.json.JSONObject(rawText).optInt("version", 0)
                    if (oldVersion in 0..3) saveOriginalBeforeV4(rawText)
                    if (oldVersion in 0..4) saveOriginalBeforeV5(rawText)
                    if (oldVersion in 0..5) saveOriginalBeforeV6(rawText)
                    if (oldVersion in 0..6) saveOriginalBeforeV7(rawText)
                    writeDatabaseAtomically(db)
                }

                db
            }
        } catch (failure: Exception) {
            _recoveryError.value = "No se puede abrir la base de datos guardada: " +
                (failure.message ?: "formato incompatible o datos dañados") +
                ". NEXUM ha bloqueado las escrituras para proteger el archivo original."
            // No sobrescribimos silenciosamente una base corrupta o creada por una
            // versión futura. Conservamos una copia recuperable antes de arrancar
            // con un estado vacío en memoria.
            if (file.exists()) {
                runCatching {
                    val recoveryFile = File(
                        file.parentFile,
                        "testario_database_recovery_${System.currentTimeMillis()}.json"
                    )
                    file.copyTo(recoveryFile, overwrite = false)
                }
            }
            TestarioDatabase()
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()
    private val databaseStateGuard = Any()

    fun saveDatabase(newDb: TestarioDatabase) {
        check(_recoveryError.value == null) {
            "La base está en modo recuperación. Restaura un backup válido desde Ajustes."
        }
        synchronized(databaseStateGuard) {
            check(_recoveryError.value == null) {
                "La base está en modo recuperación; escritura de Biblioteca bloqueada."
            }
            _database.value = newDb
        }
        ioScope.launch {
            mutex.withLock {
                try {
                    // Varias acciones rápidas pueden encolar escrituras distintas.
                    // Persistimos siempre el estado más reciente, no la copia capturada
                    // cuando se lanzó esta coroutine, para impedir regresiones en disco.
                    val latestDb = _database.value
                    writeDatabaseAtomically(latestDb)
                } catch (e: Exception) {
                    // Never leave an apparently successful edit silently
                    // unsaved after an AtomicFile failure. Keep the in-memory
                    // state available for emergency ZIP export, block further
                    // library mutations and show the existing recovery dialog.
                    android.util.Log.e("NEXUM_Library", "Atomic database write failed", e)
                    _recoveryError.value =
                        "No se han podido guardar los últimos cambios de Biblioteca " +
                        "en el almacenamiento. Algunos cambios solo existen en memoria. " +
                        "No cierres NEXUM: desde Ajustes exporta una COPIA GLOBAL " +
                        "COMPLETA ZIP a otro almacenamiento y comprueba que se ha creado."
                }
            }
        }
    }

    /**
     * Commit a complete import in one AtomicFile transaction, not via the
     * fire-and-forget saveDatabase() queue. Reject a stale snapshot instead
     * of overwriting an edit made while import conflicts were being resolved.
     * A failed disk write leaves the previously published in-memory DB intact.
     */
    suspend fun saveDatabaseDurably(
        expected: TestarioDatabase,
        replacement: TestarioDatabase
    ) {
        check(_recoveryError.value == null) {
            "La base está en modo recuperación: importación bloqueada."
        }
        mutex.withLock {
            synchronized(databaseStateGuard) {
                check(_recoveryError.value == null) {
                    "La base está en modo recuperación: importación bloqueada."
                }
                check(_database.value === expected) {
                    "La biblioteca cambió mientras se preparaba el lote. Repite la importación."
                }
                writeDatabaseAtomically(replacement)
                _database.value = replacement
            }
        }
    }

    /**
     * The ONLY write allowed while in recovery mode: a validated user-selected
     * backup. Persist before unlocking, so failures cannot replace the old DB.
     */
    fun restoreValidatedRecovery(db: TestarioDatabase) {
        require(db.version == com.example.testario.data.model.DatabaseSchema.CURRENT_VERSION)
        synchronized(databaseStateGuard) {
            check(_recoveryError.value != null) { "No hay recuperación pendiente." }
            writeDatabaseAtomically(db)
            _database.value = db
            _recoveryError.value = null
        }
    }

    /** Exact, immutable pre-v7 snapshot before annotation migration. */
    private fun saveOriginalBeforeV7(originalJson: String) {
        val backup = File(file.parentFile, "nexum_before_v7_database.json")
        if (backup.isFile && backup.length() > 0) return
        val snapshot = AtomicFile(backup)
        val output = snapshot.startWrite()
        try {
            output.write(originalJson.toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(output)
        } catch (error: Exception) {
            snapshot.failWrite(output)
            throw error
        }
    }

    /** Preserve exact source bytes before v6 migration, never replace first backup. */
    private fun saveOriginalBeforeV6(originalJson: String) {
        val backup = File(file.parentFile, "nexum_before_v6_database.json")
        if (backup.isFile && backup.length() > 0) return
        val snapshot = AtomicFile(backup)
        val output = snapshot.startWrite()
        try {
            output.write(originalJson.toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(output)
        } catch (error: Exception) {
            snapshot.failWrite(output)
            throw error
        }
    }

    private fun saveOriginalBeforeV5(originalJson: String) {
        val backup = File(file.parentFile, "nexum_before_v5_database.json")
        if (backup.isFile && backup.length() > 0) return
        val snapshot = AtomicFile(backup)
        val output = snapshot.startWrite()
        try {
            output.write(originalJson.toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(output)
        } catch (error: Exception) {
            snapshot.failWrite(output)
            throw error
        }
    }

    /**
     * Preserve exact old bytes prior to v4 migration. Never replace the first
     * snapshot with already-migrated data. Keep it in app-private storage.
     */
    private fun saveOriginalBeforeV4(originalJson: String) {
        val backup = File(file.parentFile, "nexum_before_v4_database.json")
        if (backup.isFile && backup.length() > 0) return
        val snapshot = AtomicFile(backup)
        val output = snapshot.startWrite()
        try {
            output.write(originalJson.toByteArray(Charsets.UTF_8))
            snapshot.finishWrite(output)
        } catch (error: Exception) {
            snapshot.failWrite(output)
            throw error
        }
    }

    /** Escribe mediante AtomicFile para recuperar la versión previa si el proceso muere a mitad de escritura. */
    private fun writeDatabaseAtomically(db: TestarioDatabase) {
        val bytes = json.encodeToString(db).toByteArray(Charsets.UTF_8)
        val output = atomicFile.startWrite()
        try {
            output.write(bytes)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            throw error
        }
    }

    /**
     * Refresh the library from its durable local database without losing an asynchronous save.
     * Every normal import/edit publishes to _database first, then writes via the mutex. Write
     * that latest snapshot before reading; otherwise a pull immediately after an import could
     * load old bytes and silently roll back the new topics.
     *
     * Never replace a newer in-memory snapshot published while the disk read was in progress.
     * A corrupt disk read throws, leaving the in-memory database unchanged.
     * This is a local refresh, NOT a re-import or a cross-device synchronization.
     */
    suspend fun refreshDatabaseFromDisk(): TestarioDatabase = mutex.withLock {
        check(_recoveryError.value == null) {
            "La base está en modo recuperación y no debe sobrescribirse."
        }
        val snapshot = synchronized(databaseStateGuard) { _database.value }
        writeDatabaseAtomically(snapshot)
        val raw = atomicFile.openRead().bufferedReader().use { it.readText() }
        val migrated = MigrationManager.migrate(raw)
        val reloaded = json.decodeFromString<TestarioDatabase>(migrated)
        synchronized(databaseStateGuard) {
            if (_database.value === snapshot) _database.value = reloaded
            _database.value
        }
    }

    /** Await durable persistence before considering a cross-device restore complete. */
    suspend fun flushCurrentForSync() {
        check(_recoveryError.value == null) { "Recuperación pendiente: sincronización bloqueada." }
        mutex.withLock { writeDatabaseAtomically(_database.value) }
    }

    companion object {
        @Volatile
        private var INSTANCE: TestarioLocalStore? = null

        fun get(context: Context): TestarioLocalStore {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TestarioLocalStore(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}