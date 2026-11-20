package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import org.junit.Assert.*
import org.junit.Test

class WrittenMarkerRulesTest {
    private val original = listOf(
        CanonicalSolution(id = "a", text = "Primera"),
        CanonicalSolution(id = "b", text = "Segunda"),
        CanonicalSolution(id = "c", text = "Tercera")
    )

    @Test fun eachSolutionKeepsAtMostOneMarkerAndCanMoveIt() {
        val once = WrittenMarkerRules.place(original, "a", .2, .3)
        val twice = WrittenMarkerRules.place(once, "a", .8, .7)
        assertEquals(1, twice[0].points.size)
        assertEquals(1, twice[0].points.single().order)
        assertEquals(.8, twice[0].points.single().x, .0001)
    }

    @Test fun removingSolutionTwoRemovesItsPointAndRenumbersThreeAsTwo() {
        val three = WrittenMarkerRules.place(
            WrittenMarkerRules.place(
                WrittenMarkerRules.place(original, "a", .2, .2), "b", .4, .4),
            "c", .8, .8)
        val reduced = WrittenMarkerRules.withoutSolution(three, "b")
        assertEquals(listOf("a", "c"), reduced.map { it.id })
        assertEquals(listOf(1, 2), reduced.map { it.points.single().order })
        assertEquals(.8, reduced.last().points.single().x, .001)
    }

    @Test fun deletingMarkerOneDoesNotRenameSolutionThree() {
        val withThird = WrittenMarkerRules.place(original, "c", .7, .8)
        val withFirst = WrittenMarkerRules.place(withThird, "a", .1, .2)
        val stripped = WrittenMarkerRules.withoutMarker(withFirst, withFirst.first().points.single().id)
        assertTrue(stripped.first().points.isEmpty())
        assertEquals(3, stripped.last().points.single().order)
    }

    @Test fun blankAndUnknownSolutionsCannotBePlaced() {
        assertTrue(runCatching { WrittenMarkerRules.place(original, "missing", .1, .1) }.isFailure)
        assertTrue(runCatching { WrittenMarkerRules.place(
            listOf(CanonicalSolution(id = "blank", text = "")), "blank", .2, .3
        ) }.isFailure)
    }
}
