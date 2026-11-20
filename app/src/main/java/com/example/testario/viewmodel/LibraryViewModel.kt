package com.example.testario.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.Question
import com.example.testario.data.model.StudyExercise
import com.example.testario.data.model.ReviewRating
import com.example.testario.data.repository.StudyExerciseRepository
import com.example.testario.domain.StudyImageStore
import com.example.testario.domain.AnkiReviewEngine
import java.util.UUID
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.repository.OrganizationRepository
import com.example.testario.data.repository.QuestionRepository
import com.example.testario.data.repository.TopicRepository
import com.example.testario.domain.ImageManager
import com.example.testario.widget.NexumQuickTestWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val localStore = TestarioLocalStore.get(application)
    val recoveryError = localStore.recoveryError

    // Instanciamos primero el ImageManager para pasárselo a los repositorios
    private val imageManager = ImageManager(application)
    private val topicRepo = TopicRepository(localStore, imageManager)
    private val questionRepo = QuestionRepository(localStore, imageManager)
    private val exerciseRepo = StudyExerciseRepository(localStore)
    private val exerciseImages = StudyImageStore(application)
    private val organizationRepo = OrganizationRepository(localStore)

    val database = localStore.database.stateIn(viewModelScope, SharingStarted.Eagerly, localStore.database.value)

    /** Persist in-flight imports, re-read verified local data, and report the visible topic count. */
    suspend fun refreshLibraryFromDisk(): Int = withContext(Dispatchers.IO) {
        localStore.refreshDatabaseFromDisk().topics.size
    }

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

    /** Keeps old questions unchanged. Media is verified before writing the database. */
    fun saveStudyExercise(topicId: String, exercise: StudyExercise, expectedRevision: Int? = null): Boolean {
        val stored = exercise.copy(
            baseImageUri = exerciseImages.persist(exercise.baseImageUri),
            flashcard = exercise.flashcard?.let { card ->
                card.copy(
                    frontImageUri = exerciseImages.persist(card.frontImageUri),
                    backImageUri = exerciseImages.persist(card.backImageUri)
                )
            }
        )
        return exerciseRepo.save(topicId, stored, expectedRevision)
    }

    fun duplicateStudyExercise(topicId: String, exerciseId: String): Boolean {
        val source = localStore.database.value.topics.firstOrNull { it.id == topicId }
            ?.exercises?.firstOrNull { it.id == exerciseId } ?: return false
        return exerciseRepo.save(
            topicId, com.example.testario.domain.StudyExerciseMerge.duplicate(source)
        )
    }

    fun deleteStudyExercise(topicId: String, exerciseId: String): Boolean =
        exerciseRepo.delete(topicId, exerciseId)

    /**
     * Advance only after the learner reveals the current solution.
     * The cursor belongs to the original card, not to each generated view.
     * Always-hidden fragments do not contribute additional rounds.
     */
    fun advanceClozeRound(exerciseId: String, expectedCursor: Int): Boolean {
        require(expectedCursor >= 0)
        val db = localStore.database.value
        val exercise = db.topics.asSequence().flatMap { it.exercises.asSequence() }
            .firstOrNull { it.id == exerciseId } ?: return false
        val groups = com.example.testario.domain.ClozeMarkupEngine
            .numberedGroups(exercise.flashcard?.gaps.orEmpty())
        if (groups.isEmpty() || exercise.draft) return false
        val old = db.ankiClozeCursors[exerciseId] ?: 0
        if (old != expectedCursor) return false
        localStore.saveDatabase(db.copy(
            ankiClozeCursors = db.ankiClozeCursors +
                (exerciseId to ((old + 1) % groups.size))
        ))
        return true
    }

    fun setAnkiReviewEnabled(enabled: Boolean) {
        val db = localStore.database.value
        localStore.saveDatabase(db.copy(ankiReviewEnabled = enabled))
    }

    /** User-submitted objective results are stored once per stable attempt ID. */
    fun recordWrittenAttempt(
        topicId: String, exercise: StudyExercise, submitted: List<String>,
        pointsCorrect: Double, penaltyWrong: Double, attemptId: String
    ): Boolean {
        require(attemptId.isNotBlank())
        val db = localStore.database.value
        if (db.exerciseAttempts.any { it.id == attemptId }) return false
        val result = com.example.testario.domain.StudyExerciseRules.gradeWritten(
            exercise, submitted, pointsCorrect, penaltyWrong
        )
        val attempt = com.example.testario.data.model.ExerciseAttempt(
            id = attemptId, exerciseId = exercise.id, topicId = topicId, kind = exercise.kind,
            timestampMillis = System.currentTimeMillis(),
            submittedText = submitted.toList(),
            correct = result.correct, incorrect = result.incorrect, omitted = result.omitted,
            scoreOutOfTen = result.scoreOutOfTen,
            pointsCorrect = pointsCorrect, penaltyWrong = penaltyWrong
        )
        localStore.saveDatabase(db.copy(exerciseAttempts = db.exerciseAttempts + attempt))
        return true
    }

    /** A review is only recorded on an explicit scheduled-review action. */
    fun recordAnkiReview(cardId: String, rating: ReviewRating, eventId: String = UUID.randomUUID().toString()): Boolean {
        val db = localStore.database.value
        if (!db.ankiReviewEnabled) return false
        val result = AnkiReviewEngine.apply(
            db.ankiReviewStates, db.ankiReviewEvents, eventId, cardId, rating,
            System.currentTimeMillis(), db.ankiScheduleSettings
        )
        if (!result.applied) return false
        localStore.saveDatabase(db.copy(
            ankiReviewStates = result.states,
            ankiReviewEvents = result.history
        ))
        return true
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

    fun saveTestTemplate(template: TestTemplate): String? {
        val result = organizationRepo.saveTemplate(template)
        if (result != null) NexumQuickTestWidgetProvider.updateAllWidgets(getApplication())
        return result
    }

    fun deleteTestTemplate(templateId: String): Boolean {
        val deleted = organizationRepo.deleteTemplate(templateId)
        if (deleted) NexumQuickTestWidgetProvider.updateAllWidgets(getApplication())
        return deleted
    }

    fun updateQuestionDifficulty(topicId: String, questionId: String, difficulty: Int?) {
        topicRepo.updateQuestionDifficulty(topicId, questionId, difficulty)
    }
}