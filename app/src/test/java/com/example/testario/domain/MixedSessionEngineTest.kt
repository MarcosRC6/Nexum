package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MixedSessionEngineTest {
    private fun question(id: String) = Question(
        id = id, text = "Pregunta $id",
        answers = listOf(Answer("$id-a", "Sí"), Answer("$id-b", "No"), Answer("$id-c", "Quizá")),
        correctAnswerId = "$id-a"
    )

    private fun written(id: String) = StudyExercise(
        id = id, kind = StudyExerciseKind.WRITTEN_MULTI,
        prompt = "Enumera", solutions = listOf(
            CanonicalSolution(id = "$id-1", text = "Edison"),
            CanonicalSolution(id = "$id-2", text = "Cajal")
        )
    )

    private fun card(id: String) = StudyExercise(
        id = id, kind = StudyExerciseKind.ANKI_CARD, prompt = "Capital",
        flashcard = FlashcardContent(front = "Capital de Francia", back = "París")
    )

    private fun source() = listOf(
        Topic(id = "1", name = "Tema 1", questions = listOf(question("q1")), exercises = listOf(written("w1"),card("a1"))),
        Topic(id = "2", name = "Tema 2", questions = listOf(question("q2")), exercises = listOf(written("w2"),card("a2")))
    )

    private fun config(count: Int, mode: TestMode = TestMode.EXAM) = TestConfig(
        mode = mode, topicIds = listOf("1","2"), questionCount = count,
        difficulties = emptySet(), pointsCorrect = 1.0, penaltyWrong = -0.25
    )

    @Test fun mixedExamRetainsAllThreeKindsAndUniqueIds() {
        val snapshot = MixedSessionEngine.create(source(), config(6), SessionContentFilter.MIXED, random = Random(3))
        assertEquals(6, snapshot.items.size)
        assertEquals(2, snapshot.items.count { it.type == MixedItemType.TEST })
        assertEquals(2, snapshot.items.count { it.type == MixedItemType.WRITTEN })
        assertEquals(2, snapshot.items.count { it.type == MixedItemType.ANKI })
        assertEquals(snapshot.items.map { it.id }, snapshot.responses.map { it.itemId })
    }

    @Test fun ankiOnlyHasNoObjectiveGrade() {
        val snapshot = MixedSessionEngine.create(source(), config(2), SessionContentFilter.ANKI, random = Random(4))
        val result = MixedSessionEngine.finish(snapshot)
        assertNull(result.scoreOutOfTen)
        assertEquals(0, result.objectiveItems)
        assertEquals(2, result.manualReviewItems)
    }

    @Test fun objectiveGradeExcludesSelfEvaluatedCards() {
        val initial = MixedSessionEngine.create(source(), config(3), SessionContentFilter.MIXED, random = Random(4))
        val desired = listOf(
            initial.items.first { it.type == MixedItemType.TEST },
            initial.items.first { it.type == MixedItemType.WRITTEN },
            initial.items.first { it.type == MixedItemType.ANKI }
        )
        val snapshot = initial.copy(
            items = desired,
            responses = listOf(
                MixedSessionResponse(desired[0].id, selectedAnswerId = desired[0].question!!.correctAnswerId),
                MixedSessionResponse(desired[1].id, writtenAnswers = listOf("Edison","Cajal")),
                MixedSessionResponse(desired[2].id, revealed = true)
            ),
            currentIndex = 0
        )
        val result = MixedSessionEngine.finish(snapshot)
        assertEquals(2, result.objectiveItems)
        assertEquals(1, result.manualReviewItems)
        assertEquals(10.0, result.scoreOutOfTen!!, 0.001)
    }

    @Test fun groupAllocationDoesNotRepeatAcrossOverlappingGroups() {
        val groups = listOf(
            TestGroupAllocation("g1", listOf("1","2"), 2),
            TestGroupAllocation("g2", listOf("1","2"), 2)
        )
        val base = config(4).copy(groupAllocations = groups)
        val selected = MixedSessionEngine.create(source(), base, SessionContentFilter.MIXED, random = Random(2))
        assertEquals(4, selected.items.map { it.id }.distinct().size)
    }

    @Test fun failsInsteadOfSilentlyTruncatingUnavailableCount() {
        val attempt = runCatching {
            MixedSessionEngine.create(source(), config(3), SessionContentFilter.WRITTEN, random = Random(2))
        }
        assertTrue(attempt.isFailure)
    }

    @Test fun snapshotTamperingOrInvalidSelectedAnswerIsRejected() {
        val initial = MixedSessionEngine.create(source(), config(2), SessionContentFilter.WRITTEN)
        assertTrue(runCatching { MixedSessionEngine.validate(initial.copy(responses = emptyList())) }.isFailure)
        val test = MixedSessionEngine.create(source(), config(1), SessionContentFilter.MIXED, random = Random(1))
        if (test.items.single().type == MixedItemType.TEST)
            assertTrue(runCatching { MixedSessionEngine.validate(test.copy(
                responses = listOf(MixedSessionResponse(test.items.single().id, selectedAnswerId = "wrong-id"))
            )) }.isFailure)
    }

    @Test fun legacyQuestionBankIsUntouchedBySelection() {
        val topics = source()
        val original = topics.first().questions.single().correctAnswerId
        MixedSessionEngine.create(topics, config(6), SessionContentFilter.MIXED)
        assertEquals(original, topics.first().questions.single().correctAnswerId)
    }
}
