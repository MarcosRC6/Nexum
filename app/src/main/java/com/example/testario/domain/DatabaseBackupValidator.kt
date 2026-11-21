package com.example.testario.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Importing a full backup is destructive. Kotlin serialization supplies
 * TestarioDatabase defaults, so a syntactically valid JSON such as {"version":7}
 * would otherwise be decoded as an EMPTY database and could erase user topics.
 *
 * Older exports may omit newer optional fields, but EVERY valid full library
 * backup must contain an explicit topics array (which may legitimately be []).
 */
object DatabaseBackupValidator {
    fun requireLibraryShape(raw: String) {
        require(raw.isNotBlank()) { "El respaldo de Biblioteca está vacío." }
        val root = JSONObject(raw)
        val topics = root.opt("topics")
        require(topics is JSONArray) {
            "La copia de Biblioteca no contiene una lista de temas. " +
                "No se restaurará una base vacía por datos incompletos."
        }
        require(topics.length() <= 10000) {
            "La copia contiene demasiados temas."
        }
        for (i in 0 until topics.length()) {
            val topic = topics.optJSONObject(i)
                ?: throw IllegalArgumentException("El tema ${i + 1} no es un objeto válido.")
            require(topic.optString("name").isNotBlank()) {
                "El tema ${i + 1} no tiene nombre válido."
            }
            for (key in listOf("questions", "exercises")) {
                if (topic.has(key)) {
                    require(topic.opt(key) is JSONArray) {
                        "El tema ${i + 1} tiene un campo $key inválido."
                    }
                }
            }
        }
        for (key in listOf("groups", "testTemplates", "testHistory",
            "exerciseAttempts", "mixedSessionHistory")) {
            if (root.has(key)) {
                require(root.opt(key) is JSONArray) {
                    "La copia de Biblioteca contiene un campo $key inválido."
                }
            }
        }
    }
}
