package com.example.testario.ui.screen

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Coil honours EXIF orientation. Our ink and solution coordinates must use
 * the same DISPLAYED aspect ratio, not the JPEG's unrotated pixel dimensions.
 */
internal object ImageDisplayGeometry {
    fun orientedDimensions(width: Int, height: Int, orientation: Int): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val quarterTurn = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        return if (quarterTurn) height to width else width to height
    }

    suspend fun read(context: Context, uriText: String): Pair<Int, Int>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(uriText)
                val local = uriText.startsWith("/") && file.isFile
                val uri = Uri.parse(uriText)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                if (local) {
                    BitmapFactory.decodeFile(file.absolutePath, options)
                } else {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, options)
                    }
                }
                val w = options.outWidth
                val h = options.outHeight
                if (w <= 0 || h <= 0 || w.toLong() * h > 100_000_000L)
                    return@runCatching null
                // PNGs and custom blank canvases may carry no EXIF at all.
                // Image bounds are valid even when metadata parsing fails.
                val orientation = runCatching {
                    if (local) {
                        ExifInterface(file.absolutePath).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                        )
                    } else {
                        context.contentResolver.openInputStream(uri)?.use {
                            ExifInterface(it).getAttributeInt(
                                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                            )
                        } ?: ExifInterface.ORIENTATION_NORMAL
                    }
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                orientedDimensions(w, h, orientation)
            }.getOrNull()
        }
}
