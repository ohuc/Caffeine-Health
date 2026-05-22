package com.uc.homehealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uc.homehealth.ui.theme.PillShape
import kotlinx.coroutines.delay
import kotlin.math.sin

// Custom animated squiggly slider — matches lights.jsx SquigglySlider.
// The filled portion is a sine wave drawn with drawPath; the remainder is a
// straight dim line. Phase animates at 60fps to create the wriggling effect.
//
// onValueChange fires continuously during drag for local UI feedback only.
// onValueChangeFinished fires once on pointer release with the final value —
// this is where smart-home commands should be dispatched.
@Composable
fun SquigglySlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: (Int) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    dimColor: Color = Color.White.copy(alpha = 0.08f),
    trackHeight: Dp = 32.dp,
) {
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            phase += 0.04f
            delay(16L)
        }
    }

    val haptic = rememberAppHaptics()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    haptic.gestureStart()
                    val initial = ((down.position.x / size.width.toFloat()) * 100).toInt().coerceIn(0, 100)
                    var lastTickBucket = initial / 5
                    var currentValue = initial
                    onValueChange(initial)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            haptic.gestureEnd()
                            onValueChangeFinished(currentValue)
                            break
                        }
                        change.consume()
                        val next = ((change.position.x / size.width.toFloat()) * 100).toInt().coerceIn(0, 100)
                        currentValue = next
                        val bucket = next / 5
                        if (bucket != lastTickBucket) {
                            lastTickBucket = bucket
                            haptic.segmentTick()
                        }
                        onValueChange(next)
                    }
                }
            }
    ) {
        val splitFraction = value / 100f
        val knobOffsetDp = (maxWidth * splitFraction - 5.dp).coerceAtLeast(0.dp)
        val capturedPhase = phase

        Canvas(modifier = Modifier.fillMaxSize()) {
            val W = size.width
            val H = size.height
            val amp = 5.dp.toPx()
            val freq = 0.10f
            val splitX = W * splitFraction

            if (splitX > 1f) {
                val path = Path()
                var first = true
                var x = 0f
                while (x <= splitX) {
                    val y = H / 2f + (sin(x * freq + capturedPhase) * amp).toFloat()
                    if (first) { path.moveTo(x, y); first = false }
                    else path.lineTo(x, y)
                    x += 1.5f
                }
                drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
            }

            if (splitX < W) {
                drawLine(
                    color = dimColor,
                    start = Offset(splitX, H / 2f),
                    end = Offset(W, H / 2f),
                    strokeWidth = 2.5.dp.toPx(),
                )
            }
        }

        // White pill knob at value% position
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffsetDp)
                .size(width = 10.dp, height = trackHeight)
                .clip(PillShape)
                .background(Color.White)
        )
    }
}
