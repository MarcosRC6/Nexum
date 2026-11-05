package com.example.testario.data.repository

import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.GroupQuestionAllocation
import com.example.testario.data.model.SavedTestScope
import com.example.testario.data.model.TestTemplate
import com.example.testario.data.model.TopicGroup
import com.example.testario.domain.GroupHierarchy
import java.util.UUID

class OrganizationRepository(private val localStore: TestarioLocalStore) {

    fun addGroup(name: String, parentGroupId: String? = null): String? {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return null

        val db = localStore.database.value
        if (parentGroupId != null && db.groups.none { it.id == parentGroupId }) return null
        if (db.groups.any { it.parentGroupId == parentGroupId && it.name.equals(cleanName, ignoreCase = true) }) return null

        val id = UUID.randomUUID().toString()
        localStore.saveDatabase(
            db.copy(groups = db.groups + TopicGroup(id = id, name = cleanName, parentGroupId = parentGroupId))
        )
        return id
    }

    fun renameGroup(groupId: String, newName: String): Boolean {
        val cleanName = newName.trim()
        if (cleanName.isBlank()) return false
        val db = localStore.database.value
        val current = db.groups.firstOrNull { it.id == groupId } ?: return false
        if (db.groups.any {
                it.id != groupId &&
                    it.parentGroupId == current.parentGroupId &&
                    it.name.equals(cleanName, ignoreCase = true)
            }) return false

        localStore.saveDatabase(
            db.copy(groups = db.groups.map { if (it.id == groupId) it.copy(name = cleanName) else it })
        )
        return true
    }

    fun setGroupTopics(groupId: String, topicIds: Set<String>): Boolean {
        val db = localStore.database.value
        if (db.groups.none { it.id == groupId }) return false
        val validTopicIds = db.topics.mapTo(hashSetOf()) { it.id }
        val cleaned = topicIds.filter { it in validTopicIds }.distinct()
        localStore.saveDatabase(
            db.copy(groups = db.groups.map { if (it.id == groupId) it.copy(topicIds = cleaned) else it })
        )
        return true
    }

    fun moveGroup(groupId: String, newParentGroupId: String?): Boolean {
        val db = localStore.database.value
        val group = db.groups.firstOrNull { it.id == groupId } ?: return false
        if (newParentGroupId != null && db.groups.none { it.id == newParentGroupId }) return false
        if (GroupHierarchy.wouldCreateCycle(db.groups, groupId, newParentGroupId)) return false

        // Evitamos dos grupos hermanos con el mismo nombre después del movimiento.
        if (db.groups.any {
                it.id != groupId &&
                    it.parentGroupId == newParentGroupId &&
                    it.name.equals(group.name, ignoreCase = true)
            }) return false

        localStore.saveDatabase(
            db.copy(groups = db.groups.map { if (it.id == groupId) it.copy(parentGroupId = newParentGroupId) else it })
        )
        return true
    }

    /**
     * Eliminar un grupo nunca elimina temas ni preguntas. Sus hijos directos suben
     * un nivel para no perder accidentalmente toda una rama de organización.
     */
    fun deleteGroup(groupId: String): Boolean {
        val db = localStore.database.value
        val group = db.groups.firstOrNull { it.id == groupId } ?: return false

        val directChildren = db.groups.filter { it.parentGroupId == groupId }
        val occupiedNames = db.groups
            .filter { it.id != groupId && it.parentGroupId == group.parentGroupId && it.id !in directChildren.map { child -> child.id } }
            .mapTo(mutableSetOf()) { it.name.trim().lowercase() }
        val promotedNames = mutableMapOf<String, String>()

        directChildren.forEach { child ->
            val base = child.name.trim().ifBlank { "Grupo" }
            var candidate = base
            var counter = 2
            while (candidate.lowercase() in occupiedNames) {
                candidate = "$base ($counter)"
                counter++
            }
            occupiedNames += candidate.lowercase()
            promotedNames[child.id] = candidate
        }

        val newGroups = db.groups
            .filterNot { it.id == groupId }
            .map { child ->
                if (child.parentGroupId == groupId) {
                    child.copy(
                        parentGroupId = group.parentGroupId,
                        name = promotedNames[child.id] ?: child.name
                    )
                } else child
            }

        val newTemplates = db.testTemplates.map { template ->
            if (template.scope != SavedTestScope.GROUPS) return@map template
            val allocations = template.groupAllocations.filterNot { it.groupId == groupId }
            template.copy(
                groupAllocations = allocations,
                questionCount = allocations.sumOf(GroupQuestionAllocation::questionCount)
            )
        }

        localStore.saveDatabase(db.copy(groups = newGroups, testTemplates = newTemplates))
        return true
    }

    /** Guarda una plantilla. Si ya existe otra con el mismo nombre, se actualiza en vez de duplicarla. */
    fun saveTemplate(template: TestTemplate): String? {
        val cleanName = template.name.trim()
        if (cleanName.isBlank()) return null
        val db = localStore.database.value
        val existing = db.testTemplates.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
        val finalTemplate = if (existing != null) {
            template.copy(id = existing.id, name = cleanName)
        } else {
            template.copy(name = cleanName)
        }

        val updated = if (existing != null) {
            db.testTemplates.map { if (it.id == existing.id) finalTemplate else it }
        } else {
            db.testTemplates + finalTemplate
        }
        localStore.saveDatabase(db.copy(testTemplates = updated))
        return finalTemplate.id
    }

    fun deleteTemplate(templateId: String): Boolean {
        val db = localStore.database.value
        if (db.testTemplates.none { it.id == templateId }) return false
        localStore.saveDatabase(db.copy(testTemplates = db.testTemplates.filterNot { it.id == templateId }))
        return true
    }
}
