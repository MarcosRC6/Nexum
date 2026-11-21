package com.example.testario.data.local

import android.content.Context
import android.util.AtomicFile
import com.example.testario.data.model.MixedSessionSnapshot
import com.example.testario.domain.MixedSessionEngine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Additional independent save slot for mixed sessions. Never overwrites
 * the user's historical suspended Testario question-only session.
 */
class SavedMixedSessionStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val target = File(context.applicationContext.filesDir, FILE_NAME)
    private val atomic = AtomicFile(target)

    fun load(): MixedSessionSnapshot? = runCatching {
        val text = atomic.openRead().bufferedReader().use { it.readText() }
        validate(text)
    }.getOrNull()

    fun save(snapshot: MixedSessionSnapshot): Boolean = runCatching {
        MixedSessionEngine.validate(snapshot)
        val bytes = json.encodeToString(snapshot).toByteArray(Charsets.UTF_8)
        val stream = atomic.startWrite()
        try {
            stream.write(bytes)
            atomic.finishWrite(stream)
        } catch (failure: Exception) {
            atomic.failWrite(stream)
            throw failure
        }
    }.isSuccess

    fun clear(): Boolean = runCatching {
        atomic.delete()
        !target.exists() && !File(target.path + ".bak").exists() &&
            !File(target.path + ".new").exists()
    }.getOrDefault(false)

    fun exportJson(): String? = load()?.let { json.encodeToString(it) }

    fun validate(raw: String): MixedSessionSnapshot =
        json.decodeFromString<MixedSessionSnapshot>(raw).also(MixedSessionEngine::validate)

    fun importJson(raw: String?) {
        if (raw == null) check(clear()) { "No se pudo eliminar la sesión mixta previa." }
        else check(save(validate(raw))) { "No se pudo restaurar la sesión mixta." }
    }

    companion object { const val FILE_NAME = "nexum_pending_mixed_session.json" }
}
