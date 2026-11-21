package com.example.testario.domain

import com.example.testario.data.model.*

/** Restrict corrupted or hostile vector payloads before rendering/saving. */
object ImageAnnotationRules {
    private const val MAX_STROKES = 500
    private const val MAX_POINTS_PER_STROKE = 8000
    private const val MAX_POINTS_TOTAL = 100000

    fun errors(layer: ImageAnnotations): List<String> {
        val errors = mutableListOf<String>()
        if (layer.strokes.size > MAX_STROKES)
            errors += "La imagen contiene demasiados trazos."
        val ids = hashSetOf<String>()
        var pointsTotal = 0L
        layer.strokes.forEach { stroke ->
            if (stroke.id.isBlank() || !ids.add(stroke.id))
                errors += "Trazo duplicado o sin identificador."
            if (!stroke.widthFraction.isFinite() || stroke.widthFraction !in 0.001..0.1)
                errors += "Grosor de rotulador incompatible."
            if (!stroke.opacity.isFinite() || stroke.opacity !in 0.05..1.0)
                errors += "Opacidad de anotación incompatible."
            if (stroke.points.isEmpty() || stroke.points.size > MAX_POINTS_PER_STROKE)
                errors += "Trazo con longitud inválida."
            pointsTotal += stroke.points.size
            if (stroke.points.any {
                !it.x.isFinite() || !it.y.isFinite() || it.x !in 0.0..1.0 || it.y !in 0.0..1.0
            }) errors += "Anotación fuera de la imagen."
        }
        if (pointsTotal > MAX_POINTS_TOTAL) errors += "Anotación excesivamente grande."
        return errors.distinct()
    }

    fun requireValid(layer: ImageAnnotations) {
        val errors = errors(layer)
        require(errors.isEmpty()) { errors.joinToString(" ") }
    }

    /**
     * For exact deduplication only: geometry/brush/color/alpha matter;
     * random IDs from copies/imports do not.
     */
    fun signature(layer: ImageAnnotations): String =
        layer.strokes.joinToString("|") { stroke ->
            "${stroke.tool}:${stroke.colorArgb}:${stroke.widthFraction}:${stroke.opacity}:" +
                stroke.points.joinToString(";") { "${it.x},${it.y}" }
        }
}
