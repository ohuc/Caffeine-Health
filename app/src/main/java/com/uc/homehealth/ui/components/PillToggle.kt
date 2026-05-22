package com.uc.homehealth.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import com.uc.homehealth.ui.components.rememberAppHaptics
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uc.homehealth.ui.theme.PillShape

// Animated pill toggle matching lights.jsx PillToggle.
// size: md (46×28) or lg (64×36).
@Composable
fun PillToggle(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    color: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    size: PillToggleSize = PillToggleSize.Md,
) {
    val w: Dp = if (size == PillToggleSize.Lg) 64.dp else 46.dp
    val h: Dp = if (size == PillToggleSize.Lg) 36.dp else 28.dp
    val thumbSize: Dp = h - 6.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (isOn) w - thumbSize - 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "toggle_thumb",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isOn) color else Color.White.copy(alpha = 0.06f),
        animationSpec = tween(240),
        label = "toggle_bg",
    )

    val haptic = rememberAppHaptics()

    Tap(
        onClick = { haptic.toggle(!isOn); onToggle(!isOn) },
        modifier = modifier,
        scale = 0.95f,
    ) {
        Box(
            modifier = Modifier
                .width(w)
                .height(h)
                .background(bgColor, PillShape)
                .then(
                    if (!isOn) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), PillShape)
                    else Modifier
                )
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .offset(x = thumbOffset)
                    .shadow(elevation = if (isOn) 4.dp else 0.dp, shape = CircleShape)
                    .background(if (isOn) ink else Color(0xFF8A8A90), CircleShape),
            )
        }
    }
}

enum class PillToggleSize { Md, Lg }
