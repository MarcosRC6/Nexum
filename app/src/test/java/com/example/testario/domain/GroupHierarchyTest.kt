package com.example.testario.domain

import com.example.testario.data.model.TopicGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupHierarchyTest {

    private val groups = listOf(
        TopicGroup(id = "root", name = "Específico", topicIds = listOf("t1")),
        TopicGroup(id = "aea", name = "AEA", parentGroupId = "root", topicIds = listOf("t2")),
        TopicGroup(id = "tools", name = "Herramientas", parentGroupId = "root", topicIds = listOf("t3", "t1")),
        TopicGroup(id = "rope", name = "Cuerda", parentGroupId = "aea", topicIds = listOf("t4"))
    )

    @Test
    fun parentResolvesDirectAndRecursiveTopicsWithoutDuplicates() {
        assertEquals(
            linkedSetOf("t1", "t2", "t4", "t3"),
            GroupHierarchy.resolveTopicIds(groups, "root")
        )
    }

    @Test
    fun subgroupPathIncludesAllAncestors() {
        assertEquals(
            "Específico > AEA > Cuerda",
            GroupHierarchy.path(groups, "rope")
        )
    }

    @Test
    fun cycleDetectionRejectsSelfAndDescendantsButAllowsValidMoves() {
        assertTrue(GroupHierarchy.wouldCreateCycle(groups, "root", "root"))
        assertTrue(GroupHierarchy.wouldCreateCycle(groups, "root", "rope"))
        assertFalse(GroupHierarchy.wouldCreateCycle(groups, "aea", "tools"))
        assertFalse(GroupHierarchy.wouldCreateCycle(groups, "aea", null))
    }

    @Test
    fun missingGroupResolvesToEmptyTopics() {
        assertTrue(GroupHierarchy.resolveTopicIds(groups, "missing").isEmpty())
    }
}
