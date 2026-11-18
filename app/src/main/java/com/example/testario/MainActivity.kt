package com.example.testario

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import com.example.planario.domain.timer.PomodoroTimerState
import com.example.testario.ui.navigation.TestarioApp
import com.example.testario.ui.screen.NexumSplashScreen
import com.example.testario.ui.screen.NexumTutorialDialog
import com.example.testario.ui.screen.NexumTutorialPreferences
import com.example.testario.ui.theme.TestarioTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestedDestinationRoute = mutableStateOf<String?>(null)
    private val navigationRequestId = mutableIntStateOf(0)
    private val requestedQuickTestTemplateId = mutableStateOf<String?>(null)
    private val requestedQuickTestTopicIds = mutableStateOf<List<String>?>(null)
    private val requestedQuickTestCount = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NexumCrashReporter.install(applicationContext)
        // A previous fatal startup crash must not trap the user in a restart
        // loop. All existing Testario/Planario data remains untouched.
        val previousCrash = NexumCrashReporter.lastCrash(applicationContext)
        requestedDestinationRoute.value = intent.getStringExtra(EXTRA_START_DESTINATION_ROUTE)
        requestedQuickTestTemplateId.value = intent.getStringExtra(EXTRA_QUICK_TEST_TEMPLATE_ID)
        requestedQuickTestTopicIds.value = intent.getStringArrayListExtra(EXTRA_QUICK_TEST_TOPIC_IDS)?.toList()
        requestedQuickTestCount.value = intent.getIntExtra(EXTRA_QUICK_TEST_COUNT, -1)
            .takeIf { it in 1..500 }
        setContent {
            var recoveryMessage by remember { mutableStateOf(previousCrash) }
            var startupReady by remember { mutableStateOf(false) }
            var startAttempt by remember { mutableIntStateOf(0) }

            LaunchedEffect(recoveryMessage, startAttempt) {
                if (recoveryMessage == null && !startupReady) {
                    try {
                        PlanarioDependencies.init(applicationContext)
                        val recoveredTimers = PlanarioDependencies.studyTimerController.state.value
                        if (recoveredTimers.studyTimerState == StudyTimerState.RUNNING ||
                            recoveredTimers.pomodoroTimerState == PomodoroTimerState.RUNNING) {
                            // Restore the foreground notification on a fresh Activity
                            // launch if Android killed the previous app process.
                            PlanarioDependencies.startStudyTimerForegroundService()
                        }
                        startupReady = true
                    } catch (error: Exception) {
                        // Show a recoverable screen rather than repeatedly
                        // crashing in Activity.onCreate on this device.
                        NexumCrashReporter.save(applicationContext, error)
                        recoveryMessage = NexumCrashReporter.lastCrash(applicationContext)
                            ?: "Error al iniciar NEXUM: ${error.javaClass.simpleName}"
                    }
                }
            }

            if (recoveryMessage != null) {
                val report = recoveryMessage.orEmpty()
                TestarioTheme(darkTheme = true) {
                    NexumRecoveryScreen(
                        onRetry = {
                            NexumCrashReporter.clear(applicationContext)
                            recoveryMessage = null
                            startupReady = false
                            startAttempt += 1
                        },
                        onShare = { NexumCrashReporter.share(this@MainActivity, report) }
                    )
                }
            } else if (!startupReady) {
                TestarioTheme(darkTheme = true) { NexumSplashScreen() }
            } else {
                var showBrandIntro by remember { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(showBrandIntro) {
                    if (showBrandIntro) {
                        delay(780L)
                        showBrandIntro = false
                    }
                }
                if (showBrandIntro) {
                    TestarioTheme(darkTheme = true) { NexumSplashScreen() }
                } else {
                    var showTutorial by remember {
                        mutableStateOf(
                            NexumTutorialPreferences.shouldShow(applicationContext) &&
                                requestedDestinationRoute.value == null
                        )
                    }
                    TestarioApp(
                        requestedDestinationRoute = requestedDestinationRoute.value,
                        navigationRequestId = navigationRequestId.intValue,
                        requestedQuickTestTemplateId = requestedQuickTestTemplateId.value,
                        requestedQuickTestTopicIds = requestedQuickTestTopicIds.value,
                        requestedQuickTestQuestionCount = requestedQuickTestCount.value
                    )
                    if (showTutorial) {
                        NexumTutorialDialog(initialSection = 0, onClose = {
                            NexumTutorialPreferences.markSeen(applicationContext)
                            showTutorial = false
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestinationRoute.value = intent.getStringExtra(EXTRA_START_DESTINATION_ROUTE)
        requestedQuickTestTemplateId.value = intent.getStringExtra(EXTRA_QUICK_TEST_TEMPLATE_ID)
        requestedQuickTestTopicIds.value = intent.getStringArrayListExtra(EXTRA_QUICK_TEST_TOPIC_IDS)?.toList()
        requestedQuickTestCount.value = intent.getIntExtra(EXTRA_QUICK_TEST_COUNT, -1)
            .takeIf { it in 1..500 }
        navigationRequestId.intValue += 1
    }

    companion object {
        const val EXTRA_START_DESTINATION_ROUTE = "extra_start_destination_route"
        const val EXTRA_QUICK_TEST_TEMPLATE_ID = "extra_quick_test_template_id"
        const val EXTRA_QUICK_TEST_TOPIC_IDS = "extra_quick_test_topic_ids"
        const val EXTRA_QUICK_TEST_COUNT = "extra_quick_test_count"
    }
}

@Composable
private fun NexumRecoveryScreen(onRetry: () -> Unit, onShare: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Recuperación de NEXUM",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "La sesión anterior se cerró de forma inesperada. Tus temas, preguntas y objetivos no se han borrado. Puedes intentar abrir NEXUM otra vez o compartir el informe del error para localizar su causa.",
            modifier = Modifier.padding(vertical = 18.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text("Intentar abrir NEXUM")
        }
        OutlinedButton(
            onClick = onShare,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        ) {
            Text("Compartir informe de error")
        }
    }
}
