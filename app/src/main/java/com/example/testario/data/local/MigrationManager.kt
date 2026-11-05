package com.example.testario.data.local

import com.example.testario.data.model.DatabaseSchema
import org.json.JSONArray
import org.json.JSONObject

object MigrationManager {

    fun migrate(rawJson: String): String {
        if (rawJson.isBlank()) return "{ \"version\": ${DatabaseSchema.CURRENT_VERSION}, \"topics\": [], \"groups\": [], \"testTemplates\": [] }"

        val jsonObject = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return "{ \"version\": ${DatabaseSchema.CURRENT_VERSION}, \"topics\": [], \"groups\": [], \"testTemplates\": [] }"
        }

        var currentVersion = jsonObject.optInt("version", 0)

        if (currentVersion < 1) {
            currentVersion = 1
            jsonObject.put("version", currentVersion)
        }

        // v2: organización por grupos/subgrupos y plantillas de test.
        // Son arrays nuevos y vacíos por defecto: ninguna pregunta/estadística existente se modifica.
        if (currentVersion < 2) {
            if (!jsonObject.has("groups")) jsonObject.put("groups", JSONArray())
            if (!jsonObject.has("testTemplates")) jsonObject.put("testTemplates", JSONArray())
            currentVersion = 2
            jsonObject.put("version", currentVersion)
        }

        return jsonObject.toString()
    }
}
