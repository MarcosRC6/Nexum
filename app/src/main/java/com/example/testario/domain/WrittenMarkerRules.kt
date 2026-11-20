package com.example.testario.domain

import com.example.testario.data.model.CanonicalSolution
import com.example.testario.data.model.ImagePoint

/** A numbered marker belongs to ONE written solution. It is not a separate,
 * unrelated list of numbers; deleting a solution shifts subsequent labels.
 * Do not rewrite stable point IDs when only the visible order changes.
 */
object WrittenMarkerRules {
    fun place(
        solutions: List<CanonicalSolution>, solutionId: String, x: Double, y: Double
    ): List<CanonicalSolution> {
        require(x.isFinite() && x in 0.0..1.0 && y.isFinite() && y in 0.0..1.0) {
            "El marcador debe quedar dentro de la fotografía."
        }
        val index = solutions.indexOfFirst { it.id == solutionId }
        require(index >= 0) { "Selecciona primero una solución." }
        val solution = solutions[index]
        require(solution.text.isNotBlank()) {
            "Escribe primero la solución ${index + 1} para colocarla."
        }
        require(solution.points.isEmpty()) {
            "La solución ${index + 1} ya está colocada. Elimina su marcador para cambiarlo."
        }
        return solutions.mapIndexed { i, item ->
            if (item.id == solutionId) item.copy(
                points = listOf(ImagePoint(x = x, y = y, order = i + 1))
            ) else item
        }
    }

    fun withoutMarker(solutions: List<CanonicalSolution>, markerId: String): List<CanonicalSolution> =
        solutions.mapIndexed { index, solution ->
            solution.copy(points = solution.points.filterNot { it.id == markerId }
                .map { it.copy(order = index + 1) })
        }

    fun withoutSolution(solutions: List<CanonicalSolution>, solutionId: String): List<CanonicalSolution> =
        solutions.filterNot { it.id == solutionId }
            .mapIndexed { index, remaining ->
                remaining.copy(points = remaining.points.map { it.copy(order = index + 1) })
            }
}
