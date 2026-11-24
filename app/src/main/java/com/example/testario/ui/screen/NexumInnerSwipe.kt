package com.example.testario.ui.screen

/**
 * Una pantalla con pestañas propias (Plan: Plan, Reloj, Calendario) se registra aquí para
 * que el deslizamiento recorra primero sus pestañas y solo después cambie de sección.
 * Devuelve true si el gesto lo ha absorbido la propia pantalla.
 */
object NexumInnerSwipe {
    var handler: ((Int) -> Boolean)? = null
}
