package com.uc.homehealth.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

// Generic bottom-sheet overlay matching the RoomSheetOverlay animation pattern:
// fade-in scrim + spring slide-up panel, with drag-to-dismiss handle.
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    maxHeightFraction: Float = 0.85f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    BackHandler(enabled = visible, onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = EaseOut)),
            exit = fadeOut(tween(380, easing = EaseIn)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        else Modifier
                    )
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            var cumulativeDrag by remember { mutableStateOf(0f) }
            val haptic = rememberAppHaptics()

            // When the IME is up, push the *entire sheet* above it by adding
            // outer padding BEFORE the background. The visible sheet edge ends
            // at the top of the IME region.
            //
            // We also grow the effective max-height while the keyboard is open
            // so that scrollable content + IME padding have room to coexist.
            // The cap is *animated*, not switched discretely — otherwise the
            // top edge of the sheet visibly jumps upward by ~10% of screen
            // height on the first IME frame.
            val imeBottomPx = WindowInsets.ime.getBottom(LocalDensity.current)
            val keyboardOpen = imeBottomPx > 0
            val targetMaxFraction = if (keyboardOpen) maxHeightFraction.coerceAtLeast(0.95f) else maxHeightFraction
            val animatedMaxFraction by animateFloatAsState(
                targetValue = targetMaxFraction,
                animationSpec = tween(durationMillis = 250, easing = EaseInOut),
                label = "ime_max_fraction",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * animatedMaxFraction)
                    // OUTER: lifts the sheet above the keyboard. Padding here is
                    // transparent (no background applied yet), so the visible sheet
                    // edge ends at the top of the IME region. imePadding consumes
                    // the bottom inset, so navigationBarsPadding below resolves to 0
                    // while the IME is open.
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                    // INNER: nav-bar padding sits inside the surface, so when the
                    // keyboard is closed the sheet still reaches the screen edge
                    // but its content stays clear of the nav bar.
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(Unit) {
                            val threshold = 120.dp.toPx()
                            detectVerticalDragGestures(
                                onDragStart = { cumulativeDrag = 0f },
                                onDragEnd = { cumulativeDrag = 0f },
                                onVerticalDrag = { _: PointerInputChange, dragAmount: Float ->
                                    if (dragAmount > 0f) cumulativeDrag += dragAmount
                                    if (cumulativeDrag > threshold) {
                                        cumulativeDrag = 0f
                                        haptic.confirm()
                                        onDismiss()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 4.dp)
                            .background(Color.White.copy(alpha = 0.30f), RoundedCornerShape(2.dp))
                    )
                }

                content()
            }
        }
    }
}
