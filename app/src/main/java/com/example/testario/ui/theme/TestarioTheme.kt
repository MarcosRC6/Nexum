package com.example.testario.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

data class TestarioColors(
    val background: Color,
    val surface: Color,
    val surfaceLight: Color,
    val primary: Color,
    val primaryDark: Color,
    val neonSurface: Color,
    val neonSelected: Color,
    val text: Color,
    val mutedText: Color,
    val green: Color,
    val greenDark: Color,
    val red: Color,
    val redDark: Color,
    val yellow: Color,
    val outline: Color,
    val navBackground: Color
)

// "Tinta y latón": warm ink-black surfaces, ivory text and a champagne-brass accent
// in dark mode; warm paper and bronze in light mode. Semantic colors keep their
// meaning (green = done, terracotta = error, amber = warning) but share the warm cast.
private val DarkTestarioColors = TestarioColors(
    background = Color(0xE00B0D0C),
    surface = Color(0xEB1A1E1B),
    surfaceLight = Color(0xF0252A26),
    primary = Color(0xFFD8B880),
    primaryDark = Color(0xFF3A2E17),
    neonSurface = Color(0xFF282D29),
    neonSelected = Color(0xFF3A2F18),
    text = Color(0xFFF2EBDC),
    mutedText = Color(0xFFA39C8B),
    green = Color(0xFF7BCB9E),
    greenDark = Color(0xFF16342A),
    red = Color(0xFFE8826F),
    redDark = Color(0xFF43211B),
    yellow = Color(0xFFF2B63D),
    outline = Color(0xFF2E322D),
    navBackground = Color(0xE60F1210)
)

private val LightTestarioColors = TestarioColors(
    background = Color(0xEAF3EEE2),
    surface = Color(0xF5FFFEFA),
    surfaceLight = Color(0xFFF1ECDF),
    primary = Color(0xFF8A5A12),
    primaryDark = Color(0xFFF1E1BF),
    neonSurface = Color(0xFFEDE6D6),
    neonSelected = Color(0xFFF1E3C3),
    text = Color(0xFF1B1A16),
    mutedText = Color(0xFF6B6558),
    green = Color(0xFF1F7A55),
    greenDark = Color(0xFFDCEFE3),
    red = Color(0xFFB5412D),
    redDark = Color(0xFFF8DDD6),
    yellow = Color(0xFF8F6200),
    outline = Color(0xFFD9D2C0),
    navBackground = Color(0xECF8F4EA)
)

private val LocalTestarioColors = staticCompositionLocalOf { DarkTestarioColors }

val TestarioBackground: Color @Composable get() = LocalTestarioColors.current.background
val TestarioSurface: Color @Composable get() = LocalTestarioColors.current.surface
val TestarioSurfaceLight: Color @Composable get() = LocalTestarioColors.current.surfaceLight
val TestarioPrimary: Color @Composable get() = LocalTestarioColors.current.primary
val TestarioPrimaryDark: Color @Composable get() = LocalTestarioColors.current.primaryDark
val TestarioNeonSurface: Color @Composable get() = LocalTestarioColors.current.neonSurface
val TestarioNeonSelected: Color @Composable get() = LocalTestarioColors.current.neonSelected
val TestarioText: Color @Composable get() = LocalTestarioColors.current.text
val TestarioMutedText: Color @Composable get() = LocalTestarioColors.current.mutedText
val TestarioGreen: Color @Composable get() = LocalTestarioColors.current.green
val TestarioGreenDark: Color @Composable get() = LocalTestarioColors.current.greenDark
val TestarioRed: Color @Composable get() = LocalTestarioColors.current.red
val TestarioRedDark: Color @Composable get() = LocalTestarioColors.current.redDark
val TestarioYellow: Color @Composable get() = LocalTestarioColors.current.yellow
val TestarioOutline: Color @Composable get() = LocalTestarioColors.current.outline
val TestarioNavBackground: Color @Composable get() = LocalTestarioColors.current.navBackground

private val EstudarioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun TestarioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val testarioColors = if (darkTheme) DarkTestarioColors else LightTestarioColors
    val materialColors = if (darkTheme) {
        // Every role is defined so dialogs, menus, sheets, chips and switches no
        // longer fall back to the stock Material violet. The surfaceContainer*
        // roles are opaque on purpose: overlays must stay readable over the watermark.
        darkColorScheme(
            primary = testarioColors.primary,
            onPrimary = Color(0xFF2A1D05), // ~9:1 on brass
            primaryContainer = testarioColors.primaryDark,
            onPrimaryContainer = testarioColors.text,
            inversePrimary = Color(0xFF8A5A12),
            secondary = Color(0xFF6BC4B4),
            onSecondary = Color(0xFF00332D),
            secondaryContainer = Color(0xFF123A35),
            onSecondaryContainer = Color(0xFFD2F2EC),
            tertiary = testarioColors.yellow,
            onTertiary = Color(0xFF3F2E00),
            tertiaryContainer = Color(0xFF3D3000),
            onTertiaryContainer = Color(0xFFFFE9B3),
            background = testarioColors.background,
            onBackground = testarioColors.text,
            surface = testarioColors.surface,
            onSurface = testarioColors.text,
            surfaceVariant = testarioColors.surfaceLight,
            onSurfaceVariant = testarioColors.mutedText,
            surfaceTint = testarioColors.primary,
            inverseSurface = Color(0xFFF2EBDC),
            inverseOnSurface = Color(0xFF1B1A16),
            error = testarioColors.red,
            onError = Color(0xFF4A1208),
            errorContainer = testarioColors.redDark,
            onErrorContainer = Color(0xFFFFDAD2),
            outline = testarioColors.outline,
            outlineVariant = Color(0xFF252A26),
            surfaceContainerLowest = Color(0xFF080A09),
            surfaceContainerLow = Color(0xFF0F1210),
            surfaceContainer = Color(0xFF151816),
            surfaceContainerHigh = Color(0xFF1C201D),
            surfaceContainerHighest = Color(0xFF242925)
        )
    } else {
        lightColorScheme(
            primary = testarioColors.primary,
            onPrimary = Color.White, // ~5.9:1 on bronze
            primaryContainer = testarioColors.primaryDark,
            onPrimaryContainer = testarioColors.text,
            inversePrimary = Color(0xFFD8B880),
            secondary = Color(0xFF1F7A6E),
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFD4EEE9),
            onSecondaryContainer = Color(0xFF0A2F2A),
            tertiary = testarioColors.yellow,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE9B8),
            onTertiaryContainer = Color(0xFF3A2600),
            background = testarioColors.background,
            onBackground = testarioColors.text,
            surface = testarioColors.surface,
            onSurface = testarioColors.text,
            surfaceVariant = testarioColors.surfaceLight,
            onSurfaceVariant = testarioColors.mutedText,
            surfaceTint = testarioColors.primary,
            inverseSurface = Color(0xFF1B1A16),
            inverseOnSurface = Color(0xFFF2EBDC),
            error = testarioColors.red,
            onError = Color.White,
            errorContainer = testarioColors.redDark,
            onErrorContainer = Color(0xFF4A130A),
            outline = testarioColors.outline,
            outlineVariant = Color(0xFFE6E0D0),
            surfaceContainerLowest = Color(0xFFFFFDF8),
            surfaceContainerLow = Color(0xFFFAF6EC),
            surfaceContainer = Color(0xFFF4EFE3),
            surfaceContainerHigh = Color(0xFFEEE8DA),
            surfaceContainerHighest = Color(0xFFE7E0D0)
        )
    }

    val currentDensity = LocalDensity.current
    val customDensity = Density(
        density = currentDensity.density * uiScale,
        fontScale = currentDensity.fontScale * uiScale
    )

    CompositionLocalProvider(
        LocalTestarioColors provides testarioColors,
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = EstudarioTypography,
            shapes = EstudarioShapes,
            content = content
        )
    }
}
