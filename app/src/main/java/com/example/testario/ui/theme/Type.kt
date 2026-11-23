package com.example.testario.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Same sizes and weights as before; only tracking is new: tighter on big
// headings, slightly open on small labels, which reads cleaner on a phone.
// shortcut: system sans-serif, bundle a brand font (res/font) when one is chosen.
private fun style(weight: FontWeight, size: Int, line: Int, tracking: Float) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val EstudarioTypography = Typography(
    displayLarge = style(FontWeight.Bold, 44, 50, -0.5f),
    displayMedium = style(FontWeight.Bold, 36, 42, -0.5f),
    headlineLarge = style(FontWeight.Bold, 30, 36, -0.4f),
    headlineMedium = style(FontWeight.SemiBold, 26, 32, -0.3f),
    headlineSmall = style(FontWeight.SemiBold, 22, 28, -0.2f),
    titleLarge = style(FontWeight.SemiBold, 20, 26, -0.1f),
    titleMedium = style(FontWeight.SemiBold, 16, 22, 0.1f),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.1f),
    bodyMedium = style(FontWeight.Normal, 14, 21, 0.15f),
    bodySmall = style(FontWeight.Normal, 12, 18, 0.2f),
    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.3f),
    labelSmall = style(FontWeight.Medium, 11, 15, 0.4f)
)
