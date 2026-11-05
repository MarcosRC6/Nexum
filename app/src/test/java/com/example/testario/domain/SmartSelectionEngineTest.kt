package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import com.example.testario.data.model.TestConfig
import com.example.testario.data.model.TestGroupAllocation
import com.example.testario.data.model.TestMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartSelectionEngineTest {

    private val engine = SmartSelectionEngine()

    private fun question(id: String): Question {
        val a = Answer(id = "${id}_a", text = "A")
        val b = Answer(id = "${id}_b", text = "B")
        val c = Answer(id = "${id}_c", text = "C")
        return Question(
            id = id,
            text = "Pregunta $id",
            answers = listOf(a, b, c),
            correctAnswerId = a.id
        )
    }

    private fun config(
        count: Int,
        allocations: List<TestGroupAllocation>
    ) = TestConfig(
        mode = TestMode.EXAM,
        topicIds = emptyList(),
        questionCount = count,
        difficulties = emptySet(),
        randomOrder = false,
        difficultyFilterEnabled = false,
        groupAllocations = allocations
    )

    @Test
    fun overlappingGroupsAreSatisfiedWithoutRepeatingQuestions() {
        val q1 = question("q1")
        val q2 = question("q2")
        val q3 = question("q3")
        val q4 = question("q4")

        val available = mapOf(
            "t1" to listOf(q1, q2, q3),
            "t2" to listOf(q2, q3, q4)
        )
        val allocations = listOf(
            TestGroupAllocation("g1", listOf("t1"), 2),
            TestGroupAllocation("g2", listOf("t2"), 2)
        )

        assertTrue(engine.canFulfillGroupedSelection(available, allocations))

        val result = engine.generateGroupedSmartSelection(
            available,
            config(4, allocations),
            prioritizeFailuresInNormalTests = false
        )

        assertEquals(4, result.size)
        assertEquals(4, result.map { it.id }.toSet().size)
    }

    @Test
    fun impossibleOverlappingQuotasAreRejected() {
        val q1 = question("q1")
        val q2 = question("q2")
        val q3 = question("q3")

        val available = mapOf(
            "t1" to listOf(q1, q2),
            "t2" to listOf(q2, q3)
        )
        val allocations = listOf(
            TestGroupAllocation("g1", listOf("t1"), 2),
            TestGroupAllocation("g2", listOf("t2"), 2)
        )

        assertFalse(engine.canFulfillGroupedSelection(available, allocations))
        assertTrue(
            engine.generateGroupedSmartSelection(
                available,
                config(4, allocations),
                prioritizeFailuresInNormalTests = false
            ).isEmpty()
        )
    }

    @Test
    fun normalSelectionNeverReturnsSameQuestionTwiceAcrossTopics() {
        val shared = question("shared")
        val available = mapOf(
            "t1" to listOf(shared, question("q1")),
            "t2" to listOf(shared, question("q2"))
        )

        val result = engine.generateSmartSelection(
            availableByTopic = available,
            config = config(3, emptyList()),
            prioritizeFailuresInNormalTests = false
        )

        assertEquals(result.size, result.map { it.id }.toSet().size)
        assertEquals(3, result.size)
    }
}
