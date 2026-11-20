package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.ImagePoint

/** Exactly one mapped image point per written answer. Stable solution and point IDs. */
object WrittenMarkerRules {
    fun place(solutions: List<CanonicalSolution>, solutionId: String, x: Double, y: Double): List<CanonicalSolution> {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        require(solutions.any { it.id == solutionId }) { "La solución ya no existe." }
        return solutions.mapIndexed { index, solution ->
            if (solution.id != solutionId) solution
            else solution.copy(points = listOf(
                solution.points.firstOrNull()?.copy(x = x, y = y, order = index + 1)
                    ?: ImagePoint(x = x, y = y, order = index + 1)
            ))
        }
    }

    fun removeSolution(solutions: List<CanonicalSolution>, removedId: String): List<CanonicalSolution> =
        solutions.filterNot { it.id == removedId }.mapIndexed { index, solution ->
            solution.copy(points = solution.points.map { it.copy(order = index + 1) })
        }
}
