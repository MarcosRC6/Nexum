package com.example.testario.domain

import org.junit.Assert.*
import org.junit.Test

class QuickAccessVisibilityTest {
    @Test fun linksOnlyIsVisibleEvenWithoutSavedLinks() {
        assertTrue(QuickAccessVisibility.show(
            library = false, test = false, plan = false,
            statistics = false, links = true))
    }

    @Test fun noEnabledShortcutsHidesWidget() {
        assertFalse(QuickAccessVisibility.show(
            library = false, test = false, plan = false,
            statistics = false, links = false))
    }

    @Test fun legacyShortcutsStayIndependentOfLinks() {
        assertTrue(QuickAccessVisibility.show(
            library = true, test = false, plan = false,
            statistics = false, links = false))
    }
}
