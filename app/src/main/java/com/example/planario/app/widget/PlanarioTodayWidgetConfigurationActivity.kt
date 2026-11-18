package com.example.planario.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.planario.data.local.StudyLocalDataSource
import com.example.testario.ui.theme.TestarioTheme
import java.time.LocalDate

/** Previsualización de datos reales y configuración aislada por instancia de Hoy. */
class PlanarioTodayWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val dataSource = StudyLocalDataSource(applicationContext)
        val data = dataSource.load()
        val today = LocalDate.now()
        val active = dataSource.loadActiveStudyTimerSnapshot()
        val minutes = data.timeEntries.filter { it.date == today }.sumOf { it.minutes } +
            (if (active.isActive && active.date == today) active.elapsedMinutes() else 0)
        val goal = (data.studyDays[today]?.dailyGoalMinutes
            ?: data.settings.dailyGoalMinutes).coerceAtLeast(0)
        val topic = data.topics.filter { !it.isArchived }
            .minByOrNull { it.queuePosition }?.name ?: "Sin tema activo"
        val allObjectives = data.fixedObjectives.filter { it.isEnabled }
        val completed = data.studyDays[today]?.completedFixedObjectiveIds ?: emptySet()
        val completedCount = allObjectives.count { it.id in completed }
        setContent {
            var mode by rememberSaveable {
                mutableStateOf(PlanarioTodayWidgetReceiver.getMode(applicationContext, id))
            }
            TestarioTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize().background(Color(0xFF070A16)).padding(20.dp)) {
                    Text("Configurar widget · Hoy", color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text("Ajusta el tamaño en el escritorio. El widget cambia su información " +
                        "sin borrar objetivos ni tiempo registrado.", color = Color(0xFFCFD2E6))
                    Spacer(Modifier.height(14.dp))
                    Card(shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101827)),
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            Text("Estudio de hoy", color = Color(0xFFD9BCFF))
                            Spacer(Modifier.height(6.dp))
                            Text("${minutes} min / ${goal} min", color = Color.White)
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(
                                progress = { if (goal > 0) (minutes.toFloat()/goal).coerceIn(0f, 1f) else 0f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("${completedCount}/${allObjectives.size} objetivos", color = Color.White)
                            if (mode != "COMPACT") {
                                Spacer(Modifier.height(8.dp))
                                Text("Tema activo: $topic", color = Color(0xFFCFD2E6))
                                Text("Plan · Reloj · Calendario", color = Color(0xFFCFD2E6))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    listOf(
                        "AUTO" to "Automático (recomendado)",
                        "COMPACT" to "Compacto: progreso y objetivos",
                        "DETAIL" to "Detallado si hay espacio"
                    ).forEach { (value, label) ->
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(selected = mode == value, onClick = { mode = value })
                            Text(label, color = Color.White,
                                modifier = Modifier.clickable { mode = value })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        PlanarioTodayWidgetReceiver.saveMode(applicationContext, id, mode)
                        PlanarioTodayWidgetReceiver.updateWidget(applicationContext, id)
                        setResult(Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Guardar widget") }
                    OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancelar")
                    }
                }
            }
        }
    }
}
