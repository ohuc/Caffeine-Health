package com.uc.homehealth.ui.components

import android.widget.Toast
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.uc.homehealth.data.HaRoom

private val ROOM_CARD_HEIGHT = 168.dp

/**
 * Editable 2-column grid of the actual room cards. Long-press a card to drag it
 * to a new position (other cards animate out of the way); each card carries a
 * pencil (edit sensors) and × (hide) badge. Removing a card shrinks it out, then
 * the remaining cards reflow into place.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RoomTilesEditor(
    rooms: List<HaRoom>,
    onReorder: (List<HaRoom>) -> Unit,
    onRemove: (HaRoom) -> Unit,
    onEditSensors: (HaRoom) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAppHaptics()
    val context = LocalContext.current

    // `order` is the single source of truth for what's laid out. It's seeded from
    // `rooms` and reconciled when upstream changes, but a removal is applied to it
    // locally the instant the card finishes animating out — so the survivors reflow
    // immediately instead of waiting on the DataStore round trip (the lag that
    // produced the "invisible hole, then jump" jank).
    var order by remember { mutableStateOf(rooms) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    // Ids whose exit animation is running. They still occupy a slot (rendered
    // shrinking) until the animation completes.
    val removing = remember { mutableStateListOf<String>() }
    // Ids removed locally but not yet dropped by upstream. Without this, a stale
    // upstream re-emission (same visibleRoomIds, but a fresh getAllRooms tick in HA
    // mode) would resurrect a card we just removed. Cleared once upstream confirms.
    val pendingRemoved = remember { mutableStateListOf<String>() }
    // True after the first composition. Cards present at first mount appear instantly
    // (the screen's own enter transition covers them — no load-time pop); cards added
    // afterwards (re-added from the hidden list) grow in.
    var mounted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { mounted = true }

    LaunchedEffect(rooms) {
        if (draggedId != null) return@LaunchedEffect
        // Forget removals upstream has now confirmed (the room is gone from `rooms`).
        pendingRemoved.retainAll { id -> rooms.any { it.id == id } }
        val incoming = rooms.associateBy { it.id }
        // Keep the current local order for rooms still present (refreshed to the latest
        // upstream data) OR still animating out — since the hide is persisted the instant
        // × is tapped, a card can vanish from `rooms` before its exit finishes, and
        // dropping it here would snap it away instead of letting it shrink. Append
        // genuinely-new rooms, never re-adding a card already removed locally.
        val kept = order.filter { incoming.containsKey(it.id) || it.id in removing }
            .map { incoming[it.id] ?: it }
        val added = rooms.filter { r -> order.none { it.id == r.id } && r.id !in pendingRemoved }
        order = kept + added
    }

    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var pickup by remember { mutableStateOf(Offset.Zero) }
    var accum by remember { mutableStateOf(Offset.Zero) }

    LookaheadScope {
        TwoColumnGridLayout(count = order.size, cardHeight = ROOM_CARD_HEIGHT, modifier = modifier) {
            order.forEach { room ->
                val keyId = room.id
                key(keyId) {
                    val isDragged = keyId == draggedId
                    val frameMod = Modifier
                        .onGloballyPositioned { bounds[keyId] = it.boundsInRoot() }
                        .then(
                            if (isDragged) {
                                Modifier.zIndex(1f).graphicsLayer {
                                    val cur = bounds[keyId]?.center ?: Offset.Zero
                                    translationX = (pickup.x + accum.x) - cur.x
                                    translationY = (pickup.y + accum.y) - cur.y
                                    scaleX = 1.04f
                                    scaleY = 1.04f
                                }
                            } else {
                                Modifier.animateBounds(this@LookaheadScope)
                            }
                        )
                    val gestureMod = Modifier.pointerInput(keyId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedId = keyId
                                pickup = bounds[keyId]?.center ?: Offset.Zero
                                accum = Offset.Zero
                                haptic.confirm()
                            },
                            onDragEnd = { onReorder(order); draggedId = null },
                            onDragCancel = {
                                draggedId = null
                                order = rooms.filter { it.id !in pendingRemoved }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accum += dragAmount
                                val p = pickup + accum
                                val target = bounds.entries
                                    .firstOrNull { (k, r) -> k != keyId && r.contains(p) }?.key
                                if (target != null) {
                                    val from = order.indexOfFirst { it.id == keyId }
                                    val to = order.indexOfFirst { it.id == target }
                                    if (from in order.indices && to in order.indices && from != to) {
                                        order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                        haptic.tick()
                                    }
                                }
                            },
                        )
                    }

                    EditableRoomCard(
                        room = room,
                        animateIn = mounted,
                        removing = keyId in removing,
                        onRemove = {
                            if (keyId !in removing) {
                                // Require at least one visible room — dropping the last
                                // one empties the pref, which the dashboard reads as
                                // "uninitialized" and re-seeds every room. Block it loudly.
                                if (order.count { it.id !in removing } <= 1) {
                                    haptic.reject()
                                    Toast.makeText(
                                        context,
                                        "Keep at least one room on the dashboard",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else {
                                    haptic.confirm()
                                    removing.add(keyId)
                                    // Persist the hide NOW, on tap — not when the exit
                                    // animation ends. Deferring it meant leaving the screen
                                    // (back) mid-animation cancelled the callback and the
                                    // change was silently lost. pendingRemoved guards against
                                    // a lagging upstream tick resurrecting the card before
                                    // DataStore confirms the removal.
                                    pendingRemoved.add(keyId)
                                    onRemove(room)
                                }
                            }
                        },
                        onRemoved = {
                            // Exit animation done: drop the slot locally now so the
                            // survivors reflow smoothly. Persistence already happened on
                            // tap. Guarded so it can only fire once per card.
                            if (keyId in removing) {
                                order = order.filterNot { it.id == keyId }
                                removing.remove(keyId)
                            }
                        },
                        onEditSensors = { onEditSensors(room) },
                        frameModifier = frameMod,
                        gestureModifier = gestureMod,
                    )
                }
            }
        }
    }
}

@Composable
private fun TwoColumnGridLayout(
    count: Int,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // animateContentSize smooths the container's own height change: when a row's worth
    // of cards is removed (e.g. the lone 3rd card), the survivors reflow via animateBounds
    // but the grid's measured height would otherwise snap shorter, yanking the "Add rooms"
    // section up. Springing the height keeps that collapse (and re-add growth) fluid.
    Layout(
        content = content,
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val gap = 10.dp.roundToPx()
        val half = ((w - gap) / 2).coerceAtLeast(1)
        val ch = cardHeight.roundToPx()
        val placeables = measurables.map { it.measure(Constraints.fixed(half, ch)) }
        val rows = (count + 1) / 2
        val totalH = if (rows > 0) rows * ch + (rows - 1) * gap else 0
        layout(w, totalH.coerceAtLeast(ch)) {
            placeables.forEachIndexed { i, p ->
                val col = i % 2
                val row = i / 2
                p.place(col * (half + gap), row * (ch + gap))
            }
        }
    }
}

@Composable
private fun EditableRoomCard(
    room: HaRoom,
    animateIn: Boolean,
    removing: Boolean,
    onRemove: () -> Unit,
    onRemoved: () -> Unit,
    onEditSensors: () -> Unit,
    frameModifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
) {
    // A card added after the grid first mounted grows in from 0; the initial set is
    // present immediately (no load-time pop). Removal animates the card out, and
    // onRemoved() fires once animateTo() completes — unlike animateFloatAsState's
    // finishedListener, this can't be silently missed. rememberUpdatedState keeps the
    // completion callback pointed at the latest closure (live `order`/`removing`),
    // since the effect's coroutine launches once when removal starts and would
    // otherwise capture a stale lambda.
    val currentOnRemoved by rememberUpdatedState(onRemoved)
    val appear = remember { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateIn && !removing) appear.animateTo(1f, tween(220))
    }
    LaunchedEffect(removing) {
        if (removing) {
            appear.animateTo(0f, tween(220))
            currentOnRemoved()
        }
    }

    Box(
        modifier = frameModifier.graphicsLayer {
            alpha = appear.value
            val s = 0.9f + 0.1f * appear.value
            scaleX = s
            scaleY = s
        },
    ) {
        // Real room card — non-interactive so its own click doesn't compete with the
        // drag gesture (on the body) or the badge taps (siblings above).
        Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
            RoomTile(room = room, height = ROOM_CARD_HEIGHT, onTap = {}, interactive = false)
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EditorBadge(icon = Icons.Outlined.Edit, description = "Edit room sensors", onClick = onEditSensors)
            EditorBadge(icon = Icons.Outlined.Close, description = "Hide room", onClick = onRemove)
        }
    }
}

@Composable
private fun EditorBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier.size(30.dp).background(Color.Black.copy(alpha = 0.72f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
