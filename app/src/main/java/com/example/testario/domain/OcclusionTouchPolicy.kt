package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import kotlin.math.abs

/**
 * Single-mode occlusion interactions. Geometry stays normalized and independent
 * of screen size, zoom and pan. Rendered corner handles remain visual only:
 * hit-testing always selects the nearest actual corner.
 */
object OcclusionTouchPolicy {
    sealed interface Hit {
        data object Background : Hit
        data class Body(val id: String) : Hit
        data class Corner(val id: String, val handle: OcclusionHandle) : Hit
    }

    fun hitTest(
        masks: List<ImageOcclusionMask>,
        selectedId: String?,
        x: Double,
        y: Double,
        cornerToleranceX: Double,
        cornerToleranceY: Double
    ): Hit {
        if (!x.isFinite() || !y.isFinite()) return Hit.Background
        val selected = masks.firstOrNull { it.id == selectedId }
        if (selected != null && cornerToleranceX > 0.0 && cornerToleranceY > 0.0) {
            val corners = listOf(
                Triple(selected.x, selected.y, OcclusionHandle.TOP_LEFT),
                Triple(selected.x + selected.width, selected.y, OcclusionHandle.TOP_RIGHT),
                Triple(selected.x, selected.y + selected.height, OcclusionHandle.BOTTOM_LEFT),
                Triple(selected.x + selected.width, selected.y + selected.height,
                    OcclusionHandle.BOTTOM_RIGHT)
            )
            val nearest = corners.mapNotNull { (cx, cy, handle) ->
                val dx = abs(cx - x) / cornerToleranceX
                val dy = abs(cy - y) / cornerToleranceY
                if (dx <= 1.0 && dy <= 1.0) {
                    val distance = dx * dx + dy * dy
                    handle to distance
                } else null
            }.minByOrNull { it.second }
            if (nearest != null) return Hit.Corner(selected.id, nearest.first)
        }

        // Tap a previously created rectangle to make it active, even if the
        // user has just deselected another one. Later masks are visually on top.
        val tapped = masks.asReversed().firstOrNull { mask ->
            x >= mask.x && x <= mask.x + mask.width &&
                y >= mask.y && y <= mask.y + mask.height
        }
        return tapped?.let { Hit.Body(it.id) } ?: Hit.Background
    }

    fun createAt(x: Double, y: Double): ImageOcclusionMask? =
        OcclusionDrawRules.rectangle(
            (x - .075).coerceIn(0.0, 1.0),
            (y - .05).coerceIn(0.0, 1.0),
            (x + .075).coerceIn(0.0, 1.0),
            (y + .05).coerceIn(0.0, 1.0)
        )
}
