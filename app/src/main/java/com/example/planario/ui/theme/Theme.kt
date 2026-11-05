package com.example.planario.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PlanarioDarkColorScheme = darkColorScheme(
    primary = Color(0xFFA7C7FF),
    onPrimary = Color(0xFF071326),
    primaryContainer = Color(0xFF1E3F73),
    onPrimaryContainer = Color(0xFFEAF2FF),

    secondary = Color(0xFF8DEFA5),
    onSecondary = Color(0xFF062611),
    secondaryContainer = Color(0xFF123B20),
    onSecondaryContainer = Color(0xFFE5FFEA),

    tertiary = PlanarioYellow,
    onTertiary = Color(0xFF281D00),
    tertiaryContainer = Color(0xFF4A3700),
    onTertiaryContainer = Color(0xFFFFF1C2),

    error = PlanarioRed,
    onError = Color.White,
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFFDADA),

    background = PlanarioDarkBackground,
    onBackground = PlanarioTextLight,

    surface = PlanarioDarkSurface,
    onSurface = PlanarioTextLight,

    surfaceVariant = PlanarioDarkSurfaceVariant,
    onSurfaceVariant = PlanarioTextSecondaryDark,

    outline = PlanarioDarkOutline,
    outlineVariant = PlanarioDarkOutline.copy(alpha = 0.18f),

    inverseSurface = Color(0xFFE8ECF2),
    inverseOnSurface = Color(0xFF111318),
    inversePrimary = PlanarioBlue
)

private val PlanarioLightColorScheme = lightColorScheme(
    primary = PlanarioBlue,
    onPrimary = Color.White,
    primaryContainer = PlanarioBlueSoft,
    onPrimaryContainer = Color(0xFF0B2147),

    secondary = Color(0xFF1F8F3A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDFBE5),
    onSecondaryContainer = Color(0xFF083B15),

    tertiary = Color(0xFF8A6500),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9A8),
    onTertiaryContainer = Color(0xFF2D2100),

    error = Color(0xFFB91C1C),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),

    background = PlanarioLightBackground,
    onBackground = PlanarioTextDark,

    surface = PlanarioLightSurface,
    onSurface = PlanarioTextDark,

    surfaceVariant = PlanarioLightSurfaceVariant,
    onSurfaceVariant = PlanarioTextSecondaryLight,

    outline = PlanarioLightOutline,
    outlineVariant = PlanarioLightOutline.copy(alpha = 0.35f),

    inverseSurface = Color(0xFF111827),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = PlanarioBlueLight
)

@Composable
fun PlanarioTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            PlanarioDarkColorScheme
        } else {
            PlanarioLightColorScheme
        },
        shapes = PlanarioShapes,
        content = content
    )
}