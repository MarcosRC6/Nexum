package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask

/** Geometry-only transforms. Every operation preserves valid normalized bounds. */
object OcclusionGeometry {
    enum class Handle { MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    private const val MIN_SIDE = .035
    fun transform(mask: ImageOcclusionMask, handle: Handle, dx: Double, dy: Double): ImageOcclusionMask {
        if (!dx.isFinite() || !dy.isFinite()) return mask
        var left = mask.x
        var top = mask.y
        var right = mask.x + mask.width
        var bottom = mask.y + mask.height
        when (handle) {
            Handle.MOVE -> {
                val width = mask.width
                val height = mask.height
                left = (left + dx).coerceIn(0.0, 1.0 - width)
                top = (top + dy).coerceIn(0.0, 1.0 - height)
                right = left + width; bottom = top + height
            }
            Handle.TOP_LEFT -> {
                left = (left + dx).coerceIn(0.0, (right - MIN_SIDE).coerceAtLeast(0.0))
                top = (top + dy).coerceIn(0.0, (bottom - MIN_SIDE).coerceAtLeast(0.0))
            }
            Handle.TOP_RIGHT -> {
                right = (right + dx).coerceIn((left + MIN_SIDE).coerceAtMost(1.0), 1.0)
                top = (top + dy).coerceIn(0.0, (bottom - MIN_SIDE).coerceAtLeast(0.0))
            }
            Handle.BOTTOM_LEFT -> {
                left = (left + dx).coerceIn(0.0, (right - MIN_SIDE).coerceAtLeast(0.0))
                bottom = (bottom + dy).coerceIn((top + MIN_SIDE).coerceAtMost(1.0), 1.0)
            }
            Handle.BOTTOM_RIGHT -> {
                right = (right + dx).coerceIn((left + MIN_SIDE).coerceAtMost(1.0), 1.0)
                bottom = (bottom + dy).coerceIn((top + MIN_SIDE).coerceAtMost(1.0), 1.0)
            }
        }
        return mask.copy(x = left, y = top, width = right - left, height = bottom - top)
    }
}
