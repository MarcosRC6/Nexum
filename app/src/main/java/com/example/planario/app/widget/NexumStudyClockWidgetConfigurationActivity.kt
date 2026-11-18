package com.example.planario.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.planario.app.PlanarioDependencies
import com.example.planario.domain.timer.StudyTimerState
import com.example.planario.domain.timer.PomodoroTimerState
import com.example.testario.ui.theme.TestarioTheme

/** Vista previa dinámica de ambas caras; la página inicial se guarda por widget. */
class NexumStudyClockWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        PlanarioDependencies.init(applicationContext)
        setContent {
            val state by PlanarioDependencies.studyTimerController.state.collectAsState()
            var page by rememberSaveable {
                mutableIntStateOf(NexumStudyClockWidgetReceiver.getPage(applicationContext, id))
            }
            TestarioTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF070A16)).padding(20.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Configurar widget · Estudio", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tamaño adaptable desde el escritorio (compacto 2 × 2 o ampliado). " +
                            "Dos vistas con flechas: tiempo de estudio y Pomodoro. " +
                            "El Pomodoro no añade horas al estudio.",
                        color = Color(0xFFCFD2E6)
                    )
                    Spacer(Modifier.height(14.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111126)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (page == 0) "‹  ESTUDIO  ›" else "‹  POMODORO  ›",
                                color = Color(0xFFD9BCFF),
                                modifier = Modifier.clickable { page = 1 - page })
                            Spacer(Modifier.height(8.dp))
                            val seconds = if (page == 0) state.studyElapsedSeconds
                                else state.pomodoroRemainingSeconds
                            val clock = "%02d:%02d:%02d".format(
                                seconds / 3600, (seconds % 3600) / 60, seconds % 60
                            )
                            Text(clock, color = Color.White, fontSize = 30.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (page == 0) when (state.studyTimerState) {
                                    StudyTimerState.RUNNING -> "Pausar · Guardar tiempo"
                                    StudyTimerState.PAUSED -> "Reanudar · Guardar tiempo"
                                    StudyTimerState.STOPPED -> "Iniciar"
                                } else when (state.pomodoroTimerState) {
                                    PomodoroTimerState.RUNNING -> "Pausar · Reiniciar"
                                    PomodoroTimerState.PAUSED -> "Reanudar · Reiniciar"
                                    else -> "Iniciar · Reiniciar"
                                },
                                color = Color(0xFFD9BCFF)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(if (page == 0) "● ○" else "○ ●", color = Color(0xFFB46BFF))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = page == 0, onClick = { page = 0 })
                        Text("Mostrar estudio primero", color = Color.White,
                            modifier = Modifier.clickable { page = 0 })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = page == 1, onClick = { page = 1 })
                        Text("Mostrar Pomodoro primero", color = Color.White,
                            modifier = Modifier.clickable { page = 1 })
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        NexumStudyClockWidgetReceiver.savePage(applicationContext, id, page)
                        NexumStudyClockWidgetReceiver.updateWidget(applicationContext, id)
                        setResult(Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Guardar widget")
                    }
                    OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}
