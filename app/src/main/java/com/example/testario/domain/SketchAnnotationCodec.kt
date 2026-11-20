package com.example.testario.domain

import com.example.testario.data.model.*

/**
 * Creates and edits blank written-answer diagrams with exactly the same
 * non-destructive pen/highlighter tools used for imported photographs.
 * Legacy brush-only sketches round-trip without changing their geometry.
 */
object SketchAnnotationCodec {
    private fun legacyArgb(brush: String): Long = when (brush) {
        "white" -> 0xFFFFFFFFL
        "green" -> 0xFF4CAF50L
        "red" -> 0xFFEF5350L
        "black" -> 0xFF171717L
        else -> 0xFFA855F7L
    }

    fun toAnnotations(drawing: SketchDrawing): ImageAnnotations =
        ImageAnnotations(drawing.strokes.map { stroke ->
            ImageAnnotationStroke(
                id = stroke.id,
                tool = if (stroke.brush == "highlighter") ImageAnnotationTool.HIGHLIGHTER
                    else ImageAnnotationTool.PEN,
                colorArgb = stroke.colorArgb ?: legacyArgb(stroke.brush),
                opacity = stroke.opacity ?: 1.0,
                widthFraction = stroke.widthFraction ?: 0.005,
                points = stroke.points.map { ImageAnnotationPoint(it.x, it.y) }
            )
        })

    fun fromAnnotations(layer: ImageAnnotations): SketchDrawing {
        ImageAnnotationRules.requireValid(layer)
        return SketchDrawing(layer.strokes.map { stroke ->
            SketchStroke(
                id = stroke.id,
                points = stroke.points.map { SketchPoint(it.x, it.y) },
                brush = if (stroke.tool == ImageAnnotationTool.HIGHLIGHTER)
                    "highlighter" else "purple",
                colorArgb = stroke.colorArgb,
                opacity = stroke.opacity,
                widthFraction = stroke.widthFraction
            )
        })
    }
}
