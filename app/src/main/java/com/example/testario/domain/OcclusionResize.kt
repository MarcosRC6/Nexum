package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask

/** Resize one or two edges without moving the opposite anchored edges. */
enum class OcclusionHandle(val horizontal: Int, val vertical: Int) {
    TOP_LEFT(-1, -1), TOP(0, -1), TOP_RIGHT(1, -1),
    LEFT(-1, 0), RIGHT(1, 0),
    BOTTOM_LEFT(-1, 1), BOTTOM(0, 1), BOTTOM_RIGHT(1, 1)
}

object OcclusionResize {
    fun translate(mask: ImageOcclusionMask, dx: Double, dy: Double): ImageOcclusionMask {
        if (!dx.isFinite() || !dy.isFinite()) return mask
        return mask.copy(
            x = (mask.x + dx).coerceIn(0.0, (1.0 - mask.width).coerceAtLeast(0.0)),
            y = (mask.y + dy).coerceIn(0.0, (1.0 - mask.height).coerceAtLeast(0.0))
        )
    }

    fun apply(
        mask: ImageOcclusionMask,
        handle: OcclusionHandle,
        deltaX: Double,
        deltaY: Double
    ): ImageOcclusionMask {
        if (!deltaX.isFinite() || !deltaY.isFinite()) return mask
        // A 4% minimum made small map labels impossible to cover precisely.
        // Preserve even smaller legacy masks without enlarging them.
        val minimumWidth = minOf(0.008, mask.width)
        val minimumHeight = minOf(0.008, mask.height)
        var left = mask.x
        var right = mask.x + mask.width
        var top = mask.y
        var bottom = mask.y + mask.height

        when (handle.horizontal) {
            -1 -> left = (left + deltaX).coerceIn(0.0, right - minimumWidth)
            1 -> right = (right + deltaX).coerceIn(left + minimumWidth, 1.0)
        }
        when (handle.vertical) {
            -1 -> top = (top + deltaY).coerceIn(0.0, bottom - minimumHeight)
            1 -> bottom = (bottom + deltaY).coerceIn(top + minimumHeight, 1.0)
        }

        return mask.copy(x = left, y = top, width = right - left, height = bottom - top)
    }
}
