package com.example.testario.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NexumUpdateVersionTest {
    @Test fun newerMinorVersion() {
        assertTrue(isNewerVersion("1.2", "1.1"))
        assertFalse(isNewerVersion("1.1", "1.1"))
        assertFalse(isNewerVersion("1.0", "1.1"))
    }

    @Test fun comparesNumericPartsNotAlphabetical() {
        assertTrue(isNewerVersion("1.10", "1.9"))
        assertFalse(isNewerVersion("1.9", "1.10"))
        assertTrue(isNewerVersion("2.0", "1.99"))
    }

    @Test fun rejectsMalformedVersion() {
        assertFalse(isNewerVersion("beta", "1.0"))
        assertFalse(isNewerVersion("1.1", "unknown"))
        assertFalse(isNewerVersion("1.1-neon-preview", "1.0"))
    }
}
