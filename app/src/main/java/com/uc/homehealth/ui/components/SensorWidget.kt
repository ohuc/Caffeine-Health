package com.uc.homehealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState
import kotlin.math.PI
import kotlin.math.sin

// ── Shared graph drawing ──────────────────────────────────────────────────────
// Mirrors the room hero TempHistoryGraph: bottom-anchored area fill with a cubic
// path through the (downsampled) history points. Stroke is optional for the
// detail sheet where we want a visible line on top of the fill.

@Composable
internal fun SensorHistoryGraph(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    fillOpacity: Float = 0.22f,
    strokeWidthDp: Float = 0f,
) {
    if (history.size < 3) {
        SensorSineWaveFill(color = color, modifier = modifier, fillOpacity = fillOpacity)
        return
    }
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height
        val minV = history.min()
        val maxV = history.max()
        val range = (maxV - minV).coerceAtLeast(0.5f)
        val points = history.mapIndexed { i, v ->
            val x = (i.toFloat() / (history.size - 1)) * W
            val y = H * 0.85f - ((v - minV) / range) * (H * 0.70f)
            Offset(x, y)
        }
        val area = Path()
        area.moveTo(0f, H)
        area.lineTo(points.first().x, points.first().y)
        val line = Path()
        line.moveTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val cx = (p0.x + p1.x) / 2f
            area.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            line.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
        area.lineTo(W, H)
        area.close()
        drawPath(path = area, color = color.copy(alpha = fillOpacity))
        if (strokeWidthDp > 0f) {
            drawPath(path = line, color = color, style = Stroke(width = strokeWidthDp.dp.toPx()))
        }
    }
}

@Composable
private fun SensorSineWaveFill(
    color: Color,
    modifier: Modifier = Modifier,
    fillOpacity: Float = 0.22f,
    frequency: Float = 1.8f,
) {
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height
        val baseY = H * 0.55f
        val amp = H * 0.22f
        val path = Path()
        path.moveTo(0f, H)
        path.lineTo(0f, baseY)
        val steps = 200
        for (i in 0..steps) {
            val x = (i.toFloat() / steps) * W
            val y = baseY + sin((i.toFloat() / steps) * PI.toFloat() * frequency).toFloat() * amp
            path.lineTo(x, y)
        }
        path.lineTo(W, H)
        path.close()
        drawPath(path = path, color = color.copy(alpha = fillOpacity))
    }
}

// ── Collapsed tile — horizontal card with mini graph background ──────────────
// Tap opens the detail sheet (no toggle action for a read-only sensor). Long-press
// matches the switch tile UX for consistency.

@Composable
fun SensorWidgetTile(
    name: String,
    subtitle: String,
    state: HaEntityValue?,
    history: List<Float>,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val accent = cs.primary
    val value = state?.state ?: "—"
    val unit = state?.unit.orEmpty()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .then(
                if (enabled) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptic.tick(); onClick() },
                    onLongClick = { haptic.confirm(); onLongPress() },
                ) else Modifier
            ),
    ) {
        SensorHistoryGraph(
            history = history,
            color = accent,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
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
                    fontFamily = InterFamily,
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
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 32.sp,
                    lineHeight = 32.sp,
                    color = cs.onSurface,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

// ── Detail sheet (long-press / tap) ──────────────────────────────────────────
// Big value + unit, full-width graph beneath it.

@Composable
fun SensorDetailSheet(
    visible: Boolean,
    entityId: String,
    name: String,
    state: HaEntityValue?,
    history: List<Float>,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val value = state?.state ?: "—"
    val unit = state?.unit.orEmpty()
    val accent = cs.primary

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.85f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 4.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 64.sp,
                    lineHeight = 66.sp,
                    color = cs.onSurface,
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 28.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
                    )
                }
            }
            Text(
                text = name,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(cs.surfaceContainerHigh),
            ) {
                SensorHistoryGraph(
                    history = history,
                    color = accent,
                    modifier = Modifier.fillMaxSize(),
                    fillOpacity = 0.28f,
                    strokeWidthDp = 1.6f,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Last 24 hours",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
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
