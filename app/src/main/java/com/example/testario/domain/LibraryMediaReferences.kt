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
}
