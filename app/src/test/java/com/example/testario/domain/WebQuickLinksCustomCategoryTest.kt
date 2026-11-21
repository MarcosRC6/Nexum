package com.example.testario.domain

import com.example.testario.data.local.WebQuickLink
import com.example.testario.data.local.WebQuickLinkPolicy
import org.junit.Assert.*
import org.junit.Test

class WebQuickLinksCustomCategoryTest {
    private fun link(icon: String = "other", label: String? = "Simulacros") =
        WebQuickLink(id = "id1", title = "Banco", url = "https://example.com",
            icon = icon, customIconLabel = label)

    @Test fun customIconIsSavedAndRestoredInGlobalBackupFormat() {
        val normalized = requireNotNull(WebQuickLinkPolicy.validate(link()))
        val decoded = WebQuickLinkPolicy.parseBackup(WebQuickLinkPolicy.encodeBackup(listOf(normalized)))
        assertEquals("other", decoded.single().icon)
        assertEquals("Simulacros", decoded.single().customIconLabel)
    }

    @Test fun otherRequiresNonemptySafeLabel() {
        assertNull(WebQuickLinkPolicy.validate(link(label = "")))
        assertNull(WebQuickLinkPolicy.validate(link(label = "a".repeat(29))))
        assertNull(WebQuickLinkPolicy.validate(link(label = "X\u0000bad")))
    }

    @Test fun oldWebLinksWithoutCustomCategoryStillDecode() {
        val old = """{"format":"nexum.quick_web_links.v1","links":[{"id":"id1","title":"Mapa","url":"https://example.com","icon":"map","hidden":false}]}"""
        val decoded = WebQuickLinkPolicy.parseBackup(old)
        assertEquals("map", decoded.single().icon)
        assertNull(decoded.single().customIconLabel)
    }
}
