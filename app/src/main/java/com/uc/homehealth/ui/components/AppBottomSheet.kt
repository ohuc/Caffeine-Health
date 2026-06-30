package com.uc.homehealth.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.coroutines.cancellation.CancellationException

// ─── Predictive back for sheet overlays ──────────────────────────────────────
// Two rules every sheet overlay must follow for Android's back gesture to behave:
//
// 1. Register the back callback ONLY while the sheet is open. Sheets are all
//    composed unconditionally (visibility flags), so an always-registered handler
//    made back precedence follow COMPOSITION order in Navigation.kt instead of
//    OPENING order — when one sheet opened on top of another (or a sheet morphed
//    into a different overlay, as with light cards), the gesture went to whichever
//    sheet happened to sit later in the file, which often did nothing visible:
//    "back stopped working". Registering on open keeps the dispatcher ordered
//    like a real back stack — the newest sheet wins, closing it hands back to the
//    previous one, and once everything is closed no callback remains registered,
//    so the system's own predictive back-to-home animation returns.
//
// 2. Use PredictiveBackHandler, not BackHandler, so the sheet participates in the
//    gesture instead of only reacting at commit: the panel sinks with the swipe,
//    springs back if the user cancels, and dismisses when they let go.

/**
 * Back-gesture wiring for a sheet overlay. Registers only while [visible] and
 * returns the live gesture progress (0..1) for [predictiveSheetTransform].
 * Commit → [onDismiss]; cancel → springs the progress back to 0.
 */
@Composable
fun rememberSheetPredictiveBack(visible: Boolean, onDismiss: () -> Unit): Animatable<Float, AnimationVector1D> {
    val progress = remember { Animatable(0f) }
    // A committed back leaves the final gesture offset in place so the exit slide
    // continues downward from where the finger released; reset on (re)open instead.
    LaunchedEffect(visible) { if (visible) progress.snapTo(0f) }
    if (visible) {
        PredictiveBackHandler { events ->
            try {
                events.collect { event -> progress.snapTo(event.progress) }
                onDismiss()
            } catch (_: CancellationException) {
                progress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }
    return progress
}

/**
 * Pre-commit gesture visual per the M3 predictive-back idiom for bottom sheets:
 * the panel sinks and shrinks slightly toward the bottom edge. [progress] is read
 * inside the layer block, so gesture frames update the layer without recomposing.
 */
fun Modifier.predictiveSheetTransform(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    translationY = p * 36.dp.toPx()
    val scale = 1f - 0.03f * p
    scaleX = scale
    scaleY = scale
    transformOrigin = TransformOrigin(0.5f, 1f)
}

// Generic bottom-sheet overlay matching the RoomSheetOverlay animation pattern:
// fade-in scrim + spring slide-up panel, with drag-to-dismiss handle.
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    hazeState: HazeState? = null,
    maxHeightFraction: Float = 0.85f,
    // Default keeps content clear of the gesture-nav area. Pass false when the sheet hosts
    // an edge-to-edge scrolling list that handles the inset itself via contentPadding — the
    // list then draws under the gesture pill instead of stopping above it.
    applyNavigationBarPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    val backProgress = rememberSheetPredictiveBack(visible = visible, onDismiss = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
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
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                    // Tracks the in-progress back gesture (sink + slight shrink).
                    .predictiveSheetTransform { backProgress.value }
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
                    // Spring the sheet's wrap-content height when its content changes size
                    // (e.g. a picker list shrinking/growing under a search query) instead of
                    // snapping between heights. Placed BELOW imePadding in the chain so
                    // keyboard tracking stays 1:1 — only content-driven changes animate.
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                            visibilityThreshold = IntSize.VisibilityThreshold,
                        ),
                    )
                    // Absorb taps that land on the panel so they don't fall through to the
                    // dismiss scrim behind it. Without this, hit-testing reaches the scrim's
                    // clickable for any tap on a non-interactive part of the sheet (e.g. the
                    // camera video, the title, padding) and the sheet closes "on clicking
                    // anywhere". An empty pointerInput registers a full-bounds pointer node —
                    // the same way Material's Surface blocks touch-through — while children
                    // (buttons, sliders, the drag handle) still get the gesture first.
                    .pointerInput(Unit) {}
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                    // INNER: nav-bar padding sits inside the surface, so when the
                    // keyboard is closed the sheet still reaches the screen edge
                    // but its content stays clear of the nav bar.
                    .then(if (applyNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier),
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
