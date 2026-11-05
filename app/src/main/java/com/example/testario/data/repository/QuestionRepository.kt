package com.example.testario.data.repository

import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic
import com.example.testario.domain.ImageManager
import java.util.UUID

class QuestionRepository(
    private val localStore: TestarioLocalStore,
    private val imageManager: ImageManager
) {
    private val fingerprintCombiningMarks = Regex("\\p{Mn}+")
    private val fingerprintNonAlnum = Regex("[^a-z0-9]")

    fun addQuestion(topicId: String, question: Question) {
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    if (topic.id == topicId) topic.copy(questions = topic.questions + question)
                    else topic
                }
            )
        )
    }

    fun updateQuestion(topicId: String, question: Question) {
        val db = localStore.database.value
        val topic = db.topics.firstOrNull { it.id == topicId }
        val oldQuestion = topic?.questions?.firstOrNull { it.id == question.id }

        if (oldQuestion?.explanationImageUri != null && oldQuestion.explanationImageUri != question.explanationImageUri) {
            deleteImageIfUnreferenced(
                path = oldQuestion.explanationImageUri,
                db = db,
                excludedQuestionIds = setOf(question.id)
            )
        }
        if (oldQuestion?.correctionImageUri != null && oldQuestion.correctionImageUri != question.correctionImageUri) {
            deleteImageIfUnreferenced(
                path = oldQuestion.correctionImageUri,
                db = db,
                excludedQuestionIds = setOf(question.id)
            )
        }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { t ->
                    if (t.id == topicId) {
                        t.copy(
                            questions = t.questions.map {
                                if (it.id == question.id) question else it
                            }
                        )
                    } else t
                }
            )
        )
    }

    fun toggleQuestionFlag(questionId: String): Boolean {
        val db = localStore.database.value
        if (db.topics.none { topic -> topic.questions.any { it.id == questionId } }) return false

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    topic.copy(
                        questions = topic.questions.map { question ->
                            if (question.id == questionId) {
                                question.copy(flagged = !question.flagged)
                            } else {
                                question
                            }
                        }
                    )
                }
            )
        )
        return true
    }

    fun deleteQuestion(topicId: String, questionId: String) {
        val db = localStore.database.value
        val topic = db.topics.firstOrNull { it.id == topicId }
        val questionToDelete = topic?.questions?.firstOrNull { it.id == questionId }

        questionToDelete?.let { question ->
            deleteQuestionImagesIfUnreferenced(
                question = question,
                db = db,
                excludedQuestionIds = setOf(question.id)
            )
        }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { t ->
                    if (t.id == topicId) {
                        t.copy(questions = t.questions.filterNot { it.id == questionId })
                    } else t
                }
            )
        )
    }

    fun moveQuestions(
        sourceTopicId: String,
        targetTopicId: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean = false,
        avoidDuplicates: Boolean = true
    ): Int {
        if (sourceTopicId == targetTopicId || questionIds.isEmpty()) return 0

        val db = localStore.database.value
        val sourceTopic = db.topics.firstOrNull { it.id == sourceTopicId } ?: return 0
        val targetTopic = db.topics.firstOrNull { it.id == targetTopicId } ?: return 0
        val targetQuestionIds = targetTopic.questions.mapTo(mutableSetOf()) { it.id }
        val targetFingerprints = targetTopic.questions
            .mapTo(mutableSetOf()) { createQuestionFingerprint(it) }

        val questionsToMove = mutableListOf<Question>()
        sourceTopic.questions.forEach { question ->
            if (question.id !in questionIds || question.id in targetQuestionIds) return@forEach

            val fingerprint = createQuestionFingerprint(question)
            if (avoidDuplicates && fingerprint in targetFingerprints) {
                // La pregunta se queda en su tema de origen: no se borra ni se duplica.
                return@forEach
            }

            questionsToMove += prepareForMove(question, sourceTopic.name, preserveOrigin)
            if (avoidDuplicates) targetFingerprints += fingerprint
        }

        if (questionsToMove.isEmpty()) return 0
        val movedIds = questionsToMove.mapTo(mutableSetOf()) { it.id }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    when (topic.id) {
                        sourceTopicId -> topic.copy(questions = topic.questions.filterNot { it.id in movedIds })
                        targetTopicId -> topic.copy(questions = topic.questions + questionsToMove)
                        else -> topic
                    }
                }
            )
        )
        return questionsToMove.size
    }

    /**
     * Mueve preguntas seleccionadas desde cualquier número de temas a un único tema destino.
     * Los IDs y las estadísticas no se regeneran y las imágenes siguen usando los mismos archivos.
     * Si avoidDuplicates está activo, una coincidencia por enunciado + respuestas normalizados
     * se deja en su tema de origen y no se duplica en el destino.
     */
    fun moveQuestionsFromMany(
        targetTopicId: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean,
        avoidDuplicates: Boolean = true
    ): Int {
        if (questionIds.isEmpty()) return 0

        val db = localStore.database.value
        val targetTopic = db.topics.firstOrNull { it.id == targetTopicId } ?: return 0
        val alreadyInTarget = targetTopic.questions.mapTo(mutableSetOf()) { it.id }
        val targetFingerprints = targetTopic.questions
            .mapTo(mutableSetOf()) { createQuestionFingerprint(it) }

        val collected = mutableListOf<Question>()
        db.topics.forEach { sourceTopic ->
            if (sourceTopic.id != targetTopicId) {
                sourceTopic.questions
                    .filter { it.id in questionIds && it.id !in alreadyInTarget }
                    .forEach questionLoop@ { question ->
                        val fingerprint = createQuestionFingerprint(question)
                        if (avoidDuplicates && fingerprint in targetFingerprints) {
                            return@questionLoop
                        }

                        collected += prepareForMove(question, sourceTopic.name, preserveOrigin)
                        if (avoidDuplicates) targetFingerprints += fingerprint
                    }
            }
        }

        if (collected.isEmpty()) return 0
        val movedIds = collected.mapTo(mutableSetOf()) { it.id }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    when (topic.id) {
                        targetTopicId -> topic.copy(questions = topic.questions + collected)
                        else -> topic.copy(questions = topic.questions.filterNot { it.id in movedIds })
                    }
                }
            )
        )
        return collected.size
    }

    /** Crea un tema nuevo moviendo allí las preguntas seleccionadas. */
    fun createTopicFromQuestions(
        newTopicName: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean,
        avoidDuplicates: Boolean = true
    ): String? {
        val cleanName = newTopicName.trim()
        if (cleanName.isBlank() || questionIds.isEmpty()) return null

        val db = localStore.database.value
        val selectedQuestions = mutableListOf<Question>()
        val newTopicFingerprints = mutableSetOf<String>()

        db.topics.forEach { sourceTopic ->
            sourceTopic.questions
                .filter { it.id in questionIds }
                .forEach questionLoop@ { question ->
                    val fingerprint = createQuestionFingerprint(question)
                    if (avoidDuplicates && fingerprint in newTopicFingerprints) {
                        return@questionLoop
                    }

                    selectedQuestions += prepareForMove(question, sourceTopic.name, preserveOrigin)
                    if (avoidDuplicates) newTopicFingerprints += fingerprint
                }
        }

        if (selectedQuestions.isEmpty()) return null

        val movedIds = selectedQuestions.mapTo(mutableSetOf()) { it.id }
        val newTopicId = UUID.randomUUID().toString()
        val newTopic = Topic(
            id = newTopicId,
            name = cleanName,
            questions = selectedQuestions
        )

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { topic ->
                    topic.copy(questions = topic.questions.filterNot { it.id in movedIds })
                } + newTopic
            )
        )
        return newTopicId
    }

    fun deleteQuestions(topicId: String, questionIds: Set<String>): Int {
        if (questionIds.isEmpty()) return 0

        val db = localStore.database.value
        val topic = db.topics.firstOrNull { it.id == topicId } ?: return 0
        val questionsToDelete = topic.questions.filter { it.id in questionIds }
        if (questionsToDelete.isEmpty()) return 0

        val deletedIds = questionsToDelete.mapTo(mutableSetOf()) { it.id }
        questionsToDelete.forEach { question ->
            deleteQuestionImagesIfUnreferenced(
                question = question,
                db = db,
                excludedQuestionIds = deletedIds
            )
        }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map { currentTopic ->
                    if (currentTopic.id == topicId) {
                        currentTopic.copy(questions = currentTopic.questions.filterNot { it.id in deletedIds })
                    } else {
                        currentTopic
                    }
                }
            )
        )
        return questionsToDelete.size
    }

    fun duplicateQuestion(topicId: String, questionId: String) {
        val db = localStore.database.value
        val topic = db.topics.firstOrNull { it.id == topicId } ?: return
        val question = topic.questions.firstOrNull { it.id == questionId } ?: return

        val duplicatedStatementImage = imageManager.duplicatePhysicalImage(question.explanationImageUri)
        val duplicatedCorrectionImage = imageManager.duplicatePhysicalImage(question.correctionImageUri)

        val answerIdMap = question.answers.associate { answer ->
            answer.id to UUID.randomUUID().toString()
        }
        val duplicatedAnswers = question.answers.map { answer ->
            answer.copy(id = answerIdMap[answer.id] ?: UUID.randomUUID().toString())
        }

        addQuestion(
            topicId = topicId,
            question = question.copy(
                id = UUID.randomUUID().toString(),
                text = question.text + " copia",
                answers = duplicatedAnswers,
                correctAnswerId = answerIdMap[question.correctAnswerId]
                    ?: duplicatedAnswers.firstOrNull()?.id.orEmpty(),
                stats = com.example.testario.data.model.QuestionStats(),
                explanationImageUri = duplicatedStatementImage,
                correctionImageUri = duplicatedCorrectionImage
            )
        )
    }

    private fun createQuestionFingerprint(question: Question): String {
        val questionText = normalizeForFingerprint(question.text)
        val answersText = question.answers
            .map { normalizeForFingerprint(it.text) }
            .sorted()
            .joinToString("|")
        return "$questionText::$answersText"
    }

    private fun normalizeForFingerprint(text: String): String {
        return java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(fingerprintCombiningMarks, "")
            .replace(fingerprintNonAlnum, "")
    }

    private fun prepareForMove(question: Question, sourceTopicName: String, preserveOrigin: Boolean): Question {
        if (!preserveOrigin || !question.originTopicName.isNullOrBlank()) return question
        return question.copy(originTopicName = sourceTopicName)
    }

    private fun deleteQuestionImagesIfUnreferenced(
        question: Question,
        db: TestarioDatabase,
        excludedQuestionIds: Set<String>
    ) {
        deleteImageIfUnreferenced(question.explanationImageUri, db, excludedQuestionIds)
        deleteImageIfUnreferenced(question.correctionImageUri, db, excludedQuestionIds)
    }

    private fun deleteImageIfUnreferenced(
        path: String?,
        db: TestarioDatabase,
        excludedQuestionIds: Set<String>
    ) {
        if (path.isNullOrBlank()) return

        val stillReferenced = db.topics
            .asSequence()
            .flatMap { it.questions.asSequence() }
            .filterNot { it.id in excludedQuestionIds }
            .any { question ->
                question.explanationImageUri == path || question.correctionImageUri == path
            }

        if (!stillReferenced) {
            imageManager.deleteImage(path)
        }
    }
}
