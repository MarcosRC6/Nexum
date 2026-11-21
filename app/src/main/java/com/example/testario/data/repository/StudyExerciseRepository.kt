package com.example.testario.data.repository

import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.StudyExercise
import com.example.testario.domain.StudyExerciseRules

/**
 * No destructive conversions of legacy Questions. Topic copy() keeps all other
 * fields and the AtomicFile-backed store remains the single writer.
 */
class StudyExerciseRepository(private val store: TestarioLocalStore) {
    fun save(topicId: String, exercise: StudyExercise, expectedRevision: Int? = null): Boolean {
        if (!exercise.draft) StudyExerciseRules.requireValid(exercise)
        val db = store.database.value
        val topic = db.topics.firstOrNull { it.id == topicId } ?: return false
        val old = topic.exercises.firstOrNull { it.id == exercise.id }
        if (expectedRevision != null && old?.revision != expectedRevision) return false
        if (old == null && db.topics.any { other ->
                other.exercises.any { it.id == exercise.id }
            }) return false
        val updated = if (old == null) exercise.copy(revision = 1)
            else exercise.copy(revision = old.revision + 1)
        val exercises = if (old == null) topic.exercises + updated
            else topic.exercises.map { if (it.id == old.id) updated else it }
        store.saveDatabase(db.copy(topics = db.topics.map {
            if (it.id == topicId) it.copy(exercises = exercises) else it
        }))
        return true
    }

    /** Media is retained conservatively; no file is deleted while other refs may exist. */
    fun delete(topicId: String, exerciseId: String): Boolean {
        val db = store.database.value
        val topic = db.topics.firstOrNull { it.id == topicId } ?: return false
        if (topic.exercises.none { it.id == exerciseId }) return false
        store.saveDatabase(db.copy(topics = db.topics.map {
            if (it.id == topicId)
                it.copy(exercises = it.exercises.filterNot { ex -> ex.id == exerciseId })
            else it
        }))
        return true
    }
}
