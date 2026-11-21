package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.ImagePoint

/** One numbered answer location belongs to one stable solution ID. */
object WrittenMarkerRules {
    fun place(solutions: List<CanonicalSolution>, solutionId: String, x: Double, y: Double):
        List<CanonicalSolution> {
        require(x.isFinite() && y.isFinite() && x in 0.0..1.0 && y in 0.0..1.0)
        val target = solutions.indexOfFirst { it.id == solutionId }
        require(target >= 0 && solutions[target].text.isNotBlank()) {
            "Escribe primero la solución correspondiente."
        }
        return solutions.mapIndexed { index, solution ->
            if (solution.id == solutionId)
                solution.copy(points = listOf(ImagePoint(x = x, y = y, order = index + 1)))
            else solution
        }
    }

    fun withoutSolution(solutions: List<CanonicalSolution>, solutionId: String):
        List<CanonicalSolution> =
        solutions.filterNot { it.id == solutionId }.mapIndexed { index, solution ->
            solution.copy(points = solution.points.map { it.copy(order = index + 1) })
        }

    fun withoutMarker(solutions: List<CanonicalSolution>, markerId: String):
        List<CanonicalSolution> =
        solutions.mapIndexed { index, solution ->
            solution.copy(points = solution.points.filterNot { it.id == markerId }
                .map { it.copy(order = index + 1) })
        }
}
