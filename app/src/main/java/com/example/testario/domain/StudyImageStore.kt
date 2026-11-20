package com.example.testario.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * New exercise media persistence; deliberately does not relabel PNG/WebP as .jpg.
 * Refuses missing, invalid or over-large images instead of keeping a broken URI.
 */
class StudyImageStore(private val context: Context) {
    private val folder = File(context.filesDir, "question_images")
    private val maxBytes = 32L * 1024L * 1024L

    /**
     * Create a genuine private PNG so a drawing and an imported photo use the
     * SAME non-destructive annotation editor. The file is included in NEXUM
     * image ZIP backups; an unsaved draft never mutates another image.
     */
    fun createBlankCanvas(width: Int = 1600, height: Int = 1000): String {
        require(width in 320..4096 && height in 320..4096)
        require(folder.isDirectory || folder.mkdirs()) { "No se pudo crear la carpeta de imágenes." }
        val target = File(folder, "NEXUM_canvas_${UUID.randomUUID()}.png")
        val temp = File(folder, ".blank-${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            FileOutputStream(temp).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "No se pudo generar el lienzo."
                }
                output.fd.sync()
            }
            require(temp.renameTo(target)) { "No se pudo guardar el lienzo." }
            return target.absolutePath
        } finally {
            bitmap.recycle()
            temp.delete()
        }
    }

    fun persist(uriOrPath: String?): String? {
        if (uriOrPath.isNullOrBlank()) return null
        val source = File(uriOrPath)
        val safeRoot = folder.canonicalPath + File.separator
        if (source.canonicalPath.startsWith(safeRoot)) {
            require(source.isFile) { "La imagen guardada ya no existe." }
            return source.absolutePath
        }
        require(folder.isDirectory || folder.mkdirs()) { "No se pudo preparar el almacenamiento de imágenes." }
        val temp = File(folder, ".pending-${UUID.randomUUID()}")
        try {
            val uri = Uri.parse(uriOrPath)
            val input = when (uri.scheme) {
                "content", "android.resource" -> context.contentResolver.openInputStream(uri)
                else -> null
            } ?: throw IllegalArgumentException("Imagen no accesible o URI incompatible.")
            val sha = MessageDigest.getInstance("SHA-256")
            var total = 0L
            input.use { stream ->
                FileOutputStream(temp).use { output ->
                    val bytes = ByteArray(8192)
                    while (true) {
                        val n = stream.read(bytes)
                        if (n < 0) break
                        total += n
                        require(total <= maxBytes) { "La imagen supera 32 MiB." }
                        sha.update(bytes, 0, n)
                        output.write(bytes, 0, n)
                    }
                    output.fd.sync()
                }
            }
            require(total > 0) { "La imagen está vacía." }
            val suffix = detectFormat(temp)
                ?: throw IllegalArgumentException("Se admiten imágenes JPEG, PNG o WebP válidas.")
            val dims = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, dims)
            require(dims.outWidth > 0 && dims.outHeight > 0 &&
                dims.outWidth <= 16000 && dims.outHeight <= 16000 &&
                dims.outWidth.toLong() * dims.outHeight <= 100_000_000L) {
                "No se pudo verificar la imagen o excede las dimensiones permitidas."
            }
            val hex = sha.digest().joinToString("") { "%02x".format(it) }
            val target = File(folder, "NEXUM_${hex}.${suffix}")
            if (target.exists()) {
                require(target.length() == total) { "Conflicto de integridad del archivo." }
                return target.absolutePath
            }
            require(temp.renameTo(target)) { "No se pudo confirmar la imagen." }
            return target.absolutePath
        } finally {
            temp.delete()
        }
    }

    private fun detectFormat(file: File): String? {
        val header = ByteArray(12)
        val read = FileInputStream(file).use { it.read(header) }
        if (read >= 3 && (header[0].toInt() and 255) == 255 &&
            (header[1].toInt() and 255) == 216 &&
            (header[2].toInt() and 255) == 255) return "jpg"
        if (read >= 8 && header.copyOfRange(0, 8).contentEquals(
            byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        )) return "png"
        if (read >= 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP") return "webp"
        return null
    }
}
