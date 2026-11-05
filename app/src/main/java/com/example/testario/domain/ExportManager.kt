package com.example.testario.domain

import android.content.Context
import com.example.testario.data.local.TestarioLocalStore
import com.example.testario.data.model.Answer
import com.example.testario.data.model.Question
import com.example.testario.data.model.QuestionStats
import com.example.testario.data.model.SimpleAnswerJson
import com.example.testario.data.model.SimpleQuestionJson
import com.example.testario.data.model.SimpleTopicJson
import com.example.testario.data.model.TestarioDatabase
import com.example.testario.data.model.Topic
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExportManager(private val context: Context, private val localStore: TestarioLocalStore) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val imagesDir = File(context.filesDir, "question_images")

    fun exportCompleteZip(outputStream: OutputStream, topicIds: List<String>? = null) {
        val db = localStore.database.value
        val topicsToExport = if (topicIds != null) db.topics.filter { it.id in topicIds } else db.topics
        val cleanTopics = topicsToExport.map { topic ->
            topic.copy(
                isPinned = false,
                questions = topic.questions.map { q ->
                    q.copy(stats = QuestionStats(), favorite = false)
                }
            )
        }
        val jsonString = json.encodeToString(TestarioDatabase(topics = cleanTopics, testsDone = 0))
        val referencedImageNames = cleanTopics
            .asSequence()
            .flatMap { it.questions.asSequence() }
            .flatMap { question ->
                sequenceOf(question.explanationImageUri, question.correctionImageUri)
            }
            .mapNotNull { path -> path?.takeIf { it.isNotBlank() } }
            .map { path -> File(path).name }
            .filter { it.isNotBlank() }
            .toSet()

        packageZip(outputStream, jsonString, referencedImageNames)
    }

    fun exportBackupZip(outputStream: OutputStream) {
        val jsonString = json.encodeToString(localStore.database.value)
        packageZip(outputStream, jsonString, imageNames = null)
    }

    private fun packageZip(
        outputStream: OutputStream,
        jsonString: String,
        imageNames: Set<String>?
    ) {
        ZipOutputStream(outputStream).use { zos ->
            // 1. Archivo de datos JSON
            zos.putNextEntry(ZipEntry("testario_database.json"))
            zos.write(jsonString.toByteArray())
            zos.closeEntry()

            // 2. Empaquetar todas las imágenes internas
            if (imagesDir.exists() && imagesDir.isDirectory) {
                imagesDir.listFiles()
                    ?.asSequence()
                    ?.filter { it.isFile }
                    ?.filter { imageNames == null || it.name in imageNames }
                    ?.forEach { file ->
                        zos.putNextEntry(ZipEntry("question_images/${file.name}"))
                        file.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
            }
        }
    }

    fun exportSimpleJson(topicIds: List<String>? = null): String {
        val db = localStore.database.value
        val topicsToExport = if (topicIds != null) db.topics.filter { it.id in topicIds } else db.topics
        val simpleTopics = topicsToExport.map { topic ->
            SimpleTopicJson(
                name = topic.name,
                questions = topic.questions.map { question ->
                    SimpleQuestionJson(
                        text = question.text,
                        answers = question.answers.map { answer ->
                            SimpleAnswerJson(text = answer.text, isCorrect = answer.id == question.correctAnswerId)
                        }
                    )
                }
            )
        }
        return json.encodeToString(simpleTopics)
    }

    fun exampleFullJson(): String {
        val answer1 = Answer(text = "Madrid")
        val answer2 = Answer(text = "Barcelona")
        val answer3 = Answer(text = "Valencia")
        val example = TestarioDatabase(
            topics = listOf(
                Topic(
                    name = "Ejemplo completo",
                    questions = listOf(
                        Question(
                            text = "¿Cuál es la capital de España?",
                            answers = listOf(answer1, answer2, answer3),
                            correctAnswerId = answer1.id,
                            difficulty = 1,
                            explanation = "La capital de España es Madrid.",
                            favorite = false,
                            flagged = false,
                            stats = QuestionStats()
                        )
                    )
                )
            )
        )
        return json.encodeToString(example)
    }

    fun exampleSimpleJson(): String {
        val example = listOf(
            SimpleTopicJson(
                name = "Ejemplo simple",
                questions = listOf(
                    SimpleQuestionJson(
                        text = "¿Cuál es la capital de España?",
                        answers = listOf(
                            SimpleAnswerJson("Madrid", true),
                            SimpleAnswerJson("Barcelona", false),
                            SimpleAnswerJson("Valencia", false)
                        )
                    )
                )
            )
        )
        return json.encodeToString(example)
    }

    fun importInstructionsTxt(): String {
        return """
            INSTRUCCIONES DE IMPORTACIÓN - TESTARIO

           Puedes importar preguntas en formato ZIP, JSON, TXT y CSV.

            ============================================================
            1) FORMATO JSON COMPLETO
            ============================================================

            Mantiene todos los datos internos de Testario: temas, preguntas,
            respuestas, dificultad, explicación, imágenes de enunciado y explicación,
            procedencia opcional, favoritos, marcadas y estadísticas.

            Ejemplo:

            ${exampleFullJson()}

            ============================================================
            2) FORMATO JSON SIMPLE
            ============================================================

            Recomendado si quieres crear preguntas de forma más limpia sin IDs.

            Ejemplo:

            ${exampleSimpleJson()}

            ============================================================
            3) FORMATO TXT / CSV CON PUNTO Y COMA
            ============================================================

            Cada pregunta debe ir en una línea.

            Estructura obligatoria:

            Tema;Pregunta;Respuesta A;Respuesta B;Respuesta C;Correcta

            La columna "Correcta" puede ser:
            A
            B
            C
            1
            2
            3
            o el texto exacto de la respuesta correcta.

            Ejemplo:

            Constitución;¿Cuál es la capital de España?;Madrid;Barcelona;Valencia;A
            Constitución;¿Cuántos artículos tiene la Constitución Española?;169;155;197;A
            Incendios;¿Qué gas es incoloro e inodoro?;CO;CO2;O2;A

            ============================================================
            4) FORMATO TXT / CSV SIN TEMA
            ============================================================

            Si no quieres indicar tema en cada línea, puedes usar:

            Pregunta;Respuesta A;Respuesta B;Respuesta C;Correcta

            En ese caso, las preguntas se importarán en un tema llamado:

            Importado TXT/CSV

            Ejemplo:

            ¿Cuál es la capital de España?;Madrid;Barcelona;Valencia;A
            ¿Cuántos lados tiene un triángulo?;3;4;5;A

            ============================================================
            NOTAS IMPORTANTES
            ============================================================

            - Usa siempre punto y coma: ;
            - No pongas punto y coma dentro de una pregunta o respuesta.
            - Cada línea equivale a una pregunta.
            - La primera línea puede ser cabecera. Si pone "Pregunta;Respuesta A...",
              Testario la ignorará automáticamente.
            - TXT y CSV usan el mismo modelo de columnas.
            - Antes de importar, Testario mostrará una previsualización con el número
              de temas y preguntas detectadas.
        """.trimIndent()
    }
}