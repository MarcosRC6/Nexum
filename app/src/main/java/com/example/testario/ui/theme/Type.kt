package com.example.testario.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same sizes and weights as before; only tracking is new: tighter on big
// headings, slightly open on small labels, which reads cleaner on a phone.
// Editorial pairing: the system serif (Noto Serif) for display, headlines and large
// titles, sans-serif for everything you read or tap.
// shortcut: system fonts only, bundle a brand font pair (res/font) when one is chosen.
private fun style(
    weight: FontWeight, size: Int, line: Int, tracking: Float,
    family: FontFamily = FontFamily.SansSerif
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val EstudarioTypography = Typography(
    displayLarge = style(FontWeight.Bold, 44, 50, -0.5f, FontFamily.Serif),
    displayMedium = style(FontWeight.Bold, 36, 42, -0.5f, FontFamily.Serif),
    headlineLarge = style(FontWeight.Bold, 30, 36, -0.4f, FontFamily.Serif),
    headlineMedium = style(FontWeight.SemiBold, 26, 32, -0.3f, FontFamily.Serif),
    headlineSmall = style(FontWeight.SemiBold, 22, 28, -0.2f, FontFamily.Serif),
    titleLarge = style(FontWeight.SemiBold, 20, 26, -0.1f, FontFamily.Serif),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.1f),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.1f),
    bodyMedium = style(FontWeight.Normal, 14, 21, 0.15f),
    bodySmall = style(FontWeight.Normal, 12, 18, 0.2f),
    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.3f),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.4f)
)
