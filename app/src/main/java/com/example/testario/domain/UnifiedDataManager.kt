package com.example.testario.domain

import android.content.Context
import com.example.planario.app.PlanarioDependencies
import com.example.planario.data.local.StudyLocalDataSource
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.local.MigrationManager
import com.example.testario.data.local.QuickTestLinkManager
import com.example.testario.data.local.TestarioLocalStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class UnifiedDataManager(private val context: Context) {
    enum class ImportKind { GLOBAL_BACKUP, TESTARIO_BACKUP, PLAN_BACKUP, LIBRARY_DATA, UNKNOWN }

    data class Detection(val kind: ImportKind, val text: String? = null)

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val localStore = TestarioLocalStore.get(context)
    private val imagesDir = File(context.filesDir, "question_images")

    fun detect(bytes: ByteArray, fileName: String?): Detection {
        val isZip = fileName?.endsWith(".zip", true) == true || bytes.take(2) == listOf(0x50.toByte(), 0x4b.toByte())
        if (isZip) {
            val entries = readZipTextEntries(bytes, setOf("manifest.json", "testario_database.json", "planario_backup.json"))
            val manifest = entries["manifest.json"]
            if (manifest != null) {
                val format = runCatching { JSONObject(manifest).optString("format") }.getOrDefault("")
                if (format == GLOBAL_FORMAT) return Detection(ImportKind.GLOBAL_BACKUP)
            }
            val testarioDatabase = entries["testario_database.json"]
            if (testarioDatabase != null) {
                val normalizedName = fileName.orEmpty().lowercase()
                val backupByName = "backup" in normalizedName || "copia" in normalizedName
                val backupByContent = looksLikeTestarioBackup(testarioDatabase)
                return Detection(
                    if (backupByName || backupByContent) ImportKind.TESTARIO_BACKUP
                    else ImportKind.LIBRARY_DATA
                )
            }
            return Detection(ImportKind.UNKNOWN)
        }

        val text = bytes.toString(Charsets.UTF_8)
        val trimmed = text.trimStart()
        if (trimmed.startsWith("{")) {
            val root = runCatching { JSONObject(text) }.getOrNull()
            if (root != null) {
                val looksPlan = root.has("fixedObjectives") && root.has("studyDays") && root.has("settings")
                if (looksPlan) return Detection(ImportKind.PLAN_BACKUP, text)
                if (root.has("topics")) return Detection(ImportKind.LIBRARY_DATA, text)
            }
        }
        if (trimmed.startsWith("[") || text.contains(';')) {
            return Detection(ImportKind.LIBRARY_DATA, text)
        }
        return Detection(ImportKind.UNKNOWN, text)
    }

    fun restoreTestarioBackup(bytes: ByteArray): Boolean {
        val raw = runCatching {
            readZipTextEntries(bytes, setOf("testario_database.json"))["testario_database.json"]
        }.getOrNull() ?: return false

        // Validamos completamente la base antes de escribir una sola imagen o dato.
        runCatching {
            val migrated = MigrationManager.migrate(raw)
            json.decodeFromString<TestarioDatabase>(migrated)
        }.getOrElse { return false }

        if (!extractQuestionImages(bytes)) return false

        return runCatching {
            ImportManager(context, localStore).restoreBackup(raw)
            true
        }.getOrDefault(false)
    }

    fun exportGlobalBackup(outputStream: OutputStream) {
        val databaseJson = json.encodeToString(localStore.database.value)
        val planJson = PlanarioDependencies.studyRepository.exportBackupJson()
        val quickLinksJson = QuickTestLinkManager(context).exportJson()
        val settingsJson = exportTestarioSettings()

        ZipOutputStream(outputStream).use { zos ->
            writeText(zos, "manifest.json", JSONObject().apply {
                put("format", GLOBAL_FORMAT)
                put("version", 1)
                put("createdAt", System.currentTimeMillis())
            }.toString(2))
            writeText(zos, "testario_database.json", databaseJson)
            writeText(zos, "planario_backup.json", planJson)
            writeText(zos, "quick_test_links.json", quickLinksJson)
            writeText(zos, "testario_settings.json", settingsJson)

            if (imagesDir.exists()) {
                imagesDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                    zos.putNextEntry(ZipEntry("question_images/${file.name}"))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    fun restoreGlobalBackup(bytes: ByteArray): Boolean {
        val wanted = setOf(
            "manifest.json",
            "testario_database.json",
            "planario_backup.json",
            "quick_test_links.json",
            "testario_settings.json"
        )
        val textEntries = runCatching { readZipTextEntries(bytes, wanted) }.getOrElse { return false }

        val manifest = textEntries["manifest.json"] ?: return false
        val manifestJson = runCatching { JSONObject(manifest) }.getOrNull() ?: return false
        if (manifestJson.optString("format") != GLOBAL_FORMAT) return false
        if (manifestJson.optInt("version", 0) !in 1..1) return false

        val dbRaw = textEntries["testario_database.json"] ?: return false
        runCatching {
            val migrated = MigrationManager.migrate(dbRaw)
            json.decodeFromString<TestarioDatabase>(migrated)
        }.getOrElse { return false }

        val planRaw = textEntries["planario_backup.json"]
        if (planRaw != null && StudyLocalDataSource(context).importFromJson(planRaw) == null) {
            return false
        }

        val quickLinksRaw = textEntries["quick_test_links.json"]
        if (quickLinksRaw != null && runCatching { JSONObject(quickLinksRaw) }.isFailure) {
            return false
        }

        val settingsRaw = textEntries["testario_settings.json"]
        if (settingsRaw != null && runCatching { JSONObject(settingsRaw) }.isFailure) {
            return false
        }

        // Solo después de validar todos los componentes empezamos a modificar el estado.
        if (!extractQuestionImages(bytes)) return false

        // Una sesión antigua no debe sobrevivir a una restauración de Planario.
        PlanarioDependencies.studyTimerController.discardStudyTimer()
        PlanarioDependencies.studyRepository.clearActiveStudyTimerTracking()
        PlanarioDependencies.stopStudyTimerForegroundService()

        runCatching { ImportManager(context, localStore).restoreBackup(dbRaw) }.getOrElse { return false }

        if (planRaw != null && !PlanarioDependencies.studyRepository.importBackupJson(planRaw)) {
            return false
        }
        if (quickLinksRaw != null) {
            runCatching { QuickTestLinkManager(context).importJson(quickLinksRaw) }.getOrElse { return false }
        }
        if (settingsRaw != null) {
            runCatching { restoreTestarioSettings(settingsRaw) }.getOrElse { return false }
        }
        return true
    }

    /**
     * Extrae las imágenes en dos fases: primero valida y descomprime todo en
     * caché; únicamente después modifica los ficheros en uso. Si falla una
     * escritura, restaura las imágenes anteriores.
     */
    private fun extractQuestionImages(bytes: ByteArray): Boolean {
        val operationId = java.util.UUID.randomUUID().toString()
        val stageDir = File(context.cacheDir, "nexum-images-stage-$" + "{operationId}")
        val rollbackDir = File(context.cacheDir, "nexum-images-rollback-$" + "{operationId}")
        val installed = mutableListOf<File>()
        val names = linkedSetOf<String>()

        try {
            require(stageDir.mkdirs() && rollbackDir.mkdirs()) {
                "No se pudo preparar el espacio temporal para restaurar las imágenes."
            }

            var totalBytes = 0L
            ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(8192)
                while (entry != null) {
                    if (entry.name.startsWith("question_images/") && !entry.isDirectory) {
                        val name = entry.name.removePrefix("question_images/")
                        require(name.isNotBlank() && !name.contains('/') && !name.contains('\\')) {
                            "Nombre de imagen no válido en la copia de seguridad."
                        }
                        require(names.add(name)) { "La copia contiene una imagen duplicada." }

                        val stagedFile = File(stageDir, name)
                        var fileBytes = 0L
                        stagedFile.outputStream().use { output ->
                            while (true) {
                                val count = zis.read(buffer)
                                if (count < 0) break
                                fileBytes += count
                                totalBytes += count
                                require(fileBytes <= MAX_IMPORT_IMAGE_BYTES &&
                                    totalBytes <= MAX_IMPORT_IMAGE_TOTAL_BYTES) {
                                    "La copia excede el tamaño máximo de imágenes permitido."
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            require(imagesDir.isDirectory || imagesDir.mkdirs()) {
                "No se pudo crear la carpeta de imágenes."
            }
            val canonicalImages = imagesDir.canonicalPath + File.separator
            names.forEach { name ->
                val target = File(imagesDir, name)
                require(target.canonicalPath.startsWith(canonicalImages)) {
                    "Ruta de imagen fuera del almacenamiento permitido."
                }
                if (target.exists()) {
                    target.copyTo(File(rollbackDir, name), overwrite = false)
                }
                // Registrar antes de copiar, pues una copia incompleta también debe revertirse.
                installed.add(target)
                File(stageDir, name).copyTo(target, overwrite = true)
            }
            return true
        } catch (_: Exception) {
            // No dejar imágenes nuevas/incompletas ni sobrescribir las anteriores.
            installed.asReversed().forEach { target ->
                val previous = File(rollbackDir, target.name)
                runCatching {
                    if (previous.isFile) previous.copyTo(target, overwrite = true)
                    else target.delete()
                }
            }
            return false
        } finally {
            stageDir.deleteRecursively()
            rollbackDir.deleteRecursively()
        }
    }

    private companion object {
        const val MAX_IMPORT_IMAGE_BYTES = 64L * 1024L * 1024L
        const val MAX_IMPORT_IMAGE_TOTAL_BYTES = 512L * 1024L * 1024L
    }

    private fun exportTestarioSettings(): String {
        val prefs = context.getSharedPreferences("testario_settings", Context.MODE_PRIVATE)
        val root = JSONObject()
        prefs.all.forEach { (key, value) -> root.put(key, value) }
        return root.toString(2)
    }

    private fun restoreTestarioSettings(raw: String) {
        val root = JSONObject(raw)
        val prefs = context.getSharedPreferences("testario_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        root.keys().forEach { key ->
            when (val value = root.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Double -> if (key == "ui_scale") editor.putFloat(key, value.toFloat()) else editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.apply()
    }

    private fun looksLikeTestarioBackup(raw: String): Boolean {
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("testsDone", 0) > 0) return@runCatching true
            if (root.optInt("totalTrainingMinutes", 0) > 0) return@runCatching true
            if ((root.optJSONArray("testHistory")?.length() ?: 0) > 0) return@runCatching true

            val topics = root.optJSONArray("topics") ?: return@runCatching false
            for (i in 0 until topics.length()) {
                val topic = topics.optJSONObject(i) ?: continue
                if (topic.optBoolean("isPinned", false)) return@runCatching true
                val questions = topic.optJSONArray("questions") ?: continue
                for (j in 0 until questions.length()) {
                    val question = questions.optJSONObject(j) ?: continue
                    if (question.optBoolean("favorite", false)) return@runCatching true
                    val stats = question.optJSONObject("stats") ?: continue
                    if (stats.optInt("timesAnswered", 0) > 0 ||
                        stats.optInt("timesCorrect", 0) > 0 ||
                        stats.optInt("timesWrong", 0) > 0 ||
                        stats.optInt("timesBlank", 0) > 0 ||
                        stats.optBoolean("inFailures", false) ||
                        stats.optLong("totalTimeSpentSeconds", 0L) > 0L
                    ) return@runCatching true
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun writeText(zos: ZipOutputStream, name: String, text: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(text.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun readZipTextEntries(bytes: ByteArray, wanted: Set<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name in wanted && !entry.isDirectory) {
                    result[entry.name] = String(zis.readBytes(), Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }

    companion object {
        const val GLOBAL_FORMAT = "testario_planario_global"
    }
}
