package com.example.testario.domain

import com.example.testario.data.model.TopicGroup

object GroupHierarchy {

    fun descendantIds(groups: List<TopicGroup>, groupId: String): Set<String> {
        val childrenByParent = groups.groupBy { it.parentGroupId }
        val visited = linkedSetOf<String>()
        val stack = ArrayDeque<String>()
        stack.add(groupId)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            childrenByParent[current].orEmpty().forEach { child ->
                if (visited.add(child.id)) stack.add(child.id)
            }
        }
        return visited
    }

    fun resolveTopicIds(groups: List<TopicGroup>, groupId: String): Set<String> {
        val byId = groups.associateBy { it.id }
        if (groupId !in byId) return emptySet()

        val allGroupIds = linkedSetOf(groupId)
        allGroupIds.addAll(descendantIds(groups, groupId))
        return allGroupIds.flatMapTo(linkedSetOf()) { id -> byId[id]?.topicIds.orEmpty() }
    }

    fun path(groups: List<TopicGroup>, groupId: String): String {
        val byId = groups.associateBy { it.id }
        val names = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var current = byId[groupId]
        while (current != null && seen.add(current.id)) {
            names += current.name
            current = current.parentGroupId?.let(byId::get)
        }
        return names.asReversed().joinToString(" > ")
    }

    fun wouldCreateCycle(groups: List<TopicGroup>, groupId: String, newParentGroupId: String?): Boolean {
        if (newParentGroupId == null) return false
        if (groupId == newParentGroupId) return true
        return newParentGroupId in descendantIds(groups, groupId)
    }
}
