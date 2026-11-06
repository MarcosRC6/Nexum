package com.example.testario.data.local

import com.example.testario.data.model.DatabaseSchema
import org.json.JSONArray
import org.json.JSONObject

object MigrationManager {

    fun migrate(rawJson: String): String {
        // Un JSON existente pero vacío indica corrupción o truncado. No lo
        // migramos a una base vacía, pues se perdería la posibilidad de recuperar datos.
        require(rawJson.isNotBlank()) {
            "La base de datos está vacía o truncada; se requiere recuperación."
        }

        val jsonObject = JSONObject(rawJson)
        var currentVersion = jsonObject.optInt("version", 0)

        require(currentVersion <= DatabaseSchema.CURRENT_VERSION) {
            "La base de datos pertenece a una versión más reciente de NEXUM."
        }

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
