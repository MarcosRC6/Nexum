package com.example.testario.domain

import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.TopicGroup
import java.util.UUID

/**
 * Merge the organization carried by a library ZIP, not just its questions.
 *
 * Every source topic ID is remapped to the destination selected in the preview.
 * Group paths (name + parent) are merged on repeat imports, while templates
 * are retained without overwriting a user's existing settings.
 *
 * This is a pure staging step: caller commits its result with the questions
 * in ONE durable DB write. Malformed references reject the whole batch.
 */
object ImportOrganizationMerge {
    fun merge(
        current: TestarioDatabase,
        incoming: TestarioDatabase,
        decisions: List<ImportBatchDecision>,
        plan: ImportBatchPlan
    ): TestarioDatabase {
        require(incoming.topics.size == decisions.size &&
            decisions.size == plan.outcomes.size) {
            "Los temas del ZIP cambiaron durante la importación; repite la operación."
        }
        val sourceIds = incoming.topics.map { it.id }
        require(sourceIds.all { it.isNotBlank() } && sourceIds.distinct().size == sourceIds.size) {
            "El paquete tiene identificadores de temas duplicados o vacíos."
        }

        val topicIds = linkedMapOf<String, String>()
        incoming.topics.forEachIndexed { index, source ->
            val choice = decisions[index].topic
            // Preview uses regenerateTopicIds() to avoid collisions, so the
            // selected topic has DIFFERENT IDs from the original JSON. Map by
            // source order and verify stable content, never by those new IDs.
            require(source.name.trim().equals(choice.name.trim(), true) &&
                source.questions.size == choice.questions.size &&
                source.exercises.size == choice.exercises.size &&
                source.questions.map(TopicImportMatcher::fingerprint) ==
                    choice.questions.map(TopicImportMatcher::fingerprint) &&
                source.exercises.map(StudyExerciseMerge::signature) ==
                    choice.exercises.map(StudyExerciseMerge::signature)) {
                "El orden o contenido de los temas cambió durante la importación."
            }
            val destination = plan.outcomes[index].destinationName
            val matched = plan.topics.filter { it.name.trim().equals(destination.trim(), true) }
            require(matched.size == 1) { "Destino de tema ambiguo: $destination." }
            topicIds[source.id] = matched.single().id
        }

        val groups = current.groups.toMutableList()
        val groupIds = linkedMapOf<String, String>()
        val sourceGroupIds = incoming.groups.map { it.id }
        require(sourceGroupIds.all { it.isNotBlank() } &&
            sourceGroupIds.distinct().size == sourceGroupIds.size) {
            "El ZIP contiene identificadores de grupos duplicados."
        }
        val pending = incoming.groups.toMutableList()
        while (pending.isNotEmpty()) {
            val ready = pending.filter {
                it.parentGroupId == null || it.parentGroupId in groupIds
            }
            require(ready.isNotEmpty()) {
                "El ZIP contiene grupos con padres desconocidos o una jerarquía circular."
            }
            ready.forEach { source ->
                val name = source.name.trim()
                require(name.isNotBlank()) { "El ZIP contiene un grupo sin nombre." }
                val parent = source.parentGroupId?.let(groupIds::get)
                // Un ZIP parcial puede traer grupos que mencionan temas que no se
                // exportaron: esa pertenencia se ignora, nunca se rechaza el lote.
                val mappedTopics = source.topicIds.mapNotNull(topicIds::get).distinct()
                val matches = groups.withIndex().filter { (_, group) ->
                    group.parentGroupId == parent && group.name.trim().equals(name, true)
                }
                require(matches.size <= 1) {
                    "Hay varios grupos «$name» con el mismo padre: revisa la organización."
                }
                val id = if (matches.isNotEmpty()) {
                    val match = matches.single()
                    val old = match.value
                    groups[match.index] = old.copy(
                        topicIds = (old.topicIds + mappedTopics).distinct()
                    )
                    old.id
                } else {
                    val newId = UUID.randomUUID().toString()
                    groups += TopicGroup(
                        id = newId, name = name, parentGroupId = parent,
                        topicIds = mappedTopics
                    )
                    newId
                }
                groupIds[source.id] = id
            }
            pending.removeAll(ready.toSet())
        }

        val templates = current.testTemplates.toMutableList()
        incoming.testTemplates.forEach { source ->
            val name = source.name.trim()
            require(name.isNotBlank()) { "El ZIP contiene una plantilla sin nombre." }
            val remapped = source.copy(
                id = UUID.randomUUID().toString(),
                name = name,
                selectedTopicIds = source.selectedTopicIds.mapNotNull(topicIds::get).distinct(),
                selectedFailureTopicIds = source.selectedFailureTopicIds.mapNotNull(topicIds::get).distinct(),
                groupAllocations = source.groupAllocations.map { allocation ->
                    val destination = groupIds[allocation.groupId]
                        ?: throw IllegalArgumentException(
                            "La plantilla «$name» referencia un grupo desconocido."
                        )
                    allocation.copy(groupId = destination)
                }
            )
            // A conflicting personal template is imported as "Name (2)".
            // On subsequent ZIP imports, recognize that unchanged suffixed copy
            // instead of creating "Name (3)", "Name (4)", and so on.
            val suffix = Regex("^" + Regex.escape(name) + " \\(\\d+\\)$",
                RegexOption.IGNORE_CASE)
            val alreadyPresent = templates.any { old ->
                (old.name.trim().equals(name, true) || suffix.matches(old.name.trim())) &&
                    old.copy(id = remapped.id, name = remapped.name) == remapped
            }
            if (!alreadyPresent) {
                templates += remapped.copy(
                    name = uniqueName(name, templates.map { it.name })
                )
            }
        }

        return current.copy(
            topics = plan.topics,
            groups = groups,
            testTemplates = templates
        )
    }

    private fun uniqueName(base: String, existing: List<String>): String {
        if (existing.none { it.trim().equals(base, true) }) return base
        var n = 2
        while (existing.any { it.trim().equals("$base ($n)", true) }) n++
        return "$base ($n)"
    }
}
