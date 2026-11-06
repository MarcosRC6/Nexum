package com.example.testario.domain

import com.example.testario.data.local.MigrationManager
import org.junit.Assert.assertTrue
import org.junit.Test

class BlankDatabaseMigrationTest {
    @Test
    fun blankStoredDatabaseIsNotSilentlyReplacedWithEmptyUserData() {
        val error = runCatching { MigrationManager.migrate("  ") }.exceptionOrNull()
        assertTrue("El almacenamiento vacío debe considerarse corrupto", error is IllegalArgumentException)
    }
}
