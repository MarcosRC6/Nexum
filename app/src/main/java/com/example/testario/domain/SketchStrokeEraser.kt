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
        val maxTrailSamples = 256
        val sampledTrail = if (trail.size <= maxTrailSamples) trail else {
            val stride = trail.size / maxTrailSamples + 1
            trail.filterIndexed { index, _ -> index % stride == 0 } + trail.last()
        }
        val radiusSq = radiusPx * radiusPx
        data class Segment(val ax: Double, val ay: Double, val dx: Double,
            val dy: Double, val lengthSq: Double)
        val segments = sampledTrail.zipWithNext().map { (a, b) ->
            val ax = a.x * width
            val ay = a.y * height
            val dx = (b.x - a.x) * width
            val dy = (b.y - a.y) * height
            Segment(ax, ay, dx, dy, dx * dx + dy * dy)
        }
        val xs = sampledTrail.map { it.x * width }
        val ys = sampledTrail.map { it.y * height }
        val left = xs.min() - radiusPx
        val right = xs.max() + radiusPx
        val top = ys.min() - radiusPx
        val bottom = ys.max() + radiusPx

        fun nearEraser(p: SketchPoint): Boolean {
            val px = p.x * width
            val py = p.y * height
            if (px !in left..right || py !in top..bottom) return false
            if (segments.isEmpty()) {
                val dx = px - xs.first()
                val dy = py - ys.first()
                return dx * dx + dy * dy <= radiusSq
            }
            return segments.any { segment ->
                val dot = (px - segment.ax) * segment.dx + (py - segment.ay) * segment.dy
                val t = if (segment.lengthSq < 1e-12) 0.0 else
                    (dot / segment.lengthSq).coerceIn(0.0, 1.0)
                val dx = px - (segment.ax + t * segment.dx)
                val dy = py - (segment.ay + t * segment.dy)
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
