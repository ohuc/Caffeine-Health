package com.uc.homehealth.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.Spacing

/**
 * Animated shimmer fill: sweeps a lighter highlight band left-to-right across the
 * element. Theme-adaptive — the base/highlight come from surface tones, so it reads
 * correctly in both light and dark (no hardcoded white "glow"). Apply to a sized box
 * after clipping, e.g. `Modifier.clip(shape).shimmer()`.
 */
fun Modifier.shimmer(): Modifier = composed {
    val cs = MaterialTheme.colorScheme
    val base = cs.surfaceContainerHigh
    val highlight = cs.surfaceBright

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )

    var size by remember { mutableStateOf(IntSize.Zero) }
    val brush = if (size.width == 0) {
        SolidColor(base)
    } else {
        val widthPx = size.width.toFloat()
        val band = widthPx // soft highlight band roughly the width of the block
        // Travel the band's centre from fully off the left to fully off the right.
        val centre = -band + progress * (widthPx + 2 * band)
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(centre - band, 0f),
            end = Offset(centre + band, 0f),
        )
    }

    this
        .onSizeChanged { size = it }
        .background(brush)
}

/** A single shimmering placeholder block, clipped to [shape]. */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
) {
    Box(modifier = modifier.clip(shape).shimmer())
}

/**
 * Launch placeholder shown while the WebSocket connects, mirroring the real dashboard
 * scaffold — greeting → "At a glance" → Rooms — at the same paddings/heights so live
 * content "develops in place" when it swaps in. Section labels render for real (instant
 * orientation); only the data-dependent tiles shimmer. The caller (DashboardScreen)
 * overlays this opaquely and crossfades it out once the connection is READY.
 */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        // ─── Greeting ── a bar where "Hello 👋 {name}" will render (name is data).
        SkeletonBox(
            modifier = Modifier
                .padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 18.dp)
                .fillMaxWidth(0.6f)
                .height(42.dp),
            shape = RoundedCornerShape(14.dp),
        )

        // ─── At a glance ──
        SkeletonSectionLabel(text = "◐  At a glance", topPadding = 2.dp)
        GlanceSkeleton()

        // ─── Rooms ──
        SkeletonSectionLabel(text = "🏡  Rooms", topPadding = 16.dp)
        RoomsSkeleton()
    }
}

/** Section heading rendered for real, matching DashboardScreen's SectionLabel. */
@Composable
private fun SkeletonSectionLabel(text: String, topPadding: Dp) {
    Text(
        text = text,
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
        lineHeight = 30.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(start = Spacing.xl, end = Spacing.xl)
            .padding(top = topPadding, bottom = 12.dp),
    )
}

/** Mirrors a single "at a glance" page: one featured tile + three stacked mini tiles. */
@Composable
private fun GlanceSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ml)
            .height(224.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SkeletonBox(modifier = Modifier.weight(1f).fillMaxHeight())
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            repeat(3) {
                SkeletonBox(modifier = Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** Mirrors the rooms mosaic: two columns, each a tall (188dp) then short (160dp) tile. */
@Composable
private fun RoomsSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ml),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(2) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(188.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        }
    }
}
