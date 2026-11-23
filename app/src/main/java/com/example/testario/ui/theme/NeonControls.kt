package com.example.testario.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Shared, theme-aware action style for NEXUM.
 * Default: ink/paper panel + brass hairline outline.
 * Selected: the same outline + a restrained translucent brass tint.
 * Disabled: neutral subdued panel, no accent outline.
 *
 * A ButtonColors parameter is retained for source compatibility with legacy
 * screens; the surface and outline are intentionally standardized here. Destructive
 * operations can still provide their own explicit border and red text.
 */
@Composable
private fun neonSurface(selected: Boolean): Color =
    if (selected) TestarioNeonSelected else TestarioNeonSurface

@Composable
private fun neonActionColors(selected: Boolean): ButtonColors = ButtonDefaults.buttonColors(
    containerColor = neonSurface(selected),
    contentColor = if (selected) TestarioPrimary else TestarioText,
    disabledContainerColor = TestarioNeonSurface.copy(alpha = 0.50f),
    disabledContentColor = TestarioMutedText.copy(alpha = 0.67f)
)

@Composable
private fun neonActionBorder(enabled: Boolean, border: BorderStroke?): BorderStroke =
    if (!enabled) {
        BorderStroke(1.dp, TestarioOutline.copy(alpha = 0.42f))
    } else {
        border ?: BorderStroke(1.dp, TestarioPrimary)
    }

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
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected),
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
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialOutlinedButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected),
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
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialTextButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected),
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
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val source = rememberSource(interactionSource)
    MaterialFilledTonalButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source),
        enabled = enabled,
        shape = shape,
        colors = neonActionColors(selected),
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
    val shape = RoundedCornerShape(12.dp)
    val source = rememberSource(interactionSource)
    MaterialIconButton(
        onClick = onClick,
        modifier = pressModifier(modifier, enabled, source).border(
            width = 1.dp,
            color = if (enabled) TestarioPrimary else TestarioOutline.copy(alpha = 0.42f),
            shape = shape
        ),
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = TestarioNeonSurface,
            contentColor = TestarioText,
            disabledContainerColor = TestarioNeonSurface.copy(alpha = 0.50f),
            disabledContentColor = TestarioMutedText.copy(alpha = 0.67f)
        ),
        interactionSource = source,
        content = content
    )
}
