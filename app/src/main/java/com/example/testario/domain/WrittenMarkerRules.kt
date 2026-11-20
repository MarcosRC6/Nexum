package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.ImagePoint

/**
 * Image markers belong to canonical written solutions, not to the source image.
 * One NEW marker per solution. Moving an existing marker retains its stable ID.
 * Older imported multi-marker exercises are not truncated silently.
 */
object WrittenMarkerRules {
    fun place(
        solutions: List<CanonicalSolution>, selectedId: String?,
        x: Double, y: Double
    ): List<CanonicalSolution> {
        if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0)
            return solutions
        val id = selectedId ?: return solutions
        val index = solutions.indexOfFirst { it.id == id }
        if (index < 0 || solutions[index].text.isBlank()) return solutions
        val existing = solutions[index].points.firstOrNull()
        val next = if (existing != null) existing.copy(x = x, y = y)
            else {
                if (solutions.sumOf { it.points.size } >= solutions.size) return solutions
                ImagePoint(x = x, y = y, order = index + 1)
            }
        return solutions.mapIndexed { i, solution ->
            if (i != index) solution else solution.copy(
                points = if (existing == null) listOf(next)
                    else listOf(next) + solution.points.drop(1)
            )
        }
    }

    /** Removing solution #2 also removes its drawing marker and reindexes #3 as #2. */
    fun removeSolution(
        solutions: List<CanonicalSolution>, removedId: String
    ): List<CanonicalSolution> {
        val remaining = solutions.filterNot { it.id == removedId }
        var order = 0
        return remaining.map { solution ->
            solution.copy(points = solution.points.map { marker ->
                marker.copy(order = ++order)
            })
        }
    }

    fun activeMarkerCount(solutions: List<CanonicalSolution>) =
        solutions.sumOf { it.points.size }
}
