package com.example.testario.ui.screen

import com.example.testario.data.model.ImageOcclusionMask

/** Pure image-space transform. 8 corner/edge handles; min 4% of the picture.
 * No EXIF or device-pixel coupling because coordinates are already normalized.
 */
internal fun moveOrResizeMask(
    source: ImageOcclusionMask,
    handle: String,
    deltaX: Double,
    deltaY: Double
): ImageOcclusionMask {
    require(deltaX.isFinite() && deltaY.isFinite())
    var x = source.x
    var y = source.y
    var width = source.width
    var height = source.height
    val minimum = .04
    if (handle == "MOVE") {
        x = (source.x + deltaX).coerceIn(0.0, 1.0 - width)
        y = (source.y + deltaY).coerceIn(0.0, 1.0 - height)
    } else {
        if ("L" in handle) {
            x = (source.x + deltaX).coerceIn(0.0, source.x + source.width - minimum)
            width = source.width + source.x - x
        }
        if ("R" in handle)
            width = (source.width + deltaX).coerceIn(minimum, 1.0 - x)
        if ("T" in handle) {
            y = (source.y + deltaY).coerceIn(0.0, source.y + source.height - minimum)
            height = source.height + source.y - y
        }
        if ("B" in handle)
            height = (source.height + deltaY).coerceIn(minimum, 1.0 - y)
    }
    return source.copy(x = x, y = y, width = width, height = height)
}
