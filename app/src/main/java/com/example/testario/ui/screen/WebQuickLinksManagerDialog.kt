package com.example.testario.ui.screen

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.testario.data.local.WebQuickLink
import com.example.testario.data.local.WebQuickLinkPolicy
import com.example.testario.data.local.WebQuickLinkStore
import com.example.testario.ui.theme.NeonButton
import com.example.testario.ui.theme.TestarioMutedText
import com.example.testario.ui.theme.TestarioPrimary
import com.example.testario.ui.theme.TestarioText
import java.util.UUID

internal fun openWebQuickLink(context: Context, link: WebQuickLink) {
    val validated = WebQuickLinkPolicy.normalizeUrl(link.url)
    if (validated == null) {
        Toast.makeText(context, "Dirección web no válida", Toast.LENGTH_LONG).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(validated)).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No hay un navegador disponible", Toast.LENGTH_LONG).show()
    } catch (_: SecurityException) {
        Toast.makeText(context, "No se pudo abrir este enlace", Toast.LENGTH_LONG).show()
    }
}

internal fun webQuickLinkIcon(icon: String): String = when (icon) {
    "academy" -> "🎓"
    "map" -> "🗺️"
    "document" -> "📄"
    "other" -> "🏷️"
    else -> "🌐"
}

private val webIconNames = listOf(
    "web" to "Página web",
    "academy" to "Academia",
    "map" to "Mapa",
    "document" to "Documento",
    "other" to "Otro"
)

@Composable
internal fun WebQuickLinksManagerDialog(
    store: WebQuickLinkStore,
    onDismiss: () -> Unit
) {
    val links by store.links.collectAsState()
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("web") }
    var customTypeLabel by remember { mutableStateOf("") }
    var iconMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        editingId = null
        title = ""
        url = ""
        icon = "web"
        customTypeLabel = ""
        error = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Administrar enlaces web") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Añade academias, mapas y documentos. Se abren en el navegador " +
                    "y se guardan en tu copia global de NEXUM.", color = TestarioMutedText)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(WebQuickLinkPolicy.MAX_TITLE_LENGTH) },
                    label = { Text("Nombre del acceso") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.take(WebQuickLinkPolicy.MAX_URL_LENGTH) },
                    label = { Text("Enlace (https://...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(onClick = { iconMenu = true }) {
                            Text("${webQuickLinkIcon(icon)} ${webIconNames.first { it.first == icon }.second}")
                        }
                        DropdownMenu(expanded = iconMenu, onDismissRequest = { iconMenu = false }) {
                            webIconNames.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text("${webQuickLinkIcon(key)} $label") },
                                    onClick = { icon = key; iconMenu = false }
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { resetForm() }) { Text("Limpiar") }
                }
                if (icon == "other") OutlinedTextField(
                    value = customTypeLabel,
                    onValueChange = { customTypeLabel = it.take(30) },
                    label = { Text("Tipo personalizado") },
                    placeholder = { Text("Ej.: Recursos, simulacros…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = Color(0xFFFF8080)) }
                NeonButton(
                    onClick = {
                        val link = WebQuickLink(
                            id = editingId ?: UUID.randomUUID().toString(),
                            title = title,
                            url = url,
                            icon = icon,
                            customTypeLabel = customTypeLabel,
                            hidden = links.firstOrNull { it.id == editingId }?.hidden ?: false
                        )
                        val checked = WebQuickLinkPolicy.validate(link)
                        when {
                            checked == null -> error = "Introduce un nombre y una dirección http(s) válida, sin credenciales ni espacios."
                            !store.save(checked) -> error = "No se pudo guardar. Límite: 40 accesos."
                            else -> resetForm()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (editingId == null) "Añadir enlace" else "Guardar cambios") }

                if (links.isNotEmpty()) {
                    Text("Mis enlaces · ${links.size}/40", color = TestarioText)
                }
                links.forEachIndexed { index, link ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            "${webQuickLinkIcon(link.icon)} ${link.title}" +
                                if (link.hidden) " (oculto)" else "",
                            color = TestarioText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(link.url, color = TestarioMutedText,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                            IconButton(
                                onClick = { store.move(link.id, -1) },
                                enabled = index > 0
                            ) { Text("↑", color = TestarioPrimary) }
                            IconButton(
                                onClick = { store.move(link.id, 1) },
                                enabled = index < links.lastIndex
                            ) { Text("↓", color = TestarioPrimary) }
                            IconButton(onClick = {
                                editingId = link.id
                                title = link.title
                                url = link.url
                                icon = link.icon
                                customTypeLabel = link.customTypeLabel
                                error = null
                            }) { Text("✎", color = TestarioPrimary) }
                            IconButton(onClick = { store.setHidden(link.id, !link.hidden) }) {
                                Text(if (link.hidden) "◉" else "◌", color = TestarioPrimary)
                            }
                            IconButton(onClick = { toDelete = link.id }) {
                                Text("×", color = Color(0xFFFF9090))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )

    val deleting = links.firstOrNull { it.id == toDelete }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar enlace") },
            text = { Text("¿Quieres quitar «${deleting.title}» de tus accesos rápidos?") },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(deleting.id)
                    if (editingId == deleting.id) resetForm()
                    toDelete = null
                }) { Text("Eliminar", color = Color(0xFFFF8080)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
