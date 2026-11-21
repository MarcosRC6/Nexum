package com.example.testario.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileInputStream

/** Coil displays EXIF-rotated photos. Coordinates MUST use the displayed
 * orientation, otherwise an annotation lands on a different part of the image.
 */
internal fun exifSwapsAxes(orientation: Int): Boolean =
    orientation in setOf(5, 6, 7, 8)

internal fun orientedImageSize(context: Context, imageUri: String): Pair<Int, Int>? {
    fun source() = if (imageUri.startsWith("/")) FileInputStream(File(imageUri))
        else context.contentResolver.openInputStream(Uri.parse(imageUri))
    return runCatching {
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        source()?.use { stream -> BitmapFactory.decodeStream(stream, null, size) }
        if (size.outWidth <= 0 || size.outHeight <= 0 ||
            size.outWidth.toLong() * size.outHeight > 100_000_000L) return null
        val orientation = runCatching {
            source()?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        if (exifSwapsAxes(orientation))
            size.outHeight to size.outWidth
        else size.outWidth to size.outHeight
    }.getOrNull()
}
