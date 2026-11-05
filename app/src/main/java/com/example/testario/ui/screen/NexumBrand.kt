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

private val NexumBlue = Color(0xFF526BFF)
private val NexumViolet = Color(0xFF8B45FF)
private val NexumLilac = Color(0xFFDDB9FF)

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

        val nPath = Path().apply {
            moveTo(p(.24f, .78f).x, p(.24f, .78f).y)
            lineTo(p(.24f, .24f).x, p(.24f, .24f).y)
            lineTo(p(.69f, .74f).x, p(.69f, .74f).y)
            lineTo(p(.69f, .36f).x, p(.69f, .36f).y)
        }
        val logoBrush = if (monochrome) {
            Brush.linearGradient(listOf(Color.White, Color.White))
        } else {
            Brush.linearGradient(
                colors = listOf(NexumBlue, NexumViolet),
                start = p(.16f, .24f),
                end = p(.78f, .80f)
            )
        }
        drawPath(
            path = nPath,
            brush = logoBrush,
            style = Stroke(
                width = s * .17f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val check = Path().apply {
            moveTo(p(.59f, .28f).x, p(.59f, .28f).y)
            lineTo(p(.69f, .38f).x, p(.69f, .38f).y)
            lineTo(p(.87f, .17f).x, p(.87f, .17f).y)
        }
        drawPath(
            path = check,
            color = if (monochrome) Color.White else NexumLilac,
            style = Stroke(
                width = s * .095f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        val progress = Path().apply {
            moveTo(p(.18f, .72f).x, p(.18f, .72f).y)
            cubicTo(
                p(.34f, .64f).x, p(.34f, .64f).y,
                p(.48f, .62f).x, p(.48f, .62f).y,
                p(.64f, .56f).x, p(.64f, .56f).y
            )
        }
        drawPath(
            path = progress,
            color = if (monochrome) Color.White else NexumLilac,
            alpha = if (monochrome) .92f else .90f,
            style = Stroke(width = s * .026f, cap = StrokeCap.Round)
        )
        listOf(p(.22f, .70f), p(.43f, .64f), p(.64f, .56f)).forEachIndexed { index, point ->
            drawCircle(
                color = if (monochrome) Color.White else if (index == 2) NexumLilac else Color(0xFFB990FF),
                radius = s * if (index == 2) .052f else .036f,
                center = point
            )
        }
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
                    colors = listOf(Color(0xFF070A16), Color(0xFF0B1023), Color(0xFF080B17))
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
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                letterSpacing = 7.sp
            )
            Text(
                text = "plan  •  test  •  progreso",
                color = TestarioMutedText,
                style = MaterialTheme.typography.bodyMedium,
                letterSpacing = 1.4.sp
            )
        }
    }
}
