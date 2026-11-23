package com.example.testario.domain

import kotlinx.coroutines.flow.MutableStateFlow

/** Paso actual de una importación o restauración, para la barra de progreso de Ajustes. */
object NexumProgress {
    data class Step(val label: String, val fraction: Float)

    val current = MutableStateFlow<Step?>(null)

    fun set(label: String, fraction: Float) { current.value = Step(label, fraction) }

    fun clear() { current.value = null }
}
