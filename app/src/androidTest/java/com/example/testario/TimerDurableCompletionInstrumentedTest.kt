package com.example.testario

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planario.data.local.StudyLocalDataSource
import com.example.planario.data.repository.StudyRepository
import com.example.planario.domain.timer.StudyTimerController
import com.example.planario.domain.timer.StudyTimerState
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises actual SharedPreferences.commit and Planario JSON persistence on
 * an Android emulator. NO user files, test-bank imports, or APK credentials.
 * Snapshot and restore all preference values around each case.
 */
@RunWith(AndroidJUnit4::class)
class TimerDurableCompletionInstrumentedTest {
    private fun withIsolatedPreferences(block: (Context, SharedPreferences, SharedPreferences) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val timer = context.getSharedPreferences("planario_study_timer_state", Context.MODE_PRIVATE)
        val plan = context.getSharedPreferences("planario_study_data", Context.MODE_PRIVATE)
        val originalTimer = timer.all.toMap()
        val originalPlan = plan.all.toMap()
        try {
            assertTrue(timer.edit().clear().commit())
            assertTrue(plan.edit().clear().commit())
            block(context, timer, plan)
        } finally {
            restore(timer, originalTimer)
            restore(plan, originalPlan)
        }
    }

    private fun restore(prefs: SharedPreferences, previous: Map<String, *>) {
        val editor = prefs.edit().clear()
        for ((key, value) in previous) {
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, (value as Set<String>).toSet())
                }
            }
        }
        assertTrue(editor.commit())
    }

    @Test fun committedMinutesAreRecordedOnceEvenAfterProcessRecreation() =
        withIsolatedPreferences { context, timer, _ ->
            val today = LocalDate.now()
            val controller = StudyTimerController().apply { attachContext(context) }
            controller.restorePausedStudyTimer(today, 125L * 60L)
            val session = requireNotNull(controller.prepareStudyTimerCompletion())
            assertEquals(125, session.minutes)
            assertEquals(session.id, timer.getString("pending_session_id", null))
            assertEquals(StudyTimerState.PAUSED, controller.state.value.studyTimerState)

            val repository = StudyRepository(StudyLocalDataSource(context))
            assertTrue(repository.recordTimerSessionOnce(session))
            assertTrue(repository.recordTimerSessionOnce(session))
            assertEquals(1, repository.timeEntries.value.count { it.id == session.id })

            // Simulate process death BEFORE journal acknowledgement.
            val relaunchedRepository = StudyRepository(StudyLocalDataSource(context))
            assertTrue(relaunchedRepository.recordTimerSessionOnce(session))
            assertEquals(1, relaunchedRepository.timeEntries.value.count { it.id == session.id })
            val relaunchedTimer = StudyTimerController().apply { attachContext(context) }
            assertEquals(session, relaunchedTimer.recoverPendingStudyTimerIfNeeded())
            assertTrue(relaunchedTimer.acknowledgeSavedStudyTimerSession(session))
            assertNull(timer.getString("pending_session_id", null))
            assertFalse(timer.getBoolean("active", false))
            assertNull(relaunchedTimer.recoverPendingStudyTimerIfNeeded())
        }

    @Test fun failedDiskWriteLeavesJournalAndActiveTimerForRetry() =
        withIsolatedPreferences { context, timer, plan ->
            val controller = StudyTimerController().apply { attachContext(context) }
            controller.restorePausedStudyTimer(LocalDate.now(), 75L * 60L)
            val session = requireNotNull(controller.prepareStudyTimerCompletion())

            // Force the real storage guard to reject the Planario commit.
            assertTrue(plan.edit().putBoolean("study_data_recovery_locked", true).commit())
            val blocked = StudyRepository(StudyLocalDataSource(context))
            assertFalse(blocked.recordTimerSessionOnce(session))
            assertTrue(blocked.timeEntries.value.isEmpty())
            assertEquals(session.id, timer.getString("pending_session_id", null))
            assertTrue(timer.getBoolean("active", false))

            // Storage is repaired; the exact same staged session is replayed.
            assertTrue(plan.edit().remove("study_data_recovery_locked").commit())
            val retry = StudyRepository(StudyLocalDataSource(context))
            val restarted = StudyTimerController().apply { attachContext(context) }
            assertEquals(session, restarted.recoverPendingStudyTimerIfNeeded())
            assertTrue(retry.recordTimerSessionOnce(session))
            assertTrue(restarted.acknowledgeSavedStudyTimerSession(session))
            val again = StudyRepository(StudyLocalDataSource(context))
            assertEquals(1, again.timeEntries.value.count { it.id == session.id })
        }

    @Test fun zeroMinuteStopAcknowledgesWithoutFakeTimeEntry() =
        withIsolatedPreferences { context, timer, _ ->
            val controller = StudyTimerController().apply { attachContext(context) }
            controller.restorePausedStudyTimer(LocalDate.now(), 17)
            val session = requireNotNull(controller.prepareStudyTimerCompletion())
            assertEquals(0, session.minutes)
            val repository = StudyRepository(StudyLocalDataSource(context))
            assertTrue(repository.recordTimerSessionOnce(session))
            assertTrue(repository.timeEntries.value.isEmpty())
            assertTrue(controller.acknowledgeSavedStudyTimerSession(session))
            assertNull(timer.getString("pending_session_id", null))
            assertEquals(StudyTimerState.STOPPED, controller.state.value.studyTimerState)
        }
}
