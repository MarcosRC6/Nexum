package com.example.testario.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebQuickLinkPolicyTest {
    @Test fun bareDomainsDefaultToHttpsAndKeepTheirPaths() {
        assertEquals("https://academia.example.com/moodle",
            WebQuickLinkPolicy.normalizeUrl("academia.example.com/moodle"))
        assertEquals("https://maps.example.com/?zoom=10",
            WebQuickLinkPolicy.normalizeUrl("https://maps.example.com/?zoom=10"))
    }

    @Test fun disallowUnsafeSchemesCredentialsAndMalformedUrls() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "content://host/file",
            "intent://abc", "data:text/html,x", "https://user@sample.invalid",
            "ftp://example.com", "https://", "https://bad url.com", "localhost").forEach {
            assertNull(it, WebQuickLinkPolicy.normalizeUrl(it))
        }
    }

    @Test fun emptyAndLongTitlesAreRejected() {
        assertNull(WebQuickLinkPolicy.validate(WebQuickLink(title = " ", url = "example.com")))
        assertNull(WebQuickLinkPolicy.validate(WebQuickLink(
            title = "a".repeat(51), url = "example.com")))
        assertNotNull(WebQuickLinkPolicy.validate(WebQuickLink(title = "Academia",
            url = "example.com", icon = "academy")))
    }

    @Test fun backupRoundTripRetainsOrderHiddenStatusAndIds() {
        val items = listOf(
            WebQuickLink("a", "Academia", "https://academia.example.com", "academy", false),
            WebQuickLink("b", "Mapa", "https://maps.example.com", "map", true)
        )
        assertEquals(items, WebQuickLinkPolicy.parseBackup(
            WebQuickLinkPolicy.encodeBackup(items)))
    }

    @Test fun customOtherCategoryRoundTripsAndValidates() {
        val custom = WebQuickLink(
            id = "other", title = "Apuntes personales", url = "https://example.com",
            icon = "other", customIconLabel = "Mis recursos"
        )
        val checked = WebQuickLinkPolicy.validate(custom)
        assertNotNull(checked)
        assertEquals("Mis recursos", checked!!.customIconLabel)
        assertEquals(custom, WebQuickLinkPolicy.parseBackup(
            WebQuickLinkPolicy.encodeBackup(listOf(custom))).single())
        assertNull(WebQuickLinkPolicy.validate(custom.copy(customIconLabel = "")))
        assertNull(WebQuickLinkPolicy.validate(custom.copy(customIconLabel = "a".repeat(31))))
        assertNull(WebQuickLinkPolicy.validate(custom.copy(customIconLabel = "linea\\nroto")))
    }

    @Test fun malformedOrDuplicatedBackupFailsBeforeMutation() {
        val invalid = WebQuickLinkPolicy.encodeBackup(listOf(
            WebQuickLink("same", "A", "https://example.com"),
            WebQuickLink("same", "B", "https://example.org")
        ))
        assertTrue(runCatching { WebQuickLinkPolicy.parseBackup(invalid) }.isFailure)
        assertTrue(runCatching {
            WebQuickLinkPolicy.parseBackup(
                """{"format":"nexum.quick_web_links.v1","links":[{"id":"x","title":"Bad","url":"javascript:evil()"}]}"""
            )
        }.isFailure)
        assertFalse(WebQuickLinkPolicy.MAX_LINKS < 1)
    }
}
