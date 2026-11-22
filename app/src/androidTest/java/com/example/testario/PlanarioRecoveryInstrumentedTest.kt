package com.example.testario

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planario.app.PlanarioDependencies
import com.example.planario.core.model.StudyTopic
import com.example.testario.domain.UnifiedDataManager
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import com.example.planario.data.local.StudyLocalDataSource
import com.example.planario.data.local.StoredStudyData
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Physical Android/emulator regression: a corrupt Planario preference must not
 * be replaced by a new empty planning database. This test is NOT run by the JVM
 * unit-test workflow and needs an instrumented runner before stable release.
 */
@RunWith(AndroidJUnit4::class)
class PlanarioRecoveryInstrumentedTest {
    @Test
    fun incompleteAndFuturePlanarioJsonMustNeverOverwriteValidPlanning() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = StudyLocalDataSource(context)
        for (broken in listOf(
            "{}",
            """{"version":2}""",
            """{"version":2,"topics":null}""",
            """{"version":3,"topics":[]}""",
            """{"version":2,"topics":{}}""",
            """{"version":2,"settings":[]}""",
            """{"version":2,"topics":[null]}""",
            """{"version":2,"topics":[{"id":"","name":"Sin ID"}]}""",
            """{"version":2,"fixedObjectives":[{"id":"dup","name":"A"},{"id":"dup","name":"B"}]}""",
            """{"version":2,"studyDays":[{"date":"2026-11-21"},{"date":"2026-11-21"}]}""",
            """{"version":2,"timeEntries":[{"id":"x","date":"fecha-incorrecta","minutes":5,"type":"TIMER"}]}"""
        )) {
            assertNull("Invalid backup must reject: $broken", store.importFromJson(broken))
        }
        assertNotNull(store.importFromJson("""{"version":1,"topics":[]}"""))
        assertNotNull(store.importFromJson("""{"settings":{}}"""))
    }

    @Test
    fun corruptPlanningBlocksNormalSavesUntilExplicitValidatedRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("planario_study_data", Context.MODE_PRIVATE)
        val previousData = prefs.getString("study_data", null)
        val previousRecovery = prefs.getString("study_data_corrupt_recovery", null)
        val previousLock = prefs.getBoolean("study_data_recovery_locked", false)
        try {
            assertTrue(prefs.edit()
                .putString("study_data", "{invalid-json")
                .remove("study_data_corrupt_recovery")
                .remove("study_data_recovery_locked").commit())

            val store = StudyLocalDataSource(context)
            assertTrue(store.load().topics.isEmpty())
            assertNotNull(store.recoveryError.value)
            assertTrue(prefs.getBoolean("study_data_recovery_locked", false))

            val replacement = StoredStudyData(topics = listOf(StudyTopic(
                name = "Recovery regression", queuePosition = 0)))
            assertFalse(store.save(replacement))
            assertEquals("{invalid-json", prefs.getString("study_data", null))
            assertEquals("{invalid-json",
                prefs.getString("study_data_corrupt_recovery", null))

            // A normal "complete" backup must fail closed: exporting an empty
            // in-memory Planario would falsely claim all planning was saved.
            PlanarioDependencies.init(context)
            val unified = UnifiedDataManager(context)
            val normal = runCatching {
                unified.exportGlobalBackup(ByteArrayOutputStream())
            }
            assertTrue(normal.isFailure)

            // A PRIVATE pre-restore rescue, in contrast, preserves the exact
            // damaged bytes with a flag forbidding automatic re-import.
            val rescue = ByteArrayOutputStream()
            unified.exportGlobalBackup(rescue, privateRescueOnly = true)
            var manifest: JSONObject? = null
            var rescuedPlan: String? = null
            ZipInputStream(ByteArrayInputStream(rescue.toByteArray())).use { zip ->
                while (true) {
                    val item = zip.nextEntry ?: break
                    val bytes = zip.readBytes()
                    when (item.name) {
                        "manifest.json" -> manifest = JSONObject(bytes.toString(Charsets.UTF_8))
                        "planario_backup.json" -> rescuedPlan = bytes.toString(Charsets.UTF_8)
                    }
                    zip.closeEntry()
                }
            }
            assertTrue(manifest?.optBoolean("recoveryOnly", false) == true)
            assertEquals("{invalid-json", rescuedPlan)
            assertFalse(unified.restoreGlobalBackup(rescue.toByteArray()))
            assertEquals("{invalid-json", prefs.getString("study_data", null))

            assertTrue(store.restoreValidatedBackup(replacement))
            assertNull(store.recoveryError.value)
            assertFalse(prefs.getBoolean("study_data_recovery_locked", false))
            assertEquals("Recovery regression", store.load().topics.single().name)
        } finally {
            val editor = prefs.edit()
            if (previousData == null) editor.remove("study_data")
            else editor.putString("study_data", previousData)
            if (previousRecovery == null) editor.remove("study_data_corrupt_recovery")
            else editor.putString("study_data_corrupt_recovery", previousRecovery)
            if (previousLock) editor.putBoolean("study_data_recovery_locked", true)
            else editor.remove("study_data_recovery_locked")
            assertTrue(editor.commit())
        }
    }
}
