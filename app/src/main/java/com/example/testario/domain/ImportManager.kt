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
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

class ImportManager(private val context: Context, private val localStore: TestarioLocalStore) {
    private val json = Json { ignoreUnknownKeys = true }
    private val imagesDir = File(context.filesDir, "question_images")

    fun extractPayload(inputStream: InputStream, fileName: String?): String {
        if (fileName?.endsWith(".zip", ignoreCase = true) != true) {
            return inputStream.bufferedReader().use { it.readText() }
        }

        var jsonContent = ""
        if (!imagesDir.exists()) imagesDir.mkdirs()

        val canonicalImagesDir = imagesDir.canonicalPath + File.separator

        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "testario_database.json") {
                    jsonContent = String(zis.readBytes())
                } else if (entry.name.startsWith("question_images/") && !entry.isDirectory) {
                    val imgName = entry.name.substringAfter("question_images/")
                    if (imgName.isBlank() || imgName.contains('/') || imgName.contains('\\')) {
                        throw SecurityException("Ruta de imagen no válida en el ZIP: ${entry.name}")
                    }

                    val destFile = File(imagesDir, imgName)
                    if (!destFile.canonicalPath.startsWith(canonicalImagesDir)) {
                        throw SecurityException("Vulnerabilidad Zip Slip detectada en el archivo: ${entry.name}")
                    }

                    destFile.outputStream().use { output -> zis.copyTo(output) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return jsonContent
    }

    fun restoreBackup(rawJson: String) {
        val migratedJson = MigrationManager.migrate(rawJson)
        val backup = json.decodeFromString<TestarioDatabase>(migratedJson)
        localStore.saveDatabase(
            backup.copy(
                version = DatabaseSchema.CURRENT_VERSION,
                topics = backup.topics.map(::resolveTopicImagePaths)
            )
        )
    }

    fun importAllJson(rawJson: String) {
        val imported = json.decodeFromString<TestarioDatabase>(rawJson)
        val topicIdMap = imported.topics.associate { it.id to UUID.randomUUID().toString() }
        val regeneratedTopics = imported.topics.map { topic ->
            regenerateTopicIds(topic, topicIdMap[topic.id] ?: UUID.randomUUID().toString())
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

            ImportPreview(
                fileType = fileType,
                topicCount = topics.size,
                questionCount = topics.sumOf { it.questions.size },
                validQuestionCount = topics.sumOf { it.questions.size },
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
        return decodeTopicsFromAnyFormat(rawText, fileType)
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

        // Mapeamos las preguntas externas usando el texto normalizado como clave
        val externalQuestionsMap = externalTopics.flatMap { it.questions }.associateBy { normalizeText(it.text) }

        for (i in finalTopics.indices) {
            val currentTopic = finalTopics[i]
            val updatedQuestions = currentTopic.questions.map { myQuestion ->
                val normalizedText = normalizeText(myQuestion.text)
                val externalMatch = externalQuestionsMap[normalizedText]

                if (externalMatch != null && hasSyncDifferences(myQuestion, externalMatch)) {
                    updatedCount++
                    val updatedAnswers = externalMatch.answers.map { extAns ->
                        val existingAns = myQuestion.answers.firstOrNull {
                            normalizeText(it.text) == normalizeText(extAns.text)
                        }
                        extAns.copy(id = existingAns?.id ?: extAns.id)
                    }
                    val externalCorrectText = externalMatch.answers
                        .firstOrNull { it.id == externalMatch.correctAnswerId }
                        ?.text
                    val updatedCorrectId = updatedAnswers
                        .firstOrNull { normalizeText(it.text) == normalizeText(externalCorrectText.orEmpty()) }
                        ?.id
                        ?: updatedAnswers.firstOrNull()?.id.orEmpty()

                    myQuestion.copy(
                        answers = updatedAnswers,
                        correctAnswerId = updatedCorrectId,
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
            localStore.saveDatabase(db.copy(topics = finalTopics))
        }
        return updatedCount
    }

    fun importSingleTopicProcessed(topic: Topic, action: DuplicateTopicAction, customName: String? = null) {
        val db = localStore.database.value
        val finalTopics = db.topics.toMutableList()
        val existingFingerprints = finalTopics.flatMap { it.questions }.map { createQuestionFingerprint(it) }.toMutableSet()

        val uniqueNewQuestions = topic.questions.filter { incomingQ ->
            val fingerprint = createQuestionFingerprint(incomingQ)
            if (existingFingerprints.contains(fingerprint)) false else { existingFingerprints.add(fingerprint); true }
        }

        if (uniqueNewQuestions.isEmpty()) return
        val cleanTopic = topic.copy(questions = uniqueNewQuestions)
        val existingIndex = finalTopics.indexOfFirst { it.name.equals(cleanTopic.name, ignoreCase = true) }

        if (existingIndex >= 0) {
            when (action) {
                DuplicateTopicAction.RENAME_AUTO -> finalTopics.add(cleanTopic.copy(name = generateUniqueTopicName(cleanTopic.name, finalTopics)))
                DuplicateTopicAction.RENAME -> {
                    val wantedName = customName?.trim().orEmpty().ifBlank { cleanTopic.name }
                    finalTopics.add(cleanTopic.copy(name = generateUniqueTopicName(wantedName, finalTopics)))
                }
                DuplicateTopicAction.MERGE -> {
                    val existingTopic = finalTopics[existingIndex]
                    finalTopics[existingIndex] = existingTopic.copy(questions = existingTopic.questions + cleanTopic.questions)
                }
            }
        } else {
            val finalName = customName?.trim()?.ifBlank { cleanTopic.name } ?: cleanTopic.name
            finalTopics.add(cleanTopic.copy(name = finalName))
        }
        localStore.saveDatabase(db.copy(topics = finalTopics))
    }

    private fun createQuestionFingerprint(question: Question): String {
        val qText = normalizeText(question.text)
        val aText = question.answers
            .map { normalizeText(it.text) }
            .sorted()
            .joinToString("|")
        return "$qText::$aText"
    }

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

    private fun decodeTopicsFromAnyJson(rawJson: String): List<Topic> = try { json.decodeFromString<TestarioDatabase>(rawJson).topics.map { regenerateTopicIds(it) } } catch (_: Exception) { decodeSimpleTopics(rawJson) }

    private fun decodeSimpleTopics(rawJson: String): List<Topic> {
        return json.decodeFromString<List<SimpleTopicJson>>(rawJson).map { simpleTopic ->
            Topic(
                id = UUID.randomUUID().toString(),
                name = simpleTopic.name.ifBlank { "Importado" },
                questions = simpleTopic.questions.mapNotNull { simpleQuestion ->
                    if (simpleQuestion.text.isBlank() || simpleQuestion.answers.size < 2) null else {
                        val answers = simpleQuestion.answers.map { Answer(id = UUID.randomUUID().toString(), text = it.text) }
                        val correctIndex = simpleQuestion.answers.indexOfFirst { it.isCorrect }
                        Question(id = UUID.randomUUID().toString(), text = simpleQuestion.text, answers = answers, correctAnswerId = answers.getOrNull(if (correctIndex >= 0) correctIndex else 0)?.id ?: "", difficulty = null, explanation = "")
                    }
                }
            )
        }.filter { it.questions.isNotEmpty() }
    }

    private fun regenerateTopicIds(topic: Topic, newTopicId: String = UUID.randomUUID().toString()): Topic {
        return topic.copy(
            id = newTopicId,
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