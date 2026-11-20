package com.example.testario.domain

import com.example.testario.data.local.WebQuickLink
import com.example.testario.data.local.WebQuickLinkPolicy
import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class Beta3QaRegressionTest {
    private fun solutions() = listOf(
        CanonicalSolution(id = "a", text = "Alfa"),
        CanonicalSolution(id = "b", text = "Beta"),
        CanonicalSolution(id = "c", text = "Gamma")
    )

    @Test fun cannotPlaceMoreNewMarkersThanWrittenSolutions() {
        var entries = solutions()
        entries = WrittenMarkerRules.place(entries, "a", .1, .2)
        entries = WrittenMarkerRules.place(entries, "b", .3, .4)
        entries = WrittenMarkerRules.place(entries, "c", .6, .7)
        assertEquals(3, WrittenMarkerRules.activeMarkerCount(entries))
        val firstId = entries[0].points.single().id
        entries = WrittenMarkerRules.place(entries, "a", .25, .35)
        assertEquals(3, WrittenMarkerRules.activeMarkerCount(entries))
        assertEquals(firstId, entries[0].points.single().id)
        assertEquals(.25, entries[0].points.single().x, .00001)
        assertEquals(entries, WrittenMarkerRules.place(entries, "invalid", .9, .9))
    }

    @Test fun removeSolutionDeletesOnlyItsMarkerAndRenumbersFollowing() {
        var entries = solutions()
        entries = WrittenMarkerRules.place(entries, "a", .1, .1)
        entries = WrittenMarkerRules.place(entries, "b", .5, .5)
        entries = WrittenMarkerRules.place(entries, "c", .8, .8)
        val preservedThirdId = entries[2].points.single().id
        val updated = WrittenMarkerRules.removeSolution(entries, "b")
        assertEquals(listOf("a","c"),updated.map { it.id })
        assertEquals(listOf(1,2),updated.flatMap { it.points }.map { it.order })
        assertEquals(preservedThirdId, updated[1].points.single().id)
    }

    @Test fun numberedImageRejectsSwappedAnswersButUnorderedListAcceptsThem() {
        val base = solutions()
        val withMarkers = base.mapIndexed { i, sol ->
            sol.copy(points = listOf(ImagePoint(id = "marker-${i}", x = .1 + i*.25,
                y = .4, order = i+1)))
        }
        val positioned = StudyExercise(id = "numbered", kind = StudyExerciseKind.WRITTEN_MULTI,
            prompt = "¿Qué hay en 1, 2 y 3?", solutions = withMarkers,
            baseImageUri = "/private/test.png")
        val unordered = positioned.copy(
            id = "unordered", baseImageUri = null,
            solutions = base
        )
        val typed = listOf("Beta", "Alfa", "Gamma")
        val a = StudyExerciseRules.gradeWritten(positioned, typed, 1.0, -.25)
        val b = StudyExerciseRules.gradeWritten(unordered, typed, 1.0, -.25)
        assertEquals(setOf("c"), a.correctIds)
        assertEquals(2, a.incorrect)
        assertEquals(0, a.omitted)
        assertEquals(1.666666, a.scoreOutOfTen, .001)
        assertEquals(3, b.correct)
        assertEquals(10.0, b.scoreOutOfTen, .001)
    }

    @Test fun missingNumberedAnswerIsOmittedAndItsMarkerNotCorrect() {
        val entries = solutions().mapIndexed { i, sol ->
            sol.copy(points = listOf(ImagePoint(id="p-${i}", x=.2+i*.2,y=.3,order=i+1)))
        }
        val exercise = StudyExercise(kind=StudyExerciseKind.WRITTEN_MULTI,prompt="Puntos",
            solutions=entries,baseImageUri="/private/map.png")
        val result = StudyExerciseRules.gradeWritten(
            exercise, listOf("Alfa","","Gamma"), 1.0,-.25
        )
        assertEquals(2,result.correct)
        assertEquals(0,result.incorrect)
        assertEquals(1,result.omitted)
        assertEquals(setOf("a","c"),result.correctIds)
    }

    @Test fun ankiMaskRectanglesCanMoveAndResizeWithoutEscapingImage() {
        val original = ImageOcclusionMask(id="x",x=.3,y=.3,width=.2,height=.2)
        val moved = OcclusionGeometry.transform(original,
            OcclusionGeometry.Handle.MOVE,2.0,2.0)
        assertEquals(.8,moved.x,.00001)
        assertEquals(.8,moved.y,.00001)
        val enlarged = OcclusionGeometry.transform(original,
            OcclusionGeometry.Handle.BOTTOM_RIGHT,3.0,3.0)
        assertEquals(1.0,enlarged.x+enlarged.width,.00001)
        assertEquals(1.0,enlarged.y+enlarged.height,.00001)
        val tiny = OcclusionGeometry.transform(original,
            OcclusionGeometry.Handle.TOP_LEFT,.999,.999)
        assertTrue(tiny.width >= .035-1e-8)
        assertTrue(tiny.height >= .035-1e-8)
    }

    @Test fun legacyTinyOcclusionHandlesNeverThrowOrEscapeCanvas() {
        val tiny = ImageOcclusionMask(id="tiny",x=.0,y=.0,width=.01,height=.01)
        OcclusionGeometry.Handle.entries.forEach { handle ->
            val resized = OcclusionGeometry.transform(tiny,handle,-.8,-.8)
            assertTrue(resized.x >= 0.0 && resized.y >= 0.0)
            assertTrue(resized.width > 0 && resized.height > 0)
            assertTrue(resized.x + resized.width <= 1.0)
            assertTrue(resized.y + resized.height <= 1.0)
        }
    }

    @Test fun customOtherWebLinkRoundTripsInCompatibleGlobalBackup() {
        val link = WebQuickLink(id="other-1",title="Herramientas",url="https://example.org",
            icon="other",customTypeLabel="Material auxiliar")
        val validated = WebQuickLinkPolicy.validate(link)
        assertNotNull(validated)
        val restored = WebQuickLinkPolicy.parseBackup(
            WebQuickLinkPolicy.encodeBackup(listOf(validated!!))
        ).single()
        assertEquals("other",restored.icon)
        assertEquals("Material auxiliar",restored.customTypeLabel)
        assertNull(WebQuickLinkPolicy.validate(link.copy(customTypeLabel = "")))
        assertNotNull(WebQuickLinkPolicy.validate(link.copy(icon = "web")))
    }
}
