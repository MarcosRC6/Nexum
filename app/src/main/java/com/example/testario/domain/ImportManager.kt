package com.example.testario.domain

import android.content.Context
import com.example.testario.data.local.MigrationManager
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.Answer
import com.example.testario.data.model.DatabaseSchema
import com.example.testario.data.model.DuplicateTopicAction
import com.example.testario.data.model.ImportFileType
import com.example.testario.data.model.ImportPreview
import com.example.testario.data.model.ParsedDelimitedQuestion
import com.example.testario.data.model.Question
import com.example.testario.data.model.SimpleTopicJson
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic
import com.example.testario.data.model.StudyExercise
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Result of a single topic operation. Reports empty merges explicitly instead of
 * claiming a full ZIP was imported when some topics were silently skipped.
 */
data class ImportTopicOutcome(
    val created: Boolean,
    val merged: Boolean,
    val addedQuestions: Int,
    val duplicateQuestions: Int,
    val destinationName: String,
    val addedExercises: Int = 0,
    val duplicateExercises: Int = 0
)

class ImportManager(private val context: Context, private val localStore: TestarioLocalStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val imagesDir = File(context.filesDir, "question_images")

    // Las imágenes de un ZIP permanecen en caché hasta que se confirma la operación.
    // Leer una vista previa o cancelar el diálogo nunca toca imágenes del usuario.
    private var pendingImageStage: File? = null

    @Synchronized
    fun extractPayload(inputStream: InputStream, fileName: String?): String {
        pendingImageStage?.deleteRecursively()
        pendingImageStage = null
        // Android OpenDocument puede devolver un URI opaco sin extensión.
        // Detectamos el ZIP por su cabecera además del nombre visible.
        val buffered = inputStream.buffered()
        buffered.mark(4)
        val signature = ByteArray(4)
        val bytesRead = buffered.read(signature)
        buffered.reset()
        val hasZipSignature = bytesRead == 4 &&
            signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte() &&
            (signature[2] == 0x03.toByte() || signature[2] == 0x05.toByte() ||
                signature[2] == 0x07.toByte())
        if (!hasZipSignature && fileName?.endsWith(".zip", ignoreCase = true) != true) {
            return buffered.bufferedReader().use { it.readText() }
        }

        val stage = File(context.cacheDir, "nexum-import-${UUID.randomUUID()}")
        require(stage.mkdirs()) { "No se pudo preparar la importación." }

        return try {
            val names = hashSetOf<String>()
            var rawJson: String? = null
            val individualTopicJson = mutableListOf<Pair<String, String>>()
            val archiveEntries = mutableSetOf<String>()
            var totalJsonBytes = 0L
            var totalImageBytes = 0L
            val buffer = ByteArray(8192)

            fun readBoundedJson(zis: ZipInputStream): String {
                val output = java.io.ByteArrayOutputStream()
                var entryBytes = 0L
                while (true) {
                    val count = zis.read(buffer)
                    if (count < 0) break
                    entryBytes += count
                    totalJsonBytes += count
                    require(entryBytes <= 128L * 1024L * 1024L &&
                        totalJsonBytes <= 192L * 1024L * 1024L) {
                        "El ZIP contiene archivos JSON demasiado grandes."
                    }
                    output.write(buffer, 0, count)
                }
                return output.toString(Charsets.UTF_8.name())
            }

            ZipInputStream(buffered).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    require(archiveEntries.add(entry.name)) {
                        "El ZIP contiene entradas duplicadas: ${entry.name}."
                    }
                    when {
                        entry.name == "testario_database.json" && !entry.isDirectory -> {
                            require(rawJson == null) { "La copia contiene varias bases de datos." }
                            rawJson = readBoundedJson(zis)
                        }
                        entry.name.endsWith(".json", ignoreCase = true) &&
                            !entry.isDirectory &&
                            entry.name !in setOf(
                                "manifest.json", "planario_backup.json", "quick_test_links.json",
                                "testario_settings.json", "pending_test_session.json"
                            ) -> {
                            require(!entry.name.startsWith("/") && !entry.name.contains('\\') &&
                                entry.name.split('/').none { it == ".." }) {
                                "Nombre de archivo JSON no seguro."
                            }
                            require(individualTopicJson.size < 500) { "El ZIP contiene demasiados JSON." }
                            individualTopicJson.add(entry.name to readBoundedJson(zis))
                        }
                        entry.name.startsWith("question_images/") && !entry.isDirectory -> {
                            val name = entry.name.removePrefix("question_images/")
                            require(name.isNotBlank() && name != "." && name != ".." &&
                                !name.contains('/') && !name.contains('\\')) {
                                "Ruta de imagen no válida en el ZIP."
                            }
                            require(names.add(name)) { "Imagen duplicada en el ZIP." }
                            val stagedFile = File(stage, name)
                            var fileBytes = 0L
                            stagedFile.outputStream().use { output ->
                                while (true) {
                                    val count = zis.read(buffer)
                                    if (count < 0) break
                                    fileBytes += count
                                    totalImageBytes += count
                                    require(fileBytes <= 64L * 1024L * 1024L &&
                                        totalImageBytes <= 512L * 1024L * 1024L) {
                                        "El ZIP contiene imágenes demasiado grandes."
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val validJson = if (rawJson != null) {
                require(individualTopicJson.isEmpty()) {
                    "El ZIP mezcla una base completa y temas JSON separados: usa un solo formato."
                }
                val source = requireNotNull(rawJson)
                val database = json.decodeFromString<TestarioDatabase>(MigrationManager.migrate(source))
                validateStudyExercises(database.topics)
                source
            } else {
                require(individualTopicJson.isNotEmpty()) {
                    "El ZIP no contiene testario_database.json ni archivos JSON de temas."
                }
                // Parse every JSON before allowing any changes; a malformed item
                // rejects the whole archive rather than importing only its neighbours.
                val topics = individualTopicJson.flatMap { (name, raw) ->
                    try {
                        decodeTopicsFromAnyJson(raw)
                    } catch (error: Exception) {
                        throw IllegalArgumentException("No se pudo leer el tema $name: ${error.message}")
                    }
                }
                require(topics.isNotEmpty()) { "El ZIP no contiene ningún tema válido." }
                validateStudyExercises(topics)
                json.encodeToString(TestarioDatabase(topics = topics))
            }
            // New exercises must carry every referenced asset in the SAME archive.
            // Reject broken archives before any image or topic is committed.
            val validated = json.decodeFromString<TestarioDatabase>(
                MigrationManager.migrate(validJson)
            )
            val missing = validated.topics.asSequence()
                .flatMap { it.exercises.asSequence() }
                .flatMap { it.referencedImageUris().asSequence() }
                .map { File(it).name }
                .filterNot { it in names }
                .toList()
            require(missing.isEmpty()) {
                "El ZIP no contiene ${missing.size} imágenes necesarias para los ejercicios."
            }
            pendingImageStage = stage
            validJson
        } catch (error: Exception) {
            stage.deleteRecursively()
            throw error
        }
    }

    /**
     * Confirmación transaccional de imágenes: conserva una copia de cada imagen
     * sustituida para revertir una copia fallida, sin modificar la biblioteca
     * durante la lectura o previsualización del ZIP.
     */
    @Synchronized
    private fun commitPendingImages() {
        val stage = pendingImageStage ?: return
        val rollback = File(context.cacheDir, "nexum-import-undo-${UUID.randomUUID()}")
        val changed = mutableListOf<File>()
        try {
            require(imagesDir.isDirectory || imagesDir.mkdirs()) {
                "No se pudo abrir el almacenamiento de imágenes."
            }
            require(rollback.mkdirs()) { "No se pudo preparar la recuperación de imágenes." }
            val canonicalImages = imagesDir.canonicalPath + File.separator
            stage.listFiles()?.filter { it.isFile }?.forEach { staged ->
                val target = File(imagesDir, staged.name)
                require(target.canonicalPath.startsWith(canonicalImages)) {
                    "Ruta de imagen no autorizada."
                }
                if (target.exists()) {
                    // An imported archive may re-use a name already belonging to a
                    // different question. Never overwrite that question's image.
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    fun hash(file: File): ByteArray {
                        digest.reset()
                        file.inputStream().use { stream ->
                            val bytes = ByteArray(8192)
                            while (true) {
                                val count = stream.read(bytes)
                                if (count < 0) break
                                digest.update(bytes, 0, count)
                            }
                        }
                        return digest.digest()
                    }
                    require(target.length() == staged.length() &&
                        hash(target).contentEquals(hash(staged))) {
                        "La imagen ${staged.name} ya existe con contenido diferente; " +
                            "no se sobrescribirá. Renombra los recursos del paquete."
                    }
                    // Same image content already present, so preserve it as-is.
                } else {
                    changed.add(target)
                    staged.copyTo(target, overwrite = false)
                }
            }
            pendingImageStage = null
        } catch (error: Exception) {
            changed.asReversed().forEach { target ->
                runCatching {
                    val original = File(rollback, target.name)
                    if (original.isFile) original.copyTo(target, overwrite = true)
                    else target.delete()
                }
            }
            throw error
        } finally {
            if (pendingImageStage == null) stage.deleteRecursively()
            rollback.deleteRecursively()
        }
    }

    fun restoreBackup(rawJson: String) {
        val migratedJson = MigrationManager.migrate(rawJson)
        val backup = json.decodeFromString<TestarioDatabase>(migratedJson)
        validateStudyExercises(backup.topics)
        commitPendingImages()
        localStore.saveDatabase(
            backup.copy(
                version = DatabaseSchema.CURRENT_VERSION,
                topics = backup.topics.map(::resolveTopicImagePaths)
            )
        )
    }

    fun importAllJson(rawJson: String) {
        val imported = json.decodeFromString<TestarioDatabase>(MigrationManager.migrate(rawJson))
        validateStudyExercises(imported.topics)
        commitPendingImages()
        val topicIdMap = imported.topics.associate { it.id to UUID.randomUUID().toString() }
        val regeneratedTopics = imported.topics.map { topic ->
            resolveTopicImagePaths(regenerateTopicIds(
                topic, topicIdMap[topic.id] ?: UUID.randomUUID().toString()
            ))
        }
        val remappedGroups = imported.groups.map { group ->
            group.copy(topicIds = group.topicIds.mapNotNull(topicIdMap::get).distinct())
        }
        val remappedTemplates = imported.testTemplates.map { template ->
            template.copy(
                selectedTopicIds = template.selectedTopicIds.mapNotNull(topicIdMap::get).distinct(),
                selectedFailureTopicIds = template.selectedFailureTopicIds.mapNotNull(topicIdMap::get).distinct()
            )
        }
        localStore.saveDatabase(
            imported.copy(
                version = DatabaseSchema.CURRENT_VERSION,
                topics = regeneratedTopics,
                groups = remappedGroups,
                testTemplates = remappedTemplates
            )
        )
    }

    fun importSimpleJson(rawJson: String) {
        val importedTopics = decodeSimpleTopics(rawJson)
        val db = localStore.database.value
        localStore.saveDatabase(db.copy(topics = db.topics + importedTopics))
    }

    fun previewImport(rawText: String, fileName: String? = null): ImportPreview {
        return try {
            val fileType = detectImportFileType(rawText, fileName)
            val topics = decodeTopicsFromAnyFormat(rawText, fileType)
            validateStudyMediaForImport(topics)

            ImportPreview(
                fileType = fileType,
                topicCount = topics.size,
                questionCount = topics.sumOf { it.questions.size + it.exercises.size },
                validQuestionCount = topics.sumOf { it.questions.size + it.exercises.size },
                invalidLineCount = if (fileType == ImportFileType.TXT || fileType == ImportFileType.CSV) countInvalidDelimitedLines(rawText) else 0,
                errors = emptyList()
            )
        } catch (e: Exception) {
            ImportPreview(
                fileType = detectImportFileType(rawText, fileName),
                invalidLineCount = if (looksDelimited(rawText)) countInvalidDelimitedLines(rawText) else 0,
                errors = listOf(e.message ?: "No se pudo leer el archivo.")
            )
        }
    }

    fun getTopicsFromRawText(rawText: String, fileName: String? = null): List<Topic> {
        val fileType = detectImportFileType(rawText, fileName)
        return decodeTopicsFromAnyFormat(rawText, fileType).also {
            validateStudyExercises(it)
            validateStudyMediaForImport(it)
        }
    }

    // NUEVO: Motor de sincronización
    fun syncWithExternalDatabase(rawJson: String): Int {
        val db = localStore.database.value
        val finalTopics = db.topics.toMutableList()
        var updatedCount = 0

        val externalTopics = try {
            json.decodeFromString<TestarioDatabase>(rawJson).topics
        } catch (_: Exception) {
            return 0
        }

        // Agrupamos por enunciado normalizado. Puede haber varias preguntas con
        // exactamente el mismo enunciado pero respuestas distintas, así que no
        // usamos associateBy (que descartaría silenciosamente todas menos una).
        val externalQuestionsMap = externalTopics
            .flatMap { it.questions }
            .groupBy { normalizeText(it.text) }

        for (i in finalTopics.indices) {
            val currentTopic = finalTopics[i]
            val updatedQuestions = currentTopic.questions.map { myQuestion ->
                val normalizedText = normalizeText(myQuestion.text)
                val candidates = externalQuestionsMap[normalizedText].orEmpty()
                val externalMatch = QuestionSyncMatcher.findCompatible(myQuestion, candidates)
                val remapped = externalMatch?.let {
                    QuestionSyncMatcher.remapAnswers(myQuestion, it)
                }

                if (externalMatch != null && remapped != null &&
                    hasSyncDifferences(myQuestion, externalMatch)) {
                    updatedCount++
                    myQuestion.copy(
                        answers = remapped.answers,
                        correctAnswerId = remapped.correctAnswerId,
                        stats = myQuestion.stats.copy(lastAnswerId = remapped.previousLastAnswerId),
                        explanation = externalMatch.explanation,
                        difficulty = externalMatch.difficulty,
                        explanationImageUri = resolveImportedImagePath(externalMatch.explanationImageUri),
                        correctionImageUri = resolveImportedImagePath(externalMatch.correctionImageUri)
                    )
                } else {
                    myQuestion
                }
            }
            finalTopics[i] = currentTopic.copy(questions = updatedQuestions)
        }

        if (updatedCount > 0) {
            commitPendingImages()
            localStore.saveDatabase(db.copy(topics = finalTopics.map(::resolveTopicImagePaths)))
        }
        return updatedCount
    }

    fun importSingleTopicProcessed(
        topic: Topic,
        action: DuplicateTopicAction,
        customName: String? = null
    ): ImportTopicOutcome {
        val db = localStore.database.value
        val topics = db.topics.toMutableList()
        val existingIndex = topics.indexOfFirst { it.name.equals(topic.name, ignoreCase = true) }

        // Only deduplicate inside the TARGET topic. The same knowledge may
        // intentionally appear in different subjects, zones or groups.
        val targetQuestions = if (existingIndex >= 0 &&
            action == DuplicateTopicAction.MERGE) topics[existingIndex].questions
            else emptyList()
        validateStudyExercises(listOf(topic))
        val uniqueQuestions = TopicImportMatcher.uniqueQuestions(topic.questions, targetQuestions)
        val duplicates = topic.questions.size - uniqueQuestions.size
        val targetExercises = if (existingIndex >= 0 && action == DuplicateTopicAction.MERGE) 
            topics[existingIndex].exercises else emptyList()
        val uniqueExercises = StudyExerciseMerge.unique(topic.exercises, targetExercises)
        val duplicateExercises = topic.exercises.size - uniqueExercises.size
        if (existingIndex >= 0 && action == DuplicateTopicAction.MERGE &&
            uniqueQuestions.isEmpty() && uniqueExercises.isEmpty()) {
            return ImportTopicOutcome(
                created = false, merged = false, addedQuestions = 0,
                duplicateQuestions = duplicates, destinationName = topics[existingIndex].name,
                addedExercises = 0, duplicateExercises = duplicateExercises
            )
        }

        commitPendingImages()
        val fresh = resolveTopicImagePaths(regenerateTopicIds(
            topic.copy(questions = uniqueQuestions, exercises = uniqueExercises)
        ))
        val destination: String
        val created: Boolean
        val merged: Boolean

        if (existingIndex >= 0) {
            when (action) {
                DuplicateTopicAction.MERGE -> {
                    val old = topics[existingIndex]
                    topics[existingIndex] = old.copy(
                        questions = old.questions + fresh.questions,
                        exercises = old.exercises + fresh.exercises
                    )
                    destination = old.name
                    created = false
                    merged = true
                }
                DuplicateTopicAction.RENAME_AUTO, DuplicateTopicAction.RENAME -> {
                    val wanted = if (action == DuplicateTopicAction.RENAME)
                        customName?.trim().orEmpty().ifBlank { fresh.name }
                    else fresh.name
                    destination = generateUniqueTopicName(wanted, topics)
                    topics.add(fresh.copy(name = destination))
                    created = true
                    merged = false
                }
            }
        } else {
            destination = customName?.trim()?.ifBlank { fresh.name } ?: fresh.name
            topics.add(fresh.copy(name = destination))
            created = true
            merged = false
        }
        localStore.saveDatabase(db.copy(topics = topics))
        return ImportTopicOutcome(created, merged, fresh.questions.size, duplicates, destination,
            fresh.exercises.size, duplicateExercises)
    }

    private fun validateStudyMediaForImport(topics: List<Topic>) {
        val stagedNames = pendingImageStage?.listFiles()?.mapTo(hashSetOf()) { it.name }
            ?: emptySet()
        val safeRoot = imagesDir.canonicalPath + File.separator
        topics.asSequence().flatMap { it.exercises.asSequence() }
            .flatMap { it.referencedImageUris().asSequence() }.forEach { reference ->
                val image = File(reference)
                val isStaged = image.name in stagedNames
                val isOwnFile = image.isFile && image.canonicalPath.startsWith(safeRoot)
                require(isStaged || isOwnFile) {
                    "El ejercicio necesita una imagen no incluida. Importa el ZIP con sus recursos."
                }
            }
    }

    private fun validateStudyExercises(topics: List<Topic>) {
        topics.forEach { topic ->
            val ids = hashSetOf<String>()
            topic.exercises.forEach { exercise ->
                require(ids.add(exercise.id)) { "ID de ejercicio repetido en el tema ${topic.name}." }
                if (!exercise.draft) StudyExerciseRules.requireValid(exercise)
            }
        }
    }

    private fun createQuestionFingerprint(question: Question): String =
        TopicImportMatcher.fingerprint(question)

    private fun hasSyncDifferences(local: Question, external: Question): Boolean {
        if (createQuestionFingerprint(local) != createQuestionFingerprint(external)) return true
        if (local.explanation.trim() != external.explanation.trim()) return true
        if (local.difficulty != external.difficulty) return true

        val localCorrectText = local.answers.firstOrNull { it.id == local.correctAnswerId }?.text.orEmpty()
        val externalCorrectText = external.answers.firstOrNull { it.id == external.correctAnswerId }?.text.orEmpty()
        if (normalizeText(localCorrectText) != normalizeText(externalCorrectText)) return true

        fun imageName(path: String?): String = path?.let { File(it).name }.orEmpty()
        if (imageName(local.explanationImageUri) != imageName(external.explanationImageUri)) return true
        if (imageName(local.correctionImageUri) != imageName(external.correctionImageUri)) return true

        return false
    }

    private fun normalizeText(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]"), "")

    private fun detectImportFileType(rawText: String, fileName: String?): ImportFileType {
        val extension = fileName?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()?.trim().orEmpty()
        return when {
            extension == "zip" -> ImportFileType.JSON
            extension == "json" -> ImportFileType.JSON
            extension == "txt" -> ImportFileType.TXT
            extension == "csv" -> ImportFileType.CSV
            rawText.trimStart().startsWith("{") || rawText.trimStart().startsWith("[") -> ImportFileType.JSON
            looksDelimited(rawText) -> ImportFileType.TXT
            else -> ImportFileType.UNKNOWN
        }
    }

    private fun decodeTopicsFromAnyFormat(rawText: String, fileType: ImportFileType): List<Topic> {
        return when (fileType) {
            ImportFileType.JSON -> decodeTopicsFromAnyJson(rawText)
            ImportFileType.TXT, ImportFileType.CSV -> decodeDelimitedTopics(rawText)
            ImportFileType.UNKNOWN -> try { decodeTopicsFromAnyJson(rawText) } catch (_: Exception) { decodeDelimitedTopics(rawText) }
        }
    }

    private fun looksDelimited(rawText: String): Boolean = rawText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.take(5).any { it.count { char -> char == ';' } >= 4 }

    private fun countInvalidDelimitedLines(rawText: String): Int = rawText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.filterNot { isDelimitedHeader(it) }.count { line -> val parts = line.split(";").map { it.trim() }; parts.size != 5 && parts.size != 6 }

    private fun isDelimitedHeader(line: String): Boolean {
        val normalized = line.lowercase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        return normalized.startsWith("pregunta;respuesta") || normalized.startsWith("tema;pregunta;respuesta")
    }

    private fun decodeDelimitedTopics(rawText: String): List<Topic> {
        val groupedQuestions = linkedMapOf<String, MutableList<Question>>()
        val invalidLines = mutableListOf<Int>()

        rawText.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank() || isDelimitedHeader(line)) return@forEachIndexed
            val parts = line.split(";").map { it.trim() }

            val parsed = when (parts.size) {
                5 -> ParsedDelimitedQuestion("Importado TXT/CSV", parts[0], parts.subList(1, 4), parts[4])
                6 -> ParsedDelimitedQuestion(parts[0].ifBlank { "Importado TXT/CSV" }, parts[1], parts.subList(2, 5), parts[5])
                else -> { invalidLines.add(index + 1); null }
            }

            parsed?.let {
                val question = buildQuestionFromDelimited(it)
                if (question == null) invalidLines.add(index + 1) else groupedQuestions.getOrPut(it.topicName) { mutableListOf() }.add(question)
            }
        }

        if (groupedQuestions.isEmpty()) {
            val detail = if (invalidLines.isNotEmpty()) " Líneas inválidas: ${invalidLines.joinToString(", ")}." else ""
            throw IllegalArgumentException("No se encontró ninguna pregunta válida en el TXT/CSV.$detail")
        }

        return groupedQuestions.map { (topicName, questions) -> Topic(id = UUID.randomUUID().toString(), name = topicName.ifBlank { "Importado TXT/CSV" }, questions = questions) }
    }

    private fun buildQuestionFromDelimited(parsed: ParsedDelimitedQuestion): Question? {
        if (parsed.questionText.isBlank() || parsed.answerTexts.size < 3 || parsed.answerTexts.any { it.isBlank() }) return null
        val answers = parsed.answerTexts.map { Answer(id = UUID.randomUUID().toString(), text = it) }
        val correctIndex = resolveCorrectIndex(parsed.correctValue, parsed.answerTexts) ?: return null
        return Question(id = UUID.randomUUID().toString(), text = parsed.questionText, answers = answers, correctAnswerId = answers[correctIndex].id, difficulty = null, explanation = "")
    }

    private fun resolveCorrectIndex(correctValue: String, answers: List<String>): Int? {
        val clean = correctValue.trim()
        return when (clean.uppercase()) {
            "A", "1" -> 0
            "B", "2" -> 1
            "C", "3" -> 2
            else -> answers.indexOfFirst { it.equals(clean, ignoreCase = true) }.takeIf { it >= 0 }
        }
    }

    private fun decodeTopicsFromAnyJson(rawJson: String): List<Topic> {
        // Inspect the shape FIRST: TestarioDatabase has default values, so
        // blindly decoding a single-topic JSON as a database returns ZERO topics.
        val trimmed = rawJson.trimStart()
        if (trimmed.startsWith("{")) {
            val root = org.json.JSONObject(rawJson)
            if (root.has("topics")) {
                val imported = json.decodeFromString<TestarioDatabase>(
                    MigrationManager.migrate(rawJson)
                )
                return imported.topics.map { regenerateTopicIds(it) }
            }
            if (root.has("name") && root.has("questions")) {
                val sample = root.optJSONArray("questions")?.optJSONObject(0)
                return if (root.has("id") || sample?.has("correctAnswerId") == true) {
                    listOf(regenerateTopicIds(json.decodeFromString<Topic>(rawJson)))
                } else {
                    val simple = json.decodeFromString<SimpleTopicJson>(rawJson)
                    decodeSimpleTopics(json.encodeToString(listOf(simple)))
                }
            }
        }
        if (trimmed.startsWith("[")) {
            val array = org.json.JSONArray(rawJson)
            val sampleTopic = array.optJSONObject(0)
            val sampleQuestion = sampleTopic?.optJSONArray("questions")?.optJSONObject(0)
            val fullModel = sampleTopic?.has("id") == true ||
                sampleQuestion?.has("correctAnswerId") == true
            return if (fullModel) json.decodeFromString<List<Topic>>(rawJson)
                .map { regenerateTopicIds(it) } else decodeSimpleTopics(rawJson)
        }
        throw IllegalArgumentException("JSON de tema o biblioteca no reconocido.")
    }

    private fun decodeSimpleTopics(rawJson: String): List<Topic> {
        val simpleTopics = json.decodeFromString<List<SimpleTopicJson>>(rawJson)
        require(simpleTopics.size <= 10000) { "Demasiados temas en la importación." }
        return simpleTopics.map { simpleTopic ->
            val label = simpleTopic.name.ifBlank { "Importado" }
            Topic(
                id = UUID.randomUUID().toString(),
                name = label,
                questions = simpleTopic.questions.mapIndexed { index, question ->
                    require(question.text.isNotBlank() && question.answers.size >= 2 &&
                        question.answers.all { it.text.isNotBlank() }) {
                        "Pregunta ${index + 1} no válida en el tema $label."
                    }
                    val correct = question.answers.indices.filter {
                        question.answers[it].isCorrect
                    }
                    require(correct.size == 1) {
                        "Pregunta ${index + 1} de $label: debe existir una única respuesta correcta."
                    }
                    val answers = question.answers.map {
                        Answer(id = UUID.randomUUID().toString(), text = it.text)
                    }
                    Question(
                        id = UUID.randomUUID().toString(),
                        text = question.text,
                        answers = answers,
                        correctAnswerId = answers[correct.single()].id,
                        difficulty = null,
                        explanation = ""
                    )
                }
            )
        }
    }

    private fun regenerateTopicIds(topic: Topic, newTopicId: String = UUID.randomUUID().toString()): Topic {
        return topic.copy(
            id = newTopicId,
            exercises = topic.exercises.map(StudyExerciseMerge::duplicate),
            questions = topic.questions.map { question ->
                val answerIdMap = question.answers.associate { it.id to UUID.randomUUID().toString() }
                val newAnswers = question.answers.map {
                    it.copy(id = answerIdMap[it.id] ?: UUID.randomUUID().toString())
                }
                question.copy(
                    id = UUID.randomUUID().toString(),
                    answers = newAnswers,
                    correctAnswerId = answerIdMap[question.correctAnswerId]
                        ?: newAnswers.firstOrNull()?.id.orEmpty(),
                    explanationImageUri = resolveImportedImagePath(question.explanationImageUri),
                    correctionImageUri = resolveImportedImagePath(question.correctionImageUri),
                    stats = question.stats.copy(
                        lastAnswerId = answerIdMap[question.stats.lastAnswerId] ?: question.stats.lastAnswerId
                    )
                )
            }
        )
    }

    private fun resolveTopicImagePaths(topic: Topic): Topic {
        return topic.copy(
            exercises = topic.exercises.map { ex ->
                ex.copy(
                    baseImageUri = resolveImportedImagePath(ex.baseImageUri),
                    flashcard = ex.flashcard?.copy(
                        frontImageUri = resolveImportedImagePath(ex.flashcard.frontImageUri),
                        backImageUri = resolveImportedImagePath(ex.flashcard.backImageUri)
                    )
                )
            },
            questions = topic.questions.map { question ->
                question.copy(
                    explanationImageUri = resolveImportedImagePath(question.explanationImageUri),
                    correctionImageUri = resolveImportedImagePath(question.correctionImageUri)
                )
            }
        )
    }

    private fun resolveImportedImagePath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val fileName = try { File(path).name } catch (_: Exception) { return path }
        if (fileName.isBlank()) return path
        val localFile = File(imagesDir, fileName)
        return if (localFile.exists()) localFile.absolutePath else path
    }

    private fun generateUniqueTopicName(baseName: String, existingTopics: List<Topic>): String {
        val cleanBaseName = baseName.trim().ifBlank { "Importado" }
        val existingNames = existingTopics.map { it.name.trim().lowercase() }.toSet()
        if (cleanBaseName.lowercase() !in existingNames) return cleanBaseName
        var counter = 2
        var newName: String
        do { newName = "$cleanBaseName ($counter)"; counter++ } while (newName.lowercase() in existingNames)
        return newName
    }
}