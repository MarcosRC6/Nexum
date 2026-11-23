package com.example.testario.viewmodel

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/**
 * Retained, navigation-entry-scoped authoring drafts.
 *
 * A split-screen / orientation change recreates Composables, but not their
 * NavBackStackEntry ViewModel. Switching editor tabs disposes the old editor,
 * so each kind needs an independent namespace in the SAME retained store.
 *
 * No draft is written to Library automatically. Only the active editor's
 * explicit Save button creates its matching Question or StudyExercise.
 * Leaving the navigation entry discards all its unsaved draft fields.
 */
class EditorDraftViewModel : ViewModel() {
    private val fields = mutableMapOf<String, MutableState<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <T> state(scope: String, field: String, initial: () -> T): MutableState<T> {
        val key = "$scope::$field"
        return fields.getOrPut(key) { mutableStateOf(initial()) } as MutableState<T>
    }

    fun clear(scope: String) {
        fields.keys.removeAll { it.startsWith("$scope::") }
    }
}
