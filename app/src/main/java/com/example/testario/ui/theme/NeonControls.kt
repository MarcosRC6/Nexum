package com.example.testario.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.FilledTonalButton as MaterialFilledTonalButton
import androidx.compose.material3.IconButton as MaterialIconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton as MaterialOutlinedButton
import androidx.compose.material3.TextButton as MaterialTextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Shared action styles for NEXUM, with no outlines by default:
 *  - `selected = null` (default): the screen's main action, solid brass.
 *    TextButton: plain brass text.
 *  - `selected = false/true`: a toggle/segment. Off = quiet tonal fill, on = brass tint with
 *    brass label.
 * Secondary variants (Outlined/Tonal/Icon) are tonal fills when they are not toggles.
 * Disabled controls are muted. An explicit `border` is still honoured (destructive actions
 * pass a red one), except a brass one, which would just bring the old outline back.
 *
 * The ButtonColors parameter is kept for source compatibility with legacy screens.
 */
@Composable
private fun neonActionColors(selected: Boolean?, cta: Boolean): ButtonColors {
    val disabledContainer = TestarioMutedText.copy(alpha = 0.14f)
    val disabledContent = TestarioMutedText.copy(alpha = 0.55f)
    return when {
        selected == true -> ButtonDefaults.buttonColors(
            containerColor = TestarioNeonSelected, contentColor = TestarioPrimary,
            disabledContainerColor = disabledContainer, disabledContentColor = disabledContent
        )
        selected == null && cta -> ButtonDefaults.buttonColors(
            containerColor = TestarioPrimary, contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = disabledContainer, disabledContentColor = disabledContent
        )
        else -> ButtonDefaults.buttonColors(
            containerColor = TestarioNeonSurface, contentColor = TestarioText,
            disabledContainerColor = disabledContainer, disabledContentColor = disabledContent
        )
    }
}

@Composable
private fun neonTextColors(selected: Boolean?): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = if (selected == true) TestarioNeonSelected else Color.Transparent,
    contentColor = if (selected == false) TestarioText else TestarioPrimary,
    disabledContainerColor = Color.Transparent,
    disabledContentColor = TestarioMutedText.copy(alpha = 0.55f)
)

@Composable
private fun neonActionBorder(enabled: Boolean, border: BorderStroke?): BorderStroke? =
    if (!enabled || border == null || border.brush == SolidColor(TestarioPrimary)) null else border

@Composable
private fun rememberSource(given: MutableInteractionSource?): MutableInteractionSource =
    given ?: remember { MutableInteractionSource() }

// Presses shrink the control a few percent so every action feels tactile.
@Composable
private fun pressModifier(
    modifier: Modifier,
    enabled: Boolean,
    source: MutableInteractionSource
): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.97f else 1f, tween(90), label = "neonPress")
    return modifier.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun NeonButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected, cta = true),
        elevation = elevation,
        border = neonActionBorder(enabled, border),
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun NeonOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected, cta = false),
        elevation = elevation,
        border = neonActionBorder(enabled, border),
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun NeonTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(12.dp),
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialTextButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonTextColors(selected),
        elevation = elevation,
        border = neonActionBorder(enabled, border),
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun NeonTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.small,
    @Suppress("UNUSED_PARAMETER") colors: ButtonColors = ButtonDefaults.filledTonalButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    selected: Boolean? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialFilledTonalButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected, cta = false),
        elevation = elevation,
        border = neonActionBorder(enabled, border),
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun NeonIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialIconButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = TestarioText,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = TestarioMutedText.copy(alpha = 0.55f)
        ),
        interactionSource = source,
        content = content
    )
}
