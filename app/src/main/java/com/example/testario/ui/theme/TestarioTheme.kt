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

private val DarkTestarioColors = TestarioColors(
    background = Color(0xE0080C16),
    surface = Color(0xE6111827),
    surfaceLight = Color(0xD91A2233),
    primary = Color(0xFFC283FF),
    primaryDark = Color(0xFF352347),
    neonSurface = Color(0xFF111322),
    neonSelected = Color(0xFF342046),
    text = Color(0xFFF8F7FC),
    mutedText = Color(0xFF98A4BA),
    green = Color(0xFF61D995),
    greenDark = Color(0xFF183E32),
    red = Color(0xFFFF6B78),
    redDark = Color(0xFF4A2029),
    yellow = Color(0xFFFFC857),
    outline = Color(0xFF2C3850),
    navBackground = Color(0xE60D1320)
)

private val LightTestarioColors = TestarioColors(
    background = Color(0xEAF4F5FA),
    surface = Color(0xEDFFFFFF),
    surfaceLight = Color(0xE6EDEFF6),
    primary = Color(0xFF7E39D4),
    primaryDark = Color(0xFFEEE2FF),
    neonSurface = Color(0xFFFFFFFF),
    neonSelected = Color(0xFFF0E5FF),
    text = Color(0xFF151827),
    mutedText = Color(0xFF687188),
    green = Color(0xFF19895A),
    greenDark = Color(0xFFDDF4E9),
    red = Color(0xFFC63E50),
    redDark = Color(0xFFFFE1E6),
    yellow = Color(0xFFA56A00),
    outline = Color(0xFFD9DDEA),
    navBackground = Color(0xECFCFCFF)
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
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TestarioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val testarioColors = if (darkTheme) DarkTestarioColors else LightTestarioColors
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = testarioColors.primary,
            onPrimary = testarioColors.text,
            primaryContainer = testarioColors.primaryDark,
            onPrimaryContainer = testarioColors.text,
            secondary = Color(0xFF70C7FF),
            background = testarioColors.background,
            surface = testarioColors.surface,
            surfaceVariant = testarioColors.surfaceLight,
            outline = testarioColors.outline,
            onBackground = testarioColors.text,
            onSurface = testarioColors.text,
            onSurfaceVariant = testarioColors.mutedText,
            error = testarioColors.red
        )
    } else {
        lightColorScheme(
            primary = testarioColors.primary,
            onPrimary = testarioColors.text,
            primaryContainer = testarioColors.primaryDark,
            onPrimaryContainer = testarioColors.text,
            secondary = Color(0xFF2476A8),
            background = testarioColors.background,
            surface = testarioColors.surface,
            surfaceVariant = testarioColors.surfaceLight,
            outline = testarioColors.outline,
            onBackground = testarioColors.text,
            onSurface = testarioColors.text,
            onSurfaceVariant = testarioColors.mutedText,
            error = testarioColors.red
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
