package com.example.planario.app.widget

/** La pantalla compacta debe caber en el lanzador incluso en tablets con celdas estrechas. */
internal object NexumClockSizePolicy {
    fun compact(widthDp: Int, heightDp: Int): Boolean =
        widthDp < 215 || heightDp < 160
}
