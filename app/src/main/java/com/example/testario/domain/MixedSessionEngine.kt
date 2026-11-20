package com.example.testario.domain

import com.example.testario.data.model.*
import kotlin.random.Random
import java.util.UUID

/**
 * Pure selector and objective scorer. Legacy test sessions are not converted.
 * Manual Anki reveals are NEVER awarded points or written to the scheduler.
 */
object MixedSessionEngine {
    fun available(
        topics: List<Topic>, contentFilter: SessionContentFilter,
        mode: TestMode = TestMode.TRAINING, difficulties: Set<Int?> = emptySet(),
        clozeCursors: Map<String, Int> = emptyMap()
    ): List<MixedSessionItem> = buildList {
        topics.forEach { topic ->
            if (contentFilter == SessionContentFilter.TEST || contentFilter == SessionContentFilter.MIXED) {
                topic.questions.asSequence()
                    .filter { mode != TestMode.FAILURES || it.stats.inFailures }
                    .filter { difficulties.isEmpty() || it.difficulty in difficulties }
                    .forEach { question ->
                        add(MixedSessionItem(
                            id = "test:${question.id}", topicId = topic.id, topicName = topic.name,
                            type = MixedItemType.TEST, question = question
                        ))
                    }
            }
            if (mode != TestMode.FAILURES) {
                topic.exercises.asSequence().filterNot { it.draft }.forEach { exercise ->
                    val type = when (exercise.kind) {
                        StudyExerciseKind.WRITTEN_MULTI -> MixedItemType.WRITTEN
                        StudyExerciseKind.ANKI_CARD -> MixedItemType.ANKI
                    }
                    if (contentFilter == SessionContentFilter.MIXED ||
                        (contentFilter == SessionContentFilter.WRITTEN && type == MixedItemType.WRITTEN) ||
                        (contentFilter == SessionContentFilter.ANKI && type == MixedItemType.ANKI)
                    ) {
                        val variants = if (type == MixedItemType.ANKI) when (exercise.flashcard?.kind) {
                            FlashcardKind.REVERSED -> listOf("direct", "reverse")
                            FlashcardKind.IMAGE_OCCLUSION ->
                                exercise.flashcard.masks.map { it.id }
                            else -> listOf("direct")
                        } else listOf("direct")
                        variants.forEach { variant ->
                            add(MixedSessionItem(
                                id = if (variant == "direct") "exercise:${exercise.id}"
                                    else "exercise:${exercise.id}:$variant",
                                topicId = topic.id, topicName = topic.name,
                                type = type, variant = variant, exercise = exercise,
                                clozeCursor = clozeCursors[exercise.id] ?: 0
                            ))
                        }
                    }
                }
            }
        }
    }.distinctBy { it.id }

    fun create(
        topics: List<Topic>, config: TestConfig, contentFilter: SessionContentFilter,
        clozeCursors: Map<String, Int> = emptyMap(), random: Random = Random.Default
    ): MixedSessionSnapshot {
        require(contentFilter != SessionContentFilter.TEST) { "Los test normales usan el motor Testario existente." }
        require(config.mode != TestMode.FAILURES) { "El modo Fallos admite únicamente preguntas test." }
        require(config.questionCount > 0) { "La cantidad debe ser positiva." }
        require(config.pointsCorrect.isFinite() && config.pointsCorrect > 0)
        require(config.penaltyWrong.isFinite() && config.penaltyWrong <= 0)
        val eligible = available(topics, contentFilter, config.mode, config.difficulties, clozeCursors)
        val chosen = if (config.groupAllocations.isNotEmpty()) {
            val remaining = eligible.toMutableList()
            buildList {
                config.groupAllocations.forEach { allocation ->
                    require(allocation.questionCount > 0)
                    val candidates = remaining.filter { it.topicId in allocation.topicIds }.shuffled(random)
                    require(candidates.size >= allocation.questionCount) {
                        "No hay suficientes elementos sin repetir en el grupo seleccionado."
                    }
                    val selected = candidates.take(allocation.questionCount)
                    addAll(selected)
                    val selectedIds = selected.mapTo(hashSetOf()) { it.id }
                    remaining.removeAll { it.id in selectedIds }
                }
            }.also {
                require(it.size == config.questionCount) { "El reparto por grupos no coincide con la cantidad total." }
            }
        } else {
            require(eligible.size >= config.questionCount) {
                "Solo hay ${eligible.size} elementos disponibles para esta modalidad."
            }
            eligible.shuffled(random).take(config.questionCount)
        }
        // Preserve original correctAnswerId while balancing answer positions.
        val randomized = TestAnswerOrder.randomizeAndBalance(
            chosen.mapNotNull { it.question }
        ).associateBy { it.id }
        val mixed = chosen.map { item ->
            if (item.type == MixedItemType.TEST) item.copy(
                question = randomized[item.question?.id] ?: item.question
            ) else item
        }.shuffled(random)
        val snapshot = MixedSessionSnapshot(
            id = UUID.randomUUID().toString(),
            mode = config.mode, contentFilter = contentFilter,
            items = mixed, responses = mixed.map { MixedSessionResponse(it.id) },
            remainingSeconds = if (config.mode == TestMode.EXAM && config.timerEnabled)
                config.timerMinutes.also { require(it > 0 && it <= Int.MAX_VALUE / 60) } * 60
            else null,
            pointsCorrect = config.pointsCorrect,
            penaltyWrong = config.penaltyWrong
        )
        validate(snapshot)
        return snapshot
    }

    fun validate(snapshot: MixedSessionSnapshot) {
        require(snapshot.id.isNotBlank() && snapshot.contentFilter != SessionContentFilter.TEST)
        require(snapshot.items.isNotEmpty() && snapshot.items.size == snapshot.responses.size)
        require(snapshot.currentIndex in snapshot.items.indices)
        require(snapshot.elapsedSeconds >= 0 && (snapshot.remainingSeconds == null || snapshot.remainingSeconds >= 0))
        require(snapshot.pointsCorrect.isFinite() && snapshot.pointsCorrect > 0)
        require(snapshot.penaltyWrong.isFinite() && snapshot.penaltyWrong <= 0)
        val ids = snapshot.items.map { it.id }
        require(ids.size == ids.distinct().size && snapshot.responses.map { it.itemId } == ids) {
            "Respuestas desordenadas, faltantes o duplicadas."
        }
        snapshot.items.zip(snapshot.responses).forEach { (item, response) ->
            require(item.topicId.isNotBlank() && item.topicName.isNotBlank())
            when (item.type) {
                MixedItemType.TEST -> {
                    val q = requireNotNull(item.question)
                    require(item.exercise == null)
                    val idsForAnswers = q.answers.map { it.id }
                    require(idsForAnswers.isNotEmpty() && q.correctAnswerId in idsForAnswers)
                    require(response.selectedAnswerId == null || response.selectedAnswerId in idsForAnswers)
                }
                MixedItemType.WRITTEN -> {
                    require(item.question == null && item.exercise?.kind == StudyExerciseKind.WRITTEN_MULTI)
                    StudyExerciseRules.requireValid(requireNotNull(item.exercise))
                }
                MixedItemType.ANKI -> {
                    require(item.question == null && item.exercise?.kind == StudyExerciseKind.ANKI_CARD)
                    StudyExerciseRules.requireValid(requireNotNull(item.exercise))
                }
            }
        }
    }

    fun finish(snapshot: MixedSessionSnapshot): MixedSessionResult {
        validate(snapshot)
        var raw = 0.0
        var objective = 0
        var manuallyReviewed = 0
        var correct = 0
        var wrong = 0
        var blank = 0
        var written = 0
        snapshot.items.zip(snapshot.responses).forEach { (item, response) ->
            when (item.type) {
                MixedItemType.TEST -> {
                    objective++
                    val answer = response.selectedAnswerId
                    when {
                        answer == null -> blank++
                        answer == item.question!!.correctAnswerId -> {
                            correct++
                            raw += snapshot.pointsCorrect
                        }
                        else -> {
                            wrong++
                            raw += snapshot.penaltyWrong
                        }
                    }
                }
                MixedItemType.WRITTEN -> {
                    objective++
                    written++
                    val evaluated = StudyExerciseRules.gradeWritten(
                        requireNotNull(item.exercise),
                        response.writtenAnswers,
                        snapshot.pointsCorrect,
                        snapshot.penaltyWrong
                    )
                    raw += evaluated.scoreOutOfTen / 10.0 * snapshot.pointsCorrect
                }
                MixedItemType.ANKI -> manuallyReviewed++
            }
        }
        val totalPossible = objective * snapshot.pointsCorrect
        return MixedSessionResult(
            totalItems = snapshot.items.size, objectiveItems = objective,
            manualReviewItems = manuallyReviewed, correctTest = correct,
            wrongTest = wrong, blankTest = blank, writtenItems = written,
            scoreOutOfTen = if (objective == 0) null else
                (raw / totalPossible * 10.0).coerceIn(0.0, 10.0)
        )
    }
}
