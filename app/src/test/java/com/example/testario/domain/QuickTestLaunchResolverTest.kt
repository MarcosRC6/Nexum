package com.example.testario.domain

import com.example.testario.data.model.Answer
import com.example.testario.data.model.GroupQuestionAllocation
import com.example.testario.data.model.Question
import com.example.testario.data.model.QuestionStats
import com.example.testario.data.model.SavedFailureScope
import com.example.testario.data.model.SavedTestScope
import com.example.testario.data.model.TestMode
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic
import com.example.testario.data.model.TopicGroup
import org.junit.Assert.*
import org.junit.Test

class QuickTestLaunchResolverTest {
    private fun question(id: String, failed: Boolean = false, difficulty: Int? = null) =
        Question(
            id = id,
            text = "Pregunta $id",
            answers = listOf(Answer(id = "a-$id", text = "Sí"), Answer(id = "b-$id", text = "No")),
            correctAnswerId = "a-$id",
            difficulty = difficulty,
            stats = QuestionStats(inFailures = failed)
        )

    private val topicA = Topic(id = "a", name = "Tema A", questions = listOf(
        question("q1", failed = true, difficulty = 1),
        question("q2", difficulty = 2)
    ))
    private val topicB = Topic(id = "b", name = "Tema B", questions = listOf(
        question("q3", failed = true, difficulty = 3)
    ))

    @Test fun oldTopicCountLinkStillStartsAnUntimedExam() {
        val actual = QuickTestLaunchResolver.resolve(
            TestarioDatabase(topics = listOf(topicA)),
            setOf("a"), 25, null
        ) as QuickTestLaunchResult.Ready

        assertEquals(TestMode.EXAM, actual.config.mode)
        assertEquals(25, actual.config.questionCount)
        assertEquals(listOf("a"), actual.config.topicIds)
        assertFalse(actual.config.timerEnabled)
    }

    @Test fun templateOverridesManualLinkAndPreservesFullExamSettings() {
        val template = TestTemplate(
            id = "t1", name = "Simulacro", mode = TestMode.EXAM,
            scope = SavedTestScope.TOPIC, selectedTopicIds = listOf("b"),
            questionCount = 1, timerEnabled = true, timerMinutes = 12,
            pointsCorrect = 2.0, penaltyWrong = -0.5,
            difficultyFilterEnabled = true, easyPercent = 0, mediumPercent = 0, hardPercent = 100
        )
        val actual = QuickTestLaunchResolver.resolve(
            TestarioDatabase(topics = listOf(topicA, topicB), testTemplates = listOf(template)),
            setOf("a"), 25, "t1"
        ) as QuickTestLaunchResult.Ready

        assertEquals(listOf("b"), actual.config.topicIds)
        assertEquals(1, actual.config.questionCount)
        assertTrue(actual.config.timerEnabled)
        assertEquals(12, actual.config.timerMinutes)
        assertEquals(2.0, actual.config.pointsCorrect, 0.001)
        assertEquals(-0.5, actual.config.penaltyWrong, 0.001)
        assertTrue(actual.config.difficultyFilterEnabled)
    }

    @Test fun failuresTemplateUsesFailureScopeAndNoExamTimer() {
        val template = TestTemplate(
            id = "fail", name = "Falladas",
            scope = SavedTestScope.FAILURES, failureScope = SavedFailureScope.TOPIC,
            selectedFailureTopicIds = listOf("a"), questionCount = 1,
            mode = TestMode.EXAM, timerEnabled = true
        )
        val actual = QuickTestLaunchResolver.resolve(
            TestarioDatabase(topics = listOf(topicA, topicB), testTemplates = listOf(template)),
            emptySet(), 25, "fail"
        ) as QuickTestLaunchResult.Ready

        assertEquals(TestMode.FAILURES, actual.config.mode)
        assertEquals(listOf("a"), actual.config.topicIds)
        assertFalse(actual.config.timerEnabled)
    }

    @Test fun groupTemplateKeepsAllocations() {
        val template = TestTemplate(
            id = "groups", name = "Dos grupos",
            scope = SavedTestScope.GROUPS,
            groupAllocations = listOf(
                GroupQuestionAllocation("g1", 1),
                GroupQuestionAllocation("g2", 1)
            ), questionCount = 2
        )
        val db = TestarioDatabase(
            topics = listOf(topicA, topicB),
            groups = listOf(
                TopicGroup(id = "g1", name = "Uno", topicIds = listOf("a")),
                TopicGroup(id = "g2", name = "Dos", topicIds = listOf("b"))
            ),
            testTemplates = listOf(template)
        )
        val actual = QuickTestLaunchResolver.resolve(db, emptySet(), 25, "groups")
            as QuickTestLaunchResult.Ready
        assertEquals(2, actual.config.groupAllocations.size)
        assertEquals(2, actual.config.groupAllocations.sumOf { it.questionCount })
        assertEquals(setOf("a", "b"), actual.config.topicIds.toSet())
    }

    @Test fun deletedTemplateNeverSilentlyRunsAnotherTest() {
        val actual = QuickTestLaunchResolver.resolve(
            TestarioDatabase(topics = listOf(topicA)), setOf("a"), 25, "deleted"
        )
        assertTrue(actual is QuickTestLaunchResult.Invalid)
    }

    @Test fun impossibleGroupQuotaAndNoAvailableQuestionsAreRejected() {
        val invalidGroup = TestTemplate(
            id = "g", name = "Demasiadas",
            scope = SavedTestScope.GROUPS, questionCount = 3,
            groupAllocations = listOf(GroupQuestionAllocation("g1", 3))
        )
        val db = TestarioDatabase(
            topics = listOf(topicA),
            groups = listOf(TopicGroup(id = "g1", name = "Uno", topicIds = listOf("a"))),
            testTemplates = listOf(invalidGroup)
        )
        assertTrue(QuickTestLaunchResolver.resolve(db, emptySet(), 25, "g") is QuickTestLaunchResult.Invalid)

        val emptyTemplate = TestTemplate(
            id = "empty", name = "Vacía",
            scope = SavedTestScope.TOPIC, selectedTopicIds = listOf("missing"), questionCount = 1
        )
        assertTrue(
            QuickTestLaunchResolver.resolve(
                TestarioDatabase(topics = listOf(topicA), testTemplates = listOf(emptyTemplate)),
                emptySet(), 25, "empty"
            ) is QuickTestLaunchResult.Invalid
        )
    }
}
