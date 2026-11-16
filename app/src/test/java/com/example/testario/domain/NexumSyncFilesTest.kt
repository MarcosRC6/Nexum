package com.example.testario.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class NexumSyncFilesTest {
    @Test fun filenamesCarryDeviceIdentityAndUtcTimestamp() {
        val name = NexumSyncFiles.name(
            "a1b2c3d4", "Mi Pixel 9", LocalDateTime.of(2026,11,16,19,20,30)
        )
        assertEquals("NEXUM_SINCRONIZACION_COMPLETO_20261116_192030_a1b2c3d4_Mi_Pixel_9.zip", name)
        val parsed = NexumSyncFiles.parse(name)
        assertEquals("a1b2c3d4", parsed?.deviceId)
        assertEquals("Mi Pixel 9", parsed?.deviceName)
        assertEquals(1794856830000L, parsed?.createdAt)
    }

    @Test fun rejectsArbitraryAndLegacyFiles() {
        assertNull(NexumSyncFiles.parse("NEXUM_TODO_COMPLETO_20261116_201912.zip"))
        assertNull(NexumSyncFiles.parse("../NEXUM_SINCRONIZACION_COMPLETO_bad.zip"))
        assertNull(NexumSyncFiles.parse("NEXUM_SINCRONIZACION_COMPLETO_20261116_192030_zzzzzzzz_Pixel.zip"))
        assertNull(NexumSyncFiles.parse("NEXUM_SINCRONIZACION_COMPLETO_20261316_192030_a1b2c3d4_Pixel.zip"))
    }

    @Test fun sanitizesDeviceNameWithoutAffectingId() {
        assertEquals(
            "NEXUM_SINCRONIZACION_COMPLETO_20261116_192030_a1b2c3d4_Android.zip",
            NexumSyncFiles.name("a1b2c3d4","🔥",LocalDateTime.of(2026,11,16,19,20,30))
        )
    }
}
