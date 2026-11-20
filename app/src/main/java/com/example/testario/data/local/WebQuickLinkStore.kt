package com.example.testario.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

/**
 * Enlaces privados del usuario: no se suben a GitHub ni se abren dentro de un WebView.
 * Exportables sólo en el backup global de NEXUM.
 */
@Serializable
data class WebQuickLink(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val icon: String = "web",
    val hidden: Boolean = false,
    val customTypeLabel: String = ""
)

@Serializable
private data class WebQuickLinksBackup(
    val format: String = "nexum.quick_web_links.v1",
    val links: List<WebQuickLink> = emptyList()
)

object WebQuickLinkPolicy {
    private val codec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    const val MAX_LINKS = 40
    const val MAX_TITLE_LENGTH = 50
    const val MAX_URL_LENGTH = 2048
    val icons = listOf("web", "academy", "map", "document", "other")

    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH ||
            trimmed.any { it.isISOControl() || it.isWhitespace() }) return null
        // Bare domain names default to encrypted HTTPS. Never accept javascript:,
        // intent:, file:, content: or other schemes from imported backups.
        val withScheme = when {
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.contains("://") || Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(trimmed) -> return null
            else -> "https://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("https", "http") ||
            uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
            uri.port !in -1..65535 || uri.rawPath?.contains('\\') == true) return null
        val host = uri.host ?: return null
        if (!host.contains('.')) return null
        return uri.toASCIIString()
    }

    fun validate(link: WebQuickLink): WebQuickLink? {
        val title = link.title.trim()
        val url = normalizeUrl(link.url) ?: return null
        if (title.isEmpty() || title.length > MAX_TITLE_LENGTH ||
            title.any { it.isISOControl() }) return null
        val custom = link.customTypeLabel.trim()
        if (custom.length > 30 || custom.any { it.isISOControl() }) return null
        if (link.icon == "other" && custom.isBlank()) return null
        return link.copy(title = title, url = url,
            icon = link.icon.takeIf { it in icons } ?: "web",
            customTypeLabel = if (link.icon == "other") custom else "")
    }

    fun parseBackup(raw: String): List<WebQuickLink> {
        require(raw.length <= 150_000) { "Enlaces demasiado grandes" }
        val backup = codec.decodeFromString<WebQuickLinksBackup>(raw)
        require(backup.format == "nexum.quick_web_links.v1") { "Formato desconocido" }
        require(backup.links.size <= MAX_LINKS) { "Demasiados enlaces" }
        val seenIds = hashSetOf<String>()
        return backup.links.map { item ->
            require(item.id.length in 1..100 && seenIds.add(item.id)) {
                "Identificador no válido o duplicado"
            }
            validate(item) ?: error("Enlace no válido")
        }
    }

    fun encodeBackup(links: List<WebQuickLink>): String =
        codec.encodeToString(WebQuickLinksBackup(links = links))
}

class WebQuickLinkStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "nexum_home_quick_web_links_v1", Context.MODE_PRIVATE
    )
    private val _links = MutableStateFlow(
        runCatching {
            preferences.getString("data", null)?.let(WebQuickLinkPolicy::parseBackup)
                ?: emptyList()
        }.getOrDefault(emptyList())
    )
    val links: StateFlow<List<WebQuickLink>> = _links

    @Synchronized
    fun replaceLinks(newLinks: List<WebQuickLink>): Boolean {
        if (newLinks.size > WebQuickLinkPolicy.MAX_LINKS) return false
        val normalized = newLinks.map { WebQuickLinkPolicy.validate(it) ?: return false }
        if (normalized.map { it.id }.distinct().size != normalized.size) return false
        val json = WebQuickLinkPolicy.encodeBackup(normalized)
        if (!preferences.edit().putString("data", json).commit()) return false
        _links.value = normalized
        return true
    }

    fun save(link: WebQuickLink): Boolean {
        val valid = WebQuickLinkPolicy.validate(link) ?: return false
        val old = _links.value
        val exists = old.any { it.id == valid.id }
        if (!exists && old.size >= WebQuickLinkPolicy.MAX_LINKS) return false
        return replaceLinks(if (exists) old.map { if (it.id == valid.id) valid else it }
            else old + valid)
    }

    fun delete(id: String): Boolean = replaceLinks(_links.value.filterNot { it.id == id })

    fun setHidden(id: String, hidden: Boolean): Boolean =
        replaceLinks(_links.value.map { if (it.id == id) it.copy(hidden = hidden) else it })

    fun move(id: String, direction: Int): Boolean {
        val list = _links.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + direction
        if (from == -1 || to !in list.indices) return false
        val value = list.removeAt(from)
        list.add(to, value)
        return replaceLinks(list)
    }

    fun exportJson(): String = WebQuickLinkPolicy.encodeBackup(_links.value)
    fun importJson(raw: String): Boolean =
        replaceLinks(WebQuickLinkPolicy.parseBackup(raw))

    companion object {
        @Volatile private var instance: WebQuickLinkStore? = null
        fun get(context: Context): WebQuickLinkStore = instance ?: synchronized(this) {
            instance ?: WebQuickLinkStore(context).also { instance = it }
        }
    }
}
