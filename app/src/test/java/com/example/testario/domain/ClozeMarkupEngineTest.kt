package com.example.testario.domain

import com.example.testario.data.model.ClozeGap
import org.junit.Assert.*
import org.junit.Test

class ClozeMarkupEngineTest {
    @Test fun alwaysHiddenAndNumberedAlternateWithoutRevealingTheOthers() {
        val parsed = ClozeMarkupEngine.parse(
            "El río {{1:Ebro}} pasa por {{2:Zaragoza}} en {{España}}."
        )
        assertEquals("El río Ebro pasa por Zaragoza en España.", parsed.text)
        assertEquals(listOf(1, 2), ClozeMarkupEngine.numberedGroups(parsed.gaps))
        assertEquals("El río ________ pasa por Zaragoza en ________.",
            ClozeMarkupEngine.mask(parsed.text, parsed.gaps, 0))
        assertEquals("El río Ebro pasa por ________ en ________.",
            ClozeMarkupEngine.mask(parsed.text, parsed.gaps, 1))
        assertEquals(ClozeMarkupEngine.mask(parsed.text, parsed.gaps, 0),
            ClozeMarkupEngine.mask(parsed.text, parsed.gaps, 2))
    }

    @Test fun sameNumberHidesTogetherWhileUntaggedAlwaysHidden() {
        val p = ClozeMarkupEngine.parse("{{1:a}} y {{1:b}}; {{2:c}} y {{d}}.")
        assertEquals("________ y ________; c y ________.",
            ClozeMarkupEngine.mask(p.text, p.gaps, 0))
        assertEquals("a y b; ________ y ________.",
            ClozeMarkupEngine.mask(p.text, p.gaps, 1))
    }

    @Test fun otherBracketSyntaxIsSupportedAndRoundTrips() {
        val p = ClozeMarkupEngine.parse("La [[1:bomba]] y la [[2:manguera]] son {{útiles}}.")
        assertEquals("La bomba y la manguera son útiles.", p.text)
        val reparsed = ClozeMarkupEngine.parse(ClozeMarkupEngine.encode(p.text,p.gaps))
        assertEquals(p.text,reparsed.text)
        assertEquals(p.gaps.map{Triple(it.start,it.end,it.groupNumber)},
            reparsed.gaps.map{Triple(it.start,it.end,it.groupNumber)})
    }

    @Test fun unnumberedOnlyHidesEverythingOnEveryRepeat() {
        val p = ClozeMarkupEngine.parse("{{uno}} y {{dos}}")
        assertNull(ClozeMarkupEngine.activeGroup(p.gaps,25))
        assertEquals("________ y ________",ClozeMarkupEngine.mask(p.text,p.gaps,25))
    }

    @Test fun safelyEscapesLiteralBracesAndRejectsMalformedTokens() {
        val p = ClozeMarkupEngine.parse("Literal \\{{no es hueco}} más {{sí}}")
        assertEquals("Literal {{no es hueco}} más sí",p.text)
        assertEquals(1,p.gaps.size)
        assertThrows(IllegalArgumentException::class.java) {
            ClozeMarkupEngine.parse("Texto {{1:sin cierre")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ClozeMarkupEngine.parse("Texto {{1:}}")
        }
    }

    @Test fun markSelectionWrapsExactSubstringWithoutChangingOthers() {
        val text = "Capital Zaragoza y Aragón"
        val s = text.indexOf("Zaragoza")
        val e = s + "Zaragoza".length
        val marked = ClozeMarkupEngine.markSelection(text, s,e,2)
        assertEquals("Capital {{2:Zaragoza}} y Aragón",marked)
        assertEquals(text,ClozeMarkupEngine.parse(marked).text)
    }

    @Test fun legacyGapsKeepTheirPositionsAndDefaultAlways() {
        val text="La velocidad es 30 km/h"
        val gap = ClozeGap(start = 16, end=18)
        val repr = ClozeMarkupEngine.encode(text,listOf(gap))
        val p = ClozeMarkupEngine.parse(repr)
        assertEquals(text,p.text)
        assertEquals(listOf(null),p.gaps.map { it.groupNumber })
    }
}
