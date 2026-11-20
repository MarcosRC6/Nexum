package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask

/**
 * Show all masks while asking a specific occlusion card. After reveal remove
 * ONLY that mask; other answers must remain concealed in the same photograph.
 */
object ImageOcclusionPolicy {
    fun covered(
        masks: List<ImageOcclusionMask>, targetId: String, revealed: Boolean
    ): List<ImageOcclusionMask> =
        if (!revealed) masks else masks.filterNot { it.id == targetId }
}
