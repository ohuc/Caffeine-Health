package com.uc.homehealth.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaLight
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import kotlin.math.cos
import kotlin.math.sin

private val InkColor = Color(0xFF1A0F08)

private data class ScenePreset(val name: String, val brightness: Int, val kelvin: Int)

// ─── Public composable ────────────────────────────────────────────────────────

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun LightTile(
    light: HaLight,
    expanded: Boolean,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
    onColorChange: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onColorChangeFinished: (Int, Int, Int) -> Unit = { _, _, _ -> },
    onColorTempChange: (Int) -> Unit = {},
    onColorTempChangeFinished: (Int) -> Unit = {},
    onOpenColorPicker: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val available = light.isAvailable
    val on = light.isOn && available
    val lightColor = remember(light.colorHex) {
        try { Color(AndroidColor.parseColor(light.colorHex)) }
        catch (_: Exception) { Color(0xFFFFD9A8) }
    }
    val cs = MaterialTheme.colorScheme
    val hazeState = remember { HazeState() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (on) 0.22f + (light.brightness / 100f) * 0.58f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "light_glow_alpha",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 144.dp)
            .alpha(if (available) 1f else 0.55f)
            .clip(RoundedCornerShape(22.dp))
            .then(
                if (available) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onExpand,
                ) else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cs.surfaceContainerHigh)
                .haze(state = hazeState),
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(160.dp),
            ) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lightColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hazeChild(state = hazeState, style = HazeMaterials.ultraThin())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InsetGlowWell(on = on, lightColor = lightColor)
                LightPillToggle(
                    on = on,
                    enabled = available,
                    lightColor = lightColor,
                    onToggle = onToggle,
                )
            }

            Column {
                Text(
                    text = light.name,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = (-0.2).sp,
                    color = cs.onSurface,
                )
                Text(
                    text = when {
                        !available -> "Unavailable"
                        on -> buildString {
                            append("${light.brightness}%")
                            light.colorTempKelvin?.let { append(" · ${it}K") }
                        }
                        else -> "Off"
                    },
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.2.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            if (!expanded) {
                DragBar(
                    on = on && available,
                    brightness = light.brightness,
                    lightColor = lightColor,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessChangeFinished = onBrightnessChangeFinished,
                )
            }

            AnimatedVisibility(
                visible = expanded && available,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                LightExpandedContent(
                    light = light,
                    lightColor = lightColor,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessChangeFinished = onBrightnessChangeFinished,
                    onColorChange = onColorChange,
                    onColorChangeFinished = onColorChangeFinished,
                    onColorTempChange = onColorTempChange,
                    onColorTempChangeFinished = onColorTempChangeFinished,
                    onOpenColorPicker = onOpenColorPicker,
                )
            }
        }
    }
}

// ─── BulbGlyphCanvas ─────────────────────────────────────────────────────────
// 36×36dp Canvas. Viewbox coords map 1:1 to dp.
// Dome at (18,16) r=9 · 6 rays when on · white highlight ellipse · filament base rects.

@Composable
private fun BulbGlyphCanvas(on: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(36.dp)) {
        val cx = 18.dp.toPx()
        val cy = 16.dp.toPx()
        val r  = 9.dp.toPx()

        // Rays — 6 lines at 0/60/120/180/240/300°, from r=14dp to r=17dp
        if (on) {
            for (deg in listOf(0, 60, 120, 180, 240, 300)) {
                val a = ((deg - 90) * Math.PI / 180.0).toFloat()
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = Offset(cx + cos(a) * 14.dp.toPx(), cy + sin(a) * 14.dp.toPx()),
                    end   = Offset(cx + cos(a) * 17.dp.toPx(), cy + sin(a) * 17.dp.toPx()),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Dome
        if (on) {
            drawCircle(color = color, radius = r, center = Offset(cx, cy), style = Fill)
        } else {
            drawCircle(
                color = Color(0x666A6A70),
                radius = r,
                center = Offset(cx, cy),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Highlight ellipse at (15,13) rx=3 ry=2
        if (on) {
            drawOval(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset((15 - 3).dp.toPx(), (13 - 2).dp.toPx()),
                size = Size(6.dp.toPx(), 4.dp.toPx()),
            )
        }

        // Filament base: two rounded rects
        val baseColor = if (on) InkColor else Color(0xFF6A6A70)
        drawRoundRect(
            color = baseColor,
            topLeft = Offset(14.5.dp.toPx(), 24.dp.toPx()),
            size = Size(7.dp.toPx(), 3.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
        drawRoundRect(
            color = baseColor,
            topLeft = Offset(15.5.dp.toPx(), 27.5.dp.toPx()),
            size = Size(5.dp.toPx(), 2.dp.toPx()),
            cornerRadius = CornerRadius(1.dp.toPx()),
        )
    }
}

// ─── InsetGlowWell ───────────────────────────────────────────────────────────
// 46×46dp rounded square containing the bulb glyph.
// Radial gradient inner glow via drawWithContent when on.

@Composable
private fun InsetGlowWell(on: Boolean, lightColor: Color) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (on) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f),
                RoundedCornerShape(14.dp),
            )
            .drawWithContent {
                drawContent()
                if (on) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(lightColor.copy(alpha = 0.33f), Color.Transparent),
                            center = Offset(size.width / 2f, size.height / 2f),
                            radius = size.width * 0.8f,
                        )
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BulbGlyphCanvas(on = on, color = lightColor, modifier = Modifier.size(32.dp))
    }
}

// ─── LightPillToggle ─────────────────────────────────────────────────────────
// 46×28dp stadium. Spring dampingRatio=0.6, stiffness=400.
// Colored outer shadow when on. Custom ink/gray thumb.

@Composable
private fun LightPillToggle(
    on: Boolean,
    enabled: Boolean = true,
    lightColor: Color,
    onToggle: () -> Unit,
) {
    val haptic = rememberAppHaptics()
    val thumbOffset by animateDpAsState(
        targetValue = if (on) 18.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "light_toggle",
    )

    Box(
        modifier = Modifier
            .width(46.dp)
            .height(28.dp)
            .clip(PillShape)
            .background(
                color = if (on) lightColor else Color.White.copy(alpha = 0.06f),
                shape = PillShape,
            )
            .then(
                if (!on) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), PillShape)
                else Modifier
            )
            .then(
                if (enabled) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { haptic.toggle(!on); onToggle() },
                ) else Modifier
            )
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .offset(x = thumbOffset)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(
                    color = if (on) InkColor else Color(0xFF8A8A90),
                    shape = CircleShape,
                )
        )
    }
}

// ─── DragBar ─────────────────────────────────────────────────────────────────
// 8dp tall brightness track. Pointer down consumes the event so the tile's
// clickable (expand toggle) does not fire when the user adjusts brightness.

@Composable
private fun DragBar(
    on: Boolean,
    brightness: Int,
    lightColor: Color,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .pointerInput(on) {
                if (!on) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val initial = ((down.position.x / size.width.toFloat()) * 100).toInt().coerceIn(0, 100)
                    var current = initial
                    onBrightnessChange(initial)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            onBrightnessChangeFinished(current)
                            break
                        }
                        change.consume()
                        val next = ((change.position.x / size.width.toFloat()) * 100).toInt().coerceIn(0, 100)
                        current = next
                        onBrightnessChange(next)
                    }
                }
            }
    ) {
        if (on) {
            // Fill
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(brightness / 100f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(lightColor)
            )
            // Knob
            val knobOffset = (maxWidth * (brightness / 100f) - 5.dp).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = knobOffset)
                    .size(width = 10.dp, height = 12.dp)
                    .shadow(elevation = 1.dp, shape = RoundedCornerShape(5.dp))
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White)
            )
        }
    }
}

// ─── LightExpandedContent ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LightExpandedContent(
    light: HaLight,
    lightColor: Color,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
    onColorChange: (Int, Int, Int) -> Unit,
    onColorChangeFinished: (Int, Int, Int) -> Unit,
    onColorTempChange: (Int) -> Unit,
    onColorTempChangeFinished: (Int) -> Unit,
    onOpenColorPicker: () -> Unit,
) {
    var colorMode by remember { mutableStateOf("warm") }
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1dp divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.06f))
        )

        // Brightness display + Warmth/Color mode switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "BRIGHTNESS",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = cs.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    RollingNumberText(
                        text = "${light.brightness}",
                        style = TextStyle(
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 42.sp,
                            lineHeight = 38.sp,
                            color = cs.onSurface,
                        ),
                        labelPrefix = "brightness",
                    )
                    Text(
                        text = "%",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
                    )
                }
            }

            // Warmth / Color segmented switch — only shown when the light supports both
            if (light.supportsColor && light.supportsColorTemp) {
                val modes = listOf("warm" to "Warmth", "color" to "Color")
                ButtonGroup(
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    modes.forEachIndexed { index, (id, label) ->
                        ToggleButton(
                            checked = colorMode == id,
                            onCheckedChange = { colorMode = id },
                            shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                            else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                            colors = ToggleButtonDefaults.toggleButtonColors(
                                containerColor = Color.White.copy(alpha = 0.05f),
                                contentColor = cs.onSurfaceVariant,
                                checkedContainerColor = lightColor,
                                checkedContentColor = InkColor,
                            ),
                        ) {
                            Text(
                                text = label,
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }
        }

        // SquigglySlider for brightness
        SquigglySlider(
            value = light.brightness,
            onValueChange = onBrightnessChange,
            onValueChangeFinished = onBrightnessChangeFinished,
            color = lightColor,
        )

        // Warmth strip or color swatches. Slides between the two when the user
        // taps the Warmth/Color toggle.
        val bodyMode = when {
            light.supportsColor && colorMode == "color" -> "color"
            light.supportsColorTemp -> "warm"
            light.supportsColor -> "color"
            else -> "none"
        }
        if (bodyMode != "none") {
            AnimatedContent(
                targetState = bodyMode,
                transitionSpec = {
                    val forward = targetState == "color"
                    if (forward) {
                        (slideInHorizontally { it } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally { -it } + fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally { -it } + fadeIn(tween(180))) togetherWith
                            (slideOutHorizontally { it } + fadeOut(tween(150)))
                    }
                },
                label = "light_body_mode",
            ) { mode ->
                when (mode) {
                    "warm" -> WarmthPresetRow(
                        currentKelvin = light.colorTempKelvin,
                        onPickKelvin = { k ->
                            onColorTempChange(k)
                            onColorTempChangeFinished(k)
                        },
                        onOpenColorPicker = onOpenColorPicker,
                    )
                    "color" -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf(0xFFF2725C, 0xFFF2A65E, 0xFFE8C99B, 0xFF9DD8A8,
                               0xFF7DD3D8, 0xFF9CB6E8, 0xFFB8A8E8, 0xFFE8B4D6)
                            .forEach { hex ->
                                val r = ((hex shr 16) and 0xFF).toInt()
                                val g = ((hex shr 8) and 0xFF).toInt()
                                val b = (hex and 0xFF).toInt()
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(hex))
                                        .clickable(
                                            indication = null,
                                            interactionSource = remember { MutableInteractionSource() },
                                        ) {
                                            onColorChange(r, g, b)
                                            onColorChangeFinished(r, g, b)
                                        }
                                )
                            }
                        // Palette button — opens the full color/temperature picker sheet
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { onOpenColorPicker() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "Open color picker",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // Scene shortcut ButtonGroup — applies brightness + warmth. Kelvin step is
        // skipped on lights that don't advertise color_temp support.
        val scenes = listOf(
            ScenePreset("Reading", 100, 3000),
            ScenePreset("Relax",    40, 2400),
            ScenePreset("Night",    10, 2200),
        )
        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            scenes.forEachIndexed { index, preset ->
                ToggleButton(
                    checked = false,
                    onCheckedChange = {
                        onBrightnessChange(preset.brightness)
                        onBrightnessChangeFinished(preset.brightness)
                        if (light.supportsColorTemp) {
                            onColorTempChange(preset.kelvin)
                            onColorTempChangeFinished(preset.kelvin)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        scenes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        containerColor = Color.White.copy(alpha = 0.05f),
                        contentColor = cs.onSurface,
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            preset.name,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            buildString {
                                append("${preset.brightness}%")
                                if (light.supportsColorTemp) append(" · ${preset.kelvin}K")
                            },
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─── WarmthPresetRow ─────────────────────────────────────────────────────────
// Five circles: palette button (opens full picker sheet) + four kelvin presets.
// The preset closest to the light's current kelvin is ringed and checked.

private data class WarmthPreset(val kelvin: Int, val hex: Long)

private val WarmthPresets = listOf(
    WarmthPreset(2200, 0xFFFFB070),
    WarmthPreset(3000, 0xFFFFD9A8),
    WarmthPreset(4500, 0xFFFFF1DD),
    WarmthPreset(6500, 0xFFD0E0FF),
)

@Composable
private fun WarmthPresetRow(
    currentKelvin: Int?,
    onPickKelvin: (Int) -> Unit,
    onOpenColorPicker: () -> Unit,
) {
    val selectedKelvin = currentKelvin?.let { ck ->
        WarmthPresets.minByOrNull { kotlin.math.abs(it.kelvin - ck) }?.kelvin
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Palette button — opens full color/temperature picker sheet
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onOpenColorPicker() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Palette,
                contentDescription = "Open temperature picker",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        // 4 warmth presets
        WarmthPresets.forEach { preset ->
            val isSelected = preset.kelvin == selectedKelvin
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onPickKelvin(preset.kelvin) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize(if (isSelected) 0.78f else 1f)
                        .clip(CircleShape)
                        .background(Color(preset.hex)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = InkColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}
