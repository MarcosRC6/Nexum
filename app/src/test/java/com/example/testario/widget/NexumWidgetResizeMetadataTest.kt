package com.example.testario.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** El launcher decide las celdas, pero la app debe anunciar tamaños realmente redimensionables. */
class NexumWidgetResizeMetadataTest {
    private fun xml(path: String): String {
        val file = sequenceOf(File("src/main/res/xml/$path"),
            File("app/src/main/res/xml/$path"))
            .firstOrNull { it.isFile } ?: error("Missing $path")
        return file.readText()
    }

    @Test fun todayAndClockSupportBothResizeAxesAndSmallestLayouts() {
        listOf("planario_today_widget_info.xml", "widget_nexum_study_clock_info.xml",
            "widget_nexum_quick_test_info.xml").forEach {
            assertTrue("$it must resize horizontally and vertically",
                xml(it).contains("android:resizeMode=\"horizontal|vertical\""))
        }
        assertTrue(xml("planario_today_widget_info.xml")
            .contains("android:initialLayout=\"@layout/widget_planario_today_compact\""))
        assertTrue(xml("widget_nexum_study_clock_info.xml")
            .contains("android:initialLayout=\"@layout/widget_nexum_study_clock_compact\""))
    }
}
