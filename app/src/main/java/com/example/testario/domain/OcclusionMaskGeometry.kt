package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask

/** Word-style mask editing without changing the original image. Normalized 0..1. */
enum class MaskHandle { MOVE, LEFT, RIGHT, TOP, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

object OcclusionMaskGeometry {
    private const val MIN_SIDE = .04

    fun drag(mask: ImageOcclusionMask, handle: MaskHandle, dx: Double, dy: Double): ImageOcclusionMask {
        require(dx.isFinite() && dy.isFinite())
        val left = mask.x
        val right = mask.x + mask.width
        val top = mask.y
        val bottom = mask.y + mask.height
        if (handle == MaskHandle.MOVE) {
            return mask.copy(
                x = (mask.x + dx).coerceIn(0.0, 1.0 - mask.width),
                y = (mask.y + dy).coerceIn(0.0, 1.0 - mask.height)
            )
        }
        val newLeft = if (handle in setOf(MaskHandle.LEFT, MaskHandle.TOP_LEFT, MaskHandle.BOTTOM_LEFT))
            (left + dx).coerceIn(0.0, right - MIN_SIDE) else left
        val newRight = if (handle in setOf(MaskHandle.RIGHT, MaskHandle.TOP_RIGHT, MaskHandle.BOTTOM_RIGHT))
            (right + dx).coerceIn(newLeft + MIN_SIDE, 1.0) else right
        val newTop = if (handle in setOf(MaskHandle.TOP, MaskHandle.TOP_LEFT, MaskHandle.TOP_RIGHT))
            (top + dy).coerceIn(0.0, bottom - MIN_SIDE) else top
        val newBottom = if (handle in setOf(MaskHandle.BOTTOM, MaskHandle.BOTTOM_LEFT, MaskHandle.BOTTOM_RIGHT))
            (bottom + dy).coerceIn(newTop + MIN_SIDE, 1.0) else bottom
        return mask.copy(x = newLeft, y = newTop,
            width = newRight - newLeft, height = newBottom - newTop)
    }
}
