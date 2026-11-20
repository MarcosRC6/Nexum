package com.example.testario.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.testario.data.model.ImageOcclusionMask
import com.example.testario.domain.MaskHandle
import com.example.testario.ui.theme.TestarioPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Word-like bounding-box controls drawn over a fitted photo. Drag the rectangle
 * to move; drag any of the eight handles to resize. Uses normalized image pixels.
 * The black masks themselves remain in StudyVisualImage, never baked into JPEG.
 */
@Composable
internal fun ResizableMaskOverlay(
    imageUri: String,
    masks: List<ImageOcclusionMask>,
    modifier: Modifier = Modifier,
    onDrag: (String, MaskHandle, Double, Double) -> Unit
) {
    val context = LocalContext.current
    var intrinsic by remember(imageUri) { mutableStateOf<Pair<Int, Int>?>(null) }
    val latestDrag by rememberUpdatedState(onDrag)
    LaunchedEffect(imageUri) {
        intrinsic = withContext(Dispatchers.IO) { orientedImageSize(context, imageUri) }
    }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val fit = intrinsic?.let { fitImageToBox(it.first, it.second, widthPx, heightPx) }
        if (fit != null) masks.forEach { mask ->
            val rectWidth = with(density) { (fit.width * mask.width.toFloat()).toDp() }
            val rectHeight = with(density) { (fit.height * mask.height.toFloat()).toDp() }
            val x = fit.left + fit.width * mask.x.toFloat()
            val y = fit.top + fit.height * mask.y.toFloat()
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(rectWidth, rectHeight)
                    .border(2.dp, TestarioPrimary, RoundedCornerShape(2.dp))
                    .pointerInput(mask.id, fit) {
                        detectDragGestures { change, delta ->
                            change.consume()
                            latestDrag(mask.id, MaskHandle.MOVE,
                                (delta.x / fit.width).toDouble(), (delta.y / fit.height).toDouble())
                        }
                    }
            ) {
                listOf(
                    Triple(MaskHandle.TOP_LEFT, Alignment.TopStart, Pair((-9).dp, (-9).dp)),
                    Triple(MaskHandle.TOP, Alignment.TopCenter, Pair(0.dp, (-9).dp)),
                    Triple(MaskHandle.TOP_RIGHT, Alignment.TopEnd, Pair(9.dp, (-9).dp)),
                    Triple(MaskHandle.LEFT, Alignment.CenterStart, Pair((-9).dp, 0.dp)),
                    Triple(MaskHandle.RIGHT, Alignment.CenterEnd, Pair(9.dp, 0.dp)),
                    Triple(MaskHandle.BOTTOM_LEFT, Alignment.BottomStart, Pair((-9).dp, 9.dp)),
                    Triple(MaskHandle.BOTTOM, Alignment.BottomCenter, Pair(0.dp, 9.dp)),
                    Triple(MaskHandle.BOTTOM_RIGHT, Alignment.BottomEnd, Pair(9.dp, 9.dp))
                ).forEach { (part, alignment, shift) ->
                    Box(
                        Modifier.align(alignment)
                            .offset(x = shift.first, y = shift.second)
                            .size(18.dp)
                            .background(TestarioPrimary, CircleShape)
                            .border(1.dp, Color.White, CircleShape)
                            .pointerInput(mask.id, part, fit) {
                                detectDragGestures { change, delta ->
                                    change.consume()
                                    latestDrag(mask.id, part,
                                        (delta.x / fit.width).toDouble(), (delta.y / fit.height).toDouble())
                                }
                            }
                    )
                }
            }
        }
    }
}
