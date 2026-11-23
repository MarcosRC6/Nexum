package com.example.testario.viewmodel

import com.example.testario.data.model.Answer
import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.SketchDrawing
import org.junit.Assert.*
import org.junit.Test

class EditorDraftViewModelTest {
    @Test fun switchingTabsAndRecreatingComposableDoesNotDiscardOrCrossSaveDrafts() {
        val store = EditorDraftViewModel() // NavBackStackEntry retains this on configuration change.
        val test = "test:topic:new"
        val written = "exercise:WRITTEN_MULTI:topic:new"
        val anki = "exercise:ANKI_CARD:topic:new"

        store.state(test, "questionText") { "" }.value = "Enunciado TEST"
        store.state(test, "answers") { listOf<Answer>() }.value =
            listOf(Answer(id = "a", text = "Opción A"))
        store.state(written, "prompt") { "" }.value = "Enunciado ESCRITA"
        store.state(written, "solutions") { listOf<CanonicalSolution>() }.value =
            listOf(CanonicalSolution(text = "Solución escrita"))
        store.state(anki, "front") { "" }.value = "Anverso ANKI"
        store.state(anki, "sketch") { SketchDrawing() }.value = SketchDrawing()

        // Accessing the same draft from a newly composed editor reuses state.
        assertEquals("Enunciado TEST", store.state(test, "questionText") { "PÉRDIDA" }.value)
        assertEquals("Enunciado ESCRITA", store.state(written, "prompt") { "PÉRDIDA" }.value)
        assertEquals("Anverso ANKI", store.state(anki, "front") { "PÉRDIDA" }.value)

        // Editing one kind never mutates any other kind's existing answers.
        store.state(test, "questionText") { "" }.value = "TEST CORREGIDO"
        assertEquals("Enunciado ESCRITA", store.state(written, "prompt") { "" }.value)
        assertEquals("Anverso ANKI", store.state(anki, "front") { "" }.value)
        assertEquals("Solución escrita",
            store.state(written, "solutions") { listOf<CanonicalSolution>() }.value[0].text)
        store.clear(test)
        assertEquals("", store.state(test, "questionText") { "" }.value)
        assertEquals("Enunciado ESCRITA", store.state(written, "prompt") { "" }.value)
    }

    @Test fun differentTopicsAndExistingQuestionIdsCannotShareDraft() {
        val store = EditorDraftViewModel()
        store.state("test:topic-a:new", "questionText") { "" }.value = "A"
        store.state("test:topic-b:new", "questionText") { "" }.value = "B"
        store.state("test:topic-a:existing-id", "questionText") { "" }.value = "C"
        assertEquals("A", store.state("test:topic-a:new", "questionText") { "" }.value)
        assertEquals("B", store.state("test:topic-b:new", "questionText") { "" }.value)
        assertEquals("C", store.state("test:topic-a:existing-id", "questionText") { "" }.value)
    }
}
