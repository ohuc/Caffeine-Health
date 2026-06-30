package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.CameraPtz
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.RoomWidget
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState

// The four pan/tilt directions, paired with the arrow icon used everywhere.
private enum class PtzDir(val label: String, val icon: ImageVector) {
    LEFT("Left", Icons.Outlined.KeyboardArrowLeft),
    RIGHT("Right", Icons.Outlined.KeyboardArrowRight),
    UP("Up", Icons.Outlined.KeyboardArrowUp),
    DOWN("Down", Icons.Outlined.KeyboardArrowDown),
}

// Domains a PTZ move can be bound to. A PTZ "nudge" is almost always a button,
// but switches/scripts/scenes are common DIY rigs too.
private val PRESSABLE_DOMAINS = setOf("button", "input_button", "switch", "script", "scene")

/**
 * Shown during the add-camera flow, right after the user picks a camera entity.
 * A ButtonGroup asks "does this camera have PTZ controls?"; on Yes, four slots let
 * the user bind a pressable entity to each direction (tapping a slot slides over to
 * a searchable picker of pressable entities). "Add camera" commits the widget.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraPtzConfigSheet(
    visible: Boolean,
    camera: RoomWidget.Camera?,
    allEntities: List<HaEntitySummary>,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onSave: (CameraPtz?) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    // Working copy, seeded from the camera's stored PTZ each time the sheet opens.
    var enabled by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf("") }
    var right by remember { mutableStateOf("") }
    var up by remember { mutableStateOf("") }
    var down by remember { mutableStateOf("") }
    // Non-null = the picker sub-page is showing for that direction.
    var assigning by remember { mutableStateOf<PtzDir?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(camera?.id, visible) {
        if (visible && camera != null) {
            val p = camera.ptz
            enabled = p != null
            left = p?.left ?: ""
            right = p?.right ?: ""
            up = p?.up ?: ""
            down = p?.down ?: ""
            assigning = null
            query = ""
        }
    }

    fun valueFor(dir: PtzDir) = when (dir) {
        PtzDir.LEFT -> left; PtzDir.RIGHT -> right; PtzDir.UP -> up; PtzDir.DOWN -> down
    }
    fun assign(dir: PtzDir, id: String) = when (dir) {
        PtzDir.LEFT -> left = id; PtzDir.RIGHT -> right = id; PtzDir.UP -> up = id; PtzDir.DOWN -> down = id
    }

    val cameraName = camera?.entityId?.substringAfterLast('.')?.replace('_', ' ')
        ?.replaceFirstChar { it.uppercase() } ?: ""

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.9f) {
        AnimatedContent(
            targetState = assigning,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(tween(260)) { it } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(160)))
                }
            },
            label = "ptz_page",
        ) { dir ->
            if (dir == null) {
                // ── Main page: on/off + the four direction slots ─────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 20.dp),
                ) {
                    Text(
                        text = "Add camera",
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 30.sp,
                        lineHeight = 32.sp,
                        color = cs.onSurface,
                    )
                    Text(
                        text = if (cameraName.isBlank()) "Does this camera have PTZ (pan / tilt) controls?"
                        else "Does $cameraName have PTZ (pan / tilt) controls?",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    Spacer(Modifier.height(16.dp))

                    // No / Yes segmented toggle.
                    ButtonGroup(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                    ) {
                        listOf(false to "No", true to "Yes").forEachIndexed { index, (value, label) ->
                            ToggleButton(
                                checked = enabled == value,
                                onCheckedChange = { haptic.tick(); enabled = value },
                                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                colors = ToggleButtonDefaults.toggleButtonColors(
                                    containerColor = cs.surfaceContainerHigh,
                                    contentColor = cs.onSurfaceVariant,
                                    checkedContainerColor = cs.primary,
                                    checkedContentColor = cs.onPrimary,
                                ),
                            ) {
                                Text(
                                    text = label,
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }

                    // Reveal the direction slots with a height-expand + fade so tapping
                    // "Yes" grows them in smoothly instead of popping the whole block in.
                    AnimatedVisibility(
                        visible = enabled,
                        enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(160)),
                    ) {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PtzDir.entries.forEach { d ->
                                    val id = valueFor(d)
                                    val assignedName = allEntities.firstOrNull { it.entityId == id }?.friendlyName
                                        ?: id.takeIf { it.isNotBlank() }
                                    DirectionSlot(
                                        dir = d,
                                        assignedLabel = assignedName,
                                        onClick = { haptic.tick(); query = ""; assigning = d },
                                        onClear = { haptic.tick(); assign(d, "") },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    AddCameraButton(onClick = {
                        haptic.confirm()
                        onSave(if (enabled) CameraPtz(left, right, up, down) else null)
                    })
                }
            } else {
                // ── Picker sub-page for one direction ────────────────────────────
                val pool = remember(allEntities) {
                    allEntities.filter { it.domain in PRESSABLE_DOMAINS }
                }
                val filtered = remember(pool, query) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) pool
                    else pool.filter { it.friendlyName.lowercase().contains(q) || it.entityId.lowercase().contains(q) }
                }
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Tap(onClick = { haptic.tick(); assigning = null }) {
                            Box(
                                modifier = Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = cs.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = "Assign ${dir.label}",
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 26.sp,
                            color = cs.onSurface,
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        PtzSearchField(value = query, onValueChange = { query = it })
                    }

                    if (pool.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No button / switch / script entities found.",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(filtered, key = { it.entityId }) { entity ->
                                PtzEntityRow(
                                    entity = entity,
                                    selected = entity.entityId == valueFor(dir),
                                    onPick = {
                                        haptic.confirm()
                                        assign(dir, entity.entityId)
                                        assigning = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionSlot(
    dir: PtzDir,
    assignedLabel: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(cs.secondaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = dir.icon,
                    contentDescription = null,
                    tint = cs.onSecondaryContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dir.label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = assignedLabel ?: "Tap to assign an entity",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (assignedLabel != null) cs.primary else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (assignedLabel != null) {
                Tap(onClick = onClear) {
                    Box(
                        modifier = Modifier.size(28.dp).background(cs.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear ${dir.label}",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddCameraButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.primary, RoundedCornerShape(18.dp))
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add camera",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = cs.onPrimary,
            )
        }
    }
}

@Composable
private fun PtzEntityRow(
    entity: HaEntitySummary,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onPick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.friendlyName,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entity.entityId} · ${entity.areaName}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier.size(22.dp).background(cs.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Selected",
                        tint = cs.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PtzSearchField(value: String, onValueChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 14.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search button / switch entities",
                            fontFamily = MontserratFamily,
                            fontSize = 14.sp,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
            }
        },
    )
}
