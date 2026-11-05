package com.example.testario.domain

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ImageManager(private val context: Context) {

    private val imagesDir = File(context.filesDir, "question_images")

    fun persistImageToInternalStorage(temporaryUriString: String?): String? {
        if (temporaryUriString == null) return null

        // Si ya es una ruta interna nuestra, no hacemos nada
        if (temporaryUriString.contains(context.filesDir.absolutePath)) {
            return temporaryUriString
        }

        return try {
            val uri = Uri.parse(temporaryUriString)
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val destinationFile = File(imagesDir, "IMG_${UUID.randomUUID()}.jpg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            temporaryUriString
        }
    }

    // NUEVO: Método para borrar la imagen física cuando se borra la pregunta
    fun deleteImage(pathString: String?) {
        if (pathString == null) return
        try {
            val file = File(pathString)
            // Seguridad extra: Solo borramos si el archivo está realmente dentro de nuestra carpeta
            if (file.exists() && file.absolutePath.contains("question_images")) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // NUEVO: Método para hacer una copia física real al duplicar preguntas o combinar temas
    fun duplicatePhysicalImage(existingPath: String?): String? {
        if (existingPath == null) return null
        return try {
            val sourceFile = File(existingPath)
            if (!sourceFile.exists()) return null

            if (!imagesDir.exists()) imagesDir.mkdirs()
            val destinationFile = File(imagesDir, "IMG_${UUID.randomUUID()}.jpg")

            sourceFile.inputStream().use { input ->
                destinationFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}