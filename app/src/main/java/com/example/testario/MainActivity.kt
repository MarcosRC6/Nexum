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
import com.example.testario.ui.navigation.TestarioApp
import com.example.testario.ui.screen.NexumSplashScreen
import com.example.testario.ui.theme.TestarioTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val requestedDestinationRoute = mutableStateOf<String?>(null)
    private val navigationRequestId = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NexumCrashReporter.install(applicationContext)
        // A previous fatal startup crash must not trap the user in a restart
        // loop. All existing Testario/Planario data remains untouched.
        val previousCrash = NexumCrashReporter.lastCrash(applicationContext)
        requestedDestinationRoute.value = intent.getStringExtra(EXTRA_START_DESTINATION_ROUTE)
        setContent {
            var recoveryMessage by remember { mutableStateOf(previousCrash) }
            var startupReady by remember { mutableStateOf(false) }
            var startAttempt by remember { mutableIntStateOf(0) }

            LaunchedEffect(recoveryMessage, startAttempt) {
                if (recoveryMessage == null && !startupReady) {
                    try {
                        PlanarioDependencies.init(applicationContext)
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
                    TestarioApp(
                        requestedDestinationRoute = requestedDestinationRoute.value,
                        navigationRequestId = navigationRequestId.intValue
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestinationRoute.value = intent.getStringExtra(EXTRA_START_DESTINATION_ROUTE)
        navigationRequestId.intValue += 1
    }

    companion object {
        const val EXTRA_START_DESTINATION_ROUTE = "extra_start_destination_route"
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
