package com.example.testario.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size

enum class EstudarioIconKind { HOME, LIBRARY, TEST, PLAN, STATS, SETTINGS, NOTIFICATIONS, LOGO }

@Composable
fun EstudarioIcon(
    kind: EstudarioIconKind,
    tint: Color,
    modifier: Modifier = Modifier.size(24.dp)
) {
    if (kind == EstudarioIconKind.LOGO) {
        NexumLogoMark(modifier = modifier)
        return
    }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = (size.minDimension * 0.085f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            EstudarioIconKind.HOME -> {
                val p = Path().apply {
                    moveTo(w * .14f, h * .48f)
                    lineTo(w * .50f, h * .16f)
                    lineTo(w * .86f, h * .48f)
                }
                drawPath(p, tint, style = stroke)
                drawRoundRect(tint, topLeft = Offset(w * .23f, h * .44f), size = androidx.compose.ui.geometry.Size(w * .54f, h * .40f), cornerRadius = CornerRadius(w * .06f), style = stroke)
                drawLine(tint, Offset(w * .47f, h * .84f), Offset(w * .47f, h * .64f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .47f, h * .64f), Offset(w * .61f, h * .64f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .61f, h * .64f), Offset(w * .61f, h * .84f), sw, StrokeCap.Round)
            }
            EstudarioIconKind.LIBRARY -> {
                listOf(.23f, .46f, .69f).forEachIndexed { i, y ->
                    val left = if (i == 1) .18f else .22f
                    drawRoundRect(tint, topLeft = Offset(w * left, h * y), size = androidx.compose.ui.geometry.Size(w * .60f, h * .15f), cornerRadius = CornerRadius(w * .05f), style = stroke)
                    drawLine(tint, Offset(w * .31f, h * (y + .04f)), Offset(w * .69f, h * (y + .04f)), sw * .65f, StrokeCap.Round)
                }
            }
            EstudarioIconKind.TEST -> {
                drawRoundRect(tint, topLeft = Offset(w * .24f, h * .19f), size = androidx.compose.ui.geometry.Size(w * .52f, h * .66f), cornerRadius = CornerRadius(w * .07f), style = stroke)
                drawRoundRect(tint, topLeft = Offset(w * .39f, h * .11f), size = androidx.compose.ui.geometry.Size(w * .22f, h * .14f), cornerRadius = CornerRadius(w * .04f), style = stroke)
                val p = Path().apply {
                    moveTo(w * .37f, h * .56f)
                    lineTo(w * .47f, h * .66f)
                    lineTo(w * .66f, h * .44f)
                }
                drawPath(p, tint, style = stroke)
            }
            EstudarioIconKind.PLAN -> {
                drawRoundRect(tint, topLeft = Offset(w * .17f, h * .23f), size = androidx.compose.ui.geometry.Size(w * .66f, h * .60f), cornerRadius = CornerRadius(w * .07f), style = stroke)
                drawLine(tint, Offset(w * .17f, h * .40f), Offset(w * .83f, h * .40f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .34f, h * .15f), Offset(w * .34f, h * .30f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .66f, h * .15f), Offset(w * .66f, h * .30f), sw, StrokeCap.Round)
                listOf(.34f, .50f, .66f).forEach { x ->
                    drawCircle(tint, radius = sw * .72f, center = Offset(w * x, h * .57f))
                }
            }
            EstudarioIconKind.STATS -> {
                val xs = listOf(.24f, .44f, .64f, .80f)
                val tops = listOf(.60f, .42f, .27f, .51f)
                xs.indices.forEach { i ->
                    drawLine(tint, Offset(w * xs[i], h * .82f), Offset(w * xs[i], h * tops[i]), sw * 1.5f, StrokeCap.Round)
                }
            }
            EstudarioIconKind.SETTINGS -> {
                val ys = listOf(.28f, .50f, .72f)
                val knobs = listOf(.38f, .68f, .46f)
                ys.indices.forEach { i ->
                    drawLine(tint, Offset(w * .16f, h * ys[i]), Offset(w * .84f, h * ys[i]), sw, StrokeCap.Round)
                    drawCircle(tint, radius = sw * 1.45f, center = Offset(w * knobs[i], h * ys[i]))
                }
            }
            EstudarioIconKind.NOTIFICATIONS -> {
                val p = Path().apply {
                    moveTo(w * .30f, h * .66f)
                    quadraticBezierTo(w * .34f, h * .58f, w * .34f, h * .43f)
                    quadraticBezierTo(w * .34f, h * .25f, w * .50f, h * .23f)
                    quadraticBezierTo(w * .66f, h * .25f, w * .66f, h * .43f)
                    quadraticBezierTo(w * .66f, h * .58f, w * .70f, h * .66f)
                    close()
                }
                drawPath(p, tint, style = stroke)
                drawLine(tint, Offset(w * .28f, h * .68f), Offset(w * .72f, h * .68f), sw, StrokeCap.Round)
                drawLine(tint, Offset(w * .45f, h * .77f), Offset(w * .55f, h * .77f), sw, StrokeCap.Round)
            }
            EstudarioIconKind.LOGO -> Unit
        }
    }
}
