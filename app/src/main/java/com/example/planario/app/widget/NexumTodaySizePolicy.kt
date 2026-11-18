package com.example.planario.app.widget

/** Nunca obliga a mostrar secciones que no caben en el espacio concedido por launcher. */
internal object NexumTodaySizePolicy {
    fun compact(@Suppress("UNUSED_PARAMETER") mode: String, widthDp: Int, heightDp: Int): Boolean =
        // Las celdas reales priman sobre el ajuste heredado: ampliar debe mostrar objetivos.
        heightDp < 170 || widthDp < 175

    fun details(mode: String, heightDp: Int): Boolean =
        heightDp >= 310 || mode == "DETAIL" && heightDp >= 285

    fun week(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 250 && heightDp >= 425
}
