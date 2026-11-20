package com.example.testario.domain

import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.local.PreferencesManager
import com.example.testario.data.model.*
import kotlin.math.roundToInt

/**
 * One idempotent, durable completion. Never changes Anki scheduling.
 * Legacy question statistics are updated from original stable answer IDs.
 */
class MixedSessionRepository(
    private val store: TestarioLocalStore,
    private val preferences: PreferencesManager
) {
    suspend fun registerCompletion(snapshot: MixedSessionSnapshot): Boolean {
        val result = MixedSessionEngine.finish(snapshot)
        val before = store.database.value
        if (before.mixedSessionHistory.any { it.id == snapshot.id }) return false

        val timestamp = System.currentTimeMillis()
        val streakToExit = preferences.getFailureExitCorrectStreak()
        val questions = snapshot.items.zip(snapshot.responses)
            .filter { (item, _) -> item.type == MixedItemType.TEST }
            .associate { (item, response) -> item.id to (requireNotNull(item.question) to response) }
        val exercises = snapshot.items.zip(snapshot.responses)
            .filter { (item, _) -> item.type == MixedItemType.WRITTEN }
            .map { (item, response) ->
                val written = StudyExerciseRules.gradeWritten(
                    requireNotNull(item.exercise), response.writtenAnswers,
                    snapshot.pointsCorrect, snapshot.penaltyWrong
                )
                ExerciseAttempt(
                    id = "mixed:${snapshot.id}:${item.id}",
                    exerciseId = item.exercise.id,
                    topicId = item.topicId,
                    kind = StudyExerciseKind.WRITTEN_MULTI,
                    timestampMillis = timestamp,
                    submittedText = response.writtenAnswers,
                    correct = written.correct,
                    incorrect = written.incorrect,
                    omitted = written.omitted,
                    scoreOutOfTen = written.scoreOutOfTen,
                    pointsCorrect = snapshot.pointsCorrect,
                    penaltyWrong = snapshot.penaltyWrong
                )
            }
        val topicUpdates = before.topics.map { topic ->
            topic.copy(questions = topic.questions.map { question ->
                val (frozenQuestion, response) = questions["test:${question.id}"] ?: return@map question
                // Grade against the snapshot, not an edited source question.
                // If the original answer set/correct ID changed during a saved
                // exam, preserve the historical result without corrupting the
                // newly edited question's current statistics.
                if (frozenQuestion.correctAnswerId != question.correctAnswerId ||
                    frozenQuestion.answers.map { it.id }.toSet() !=
                    question.answers.map { it.id }.toSet()) return@map question
                val correct = response.selectedAnswerId == frozenQuestion.correctAnswerId
                val blank = response.selectedAnswerId == null
                val wrong = !correct && !blank
                val streak = if (correct) question.stats.correctStreak + 1 else 0
                question.copy(stats = question.stats.copy(
                    timesAnswered = question.stats.timesAnswered + 1,
                    timesCorrect = question.stats.timesCorrect + if (correct) 1 else 0,
                    timesWrong = question.stats.timesWrong + if (wrong) 1 else 0,
                    timesBlank = question.stats.timesBlank + if (blank) 1 else 0,
                    lastAnswerId = response.selectedAnswerId,
                    lastAnsweredAt = timestamp,
                    correctStreak = streak,
                    inFailures = when {
                        wrong -> true
                        correct && streak >= streakToExit -> false
                        else -> question.stats.inFailures
                    },
                    totalTimeSpentSeconds = question.stats.totalTimeSpentSeconds + response.timeSpentSeconds
                ))
            })
        }

        // Cloze turn rotation belongs to note presentation, NOT to Anki
        // scheduling. Advance once only when the user actually revealed a
        // numbered gap. Persist it with the same durable DB transaction.
        val nextClozeCursors = before.ankiClozeCursors.toMutableMap()
        snapshot.items.zip(snapshot.responses).forEach { (item, response) ->
            val exercise = item.exercise
            if (item.type == MixedItemType.ANKI && response.revealed &&
                exercise?.flashcard?.kind == FlashcardKind.CLOZE
            ) {
                val groups = ClozeMarkupEngine.numberedGroups(exercise.flashcard.gaps)
                if (groups.isNotEmpty()) {
                    val old = nextClozeCursors[exercise.id] ?: 0
                    if (old == item.clozeCursor) nextClozeCursors[exercise.id] = (old + 1) % groups.size
                }
            }
        }

        val record = MixedSessionHistory(
            id = snapshot.id, timestampMillis = timestamp,
            mode = snapshot.mode, contentFilter = snapshot.contentFilter,
            totalItems = result.totalItems, objectiveItems = result.objectiveItems,
            manualReviewItems = result.manualReviewItems,
            scoreOutOfTen = result.scoreOutOfTen,
            timeSpentSeconds = snapshot.elapsedSeconds,
            snapshot = if (snapshot.mode == TestMode.EXAM) snapshot.copy(completed = true) else null
        )
        val all = (before.mixedSessionHistory + record).takeLast(30)
        val detailedIds = all.filter { it.snapshot != null }.takeLast(5)
            .mapTo(hashSetOf()) { it.id }
        val boundedHistory = all.map { item ->
            if (item.id in detailedIds) item else item.copy(snapshot = null)
        }
        val oldGradeHistory = before.testHistory
        val extended = if (result.scoreOutOfTen == null) oldGradeHistory else
            (oldGradeHistory + HistoricalResult(
                id = snapshot.id, timestamp = timestamp, score = result.scoreOutOfTen,
                percent = (result.scoreOutOfTen * 10.0).roundToInt()
            )).takeLast(10)
        val upgraded = before.copy(
            topics = topicUpdates,
            ankiClozeCursors = nextClozeCursors,
            exerciseAttempts = before.exerciseAttempts + exercises,
            mixedSessionHistory = boundedHistory,
            testHistory = extended,
            testsDone = before.testsDone + 1,
            totalTrainingMinutes = before.totalTrainingMinutes + (snapshot.elapsedSeconds / 60L).toInt()
        )
        store.saveDatabaseDurably(before, upgraded)
        return true
    }
}
