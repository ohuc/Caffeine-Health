package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.ui.components.RoomTilesEditor
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing

/**
 * Full-screen editor for the dashboard's room mosaic. The top is the live, draggable
 * arrangement of the actual room cards (reorder + hide + edit sensors); below, an
 * "Add rooms" list re-adds any hidden rooms.
 */
@Composable
fun RoomsEditScreen(
    rooms: List<HaRoom>,
    hiddenRooms: List<HaRoom>,
    onBack: () -> Unit,
    onReorder: (List<HaRoom>) -> Unit,
    onHideRoom: (HaRoom) -> Unit,
    onShowRoom: (HaRoom) -> Unit,
    onEditSensors: (HaRoom) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    SettingsPageScaffold(
        title = "Rooms",
        subtitle = "Drag to rearrange · × to hide · pencil for sensors",
        onBack = onBack,
        backLabel = "Home",
    ) {
        if (rooms.isEmpty()) {
            Text(
                text = "No rooms shown — add some below.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp),
            )
        } else {
            RoomTilesEditor(
                rooms = rooms,
                onReorder = onReorder,
                onRemove = onHideRoom,
                onEditSensors = onEditSensors,
                modifier = Modifier.padding(horizontal = Spacing.ml),
            )
        }

        AnimatedHiddenRoomsList(hiddenRooms = hiddenRooms, onAdd = onShowRoom)
    }
}

/**
 * The "Add rooms" section. Hidden rooms are a vertical list that animates rows IN
 * (a card was just hidden from the grid, so the room shows up here) and OUT (the row
 * was tapped to re-add it, so it collapses away) — mirroring the grid so both halves
 * of the editor feel consistent. Adding fires a confirm haptic.
 *
 * A local `displayed` list is the source of truth: it appends rooms that newly become
 * hidden (so they can expand in) and retains rows whose room left the hidden set until
 * their collapse finishes (a plain `hiddenRooms.forEach` would yank them instantly,
 * killing the exit — and would drop the header before the last row could animate out).
 */
@Composable
private fun AnimatedHiddenRoomsList(
    hiddenRooms: List<HaRoom>,
    onAdd: (HaRoom) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    val displayed = remember { mutableStateListOf<HaRoom>().also { it.addAll(hiddenRooms) } }
    // Rows present at first mount appear instantly (the screen's own enter transition
    // covers them); rows arriving afterwards expand in.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    LaunchedEffect(hiddenRooms) {
        hiddenRooms.forEach { r -> if (displayed.none { it.id == r.id }) displayed.add(r) }
    }

    if (displayed.isEmpty()) return

    Text(
        text = "Add rooms",
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
        lineHeight = 30.sp,
        color = cs.onBackground,
        modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 28.dp, bottom = 10.dp),
    )
    Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
        displayed.forEach { room ->
            key(room.id) {
                val stillHidden = hiddenRooms.any { it.id == room.id }
                // Initial rows start already-visible (no pop); later arrivals start
                // collapsed and expand in. targetState then tracks `stillHidden`.
                val state = remember { MutableTransitionState(!mounted).apply { targetState = true } }
                LaunchedEffect(stillHidden) { state.targetState = stillHidden }
                // Once a collapse finishes, drop the row from the local list.
                LaunchedEffect(state.currentState, state.isIdle) {
                    if (state.isIdle && !state.currentState) displayed.removeAll { it.id == room.id }
                }
                AnimatedVisibility(
                    visibleState = state,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        HiddenRoomRow(
                            room = room,
                            onAdd = { haptic.confirm(); onAdd(room) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenRoomRow(
    room: HaRoom,
    onAdd: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = runCatching { Color(android.graphics.Color.parseColor(room.colorHex)) }
        .getOrDefault(cs.primary)
    val ink = runCatching { Color(android.graphics.Color.parseColor(room.inkHex)) }
        .getOrDefault(cs.onPrimary)
    Tap(onClick = onAdd) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor(room.icon),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${room.deviceCount} device${if (room.deviceCount == 1) "" else "s"}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add ${room.name}",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
