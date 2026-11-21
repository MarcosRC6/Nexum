package com.example.testario.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Non-destructive vector layers attached to a specific image *usage*. */
@Serializable
enum class ImageAnnotationTool { PEN, HIGHLIGHTER }

@Serializable
data class ImageAnnotationPoint(val x: Double, val y: Double)

/**
 * Geometry is relative to the actual image pixels, not a cropped preview.
 * Width is relative to the shorter image side. Alpha is preserved in backups.
 */
@Serializable
data class ImageAnnotationStroke(
    val id: String = UUID.randomUUID().toString(),
    val tool: ImageAnnotationTool = ImageAnnotationTool.PEN,
    val colorArgb: Long = 0xFFFFD54FL,
    val widthFraction: Double = 0.006,
    val opacity: Double = 1.0,
    val points: List<ImageAnnotationPoint>
)

@Serializable
data class ImageAnnotations(
    val strokes: List<ImageAnnotationStroke> = emptyList()
) {
    val isEmpty: Boolean get() = strokes.isEmpty()
}
