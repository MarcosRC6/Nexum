package com.example.testario.ui.screen

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.rememberScrollState
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.testario.ui.theme.NeonButton as Button
import com.example.testario.ui.theme.NeonOutlinedButton as OutlinedButton
import com.example.testario.ui.theme.TestarioBackground
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioText

object NexumTutorialPreferences {
    private const val PREFS = "nexum_tutorial_v1"
    fun shouldShow(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("seen", false)
    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("seen", true).apply()
    }
}

data class NexumGuideChapter(val title: String, val summary: String, val instructions: String, val tip: String)

object NexumTutorialContent {
    val chapters = listOf(
        NexumGuideChapter("Bienvenido a NEXUM",
            "Tu biblioteca, los exámenes y la planificación, juntos en una sola aplicación.",
            "La barra inferior tiene cuatro áreas principales. En Inicio tienes accesos rápidos y el resumen del día.",
            "Puedes omitir este tutorial y revisarlo después desde el ? de Ajustes."),
        NexumGuideChapter("Inicio",
            "Tu panel personalizable de estudio.",
            "Consulta los objetivos diarios, accede a tus plantillas y comienza tests vinculados. En Ajustes puedes reorganizar los bloques que se muestran.",
            "Un test guardado aparecerá aquí con la opción de reanudarlo."),
        NexumGuideChapter("Biblioteca y grupos",
            "Organiza temas, grupos y bancos de preguntas.",
            "Abre un tema para editar preguntas, añadir imágenes o consultar respuestas. Organiza varios temas en Grupos. Importa o exporta los temas que necesites.",
            "Puedes hacer búsquedas y conservar preguntas favoritas y fallos."),
        NexumGuideChapter("Tests y exámenes",
            "Entrenamiento, exámenes, plantillas y tests rápidos.",
            "Elige temas o grupos, cantidad de preguntas, modo y tiempo. Las respuestas se mezclan desde el primer intento. Puedes guardar un test y continuar más tarde.",
            "Los cinco últimos exámenes pueden revisarse en Estadísticas."),
        NexumGuideChapter("Planificación y calendario",
            "Organiza tus vueltas, sesiones y objetivos.",
            "En Plan distribuye el estudio en días, crea objetivos y vincula plantillas de test. Consulta tu calendario y usa el cronómetro para registrar tiempo.",
            "El tiempo de estudio puede seguir contando con la notificación abierta."),
        NexumGuideChapter("Estadísticas",
            "Detecta en qué temas mejoras y dónde te cuesta más.",
            "Consulta aciertos, errores, preguntas falladas, los últimos exámenes y gráficas de evolución por tema o grupo.",
            "Revisa los errores para practicar de nuevo."),
        NexumGuideChapter("Ajustes y datos",
            "Personaliza y protege toda tu información.",
            "En Ajustes → Datos crea copias completas y exporta tu biblioteca o planificación. Debajo de Exportar encontrarás Transferir a otro NEXUM: el emisor comparte sus datos, el receptor los recibe tras verificar el mismo código.",
            "Recibir sustituye los datos solo después de confirmarlo y guardar una copia de recuperación.")
    )
}

/** initialSection = null: choose chapters; 0: full tour; >0: single chapter. */
@Composable
fun NexumTutorialDialog(initialSection: Int? = 0, onClose: () -> Unit) {
    var section by remember(initialSection) { mutableIntStateOf(initialSection ?: -1) }
    var fullTour by remember(initialSection) { mutableIntStateOf(if (initialSection == 0) 1 else 0) }
    BackHandler { onClose() }
    Dialog(onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false,
            dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(TestarioBackground).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Guía de NEXUM", style = MaterialTheme.typography.titleLarge, color = TestarioText)
                OutlinedButton(onClick = onClose) { Text("Omitir / cerrar") }
            }
            if (section == -1) {
                Text("Elige el recorrido completo o un apartado específico.", color = TestarioMutedText)
                Button(modifier = Modifier.fillMaxWidth(), onClick = { fullTour = 1; section = 0 }) {
                    Text("Ver tutorial completo")
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NexumTutorialContent.chapters.forEachIndexed { index, chapter ->
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                            fullTour = 0; section = index
                        }) { Text(chapter.title) }
                    }
                }
            } else {
                val chapter = NexumTutorialContent.chapters[section]
                LinearProgressIndicator(progress = { (section + 1f) / NexumTutorialContent.chapters.size },
                    modifier = Modifier.fillMaxWidth())
                Text("Apartado ${section + 1} / ${NexumTutorialContent.chapters.size}",
                    color = TestarioMutedText)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(chapter.title, style = MaterialTheme.typography.headlineMedium, color = TestarioPrimary)
                    Text(chapter.summary, style = MaterialTheme.typography.titleMedium, color = TestarioText)
                    Text("Cómo funciona", style = MaterialTheme.typography.titleMedium, color = TestarioPrimary)
                    Text(chapter.instructions, color = TestarioText)
                    Text("Consejo", style = MaterialTheme.typography.titleMedium, color = TestarioPrimary)
                    Text(chapter.tip, color = TestarioMutedText)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(modifier = Modifier.weight(1f),
                        onClick = { if (section > 0 && fullTour == 1) section-- else section = -1 }) {
                        Text("Anterior")
                    }
                    Button(modifier = Modifier.weight(1f), onClick = {
                        if (fullTour == 0 || section == NexumTutorialContent.chapters.lastIndex) onClose()
                        else section++
                    }) {
                        Text(if (fullTour == 0 || section == NexumTutorialContent.chapters.lastIndex)
                            "Finalizar" else "Siguiente")
                    }
                }
                if (fullTour == 0) {
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { section = -1 }) {
                        Text("Otros apartados")
                    }
                }
            }
        }
    }
}
