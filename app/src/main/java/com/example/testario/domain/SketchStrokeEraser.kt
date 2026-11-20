package com.example.testario.domain

import com.example.testario.data.model.SketchPoint
import com.example.testario.data.model.SketchStroke
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Vector eraser: only the touched portions of a stroke disappear.
 * Brush metadata is kept for remaining segments, including older v6 drawings.
 * Image coordinates are normalized; distances are calculated in screen pixels.
 */
object SketchStrokeEraser {
    fun erase(
        strokes: List<SketchStroke>,
        trail: List<SketchPoint>,
        width: Int,
        height: Int,
        radiusPx: Double = 15.0
    ): List<SketchStroke> {
        if (trail.isEmpty() || strokes.isEmpty() || width <= 0 || height <= 0 ||
            !radiusPx.isFinite() || radiusPx <= 0.0) return strokes
        val sampledTrail = if (trail.size <= 512) trail else trail.filterIndexed { index, _ ->
            index % (trail.size / 512 + 1) == 0
        } + trail.last()
        val radiusSq = radiusPx * radiusPx

        fun nearEraser(p: SketchPoint): Boolean {
            val px = p.x * width
            val py = p.y * height
            if (sampledTrail.size == 1) {
                val t = sampledTrail.first()
                val dx = px - t.x * width
                val dy = py - t.y * height
                return dx * dx + dy * dy <= radiusSq
            }
            return sampledTrail.zipWithNext().any { (a, b) ->
                val ax = a.x * width
                val ay = a.y * height
                val bx = b.x * width
                val by = b.y * height
                val sx = bx - ax
                val sy = by - ay
                val distanceSq = sx * sx + sy * sy
                val t = if (distanceSq < 1e-12) 0.0 else
                    (((px - ax) * sx + (py - ay) * sy) / distanceSq).coerceIn(0.0, 1.0)
                val dx = px - (ax + t * sx)
                val dy = py - (ay + t * sy)
                dx * dx + dy * dy <= radiusSq
            }
        }

        return strokes.flatMap { stroke ->
            if (stroke.points.size < 2) return@flatMap listOf(stroke)
            val fragments = mutableListOf<List<SketchPoint>>()
            var current = mutableListOf<SketchPoint>()
            var affected = false
            fun flush() {
                if (current.size >= 2) fragments.add(current.toList())
                current = mutableListOf()
            }
            stroke.points.zipWithNext().forEachIndexed { segmentIndex, (first, last) ->
                val distance = hypot((last.x - first.x) * width, (last.y - first.y) * height)
                val steps = ceil(distance / (radiusPx / 2.0).coerceAtLeast(2.0))
                    .toInt().coerceIn(1, 256)
                for (j in 0..steps) {
                    if (segmentIndex > 0 && j == 0) continue
                    val t = j.toDouble() / steps
                    val position = SketchPoint(
                        first.x + (last.x - first.x) * t,
                        first.y + (last.y - first.y) * t
                    )
                    if (nearEraser(position)) {
                        affected = true
                        flush()
                    } else if (current.lastOrNull() != position) current.add(position)
                }
            }
            flush()
            if (!affected) listOf(stroke) else fragments.mapIndexed { index, pts ->
                stroke.copy(
                    id = if (index == 0) stroke.id else UUID.randomUUID().toString(),
                    points = pts
                )
            }
        }
    }
}
