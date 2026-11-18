package com.example.testario.widget

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.ui.theme.TestarioTheme

/** Configuration per widget instance. Existing templates remain untouched. */
class NexumQuickTestWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val storedId = NexumQuickTestWidgetProvider.getSelection(this, widgetId)
        setContent {
            val database by TestarioLocalStore.get(applicationContext).database.collectAsState()
            var selectedId by rememberSaveable { mutableStateOf(storedId.orEmpty()) }
            val templates = database.testTemplates.sortedBy { it.name.lowercase() }

            TestarioTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.fillMaxSize()
                        .background(Color(0xFF070A16))
                        .verticalScroll(rememberScrollState())
                        .padding(22.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Configurar widget · Test rápido", color = Color.White)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Elige qué test se iniciará desde esta pantalla de inicio. " +
                            "No cambia las plantillas guardadas.",
                        color = Color(0xFFCFD2E6)
                    )
                    Spacer(Modifier.height(20.dp))

                    ChoiceRow(
                        title = "10 preguntas aleatorias",
                        selected = selectedId.isEmpty(),
                        onClick = { selectedId = "" }
                    )
                    templates.forEach { template ->
                        ChoiceRow(
                            title = template.name,
                            selected = selectedId == template.id,
                            onClick = { selectedId = template.id }
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            NexumQuickTestWidgetProvider.saveSelection(
                                applicationContext, widgetId, selectedId.ifEmpty { null }
                            )
                            NexumQuickTestWidgetProvider.updateWidget(applicationContext, widgetId)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            )
                            finish()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedId.isEmpty() || templates.any { it.id == selectedId }
                    ) {
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

@androidx.compose.runtime.Composable
private fun ChoiceRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, color = Color.White, modifier = Modifier.padding(start = 8.dp))
    }
}
