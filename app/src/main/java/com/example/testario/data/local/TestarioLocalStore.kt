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
                    writeDatabaseAtomically(db)
                }

                db
            }
        } catch (_: Exception) {
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
        synchronized(databaseStateGuard) { _database.value = newDb }
        ioScope.launch {
            mutex.withLock {
                try {
                    // Varias acciones rápidas pueden encolar escrituras distintas.
                    // Persistimos siempre el estado más reciente, no la copia capturada
                    // cuando se lanzó esta coroutine, para impedir regresiones en disco.
                    val latestDb = _database.value
                    writeDatabaseAtomically(latestDb)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
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