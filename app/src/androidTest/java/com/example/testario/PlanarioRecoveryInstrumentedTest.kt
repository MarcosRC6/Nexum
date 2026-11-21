package com.example.testario

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planario.core.model.StudyTopic
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
    fun corruptPlanningBlocksNormalSavesUntilExplicitValidatedRestore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("planario_study_data", Context.MODE_PRIVATE)
        val previousData = prefs.getString("study_data", null)
        val previousRecovery = prefs.getString("study_data_corrupt_recovery", null)
        val previousLock = prefs.getBoolean("study_data_recovery_locked", false)
        try {
            assertTrue(prefs.edit()
                .putString("study_data", "{invalid-json")
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
