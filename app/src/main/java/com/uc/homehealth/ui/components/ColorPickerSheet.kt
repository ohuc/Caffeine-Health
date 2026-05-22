package com.uc.homehealth.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaLight
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val DefaultMinKelvin = 2200
private const val DefaultMaxKelvin = 6500

// Full-screen overlay sheet for color / colour-temperature selection. Slides up
// over the room sheet; tapping the scrim or dragging the handle dismisses.
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ColorPickerSheetOverlay(
    light: HaLight?,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onColorCommit: (r: Int, g: Int, b: Int) -> Unit,
    onTemperatureCommit: (kelvin: Int) -> Unit,
) {
    val visible = light != null
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

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
                    .background(Color.Black.copy(alpha = 0.45f))
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
            val capturedLight = remember { light }
            var cumulativeDrag by remember { mutableStateOf(0f) }
            val haptic = rememberAppHaptics()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.75f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
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

                capturedLight?.let { l ->
                    ColorPickerBody(
                        light = l,
                        onColorCommit = onColorCommit,
                        onTemperatureCommit = onTemperatureCommit,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorPickerBody(
    light: HaLight,
    onColorCommit: (Int, Int, Int) -> Unit,
    onTemperatureCommit: (Int) -> Unit,
) {
    val tabs = remember(light.id) {
        buildList {
            if (light.supportsColor) add("color" to "Color")
            if (light.supportsColorTemp) add("temperature" to "Temperature")
        }
    }
    val initialTab = tabs.firstOrNull()?.first ?: "color"
    var tab by remember(light.id) { mutableStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (tabs.size > 1) {
            ButtonGroup(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                tabs.forEachIndexed { index, (id, label) ->
                    ToggleButton(
                        checked = tab == id,
                        onCheckedChange = { tab = id },
                        modifier = Modifier.weight(1f),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(
                            label,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val newIdx = tabs.indexOfFirst { it.first == targetState }
                val oldIdx = tabs.indexOfFirst { it.first == initialState }
                val forward = newIdx >= oldIdx
                if (forward) {
                    (slideInHorizontally { it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally { -it } + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally { it } + fadeOut(tween(150)))
                }
            },
            label = "color_picker_tab",
        ) { current ->
            when (current) {
                "color" -> ColorWheel(
                    initialHex = light.colorHex,
                    onCommit = onColorCommit,
                )
                "temperature" -> {
                    val minK = light.minColorTempKelvin ?: DefaultMinKelvin
                    val maxK = light.maxColorTempKelvin ?: DefaultMaxKelvin
                    TemperatureSlider(
                        initialKelvin = light.colorTempKelvin ?: ((minK + maxK) / 2),
                        minKelvin = minK,
                        maxKelvin = maxK,
                        onCommit = onTemperatureCommit,
                    )
                }
            }
        }
    }
}

// HSV wheel: hue around the circle, saturation = distance from centre. Value
// fixed at 1. Commit fires on pointer release per the slider-commit rule.
@Composable
private fun ColorWheel(
    initialHex: String,
    onCommit: (Int, Int, Int) -> Unit,
) {
    val initialHsv = remember(initialHex) {
        val parsed = try { AndroidColor.parseColor(initialHex) } catch (_: Exception) { AndroidColor.WHITE }
        FloatArray(3).also { AndroidColor.colorToHSV(parsed, it) }
    }
    var hue by remember(initialHex) { mutableStateOf(initialHsv[0]) }
    var sat by remember(initialHex) { mutableStateOf(initialHsv[1].coerceIn(0f, 1f)) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val r = min(cx, cy)
                    fun update(pos: Offset) {
                        val dx = pos.x - cx
                        val dy = pos.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        sat = (dist / r).coerceIn(0f, 1f)
                        var h = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        if (h < 0f) h += 360f
                        hue = h
                    }
                    update(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            val intColor = AndroidColor.HSVToColor(floatArrayOf(hue, sat, 1f))
                            onCommit(
                                (intColor shr 16) and 0xFF,
                                (intColor shr 8) and 0xFF,
                                intColor and 0xFF,
                            )
                            break
                        }
                        change.consume()
                        update(change.position)
                    }
                }
            },
        contentAlignment = Alignment.TopStart,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val r = min(cx, cy)
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000),
                    ),
                    center = Offset(cx, cy),
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        val rDp = maxWidth / 2
        val cosH = cos(Math.toRadians(hue.toDouble())).toFloat()
        val sinH = sin(Math.toRadians(hue.toDouble())).toFloat()
        val thumbSize = 24.dp
        val thumbX = rDp + rDp * sat * cosH - thumbSize / 2
        val thumbY = rDp + rDp * sat * sinH - thumbSize / 2
        Box(
            modifier = Modifier
                .offset(x = thumbX, y = thumbY)
                .size(thumbSize)
                .shadow(elevation = 4.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color.Black.copy(alpha = 0.15f), CircleShape),
        )
    }
}

// Vertical temperature gradient. Top = light's warmest kelvin, bottom = coolest.
// Thumb is a horizontal pill outline that tracks drag; commit on release.
@Composable
private fun TemperatureSlider(
    initialKelvin: Int,
    minKelvin: Int,
    maxKelvin: Int,
    onCommit: (Int) -> Unit,
) {
    val safeMin = minKelvin.coerceAtMost(maxKelvin - 1)
    val safeMax = maxKelvin.coerceAtLeast(safeMin + 1)
    var kelvin by remember(initialKelvin, safeMin, safeMax) {
        mutableIntStateOf(initialKelvin.coerceIn(safeMin, safeMax))
    }
    val fraction = (kelvin - safeMin).toFloat() / (safeMax - safeMin)
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFF9B55),
                                Color(0xFFFFB87C),
                                Color(0xFFFFD9A8),
                                Color(0xFFFFF7E6),
                                Color(0xFFE8F1FF),
                                Color(0xFFB3D2FF),
                            )
                        )
                    )
                    .pointerInput(safeMin, safeMax) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            down.consume()
                            fun update(pos: Offset) {
                                val pct = (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                                kelvin = safeMin + (pct * (safeMax - safeMin)).toInt()
                            }
                            update(down.position)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    onCommit(kelvin)
                                    break
                                }
                                change.consume()
                                update(change.position)
                            }
                        }
                    }
            ) {
                val thumbHeight = 40.dp
                val thumbY = (maxHeight - thumbHeight) * fraction
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = thumbY)
                        .padding(horizontal = 8.dp)
                        .fillMaxWidth()
                        .height(thumbHeight)
                        .border(3.dp, Color.White, RoundedCornerShape(22.dp))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.55f),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${kelvin}K",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
