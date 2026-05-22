package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaClimate
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Climate bottom-sheet — circular gradient dial, comfort slider, mode ButtonGroup, Done CTA.
// Matches the design in Design-Handoff/project/entity-sheet.jsx ClimateBody.
//
// Range and modes come from the entity itself. Don't assume 16°/30° or that
// any particular hvac mode is supported — cross-check against climate.minTemp /
// climate.maxTemp and climate.supportedModes.

private data class ClimateMode(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

// Full palette of modes we know how to render. The visible set is filtered against
// the entity's supportedModes at render time so we never offer a mode HA can't accept.
private val AllSheetModes = listOf(
    ClimateMode("heat", "Heat", Icons.Outlined.LocalFireDepartment, Color(0xFFF2725C)),
    ClimateMode("cool", "Cool", Icons.Outlined.AcUnit, Color(0xFF9CB6E8)),
    ClimateMode("auto", "Auto", Icons.Outlined.AutoMode, Color(0xFF9DD8A8)),
    ClimateMode("heat_cool", "Auto", Icons.Outlined.AutoMode, Color(0xFF9DD8A8)),
    ClimateMode("dry", "Dry", Icons.Outlined.WaterDrop, Color(0xFFB8A8E8)),
    ClimateMode("fan_only", "Fan", Icons.Outlined.Air, Color(0xFF7DD3D8)),
    ClimateMode("off", "Off", Icons.Outlined.PowerSettingsNew, Color(0xFF8A8A90)),
)

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ClimateSheetOverlay(
    climate: HaClimate?,
    roomLabel: String,
    accentColor: Color,
    inkColor: Color,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onTargetChange: (entityId: String, temperature: Float) -> Unit,
    onModeChange: (entityId: String, mode: String) -> Unit,
) {
    val visible = climate != null
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
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
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
            )
        }

        // Sheet panel
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
            val capturedClimate = remember { climate }
            var cumulativeDrag by remember { mutableStateOf(0f) }
            val sheetHaptic = rememberAppHaptics()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.92f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                // Drag handle — drag down to dismiss
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
                                        sheetHaptic.confirm()
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

                capturedClimate?.let { c ->
                    ClimateSheetContent(
                        climate = c,
                        roomLabel = roomLabel,
                        accentColor = accentColor,
                        inkColor = inkColor,
                        onDismiss = onDismiss,
                        onTargetChange = onTargetChange,
                        onModeChange = onModeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClimateSheetContent(
    climate: HaClimate,
    roomLabel: String,
    accentColor: Color,
    inkColor: Color,
    onDismiss: () -> Unit,
    onTargetChange: (String, Float) -> Unit,
    onModeChange: (String, String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Optimistic local state — snaps back when HA confirms the change.
    // Coerce into the entity's actual range so we never send 16° to a unit that
    // tops out at 14°, or 30° to one capped at 25°.
    val initialTarget = (climate.targetTemp ?: climate.minTemp).coerceIn(climate.minTemp, climate.maxTemp)
    var target by remember(climate.id, climate.targetTemp, climate.minTemp, climate.maxTemp) {
        mutableFloatStateOf(initialTarget)
    }
    var selectedMode by remember(climate.id, climate.mode) { mutableStateOf(climate.mode) }

    // Modes we'll show: intersection of our visual catalog and what the entity supports.
    // If supportedModes is empty we fall back to whatever the entity's current mode is,
    // so the user always sees at least one labeled button.
    val visibleModes = remember(climate.supportedModes) {
        val supported = climate.supportedModes.map { it.lowercase() }.toSet()
        AllSheetModes
            .filter { it.id in supported }
            .distinctBy { it.label }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor("thermo"),
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${roomLabel.uppercase()} · CLIMATE",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = climate.name,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.4).sp,
                    color = cs.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Tap(onClick = onDismiss) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Big circular dial ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            ClimateDial(
                target = target,
                current = climate.currentTemp,
                minTemp = climate.minTemp,
                maxTemp = climate.maxTemp,
                modifier = Modifier.size(240.dp),
            )
        }

        // ── Slider card ───────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Column {
                TempThickSlider(
                    value = target,
                    minTemp = climate.minTemp,
                    maxTemp = climate.maxTemp,
                    onValueChange = { target = it },
                    onValueChangeFinished = {
                        if (climate.isAvailable) onTargetChange(climate.id, it)
                    },
                    color = Color(0xFFF2725C),
                    trackHeight = 26.dp,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val mid = ((climate.minTemp + climate.maxTemp) / 2f).toInt()
                    LabelSm("${climate.minTemp.toInt()}° Cold")
                    LabelSm("${mid}° Comfort")
                    LabelSm("${climate.maxTemp.toInt()}° Warm")
                }
            }
        }

        // ── Mode ButtonGroup ──────────────────────────────────────────────────
        if (visibleModes.isNotEmpty()) {
            ModeButtonGroup(
                modes = visibleModes,
                selectedMode = selectedMode,
                onModeSelected = { newMode ->
                    selectedMode = newMode
                    if (climate.isAvailable) onModeChange(climate.id, newMode)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        // ── Done CTA ──────────────────────────────────────────────────────────
        Tap(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(accentColor)
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Done",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = inkColor,
                )
            }
        }
    }
}

// ─── Circular gradient dial ──────────────────────────────────────────────────
// 270° arc starting at 135°. Filled portion shows progress through the entity's
// actual temp range.

@Composable
private fun ClimateDial(
    target: Float,
    current: Float?,
    minTemp: Float,
    maxTemp: Float,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val span = (maxTemp - minTemp).coerceAtLeast(1f)
    val pct = ((target - minTemp) / span).coerceIn(0f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = 16.dp.toPx()
            val radius = (minOf(w, h) - stroke) / 2f - 4f
            val center = Offset(w / 2f, h / 2f)
            val topLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            // Background track (full 270° sweep)
            drawArc(
                color = cs.surfaceContainerHigh,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Gradient progress arc — sky → mint → coral
            if (pct > 0f) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF9CB6E8), // sky
                            Color(0xFF9DD8A8), // mint
                            Color(0xFFF2725C), // coral
                        ),
                        start = Offset(topLeft.x, center.y),
                        end = Offset(topLeft.x + arcSize.width, center.y),
                    ),
                    startAngle = 135f,
                    sweepAngle = 270f * pct,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }

            // Tick marks (15 ticks evenly spaced along the 270° arc, inside the track)
            val tickOuter = radius - stroke / 2f - 2.dp.toPx()
            val tickInner = tickOuter - 8.dp.toPx()
            val tickColor = cs.onSurfaceVariant.copy(alpha = 0.45f)
            repeat(15) { i ->
                val angle = (135.0 + (i / 14.0) * 270.0) * PI / 180.0
                val cosA = cos(angle).toFloat()
                val sinA = sin(angle).toFloat()
                drawLine(
                    color = tickColor,
                    start = Offset(center.x + cosA * tickOuter, center.y + sinA * tickOuter),
                    end = Offset(center.x + cosA * tickInner, center.y + sinA * tickInner),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // Center text stack
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TARGET",
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp,
                color = cs.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${target.toInt()}",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 88.sp,
                    lineHeight = 90.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = "°",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 36.sp,
                    color = cs.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            current?.let {
                Text(
                    text = "Currently ${it.toInt()}°",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = Color(0xFFF2725C),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ─── Thick draggable slider for temperature ──────────────────────────────────
// Maps pointer X across the entity's actual minTemp..maxTemp. Half-degree haptic
// ticks during drag; HA target change fires once on pointer release.

@Composable
private fun TempThickSlider(
    value: Float,
    minTemp: Float,
    maxTemp: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit,
    color: Color,
    trackHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val span = (maxTemp - minTemp).coerceAtLeast(1f)
    val pct = ((value - minTemp) / span).coerceIn(0f, 1f)
    val haptic = rememberAppHaptics()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .pointerInput(minTemp, maxTemp) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    haptic.gestureStart()
                    val newPct = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    val initialTemp = minTemp + newPct * span
                    onValueChange(initialTemp)
                    var lastHalfDeg = (initialTemp * 2f).toInt()
                    var currentTemp = initialTemp
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            haptic.gestureEnd()
                            onValueChangeFinished(currentTemp)
                            break
                        }
                        change.consume()
                        val p = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        val temp = minTemp + p * span
                        currentTemp = temp
                        val halfDeg = (temp * 2f).toInt()
                        if (halfDeg != lastHalfDeg) {
                            lastHalfDeg = halfDeg
                            haptic.segmentTick()
                        }
                        onValueChange(temp)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val splitX = w * pct
            drawLine(
                color = Color.White.copy(alpha = 0.10f),
                start = Offset(0f, h / 2f),
                end = Offset(w, h / 2f),
                strokeWidth = 8.dp.toPx(),
                cap = StrokeCap.Round,
            )
            if (splitX > 1f) {
                drawLine(
                    color = color,
                    start = Offset(0f, h / 2f),
                    end = Offset(splitX, h / 2f),
                    strokeWidth = 10.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        val knobOffset = (maxWidth * pct - 5.dp).coerceAtLeast(0.dp)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobOffset)
                .size(width = 10.dp, height = trackHeight)
                .clip(PillShape)
                .background(color)
        )
    }
}

// ─── Mode picker using M3 Expressive ButtonGroup ─────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeButtonGroup(
    modes: List<ClimateMode>,
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    ButtonGroup(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            val active = mode.id == selectedMode
            ToggleButton(
                checked = active,
                onCheckedChange = {
                    if (!active) {
                        haptic.navigation()
                        onModeSelected(mode.id)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = mode.color,
                    checkedContentColor = Color(0xFF1A1A1D),
                    containerColor = cs.surfaceContainerHigh,
                    contentColor = cs.onSurface,
                ),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = mode.label,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelSm(text: String) {
    Text(
        text = text,
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
