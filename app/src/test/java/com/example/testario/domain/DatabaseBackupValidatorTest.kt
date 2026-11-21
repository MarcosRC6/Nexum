package com.example.testario.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBackupValidatorTest {
    @Test fun completeEmptyLibraryIsValid() {
        DatabaseBackupValidator.requireLibraryShape("""{"version":7,"topics":[]}""")
    }

    @Test fun olderFullLibraryWithoutNewFeaturesRemainsValid() {
        DatabaseBackupValidator.requireLibraryShape(
            """{"version":1,"topics":[{"name":"Tema antiguo","questions":[]}]}"""
        )
    }

    @Test fun incompleteObjectMustNeverOverwriteRealLibrary() {
        for (broken in listOf(
            "{}",
            """{"version":7}""",
            """{"version":7,"topics":null}""",
            """{"version":7,"topics":{}}""",
            """{"version":7,"topics":[null]}""",
            """{"version":7,"topics":[{"name":"Sin estructura","questions":null}]}""",
            """{"version":7,"topics":[],"groups":{}}""",
            """{"version":7,"topics":[],"testTemplates":null}"""
        )) {
            assertTrue("Malformed library must reject: $broken", runCatching {
                DatabaseBackupValidator.requireLibraryShape(broken)
            }.isFailure)
        }
    }

    @Test fun maliciousHugeTopicCountRejected() {
        // Small valid document exercises the upper bound without allocating
        // giant maps or touching on-device storage.
        val oversized = "{\"version\":7,\"topics\":[" +
            List(10001) { "{\"name\":\"T\"}" }.joinToString(",") + "]}"
        assertTrue(runCatching {
            DatabaseBackupValidator.requireLibraryShape(oversized)
        }.isFailure)
    }
}
