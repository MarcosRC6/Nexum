package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask

/**
 * During a card round, every hidden area stays covered except the one being
 * reviewed after reveal. Never expose answers belonging to future rounds.
 */
object OcclusionRevealRules {
    fun covered(
        masks: List<ImageOcclusionMask>,
        targetId: String,
        revealed: Boolean
    ): List<ImageOcclusionMask> =
        if (revealed) masks.filterNot { it.id == targetId } else masks
}
