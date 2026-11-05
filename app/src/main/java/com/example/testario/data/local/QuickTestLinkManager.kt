package com.example.testario.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QuickTestLink(
    val planTopicId: String,
    val libraryTopicIds: Set<String> = emptySet(),
    val questionCount: Int = 25
)

class QuickTestLinkManager(context: Context) {
    private val prefs = context.getSharedPreferences("quick_test_links", Context.MODE_PRIVATE)

    fun get(planTopicId: String): QuickTestLink {
        val all = loadAll()
        return all[planTopicId] ?: QuickTestLink(planTopicId = planTopicId)
    }

    fun save(link: QuickTestLink) {
        val all = loadAll().toMutableMap()
        all[link.planTopicId] = link.copy(questionCount = link.questionCount.coerceIn(1, 500))
        persist(all)
    }

    fun remove(planTopicId: String) {
        val all = loadAll().toMutableMap()
        all.remove(planTopicId)
        persist(all)
    }

    fun exportJson(): String {
        val root = JSONObject()
        val array = JSONArray()
        loadAll().values.forEach { link ->
            array.put(JSONObject().apply {
                put("planTopicId", link.planTopicId)
                put("libraryTopicIds", JSONArray(link.libraryTopicIds.toList()))
                put("questionCount", link.questionCount)
            })
        }
        root.put("links", array)
        return root.toString()
    }

    fun importJson(raw: String) {
        val root = JSONObject(raw)
        val array = root.optJSONArray("links") ?: JSONArray()
        val map = linkedMapOf<String, QuickTestLink>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val planTopicId = item.optString("planTopicId").trim()
            if (planTopicId.isBlank()) continue
            val ids = mutableSetOf<String>()
            val idsArray = item.optJSONArray("libraryTopicIds")
            if (idsArray != null) {
                for (j in 0 until idsArray.length()) {
                    val id = idsArray.optString(j).trim()
                    if (id.isNotBlank()) ids += id
                }
            }
            map[planTopicId] = QuickTestLink(
                planTopicId = planTopicId,
                libraryTopicIds = ids,
                questionCount = item.optInt("questionCount", 25).coerceIn(1, 500)
            )
        }
        persist(map)
    }

    fun prune(validPlanTopicIds: Set<String>, validLibraryTopicIds: Set<String>) {
        val cleaned = loadAll().values
            .filter { it.planTopicId in validPlanTopicIds }
            .associate { link ->
                link.planTopicId to link.copy(
                    libraryTopicIds = link.libraryTopicIds.filterTo(linkedSetOf()) { it in validLibraryTopicIds }
                )
            }
        persist(cleaned)
    }

    private fun loadAll(): Map<String, QuickTestLink> {
        val raw = prefs.getString(KEY, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val array = root.optJSONArray("links") ?: JSONArray()
            val map = linkedMapOf<String, QuickTestLink>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val planTopicId = item.optString("planTopicId").trim()
                if (planTopicId.isBlank()) continue
                val ids = linkedSetOf<String>()
                val idsArray = item.optJSONArray("libraryTopicIds")
                if (idsArray != null) {
                    for (j in 0 until idsArray.length()) {
                        val id = idsArray.optString(j).trim()
                        if (id.isNotBlank()) ids += id
                    }
                }
                map[planTopicId] = QuickTestLink(
                    planTopicId = planTopicId,
                    libraryTopicIds = ids,
                    questionCount = item.optInt("questionCount", 25).coerceIn(1, 500)
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    private fun persist(all: Map<String, QuickTestLink>) {
        val root = JSONObject()
        val array = JSONArray()
        all.values.forEach { link ->
            array.put(JSONObject().apply {
                put("planTopicId", link.planTopicId)
                put("libraryTopicIds", JSONArray(link.libraryTopicIds.toList()))
                put("questionCount", link.questionCount)
            })
        }
        root.put("links", array)
        prefs.edit().putString(KEY, root.toString()).apply()
    }

    companion object {
        private const val KEY = "quick_test_links_json"
    }
}
