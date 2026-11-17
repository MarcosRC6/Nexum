package com.example.testario.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Basic user-visible import result invariants; Android integration is covered by CI. */
class ImportTopicOutcomeTest {
    @Test fun reportsSkippedMergeWithoutClaimingTopicCreated() {
        val result = ImportTopicOutcome(false, false, 0, 12, "Zona 1")
        assertEquals(false, result.created)
        assertEquals(false, result.merged)
        assertEquals(0, result.addedQuestions)
        assertEquals(12, result.duplicateQuestions)
    }
}
