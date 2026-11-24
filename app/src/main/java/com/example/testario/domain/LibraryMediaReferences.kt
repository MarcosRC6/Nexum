package com.example.testario.domain

import com.example.testario.data.model.Topic

/**
 * All photos embedded in Testario topics. Both the regular multiple-choice
 * questions and newer study exercises must be present before a library ZIP
 * is accepted or declared successfully exported.
 */
internal object LibraryMediaReferences {
    fun paths(topics: List<Topic>): List<String> =
        topics.flatMap { topic ->
            topic.questions.flatMap { question ->
                listOfNotNull(question.explanationImageUri, question.correctionImageUri)
            } + topic.exercises.flatMap { it.referencedImageUris() }
        }.filter(String::isNotBlank).distinct()

    /** Copia de los temas sin las referencias a fotos que ya no existen en el móvil. */
    fun withoutMissing(topics: List<Topic>, missing: Set<String>): List<Topic> {
        if (missing.isEmpty()) return topics
        fun String?.keep() = this?.takeIf { it !in missing }
        return topics.map { topic ->
            topic.copy(
                questions = topic.questions.map { q ->
                    q.copy(
                        explanationImageUri = q.explanationImageUri.keep(),
                        correctionImageUri = q.correctionImageUri.keep()
                    )
                },
                exercises = topic.exercises.map { e ->
                    e.copy(
                        baseImageUri = e.baseImageUri.keep(),
                        flashcard = e.flashcard?.copy(
                            frontImageUri = e.flashcard.frontImageUri.keep(),
                            backImageUri = e.flashcard.backImageUri.keep()
                        )
                    )
                }
            )
        }
    }
}
