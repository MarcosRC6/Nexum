package com.example.testario.domain

import com.example.testario.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ImportOrganizationMergeTest {
    private val sourceQuestion = Question(
        id = "question-one",
        text = "¿Qué vía comunica las dos zonas?",
        answers = listOf(
            Answer(id = "a", text = "Avenida"),
            Answer(id = "b", text = "Calle"),
            Answer(id = "c", text = "Ronda")
        ),
        correctAnswerId = "a"
    )
    private val importedTopic = Topic(
        id = "source-topic", name = "Zona 1", questions = listOf(sourceQuestion)
    )
    private val source = TestarioDatabase(
        topics = listOf(importedTopic),
        groups = listOf(
            TopicGroup(id = "root", name = "Callejero"),
            TopicGroup(
                id = "child", name = "Sector", parentGroupId = "root",
                topicIds = listOf("source-topic")
            )
        ),
        testTemplates = listOf(TestTemplate(
            id = "old-template", name = "Repaso callejero",
            scope = SavedTestScope.GROUPS,
            groupAllocations = listOf(GroupQuestionAllocation("child", 1)),
            selectedTopicIds = listOf("source-topic"),
            questionCount = 1
        ))
    )

    private fun stage(target: TestarioDatabase, action: DuplicateTopicAction):
        Pair<List<ImportBatchDecision>, ImportBatchPlan> {
        // The actual decoder regenerates topic and answer IDs before showing preview.
        val previewTopic = importedTopic.copy(
            id = "preview-topic",
            questions = listOf(sourceQuestion.copy(
                id = "preview-question",
                answers = listOf(
                    Answer("preview-a", "Avenida"), Answer("preview-b", "Calle"),
                    Answer("preview-c", "Ronda")
                ),
                correctAnswerId = "preview-a"
            ))
        )
        val decisions = listOf(ImportBatchDecision(previewTopic, action))
        val plan = ImportBatchPlanner.prepare(target.topics, decisions) {
            it.copy(id = "local-topic")
        }
        return decisions to plan
    }

    @Test fun groupsAndTemplatesPointToImportedTopicAndFreshGroupIds() {
        val current = TestarioDatabase()
        val (decisions, plan) = stage(current, DuplicateTopicAction.RENAME_AUTO)
        val updated = ImportOrganizationMerge.merge(current, source, decisions, plan)

        assertEquals(1, updated.topics.size)
        assertEquals("local-topic", updated.topics.single().id)
        assertEquals(2, updated.groups.size)
        val root = updated.groups.first { it.name == "Callejero" }
        val child = updated.groups.first { it.name == "Sector" }
        assertNotEquals("root", root.id)
        assertEquals(root.id, child.parentGroupId)
        assertEquals(listOf("local-topic"), child.topicIds)

        val template = updated.testTemplates.single()
        assertNotEquals("old-template", template.id)
        assertEquals(listOf("local-topic"), template.selectedTopicIds)
        assertEquals(child.id, template.groupAllocations.single().groupId)
    }

    @Test fun reimportMergesSameGroupPathAndDoesNotDuplicateTemplate() {
        val (firstDecisions, firstPlan) = stage(TestarioDatabase(),
            DuplicateTopicAction.RENAME_AUTO)
        val first = ImportOrganizationMerge.merge(
            TestarioDatabase(), source, firstDecisions, firstPlan
        )
        val (againDecisions, againPlan) = stage(first, DuplicateTopicAction.MERGE)
        val repeated = ImportOrganizationMerge.merge(
            first, source, againDecisions, againPlan
        )
        assertEquals(1, repeated.topics.size)
        assertEquals(1, repeated.topics.single().questions.size)
        assertEquals(2, repeated.groups.size)
        assertEquals(1, repeated.testTemplates.size)
        assertEquals(first.groups, repeated.groups)
        assertEquals(first.testTemplates, repeated.testTemplates)
    }

    @Test fun mergingExistingGroupPreservesOldMembership() {
        val old = TestarioDatabase(
            topics = listOf(Topic(id = "old-topic", name = "Zona 0")),
            groups = listOf(TopicGroup(id = "existing-root", name = "Callejero",
                topicIds = listOf("old-topic")))
        )
        val (choices, plan) = stage(old, DuplicateTopicAction.RENAME_AUTO)
        val updated = ImportOrganizationMerge.merge(old, source, choices, plan)
        val root = updated.groups.first { it.id == "existing-root" }
        assertEquals(listOf("old-topic"), root.topicIds)
        assertEquals("existing-root",
            updated.groups.first { it.name == "Sector" }.parentGroupId)
    }

    @Test fun malformedHierarchyRejectsBeforeAnyCommit() {
        val invalid = source.copy(groups = listOf(
            TopicGroup(id = "orphan", name = "Orphan", parentGroupId = "absent")
        ))
        val original = TestarioDatabase()
        val (choices, plan) = stage(original, DuplicateTopicAction.RENAME_AUTO)
        assertTrue(runCatching {
            ImportOrganizationMerge.merge(original, invalid, choices, plan)
        }.isFailure)
        assertTrue(original.groups.isEmpty())
        assertTrue(original.topics.isEmpty())
    }
}
