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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.ui.theme.TestarioTheme

/** Plantilla, un solo tema o todo el banco con número de preguntas personalizado. */
class NexumQuickTestWidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val saved = NexumQuickTestWidgetProvider.getConfig(applicationContext, widgetId)
        setContent {
            val database by TestarioLocalStore.get(applicationContext).database.collectAsState()
            val templates = database.testTemplates.sortedBy { it.name.lowercase() }
            val topics = database.topics.filter { it.questions.isNotEmpty() }
                .sortedBy { it.name.lowercase() }

            var modeName by rememberSaveable { mutableStateOf(saved.mode.name) }
            var topicId by rememberSaveable { mutableStateOf(saved.topicId.orEmpty()) }
            var templateId by rememberSaveable { mutableStateOf(saved.templateId.orEmpty()) }
            var countText by rememberSaveable { mutableStateOf(saved.questionCount.toString()) }
            var topicsMenu by remember { mutableStateOf(false) }
            var templatesMenu by remember { mutableStateOf(false) }

            val mode = QuickWidgetMode.valueOf(modeName)
            val count = countText.toIntOrNull()
            val selection = QuickWidgetSelection(
                mode = mode,
                topicId = topicId.takeIf { it.isNotBlank() },
                templateId = templateId.takeIf { it.isNotBlank() },
                questionCount = count ?: 0
            )
            val validCount = mode == QuickWidgetMode.TEMPLATE || count != null && count in 1..500
            val valid = validCount && selection.isValid(database)

            TestarioTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B0D0C))
                        .verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text("Configurar widget · Test rápido", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Puedes iniciar un test de un tema concreto, elegir cuántas preguntas " +
                            "contiene o usar una plantilla con sus opciones completas. " +
                            "El engranaje permite modificarlo después.",
                        color = Color(0xFFD9D2C0)
                    )
                    Spacer(Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF151816)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("VISTA PREVIA · NEXUM", color = Color(0xFFE8D3A6))
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (valid) selection.label(database)
                                else "Selecciona un tema o una plantilla válida",
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("▶ Iniciar test             ⚙ Configurar",
                                color = Color(0xFFD8B880))
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    WidgetChoiceRow(
                        "Preguntas aleatorias de todos mis temas",
                        mode == QuickWidgetMode.RANDOM
                    ) { modeName = QuickWidgetMode.RANDOM.name }
                    WidgetChoiceRow(
                        "Preguntas de un tema concreto",
                        mode == QuickWidgetMode.TOPIC
                    ) { modeName = QuickWidgetMode.TOPIC.name }
                    WidgetChoiceRow(
                        "Usar una plantilla guardada",
                        mode == QuickWidgetMode.TEMPLATE
                    ) { modeName = QuickWidgetMode.TEMPLATE.name }

                    Spacer(Modifier.height(12.dp))
                    if (mode == QuickWidgetMode.TOPIC) {
                        Text("Tema", color = Color(0xFFE8D3A6))
                        Spacer(Modifier.height(6.dp))
                        Box {
                            OutlinedButton(onClick = { topicsMenu = true }) {
                                Text(
                                    topics.firstOrNull { it.id == topicId }?.name
                                        ?: "Seleccionar tema"
                                )
                            }
                            DropdownMenu(
                                expanded = topicsMenu,
                                onDismissRequest = { topicsMenu = false }
                            ) {
                                topics.forEach { topic ->
                                    DropdownMenuItem(
                                        text = { Text(topic.name) },
                                        onClick = {
                                            topicId = topic.id
                                            topicsMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }

                    if (mode == QuickWidgetMode.TEMPLATE) {
                        Text("Plantilla", color = Color(0xFFE8D3A6))
                        Spacer(Modifier.height(6.dp))
                        Box {
                            OutlinedButton(onClick = { templatesMenu = true }) {
                                Text(
                                    templates.firstOrNull { it.id == templateId }?.name
                                        ?: "Seleccionar plantilla"
                                )
                            }
                            DropdownMenu(
                                expanded = templatesMenu,
                                onDismissRequest = { templatesMenu = false }
                            ) {
                                templates.forEach { template ->
                                    DropdownMenuItem(
                                        text = { Text(template.name) },
                                        onClick = {
                                            templateId = template.id
                                            templatesMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "La plantilla decide los temas, el número de preguntas y el modo " +
                                "de test. Puedes editarla desde Testario.",
                            color = Color(0xFFD9D2C0)
                        )
                    } else {
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { new ->
                                if (new.length <= 3 && new.all { it.isDigit() }) {
                                    countText = new
                                }
                            },
                            label = { Text("Número de preguntas (1–500)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = !validCount,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            NexumQuickTestWidgetProvider.saveConfig(
                                applicationContext, widgetId, selection
                            )
                            NexumQuickTestWidgetProvider.updateWidget(applicationContext, widgetId)
                            setResult(
                                Activity.RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            )
                            finish()
                        },
                        enabled = valid,
                        modifier = Modifier.fillMaxWidth()
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
private fun WidgetChoiceRow(
    title: String, selected: Boolean, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, color = Color.White, modifier = Modifier.padding(start = 8.dp))
    }
}
