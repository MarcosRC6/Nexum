package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import org.junit.Assert.*
import org.junit.Test

class WrittenMarkerRulesTest {
    private val sample = listOf(
        CanonicalSolution(id = "a", text = "A"),
        CanonicalSolution(id = "b", text = "B"),
        CanonicalSolution(id = "c", text = "C")
    )

    @Test fun chosenSolutionOwnsMarkerWithItsNumber() {
        val first = WrittenMarkerRules.place(sample, "b", .2, .4)
        assertTrue(first[0].points.isEmpty())
        assertEquals(2, first[1].points.single().order)
        assertTrue(first[2].points.isEmpty())
    }

    @Test fun cannotPlaceTwiceOnSameSolutionOrInEmptySolution() {
        val one = WrittenMarkerRules.place(sample, "a", .1, .1)
        assertTrue(runCatching { WrittenMarkerRules.place(one, "a", .8, .8) }.isFailure)
        assertTrue(runCatching {
            WrittenMarkerRules.place(sample + CanonicalSolution(id = "x", text = ""), "x", .5, .5)
        }.isFailure)
        assertTrue(runCatching { WrittenMarkerRules.place(sample, "c", 2.0, .5) }.isFailure)
    }

    @Test fun deletingSecondOfThreeRenumbersThirdAndKeepsMarkerId() {
        val withFirst = WrittenMarkerRules.place(sample, "a", .1, .1)
        val withThird = WrittenMarkerRules.place(withFirst, "c", .8, .8)
        val thirdMarkerId = withThird[2].points.single().id
        val result = WrittenMarkerRules.withoutSolution(withThird, "b")
        assertEquals(listOf("a", "c"), result.map { it.id })
        assertEquals(listOf(1, 2), result.map { it.points.single().order })
        assertEquals(thirdMarkerId, result[1].points.single().id)
    }

    @Test fun removingMarkerDoesNotDeleteWrittenAnswer() {
        val list = WrittenMarkerRules.place(sample, "b", .2, .3)
        val id = list[1].points.single().id
        val result = WrittenMarkerRules.withoutMarker(list, id)
        assertEquals("B", result[1].text)
        assertTrue(result[1].points.isEmpty())
        assertEquals(3, result.size)
    }
}
