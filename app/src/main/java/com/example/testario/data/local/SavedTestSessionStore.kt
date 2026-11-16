package com.example.testario.data.local

import android.content.Context
import android.util.AtomicFile
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredTestAnswer(
    val question: Question,
    val selectedAnswerId: String? = null,
    val corrected: Boolean = false,
    val answersRevealed: Boolean = true,
    val timeSpentSeconds: Long = 0L
)

@Serializable
data class SavedTestSession(
    val sessionId: String,
    val savedAt: Long,
    val mode: TestMode,
    val questionIndex: Int,
    val answers: List<StoredTestAnswer>,
    val elapsedSeconds: Long,
    val remainingSeconds: Int? = null,
    val pointsCorrect: Double = 1.0,
    val penaltyWrong: Double = -0.25
)

/** Independent AtomicFile: saving a suspended test never rewrites the question bank. */
class SavedTestSessionStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val atomic = AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))

    fun load(): SavedTestSession? = runCatching {
        val raw = atomic.openRead().bufferedReader().use { it.readText() }
        decode(raw)
    }.getOrNull()

    fun save(value: SavedTestSession): Boolean = runCatching {
        val data = json.encodeToString(value).toByteArray(Charsets.UTF_8)
        val output = atomic.startWrite()
        try {
            output.write(data)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }.isSuccess

    fun clear() {
        atomic.delete()
    }

    fun exportJson(): String? =
        load()?.let { json.encodeToString(it) }

    fun importJson(raw: String?) {
        if (raw == null) {
            clear()
        } else {
            val restored = decode(raw)
            require(save(restored)) { "No se pudo restaurar el test suspendido" }
        }
    }

    fun validate(raw: String): SavedTestSession = decode(raw)

    private fun decode(raw: String): SavedTestSession {
        val value = json.decodeFromString<SavedTestSession>(raw)
        require(value.sessionId.isNotBlank() && value.answers.isNotEmpty())
        require(value.questionIndex in value.answers.indices)
        require(value.elapsedSeconds >= 0 && (value.remainingSeconds == null || value.remainingSeconds >= 0))
        require(value.answers.all { it.question.answers.isNotEmpty() })
        return value
    }

    companion object { const val FILE_NAME = "nexum_pending_test_session.json" }
}
