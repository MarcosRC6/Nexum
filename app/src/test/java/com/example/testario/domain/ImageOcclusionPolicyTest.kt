package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOcclusionPolicyTest {
    private val first = ImageOcclusionMask(
        id = "a", x = .1, y = .2, width = .2, height = .1, answer = "Primera"
    )
    private val second = ImageOcclusionMask(
        id = "b", x = .5, y = .4, width = .2, height = .1, answer = "Segunda"
    )

    @Test fun allAnswersStayCoveredBeforeRevealingOne() {
        assertEquals(listOf(first, second),
            ImageOcclusionPolicy.covered(listOf(first, second), "a", revealed = false))
    }

    @Test fun revealingOnlyUncoversTheTarget() {
        assertEquals(listOf(second),
            ImageOcclusionPolicy.covered(listOf(first, second), "a", revealed = true))
        assertEquals(listOf(first),
            ImageOcclusionPolicy.covered(listOf(first, second), "b", revealed = true))
    }

    @Test fun unmatchedTargetDoesNotAccidentallyRevealAllAnswers() {
        assertEquals(listOf(first, second),
            ImageOcclusionPolicy.covered(listOf(first, second), "unknown", revealed = true))
        assertTrue(ImageOcclusionPolicy.covered(emptyList(), "x", false).isEmpty())
    }
}
