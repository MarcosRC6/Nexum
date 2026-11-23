package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.testario.ui.theme.TestarioMutedText

private val NexumBrassLight = Color(0xFFF0D9A8)
private val NexumBrassDeep = Color(0xFFB88A3E)
private val NexumInk = Color(0xFF0B0D0C)

// Monogram: a mitred N (sharp joins, flat ends) inside a hairline seal, with a rule
// underneath. Same geometry as the launcher icon (ic_launcher_foreground.xml).
@Composable
fun NexumLogoMark(
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    monochrome: Boolean = false
) {
    Canvas(modifier = modifier.alpha(alpha)) {
        val s = size.minDimension
        val left = (size.width - s) / 2f
        val top = (size.height - s) / 2f
        fun p(x: Float, y: Float) = Offset(left + s * x, top + s * y)

        val brush = if (monochrome) {
            Brush.linearGradient(listOf(Color.White, Color.White))
        } else {
            Brush.linearGradient(
                colors = listOf(NexumBrassLight, NexumBrassDeep),
                start = p(.2f, .15f),
                end = p(.8f, .9f)
            )
        }
        drawCircle(
            brush = brush,
            radius = s * .46f,
            center = p(.5f, .5f),
            alpha = .55f,
            style = Stroke(width = s * .012f)
        )
        val n = Path().apply {
            moveTo(p(.30f, .72f).x, p(.30f, .72f).y)
            lineTo(p(.30f, .28f).x, p(.30f, .28f).y)
            lineTo(p(.70f, .72f).x, p(.70f, .72f).y)
            lineTo(p(.70f, .28f).x, p(.70f, .28f).y)
        }
        drawPath(
            path = n,
            brush = brush,
            style = Stroke(width = s * .11f, cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        )
        drawLine(
            brush = brush,
            start = p(.30f, .80f),
            end = p(.70f, .80f),
            strokeWidth = s * .02f,
            alpha = .8f
        )
    }
}

@Composable
fun NexumBackgroundWatermark(
    modifier: Modifier = Modifier,
    darkTheme: Boolean
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NexumLogoMark(
            modifier = Modifier.size(330.dp),
            alpha = if (darkTheme) 0.10f else 0.065f
        )
    }
}

@Composable
fun NexumSplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(NexumInk, Color(0xFF111412), NexumInk)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            NexumLogoMark(modifier = Modifier.size(150.dp))
            Text(
                text = "NEXUM",
                color = NexumBrassLight,
                style = MaterialTheme.typography.headlineLarge,
                letterSpacing = 9.sp
            )
            Text(
                text = "plan  ·  test  ·  progreso",
                color = TestarioMutedText,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 1.4.sp
            )
        }
    }
}
