package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MarkerAndOcclusionEditorTest {
    @Test fun solutionPositionsFollowAnswersRatherThanTapOrder() {
        val answers = listOf(
            CanonicalSolution(id = "a", text = "Uno"),
            CanonicalSolution(id = "b", text = "Dos"),
            CanonicalSolution(id = "c", text = "Tres")
        )
        val thirdFirst = WrittenMarkerRules.place(answers, "c", .7, .5)
        assertEquals(3, thirdFirst[2].points.single().order)
        assertEquals(1, thirdFirst.sumOf { it.points.size })
        val second = WrittenMarkerRules.place(thirdFirst, "b", .2, .4)
        assertEquals(listOf(2, 3), second.flatMap { it.points }.map { it.order })
        val moved = WrittenMarkerRules.place(second, "b", .3, .4)
        assertEquals(1, moved[1].points.size)
        assertEquals(second[1].points.single().id, moved[1].points.single().id)
        val removed = WrittenMarkerRules.removeSolution(moved, "b")
        assertEquals(listOf("a", "c"), removed.map { it.id })
        assertEquals(2, removed[1].points.single().order)
    }

    @Test fun neverPlacePointForMissingAnswerOrOutsideImage() {
        val answers = listOf(CanonicalSolution(id="a",text="Uno"))
        assertThrows(IllegalArgumentException::class.java) {
            WrittenMarkerRules.place(answers, "missing", .3, .4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WrittenMarkerRules.place(answers, "a", -1.0, .4)
        }
    }

    @Test fun draggingAllSidesOfOcclusionRemainsOnImageAndPreservesIdentity() {
        val start = ImageOcclusionMask(id = "hidden", x = .2, y = .3, width = .2, height = .2)
        val toRight = OcclusionMaskGeometry.drag(start, MaskHandle.RIGHT, .4, 0.0)
        assertEquals(.6, toRight.width, .00001)
        val toLeft = OcclusionMaskGeometry.drag(toRight, MaskHandle.LEFT, .1, 0.0)
        assertEquals(.3, toLeft.x, .00001)
        val moved = OcclusionMaskGeometry.drag(toLeft, MaskHandle.MOVE, 10.0, 10.0)
        assertEquals(1.0, moved.x + moved.width, .00001)
        assertEquals(1.0, moved.y + moved.height, .00001)
        val shrunk = OcclusionMaskGeometry.drag(moved, MaskHandle.TOP_LEFT, 10.0, 10.0)
        assertTrue(shrunk.width >= .04 - 1e-8)
        assertTrue(shrunk.height >= .04 - 1e-8)
        assertEquals("hidden", shrunk.id)
    }

    @Test fun otherShortcutCategoryRequiresSafeCustomLabel() {
        val base = com.example.testario.data.local.WebQuickLink(
            title="Acceso",url="https://example.org",icon="other")
        assertNull(com.example.testario.data.local.WebQuickLinkPolicy.validate(base))
        val custom = base.copy(customIconLabel="Ayuntamiento")
        assertEquals("Ayuntamiento",
            com.example.testario.data.local.WebQuickLinkPolicy.validate(custom)?.customIconLabel)
        val restored = com.example.testario.data.local.WebQuickLinkPolicy.parseBackup(
            com.example.testario.data.local.WebQuickLinkPolicy.encodeBackup(listOf(custom)))
        assertEquals("Ayuntamiento", restored.single().customIconLabel)
    }
}
