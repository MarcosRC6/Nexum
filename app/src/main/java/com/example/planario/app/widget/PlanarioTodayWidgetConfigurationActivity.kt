package com.example.planario.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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

/** El tamaño físico elige el formato; la previsualización no bloquea el redimensionado. */
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
        val objectives = data.fixedObjectives.filter { it.isEnabled }.sortedBy { it.order }
        val done = data.studyDays[today]?.completedFixedObjectiveIds ?: emptySet()
        val progress = if (goal > 0) (minutes.toFloat() / goal).coerceIn(0f, 1f) else 0f

        setContent {
            var expanded by rememberSaveable { mutableStateOf(false) }
            TestarioTheme(darkTheme = true) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF0B0D0C))
                        .verticalScroll(rememberScrollState()).padding(20.dp)
                ) {
                    Text("Configurar widget · Hoy", color = Color.White)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "El tamaño se ajusta desde el escritorio: pequeño muestra tema y tiempo; " +
                            "ampliado añade los objetivos diarios. Puedes probar ambos aquí.",
                        color = Color(0xFFD9D2C0)
                    )
                    Spacer(Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(17.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151816))
                    ) {
                        Column(Modifier.padding(15.dp)) {
                            Text("Tema activo", color = Color(0xFFCFC7B5))
                            Text(topic, color = Color.White)
                            Spacer(Modifier.height(7.dp))
                            Text("${minutes} min / ${goal} min", color = Color.White)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progress }, modifier = Modifier.fillMaxWidth()
                            )
                            if (expanded) {
                                Spacer(Modifier.height(12.dp))
                                Text("Objetivos diarios", color = Color(0xFFE8D3A6))
                                if (objectives.isEmpty()) {
                                    Text("Sin objetivos diarios", color = Color(0xFFCFC7B5))
                                } else {
                                    objectives.take(4).forEach { obj ->
                                        Text(
                                            (if (obj.id in done) "✓ " else "○ ") + obj.name,
                                            color = Color(0xFFD9D2C0)
                                        )
                                    }
                                    if (objectives.size > 4) {
                                        Text("+${objectives.size - 4} más en Plan",
                                            color = Color(0xFFD9D2C0))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (expanded) "Ver formato compacto" else "Ver formato ampliado")
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = {
                        // Restablece AUTO en widgets 1.7 sin alterar el estudio.
                        PlanarioTodayWidgetReceiver.saveMode(applicationContext, id, "AUTO")
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
