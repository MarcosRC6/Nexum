package com.example.testario

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
        PlanarioDependencies.init(applicationContext)
        requestedDestinationRoute.value = intent.getStringExtra(EXTRA_START_DESTINATION_ROUTE)
        setContent {
            var showBrandIntro by remember { mutableStateOf(savedInstanceState == null) }

            LaunchedEffect(showBrandIntro) {
                if (showBrandIntro) {
                    delay(780L)
                    showBrandIntro = false
                }
            }

            if (showBrandIntro) {
                TestarioTheme(darkTheme = true) {
                    NexumSplashScreen()
                }
            } else {
                TestarioApp(
                    requestedDestinationRoute = requestedDestinationRoute.value,
                    navigationRequestId = navigationRequestId.intValue
                )
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
