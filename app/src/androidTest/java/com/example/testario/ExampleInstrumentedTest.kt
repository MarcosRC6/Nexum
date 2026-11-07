package com.example.testario

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.planario.core.model.FixedObjective
import com.example.planario.core.model.StudyTopic
import com.example.planario.data.local.StoredStudyData
import com.example.planario.data.local.StudyLocalDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A real device/emulator check, not just a JVM unit test.
 * Covers Activity recreation with persisted topics and daily objectives.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun correctApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, context.packageName)
    }

    @Test
    fun reopensWithSixTopicsAndObjectivesSaved() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val store = StudyLocalDataSource(appContext)
        val previous = store.load()
        try {
            store.save(
                StoredStudyData(
                    topics = (1..6).map { i ->
                        StudyTopic(name = "Tema de prueba $i", queuePosition = i - 1)
                    },
                    fixedObjectives = (1..6).map { i ->
                        FixedObjective(name = "Objetivo de prueba $i", targetMinutes = 20, order = i - 1)
                    },
                    settings = previous.settings
                )
            )
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                }
            }
        } finally {
            store.save(previous)
        }
    }
}
