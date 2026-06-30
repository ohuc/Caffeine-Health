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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.uc.homehealth.data.HaClimate
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
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

@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    onFanModeChange: (entityId: String, fanMode: String) -> Unit = { _, _ -> },
    modeOrders: Map<String, List<String>> = emptyMap(),
    onModeOrderChange: (entityId: String, modes: List<String>) -> Unit = { _, _ -> },
    fanOrders: Map<String, List<String>> = emptyMap(),
    onFanOrderChange: (entityId: String, fans: List<String>) -> Unit = { _, _ -> },
) {
    val visible = climate != null
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Back gesture closes this sheet (predictively). Without this, the glance-tile
    // climate sheet had NO back handling at all — the gesture fell through to the
    // nav stack / activity and minimized the app with the sheet still open.
    val backProgress = rememberSheetPredictiveBack(visible = visible, onDismiss = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
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
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val capturedClimate = remember { climate }
            var cumulativeDrag by remember { mutableStateOf(0f) }
            val sheetHaptic = rememberAppHaptics()

            Column(
                modifier = Modifier
                    .predictiveSheetTransform { backProgress.value }
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
                        onFanModeChange = onFanModeChange,
                        savedModeOrder = modeOrders[c.id].orEmpty(),
                        onModeOrderChange = { ids -> onModeOrderChange(c.id, ids) },
                        savedFanOrder = fanOrders[c.id].orEmpty(),
                        onFanOrderChange = { ids -> onFanOrderChange(c.id, ids) },
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
    onFanModeChange: (String, String) -> Unit = { _, _ -> },
    savedModeOrder: List<String> = emptyList(),
    onModeOrderChange: (List<String>) -> Unit = {},
    savedFanOrder: List<String> = emptyList(),
    onFanOrderChange: (List<String>) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

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

    var selectedFanMode by remember(climate.id, climate.fanMode) { mutableStateOf(climate.fanMode) }
    var editingModes by remember(climate.id) { mutableStateOf(false) }

    // The saved per-device list is the user's explicit *kept* modes, in order.
    // Empty == "default" (show every supported mode). Once they reorder or remove,
    // it becomes the source of truth: anything supported but not in the list is a
    // "removed" mode, offered below for re-adding. Falls back to all modes if a
    // stale saved list no longer matches any supported mode (so the group is never
    // empty).
    val displayedModes = remember(visibleModes, savedModeOrder) {
        if (savedModeOrder.isEmpty()) visibleModes
        else {
            val byId = visibleModes.associateBy { it.id }
            savedModeOrder.mapNotNull { byId[it] }.ifEmpty { visibleModes }
        }
    }
    val removedModes = remember(visibleModes, displayedModes) {
        visibleModes.filter { m -> displayedModes.none { it.id == m.id } }
    }

    // Fan speeds get the same treatment as modes: each fan_mode string becomes a tile
    // (label + icon from fanModeSpec, uniform cyan accent), with a saved kept/ordered
    // list per device and a derived "removed" set.
    val fanTiles = remember(climate.fanModes) {
        climate.fanModes.map { id ->
            val spec = fanModeSpec(id)
            ClimateMode(id = id, label = spec.label, icon = spec.icon, color = Color(0xFF7DD3D8))
        }
    }
    val displayedFanModes = remember(fanTiles, savedFanOrder) {
        if (savedFanOrder.isEmpty()) fanTiles
        else {
            val byId = fanTiles.associateBy { it.id }
            savedFanOrder.mapNotNull { byId[it] }.ifEmpty { fanTiles }
        }
    }
    val removedFanModes = remember(fanTiles, displayedFanModes) {
        fanTiles.filter { f -> displayedFanModes.none { it.id == f.id } }
    }

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                    fontFamily = MontserratFamily,
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
            // Edit toggle — enter edit mode to drag-reorder, remove, and re-add the
            // mode and fan buttons. Shown when there's something worth managing.
            if (visibleModes.size >= 2 || climate.fanModes.size >= 2) {
                Tap(onClick = { haptic.tick(); editingModes = !editingModes }) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (editingModes) accentColor else cs.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (editingModes) Icons.Outlined.Check else Icons.Outlined.Edit,
                            contentDescription = if (editingModes) "Done reordering" else "Reorder modes",
                            tint = if (editingModes) inkColor else cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
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

        // ── Editable mode + fan controls — height animates as rows appear/leave ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ),
        ) {
        // ── Mode buttons — connected ButtonGroup, or an editable row in edit mode ─
        if (displayedModes.isNotEmpty()) {
            if (editingModes) {
                ReorderableModeRow(
                    modes = displayedModes,
                    selectedMode = selectedMode,
                    canRemove = displayedModes.size > 1,
                    onReorder = { reordered -> onModeOrderChange(reordered.map { it.id }) },
                    onRemove = { mode ->
                        if (displayedModes.size > 1) {
                            haptic.confirm()
                            onModeOrderChange(displayedModes.filterNot { it.id == mode.id }.map { it.id })
                        } else {
                            haptic.reject()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
                Text(
                    text = "Hold & drag to reorder · tap × to remove",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, end = 20.dp),
                )

                // Removed modes — tap to add one back to the group.
                if (removedModes.isNotEmpty()) {
                    Text(
                        text = "REMOVED",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                    )
                    RemovedModesRow(
                        modes = removedModes,
                        onAdd = { mode -> onModeOrderChange(displayedModes.map { it.id } + mode.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    )
                }
            } else {
                ModeButtonGroup(
                    modes = displayedModes,
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
        }

        // ── Fan speed — selectable chips, or the same editor while editing ───
        if (climate.supportsFan) {
            Text(
                text = "FAN SPEED",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
            )
            if (editingModes) {
                ReorderableModeRow(
                    modes = displayedFanModes,
                    selectedMode = selectedFanMode ?: "",
                    canRemove = displayedFanModes.size > 1,
                    onReorder = { reordered -> onFanOrderChange(reordered.map { it.id }) },
                    onRemove = { mode ->
                        if (displayedFanModes.size > 1) {
                            haptic.confirm()
                            onFanOrderChange(displayedFanModes.filterNot { it.id == mode.id }.map { it.id })
                        } else {
                            haptic.reject()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
                if (removedFanModes.isNotEmpty()) {
                    Text(
                        text = "REMOVED",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 22.dp, end = 20.dp, top = 12.dp, bottom = 8.dp),
                    )
                    RemovedModesRow(
                        modes = removedFanModes,
                        onAdd = { mode -> onFanOrderChange(displayedFanModes.map { it.id } + mode.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 4.dp),
                    )
                }
            } else {
                FanModeGroup(
                    fanModes = displayedFanModes.map { it.id },
                    selectedFanMode = selectedFanMode,
                    onFanModeSelected = { fm ->
                        selectedFanMode = fm
                        if (climate.isAvailable) onFanModeChange(climate.id, fm)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 6.dp),
                )
            }
        }
        } // end animateContentSize column

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
                    fontFamily = MontserratFamily,
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
                fontFamily = MontserratFamily,
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
                    fontFamily = MontserratFamily,
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
                        fontFamily = MontserratFamily,
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

// ─── Drag-to-reorder variant of the mode buttons (edit mode) ─────────────────
// Mirrors RoomTilesEditor: LookaheadScope + animateBounds settle the neighbours,
// a graphicsLayer translation carries the lifted button under the finger, and
// boundsInRoot hit-testing swaps order as the finger crosses a neighbour.

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ReorderableModeRow(
    modes: List<ClimateMode>,
    selectedMode: String,
    canRemove: Boolean,
    onReorder: (List<ClimateMode>) -> Unit,
    onRemove: (ClimateMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAppHaptics()
    var order by remember(modes) { mutableStateOf(modes) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var pickup by remember { mutableStateOf(Offset.Zero) }
    var accum by remember { mutableStateOf(Offset.Zero) }

    LookaheadScope {
        EvenRowLayout(height = 64.dp, gap = 8.dp, modifier = modifier) {
            order.forEach { mode ->
                key(mode.id) {
                    val isDragged = mode.id == draggedId
                    val frameMod = Modifier
                        .onGloballyPositioned { bounds[mode.id] = it.boundsInRoot() }
                        .then(
                            if (isDragged) {
                                Modifier.zIndex(1f).graphicsLayer {
                                    val cur = bounds[mode.id]?.center ?: Offset.Zero
                                    translationX = (pickup.x + accum.x) - cur.x
                                    // Reordering is horizontal; clamp vertical drift so the
                                    // lifted button stays in its row instead of floating over
                                    // the dial/slider (which read as the layout "breaking up").
                                    val maxY = 22.dp.toPx()
                                    translationY = ((pickup.y + accum.y) - cur.y).coerceIn(-maxY, maxY)
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            } else {
                                Modifier.animateBounds(this@LookaheadScope)
                            }
                        )
                    val gestureMod = Modifier.pointerInput(mode.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = mode.id
                                pickup = bounds[mode.id]?.center ?: Offset.Zero
                                accum = Offset.Zero
                                haptic.confirm()
                            },
                            onDragEnd = { onReorder(order); draggedId = null },
                            onDragCancel = { draggedId = null; order = modes },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                                val p = pickup + accum
                                val target = bounds.entries
                                    .firstOrNull { (k, r) -> k != mode.id && r.contains(p) }?.key
                                if (target != null) {
                                    val from = order.indexOfFirst { it.id == mode.id }
                                    val to = order.indexOfFirst { it.id == target }
                                    if (from in order.indices && to in order.indices && from != to) {
                                        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                        haptic.tick()
                                    }
                                }
                            },
                        )
                    }
                    ModeTile(
                        mode = mode,
                        selected = mode.id == selectedMode,
                        showRemove = canRemove && draggedId == null,
                        onRemove = { onRemove(mode) },
                        frameModifier = frameMod,
                        gestureModifier = gestureMod,
                    )
                }
            }
        }
    }
}

// Single horizontal row that sizes every child to an equal share of the width.
@Composable
private fun EvenRowLayout(
    height: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val g = gap.roundToPx()
        val n = measurables.size.coerceAtLeast(1)
        val itemW = ((w - g * (n - 1)) / n).coerceAtLeast(1)
        val h = height.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(itemW, h)) }
        layout(w, h) {
            placeables.forEachIndexed { i, p -> p.place(i * (itemW + g), 0) }
        }
    }
}

@Composable
private fun ModeTile(
    mode: ClimateMode,
    selected: Boolean,
    showRemove: Boolean,
    onRemove: () -> Unit,
    frameModifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) mode.color else cs.surfaceContainerHigh
    val content = if (selected) Color(0xFF1A1A1D) else cs.onSurface
    Box(modifier = frameModifier) {
        // Body carries the drag gesture; the × badge is a sibling on top so its tap
        // isn't swallowed by the drag (mirrors RoomTilesEditor's editable cards).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(container)
                .then(gestureModifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(mode.icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = mode.label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = content,
                )
            }
        }
        if (showRemove) {
            Tap(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color.Black.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove ${mode.label}",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

// Removed modes — tappable chips that add a mode back into the group.
@Composable
private fun RemovedModesRow(
    modes: List<ClimateMode>,
    onAdd: (ClimateMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { mode ->
            Tap(onClick = { haptic.confirm(); onAdd(mode) }) {
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(cs.surfaceContainerHigh)
                        .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add ${mode.label}",
                        tint = cs.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = mode.icon,
                        contentDescription = null,
                        tint = mode.color,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = mode.label,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = cs.onSurface,
                    )
                }
            }
        }
    }
}

// ─── Fan speed chips ─────────────────────────────────────────────────────────
// Entities can expose 2 to 8+ fan_modes, so these scroll horizontally rather
// than cramming into a fixed connected ButtonGroup.

private data class FanModeSpec(val label: String, val icon: ImageVector)

private fun fanModeSpec(id: String): FanModeSpec = when (id.lowercase()) {
    "auto" -> FanModeSpec("Auto", Icons.Outlined.AutoMode)
    "night", "sleep" -> FanModeSpec("Night", Icons.Outlined.Bedtime)
    "low", "quiet", "silent", "min" -> FanModeSpec("Low", Icons.Outlined.Air)
    "lowmedium", "low_medium" -> FanModeSpec("Low-Med", Icons.Outlined.Air)
    "medium", "mid" -> FanModeSpec("Medium", Icons.Outlined.Air)
    "mediumhigh", "medium_high" -> FanModeSpec("Med-High", Icons.Outlined.Air)
    "high" -> FanModeSpec("High", Icons.Outlined.Air)
    "powerful", "turbo", "max", "strong", "boost" -> FanModeSpec("Powerful", Icons.Outlined.Bolt)
    else -> FanModeSpec(id.replaceFirstChar { it.uppercase() }, Icons.Outlined.Air)
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FanModeGroup(
    fanModes: List<String>,
    selectedFanMode: String?,
    onFanModeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val accent = Color(0xFF7DD3D8) // cyan — matches the "Fan" hvac-mode accent
    // Material's ButtonGroup overflows extra items into a dropdown menu (it can't
    // scroll), which would bury fan speeds behind a tap. To keep every speed reachable
    // by swiping we render the ButtonGroup look — connected ToggleButtons with the same
    // connected shapes/colors as the mode group — inside a horizontal scroller.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        fanModes.forEachIndexed { index, id ->
            val spec = fanModeSpec(id)
            val active = id.equals(selectedFanMode, ignoreCase = true)
            ToggleButton(
                checked = active,
                onCheckedChange = { if (!active) { haptic.navigation(); onFanModeSelected(id) } },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    fanModes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = accent,
                    checkedContentColor = Color(0xFF1A1A1D),
                    containerColor = cs.surfaceContainerHigh,
                    contentColor = cs.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = spec.label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}
