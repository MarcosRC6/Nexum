package com.example.testario.domain

import com.example.testario.data.model.ImageOcclusionMask
import kotlin.math.abs
import kotlin.math.min

/** Normalized, resolution-independent rectangle drawn directly over a photograph. */
object OcclusionDrawRules {
    fun rectangle(x0: Double, y0: Double, x1: Double, y1: Double): ImageOcclusionMask? {
        if (!x0.isFinite() || !y0.isFinite() || !x1.isFinite() || !y1.isFinite())
            return null
        val a = x0.coerceIn(0.0, 1.0)
        val b = y0.coerceIn(0.0, 1.0)
        val c = x1.coerceIn(0.0, 1.0)
        val d = y1.coerceIn(0.0, 1.0)
        val width = abs(c - a)
        val height = abs(d - b)
        // Reject accidental taps instead of creating invisible occlusions.
        if (width < 0.006 || height < 0.006) return null
        return ImageOcclusionMask(x = min(a, c), y = min(b, d),
            width = width, height = height)
    }
}
