package com.example.planario.app.widget

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class NexumStudyClockWidgetSecurityTest {
    @Test fun timerActionsAreHandledByPrivateReceiver() {
        val manifest = sequenceOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }.readText()
        val receiverDeclaration = Regex(
            """<receiver\s+android:name="com\.example\.planario\.app\.widget\.NexumStudyClockWidgetActionReceiver"\s+android:exported="false"\s*/>"""
        )
        assertTrue("Study clock command receiver must not be exported",
            receiverDeclaration.containsMatchIn(manifest))
        val provider = sequenceOf(
            File("src/main/java/com/example/planario/app/widget/NexumStudyClockWidgetReceiver.kt"),
            File("app/src/main/java/com/example/planario/app/widget/NexumStudyClockWidgetReceiver.kt")
        ).first { it.isFile }.readText()
        assertTrue(provider.contains("Intent(context, NexumStudyClockWidgetActionReceiver::class.java)"))
        assertFalse(provider.contains("override fun onReceive(context: Context, intent: Intent)"))
    }
}
