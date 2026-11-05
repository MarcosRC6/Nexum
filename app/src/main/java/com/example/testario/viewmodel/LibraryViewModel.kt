package com.example.testario.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.repository.OrganizationRepository
import com.example.testario.data.repository.QuestionRepository
import com.example.testario.data.repository.TopicRepository
import com.example.testario.domain.ImageManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = TestarioLocalStore.get(application)

    // Instanciamos primero el ImageManager para pasárselo a los repositorios
    private val imageManager = ImageManager(application)
    private val topicRepo = TopicRepository(localStore, imageManager)
    private val questionRepo = QuestionRepository(localStore, imageManager)
    private val organizationRepo = OrganizationRepository(localStore)

    val database = localStore.database.stateIn(viewModelScope, SharingStarted.Eagerly, localStore.database.value)

    fun addTopic(name: String) = topicRepo.addTopic(name)
    fun updateTopic(topicId: String, name: String) = topicRepo.updateTopic(topicId, name)
    fun deleteTopic(topicId: String) = topicRepo.deleteTopic(topicId)
    fun combineTopics(sourceTopicIds: List<String>, newTopicName: String) = topicRepo.combineTopics(sourceTopicIds, newTopicName)
    fun togglePinTopic(topicId: String) = topicRepo.togglePinTopic(topicId)

    fun addQuestion(topicId: String, question: Question) {
        val safeStatementImageUri = imageManager.persistImageToInternalStorage(question.explanationImageUri)
        val safeCorrectionImageUri = imageManager.persistImageToInternalStorage(question.correctionImageUri)
        questionRepo.addQuestion(
            topicId,
            question.copy(
                explanationImageUri = safeStatementImageUri,
                correctionImageUri = safeCorrectionImageUri
            )
        )
    }

    fun updateQuestion(topicId: String, question: Question) {
        val safeStatementImageUri = imageManager.persistImageToInternalStorage(question.explanationImageUri)
        val safeCorrectionImageUri = imageManager.persistImageToInternalStorage(question.correctionImageUri)
        questionRepo.updateQuestion(
            topicId,
            question.copy(
                explanationImageUri = safeStatementImageUri,
                correctionImageUri = safeCorrectionImageUri
            )
        )
    }

    fun toggleQuestionFlag(questionId: String): Boolean = questionRepo.toggleQuestionFlag(questionId)

    fun deleteQuestion(topicId: String, questionId: String) = questionRepo.deleteQuestion(topicId, questionId)

    fun deleteQuestions(topicId: String, questionIds: Set<String>): Int =
        questionRepo.deleteQuestions(topicId, questionIds)

    fun moveQuestions(
        sourceTopicId: String,
        targetTopicId: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean = false,
        avoidDuplicates: Boolean = true
    ): Int = questionRepo.moveQuestions(
        sourceTopicId,
        targetTopicId,
        questionIds,
        preserveOrigin,
        avoidDuplicates
    )

    fun moveQuestionsFromMany(
        targetTopicId: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean,
        avoidDuplicates: Boolean = true
    ): Int = questionRepo.moveQuestionsFromMany(
        targetTopicId,
        questionIds,
        preserveOrigin,
        avoidDuplicates
    )

    fun createTopicFromQuestions(
        newTopicName: String,
        questionIds: Set<String>,
        preserveOrigin: Boolean,
        avoidDuplicates: Boolean = true
    ): String? = questionRepo.createTopicFromQuestions(
        newTopicName,
        questionIds,
        preserveOrigin,
        avoidDuplicates
    )

    fun duplicateQuestion(topicId: String, questionId: String) = questionRepo.duplicateQuestion(topicId, questionId)


    fun addGroup(name: String, parentGroupId: String? = null): String? =
        organizationRepo.addGroup(name, parentGroupId)

    fun renameGroup(groupId: String, newName: String): Boolean =
        organizationRepo.renameGroup(groupId, newName)

    fun setGroupTopics(groupId: String, topicIds: Set<String>): Boolean =
        organizationRepo.setGroupTopics(groupId, topicIds)

    fun moveGroup(groupId: String, newParentGroupId: String?): Boolean =
        organizationRepo.moveGroup(groupId, newParentGroupId)

    fun deleteGroup(groupId: String): Boolean = organizationRepo.deleteGroup(groupId)

    fun saveTestTemplate(template: TestTemplate): String? = organizationRepo.saveTemplate(template)

    fun deleteTestTemplate(templateId: String): Boolean = organizationRepo.deleteTemplate(templateId)

    fun updateQuestionDifficulty(topicId: String, questionId: String, difficulty: Int?) {
        topicRepo.updateQuestionDifficulty(topicId, questionId, difficulty)
    }
}