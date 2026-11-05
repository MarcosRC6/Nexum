package com.example.testario.data.model

import kotlinx.serialization.Serializable

enum class DuplicateTopicAction { RENAME, MERGE, RENAME_AUTO }
enum class ImportFileType { JSON, TXT, CSV, UNKNOWN }

@Serializable
data class ImportPreview(
    val fileType: ImportFileType = ImportFileType.UNKNOWN,
    val topicCount: Int = 0,
    val questionCount: Int = 0,
    val validQuestionCount: Int = 0,
    val invalidLineCount: Int = 0,
    val errors: List<String> = emptyList()
) {
    val canImport: Boolean get() = validQuestionCount > 0 && errors.isEmpty()
}

// Hemos extraído estas clases para que nuestro futuro ImportManager pueda usarlas libremente
@Serializable
data class SimpleTopicJson(
    val name: String,
    val questions: List<SimpleQuestionJson> = emptyList()
)

@Serializable
data class SimpleQuestionJson(
    val text: String,
    val answers: List<SimpleAnswerJson> = emptyList()
)

@Serializable
data class SimpleAnswerJson(
    val text: String,
    val isCorrect: Boolean = false
)

data class ParsedDelimitedQuestion(
    val topicName: String,
    val questionText: String,
    val answerTexts: List<String>,
    val correctValue: String
)