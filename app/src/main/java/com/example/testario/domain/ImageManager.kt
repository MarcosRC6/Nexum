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

        // Si ya es una imagen interna nuestra, no hacemos nada.
        val canonicalImagesDir = runCatching {
            imagesDir.canonicalPath + File.separator
        }.getOrNull()
        val existingFile = runCatching { File(temporaryUriString) }.getOrNull()
        if (
            canonicalImagesDir != null &&
            existingFile != null &&
            runCatching { existingFile.canonicalPath.startsWith(canonicalImagesDir) }.getOrDefault(false)
        ) {
            return existingFile.absolutePath
        }

        return try {
            val uri = Uri.parse(temporaryUriString)
            if (!imagesDir.exists()) imagesDir.mkdirs()

            val destinationFile = File(imagesDir, "IMG_${UUID.randomUUID()}.jpg")
            val input = context.contentResolver.openInputStream(uri)
                ?: return temporaryUriString

            input.use {
                FileOutputStream(destinationFile).use { output ->
                    it.copyTo(output)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            temporaryUriString
        }
    }

    fun deleteImage(pathString: String?) {
        if (pathString.isNullOrBlank()) return
        try {
            val file = File(pathString)
            val canonicalImagesDir = imagesDir.canonicalPath + File.separator
            val canonicalFile = file.canonicalFile

            // Nunca se borra nada que no esté físicamente dentro de question_images.
            if (canonicalFile.exists() && canonicalFile.path.startsWith(canonicalImagesDir)) {
                canonicalFile.delete()
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