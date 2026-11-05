package com.example.testario.data.repository

import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.QuestionStats
import com.example.testario.data.model.Topic
import com.example.testario.domain.ImageManager
import java.util.UUID

// Inyectamos el ImageManager
class TopicRepository(
    private val localStore: TestarioLocalStore,
    private val imageManager: ImageManager
) {

    fun addTopic(name: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        val db = localStore.database.value
        localStore.saveDatabase(db.copy(topics = db.topics + Topic(name = cleanName)))
    }

    fun updateTopic(topicId: String, newName: String) {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map {
                    if (it.id == topicId) it.copy(name = cleanName) else it
                }
            )
        )
    }

    fun deleteTopic(topicId: String) {
        val db = localStore.database.value
        val topicToDelete = db.topics.firstOrNull { it.id == topicId }

        // Solo borramos una imagen física si ninguna pregunta de otro tema
        // sigue apuntando al mismo archivo.
        val deletedQuestionIds = topicToDelete
            ?.questions
            .orEmpty()
            .mapTo(hashSetOf()) { it.id }
        val referencedElsewhere = db.topics
            .asSequence()
            .flatMap { it.questions.asSequence() }
            .filterNot { it.id in deletedQuestionIds }
            .flatMap { question ->
                sequenceOf(question.explanationImageUri, question.correctionImageUri)
            }
            .filterNotNull()
            .toSet()

        topicToDelete?.questions?.forEach { question ->
            if (question.explanationImageUri !in referencedElsewhere) {
                imageManager.deleteImage(question.explanationImageUri)
            }
            if (question.correctionImageUri !in referencedElsewhere) {
                imageManager.deleteImage(question.correctionImageUri)
            }
        }

        val cleanedGroups = db.groups.map { group ->
            if (topicId in group.topicIds) group.copy(topicIds = group.topicIds.filterNot { it == topicId }) else group
        }
        val cleanedTemplates = db.testTemplates.map { template ->
            template.copy(
                selectedTopicIds = template.selectedTopicIds.filterNot { it == topicId },
                selectedFailureTopicIds = template.selectedFailureTopicIds.filterNot { it == topicId }
            )
        }

        localStore.saveDatabase(
            db.copy(
                topics = db.topics.filterNot { it.id == topicId },
                groups = cleanedGroups,
                testTemplates = cleanedTemplates
            )
        )
    }

    fun togglePinTopic(topicId: String) {
        val db = localStore.database.value
        localStore.saveDatabase(
            db.copy(
                topics = db.topics.map {
                    if (it.id == topicId) it.copy(isPinned = !it.isPinned) else it
                }
            )
        )
    }

    fun updateQuestionDifficulty(topicId: String, questionId: String, difficulty: Int?) {
        val db = localStore.database.value
        localStore.saveDatabase(
            newDb = db.copy(
                topics = db.topics.map { topic ->
                    if (topic.id == topicId) {
                        topic.copy(
                            questions = topic.questions.map { q ->
                                if (q.id == questionId) q.copy(difficulty = difficulty) else q
                            }
                        )
                    } else topic
                }
            )
        )
    }

    fun combineTopics(sourceTopicIds: List<String>, newTopicName: String) {
        val cleanName = newTopicName.trim()
        if (cleanName.isBlank() || sourceTopicIds.isEmpty()) return

        val db = localStore.database.value
        val sourceTopics = db.topics.filter { it.id in sourceTopicIds }

        val combinedQuestions = sourceTopics.flatMap { topic ->
            topic.questions.map { question ->
                // Al combinar, clonamos físicamente las imágenes para evitar dependencias rotas
                val duplicatedStatementImageUri = imageManager.duplicatePhysicalImage(question.explanationImageUri)
                val duplicatedCorrectionImageUri = imageManager.duplicatePhysicalImage(question.correctionImageUri)
                question.copy(
                    stats = QuestionStats(),
                    explanationImageUri = duplicatedStatementImageUri,
                    correctionImageUri = duplicatedCorrectionImageUri
                )
            }
        }

        // Regeneramos los IDs para evitar conflictos de referencias
        val finalQuestions = combinedQuestions.map { q ->
            val answerMap = q.answers.associate { it.id to UUID.randomUUID().toString() }
            val newAnswers = q.answers.map { it.copy(id = answerMap[it.id] ?: UUID.randomUUID().toString()) }
            q.copy(
                id = UUID.randomUUID().toString(),
                answers = newAnswers,
                correctAnswerId = answerMap[q.correctAnswerId] ?: newAnswers.firstOrNull()?.id.orEmpty()
            )
        }

        val newTopic = Topic(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            questions = finalQuestions
        )

        localStore.saveDatabase(db.copy(topics = db.topics + newTopic))
    }
}