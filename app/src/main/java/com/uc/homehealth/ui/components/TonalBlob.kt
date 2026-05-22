package com.uc.homehealth.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Decorative "now playing" art used when the media_player entity has no
// album art (entity_picture) to display. A solid-filled MaterialShape that
// rotates continuously, with the shape switching every few cycles for a
// subtle morph feel.
//
// Earlier revision used three blurred breathing blobs — replaced because
// the blur passes were heavy and looked muddy on small surfaces. The
// MaterialShape clip is just a path; nearly free per frame.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RotatingMediaShape(
    fillColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
) {
    val transition = rememberInfiniteTransition(label = "rotating_media_shape")

    // 14s per full rotation feels alive but not nervous. LinearEasing keeps
    // the motion constant — no acceleration around the loop boundary.
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotating_media_shape_angle",
    )

    // toShape() is a Composable extension that re-derives the path whenever
    // startAngle changes — fine for our integer angle because we only get a
    // new path 360 times per loop, not 60×.
    val shape = MaterialShapes.Cookie9Sided.toShape(startAngle = angle.toInt())

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(fillColor),
    )
}
