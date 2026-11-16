package com.example.testario.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class NexumExportNamesTest {
    private val date = LocalDateTime.of(2026, 11, 16, 20, 19, 12)

    @Test fun allExportsUseNexumPrefixSectionCompletenessAndDate() {
        assertEquals("NEXUM_TODO_COMPLETO_20261116_201912.zip", NexumExportNames.fullBackup(date))
        assertEquals("NEXUM_PLANIFICACION_COMPLETO_20261116_201912.json", NexumExportNames.plan(date))
        assertEquals("NEXUM_BIBLIOTECA_COMPLETO_20261116_201912.zip", NexumExportNames.library(true, false, date))
        assertEquals("NEXUM_BIBLIOTECA_SIMPLE_PARCIAL_20261116_201912.json", NexumExportNames.library(false, true, date))
    }
}
