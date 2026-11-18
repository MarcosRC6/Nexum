package com.example.planario.app.widget

/** Nunca obliga a mostrar secciones que no caben en el espacio concedido por launcher. */
internal object NexumTodaySizePolicy {
    fun compact(mode: String, widthDp: Int, heightDp: Int): Boolean =
        mode == "COMPACT" || heightDp < 205 || widthDp < 180

    fun details(mode: String, heightDp: Int): Boolean =
        heightDp >= 310 || mode == "DETAIL" && heightDp >= 285

    fun week(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= 250 && heightDp >= 425
}
