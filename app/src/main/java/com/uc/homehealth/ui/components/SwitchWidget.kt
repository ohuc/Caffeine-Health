package com.uc.homehealth.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.theme.PillShape
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState

// ── Visual primitive ─────────────────────────────────────────────────────────
// Vertical pill track with an inner pill that animates between top (on) and
// bottom (off). Shared by the mini-tile and the long-press detail sheet — only
// dimensions differ between the two surfaces.
@Composable
private fun VerticalPillToggle(
    isOn: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    iconSize: Int = 18,
    knobPadding: Int = 6,
    trackCorner: Int = 24,
) {
    val cs = MaterialTheme.colorScheme
    val verticalBias by animateFloatAsState(
        targetValue = if (isOn) -1f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pill_bias",
    )
    val knobColor by animateColorAsState(
        targetValue = if (isOn) accentColor else cs.surfaceContainerHighest,
        label = "pill_knob_color",
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(trackCorner.dp))
            .background(cs.surfaceContainerHigh),
        contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = verticalBias),
    ) {
        // Knob fills width and takes ~half the parent height. Inset by knobPadding
        // so the inner pill never touches the outer track.
        Box(
            modifier = Modifier
                .padding(knobPadding.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .clip(RoundedCornerShape((trackCorner - knobPadding).dp))
                .background(knobColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = if (isOn) cs.onPrimary else cs.onSurfaceVariant,
                modifier = Modifier.size(iconSize.dp),
            )
        }
    }
}

// ── Static pill toggle (visual-only, no own click handler) ───────────────────
// Used as the right-side toggle on the collapsed widget card — the whole row is
// clickable for toggle, so the visual must not intercept gestures of its own.
@Composable
private fun StaticPillToggle(
    isOn: Boolean,
    accentColor: Color,
    inkColor: Color,
    modifier: Modifier = Modifier,
) {
    val w: Dp = 46.dp
    val h: Dp = 28.dp
    val thumbSize: Dp = h - 6.dp
    val thumbOffset by animateDpAsState(
        targetValue = if (isOn) w - thumbSize - 6.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "static_thumb",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isOn) accentColor else Color.White.copy(alpha = 0.08f),
        label = "static_bg",
    )

    Box(
        modifier = modifier
            .width(w)
            .height(h)
            .background(bgColor, PillShape)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(thumbSize)
                .offset(x = thumbOffset)
                .background(if (isOn) inkColor else Color(0xFF8A8A90), CircleShape),
        )
    }
}

// ── Collapsed tile — horizontal card ─────────────────────────────────────────
// Click anywhere on the row to toggle; long-press to open the detail sheet.
@Composable
fun SwitchWidgetTile(
    name: String,
    subtitle: String,
    isOn: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .then(
                if (enabled) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptic.tick(); onClick() },
                    onLongClick = { haptic.confirm(); onLongPress() },
                ) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(cs.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp),
        ) {
            Text(
                text = name,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = cs.onSurface,
                maxLines = 1,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        StaticPillToggle(
            isOn = isOn,
            accentColor = cs.primary,
            inkColor = cs.onPrimary,
        )
    }
}

// ── Detail sheet (long-press) ────────────────────────────────────────────────
// Matches the reference image: big state label, last-changed subtitle, large
// vertical pill toggle that fills the sheet body. Tap anywhere on the pill to
// toggle.
@Composable
fun SwitchDetailSheet(
    visible: Boolean,
    entityId: String,
    name: String,
    state: HaEntityValue?,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isOn = state?.state == "on"

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.85f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (isOn) "On" else "Off",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 64.sp,
                lineHeight = 66.sp,
                color = cs.onSurface,
            )
            Text(
                text = name,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(24.dp))

            // Large pill — fills the bottom of the sheet. aspectRatio keeps it tall.
            val haptic = rememberAppHaptics()
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(0.55f)
                    .clip(RoundedCornerShape(36.dp))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { haptic.confirm(); onToggle() },
                        onLongClick = { haptic.confirm(); onToggle() },
                    ),
            ) {
                VerticalPillToggle(
                    isOn = isOn,
                    accentColor = cs.primary,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 28,
                    knobPadding = 10,
                    trackCorner = 36,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = entityId,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
