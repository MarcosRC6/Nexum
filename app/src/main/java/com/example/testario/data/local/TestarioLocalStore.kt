package com.example.testario.data.local

import android.content.Context
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

    // Aquí vive la verdad absoluta de tu app
    private val _database = MutableStateFlow(loadDatabase())
    val database: StateFlow<TestarioDatabase> = _database

    private fun loadDatabase(): TestarioDatabase {
        return try {
            if (!file.exists()) {
                TestarioDatabase()
            } else {
                // 1. Leemos el texto crudo guardado en el dispositivo
                val rawText = file.readText()

                // 2. Pasamos el texto por el filtro de migraciones (actualiza datos viejos)
                val migratedText = MigrationManager.migrate(rawText)

                // 3. Convertimos el JSON seguro y actualizado a las clases de Kotlin
                val db = json.decodeFromString<TestarioDatabase>(migratedText)

                // 4. Si el gestor tuvo que alterar el JSON antiguo, guardamos los cambios
                // para que no tenga que volver a migrarlo en el siguiente inicio.
                if (rawText != migratedText) {
                    file.writeText(json.encodeToString(db))
                }

                db
            }
        } catch (_: Exception) {
            // Si el archivo está completamente corrupto o ilegible, arrancamos en limpio
            TestarioDatabase()
        }
    }

    private val mutex = kotlinx.coroutines.sync.Mutex()

    fun saveDatabase(newDb: TestarioDatabase) {
        _database.value = newDb
        ioScope.launch {
            mutex.withLock {
                try {
                    // Varias acciones rápidas pueden encolar escrituras distintas.
                    // Persistimos siempre el estado más reciente, no la copia capturada
                    // cuando se lanzó esta coroutine, para impedir regresiones en disco.
                    val latestDb = _database.value
                    file.writeText(json.encodeToString(latestDb))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
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