package com.example.testario.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator as MaterialLinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A slim brass progress track, shared by dark/light modes.
 * An indeterminate indicator is supported for file-import progress.
 */
@Composable
fun NeonLinearProgressIndicator(
    modifier: Modifier = Modifier,
    progress: (() -> Float)? = null,
    color: Color = Color.Unspecified,
    trackColor: Color = Color.Unspecified
) {
    val fill = if (color == Color.Unspecified) TestarioPrimary else color
    val track = if (trackColor == Color.Unspecified) TestarioNeonSelected else trackColor
    val shape = RoundedCornerShape(100.dp)
    val neonModifier = modifier
        .clip(shape)
        .background(track, shape)

    if (progress != null) {
        MaterialLinearProgressIndicator(
            progress = { progress().coerceIn(0f, 1f) },
            modifier = neonModifier,
            color = fill,
            trackColor = track
        )
    } else {
        MaterialLinearProgressIndicator(
            modifier = neonModifier,
            color = fill,
            trackColor = track
        )
    }
}
