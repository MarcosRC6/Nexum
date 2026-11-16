package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class TestHistoryAnalyticsTest {
    private fun question(id: String) = Question(
        id = id, text = "Pregunta " + id,
        answers = listOf(Answer(id = "a", text = "A"), Answer(id = "b", text = "B")),
        correctAnswerId = "b"
    )
    private fun answer(id: String, chosen: String?) =
        TestQuestionState(question = question(id), selectedAnswerId = chosen)

    @Test fun examsAreLimitedToFiveAndDeduplicatedBySessionId() {
        val db = TestarioDatabase()
        var history = emptyList<DetailedExam>()
        repeat(8) { index ->
            val result = TestResult(1,1,0,0,1.0,1.0,10.0,100)
            val attempt = TestHistoryAnalytics.detailed(
                db,listOf(answer("q$index","b")),result,TestMode.EXAM,12,"attempt-$index",index.toLong()
            )
            history = TestHistoryAnalytics.appendExam(history,attempt)
        }
        assertEquals(5, history.size)
        assertEquals("attempt-3",history.first().id)
        assertEquals("attempt-7",history.last().id)
        assertEquals(5, TestHistoryAnalytics.appendExam(history, history.last()).size)
    }

    @Test fun trainingSessionsAreNotStoredAsFullExams() {
        val item=TestHistoryAnalytics.detailed(TestarioDatabase(),emptyList(),
            TestResult(0,0,0,0,0.0,0.0,0.0,0),TestMode.TRAINING,0,"training")
        assertNull(item)
    }

    @Test fun trendsForGroupsAggregateDistinctTopicsIncludingNestedGroups() {
        val db = TestarioDatabase(
            topics=listOf(
                Topic(id="t1",name="Legislación",questions=listOf(question("q1"))),
                Topic(id="t2",name="Callejero",questions=listOf(question("q2")))
            ),
            groups=listOf(
                TopicGroup(id="g1",name="Grupo",topicIds=listOf("t1")),
                TopicGroup(id="g2",name="Subgrupo",parentGroupId="g1",topicIds=listOf("t2"))
            )
        )
        val first=TestHistoryAnalytics.trend(db,listOf(answer("q1","b"),answer("q2","a")),
            TestMode.EXAM,"s1",timestamp=1L)
        val second=TestHistoryAnalytics.trend(db,listOf(answer("q1","b"),answer("q2","b")),
            TestMode.EXAM,"s2",timestamp=2L)
        val history=listOf(first,second)
        assertEquals(listOf(50,100),TestHistoryAnalytics.series(history,"g1",true).map { it.percent })
        assertEquals(listOf(0,100),TestHistoryAnalytics.series(history,"g2",true).map { it.percent })
        assertEquals(listOf(100,100),TestHistoryAnalytics.series(history,"t1",false).map { it.percent })
    }

    @Test fun trendIsLimitedAndFrozenGroupMembershipIsStable() {
        val item=TestPerformancePoint(id="s",timestamp=1L,mode=TestMode.TRAINING,topics=listOf(
            TopicPerformancePoint("topic","Tema",listOf("group"),1,0,0)
        ))
        var history=emptyList<TestPerformancePoint>()
        repeat(36) { i -> history=TestHistoryAnalytics.appendTrend(history,item.copy(id="s$i")) }
        assertEquals(30,history.size)
        assertEquals("s6",history.first().id)
        assertEquals(100,TestHistoryAnalytics.series(history,"group",true).last().percent)
    }
}
