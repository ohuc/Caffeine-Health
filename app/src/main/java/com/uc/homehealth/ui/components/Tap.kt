package com.uc.homehealth.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

// Pressable container with spring scale feedback, matching shared.jsx Tap component.
// Press: 80ms ease-out to scale. Release: spring overshoot back to 1.0.
@Composable
fun Tap(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 0.97f,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by resolvedInteractionSource.collectIsPressedAsState()
    val haptic = rememberAppHaptics()

    LaunchedEffect(pressed) {
        if (pressed && enabled) haptic.tick()
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (pressed && enabled) scale else 1f,
        animationSpec = if (pressed) {
            tween(durationMillis = 80)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            )
        },
        label = "tap_scale",
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .clickable(
                interactionSource = resolvedInteractionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
    ) {
        content()
    }
}
