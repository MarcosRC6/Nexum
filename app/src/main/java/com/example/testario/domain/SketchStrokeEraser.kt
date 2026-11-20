package com.example.testario.domain

import com.example.testario.data.model.SketchPoint
import com.example.testario.data.model.SketchStroke
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * Continuous vector eraser. Only pixels along the swept finger path are
 * removed; unaffected portions keep their original pen/color/opacity.
 * One gesture => one edit/undo entry, not one undo per pointer event.
 */
object SketchStrokeEraser {
    fun erasePath(
        strokes: List<SketchStroke>,
        path: List<SketchPoint>,
        radius: Double = .025,
        aspectRatio: Double = 1.0
    ): List<SketchStroke> {
        if (path.isEmpty()) return strokes
        require(radius.isFinite() && radius in .001.. .15)
        require(aspectRatio.isFinite() && aspectRatio > 0)
        fun distance(x: SketchPoint, a: SketchPoint, b: SketchPoint): Double {
            val vx = (b.x - a.x) * aspectRatio
            val vy = b.y - a.y
            val wx = (x.x - a.x) * aspectRatio
            val wy = x.y - a.y
            val length2 = vx * vx + vy * vy
            val t = if (length2 == 0.0) 0.0
                else ((wx * vx + wy * vy) / length2).coerceIn(0.0, 1.0)
            return hypot(wx - t * vx, wy - t * vy)
        }
        fun hit(point: SketchPoint): Boolean =
            if (path.size == 1) distance(point, path[0], path[0]) <= radius
            else path.zipWithNext().any { (a, b) -> distance(point, a, b) <= radius }

        var modified = false
        val output = buildList {
            strokes.forEach { stroke ->
                // Densify long vector segments so an eraser crossing the middle
                // of a two-point line still removes the touched portion.
                val dense = buildList {
                    stroke.points.firstOrNull()?.let(::add)
                    stroke.points.zipWithNext().forEach { (a, b) ->
                        val length = hypot((b.x - a.x) * aspectRatio, b.y - a.y)
                        val steps = ceil(length / (radius / 2.0)).toInt().coerceIn(1, 350)
                        for (i in 1..steps) {
                            if (size >= 9900) break
                            val t = i.toDouble() / steps
                            add(SketchPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t))
                        }
                    }
                }
                if (dense.none(::hit)) {
                    add(stroke)
                } else {
                    modified = true
                    var fragment = mutableListOf<SketchPoint>()
                    fun flush() {
                        if (fragment.size >= 2) add(stroke.copy(
                            id = UUID.randomUUID().toString(),
                            points = fragment.toList()
                        ))
                        fragment = mutableListOf()
                    }
                    dense.forEach { point ->
                        if (hit(point)) flush()
                        else fragment.add(point)
                    }
                    flush()
                }
            }
        }
        return if (!modified || output.size > 500) strokes else output
    }
}
