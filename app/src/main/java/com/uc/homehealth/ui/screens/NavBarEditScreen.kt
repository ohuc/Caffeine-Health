package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.UserPreferences
import com.uc.homehealth.ui.components.BottomNavBar
import com.uc.homehealth.ui.components.NavDestination
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.navDestinationCatalog
import com.uc.homehealth.ui.components.navDestinationsFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.CardSettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// What each tab brings, shown on its "Add tabs" row.
private val TAB_DESCRIPTIONS = mapOf(
    UserPreferences.NAV_TAB_DASHBOARD to "Rooms, scenes, favorites & at-a-glance",
    UserPreferences.NAV_TAB_ACTIVITY to "Timeline of events around your home",
    UserPreferences.NAV_TAB_ENERGY to "Live solar, battery & grid",
    UserPreferences.NAV_TAB_SETTINGS to "App configuration",
)

/**
 * Full-screen editor for the bottom navigation bar, modeled on the at-a-glance editor:
 * a live preview of the real bar on top, the current tabs as a reorderable strip (the
 * same pick-up-and-float drag the glance editor uses — long-press lifts the tile, the
 * others glide aside via animateBounds), and an animated "Add tabs" catalog below.
 * Edits persist immediately, so the actual bar at the bottom of the screen updates live
 * too. Settings is mandatory — it carries a lock instead of a remove badge (this editor
 * lives inside Settings; removing it would lock the user out of changing the bar back).
 */
@Composable
fun NavBarEditScreen(
    onBack: () -> Unit,
    viewModel: CardSettingsViewModel = hiltViewModel(),
) {
    val tabKeys by viewModel.navTabKeys.collectAsStateWithLifecycle()
    val keys = tabKeys ?: return
    NavBarEditContent(
        keys = keys,
        onSave = viewModel::setNavTabKeys,
        onBack = onBack,
    )
}

@Composable
private fun NavBarEditContent(
    keys: List<String>,
    onSave: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val tabs = remember(keys) { navDestinationsFor(keys) }

    SettingsPageScaffold(
        title = "Navigation Bar",
        subtitle = "Hold & drag to reorder · tap × to hide",
        onBack = onBack,
    ) {
        // ── Live preview: the real BottomNavBar, rendered inline. Tabs are tappable to
        // preview the selection pill; it re-renders from the edited keys instantly. ──
        var previewSelected by remember { mutableStateOf(keys.first()) }
        val effectiveSelected = if (previewSelected in keys) previewSelected else keys.first()
        Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(cs.surfaceContainerHigh)
                    .padding(vertical = 26.dp),
                contentAlignment = Alignment.Center,
            ) {
                BottomNavBar(
                    currentRoute = effectiveSelected,
                    onNavigate = { previewSelected = it },
                    tabs = tabs,
                    floating = false,
                )
            }
            Text(
                text = "Live preview — tap a tab to try it. Changes apply instantly.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp),
            )
        }

        Spacer(Modifier.height(22.dp))

        // ── Current tabs: pick-up-and-float reorder, glance-editor style ──
        NavTabsEditor(
            tabs = tabs,
            onReorder = { reordered -> onSave(reordered) },
            onRemove = { route -> onSave(keys - route) },
            modifier = Modifier.padding(horizontal = Spacing.ml),
        )

        // ── Add tabs ──────────────────────────────────────────────────────────
        Text(
            text = "Add tabs",
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp,
            lineHeight = 30.sp,
            color = cs.onBackground,
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 28.dp, bottom = 10.dp),
        )

        // Every catalog tab keeps a permanent slot wrapped in AnimatedVisibility, so a
        // row expands/collapses smoothly as the tab leaves/joins the bar instead of the
        // list snapping. Spacing lives inside the visible content (a spacedBy column
        // would leave gaps for collapsed rows).
        Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
            navDestinationCatalog
                .filter { it.route != UserPreferences.NAV_TAB_SETTINGS } // never hidden
                .forEach { dest ->
                    AnimatedVisibility(
                        visible = dest.route !in keys,
                        enter = expandVertically(tween(300)) + fadeIn(tween(220, delayMillis = 90)),
                        exit = shrinkVertically(tween(280)) + fadeOut(tween(140)),
                    ) {
                        HiddenTabRow(
                            dest = dest,
                            onAdd = { haptic.confirm(); onSave(addTab(keys, dest.route)) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            AnimatedVisibility(
                visible = navDestinationCatalog.all { it.route in keys },
                enter = expandVertically(tween(300)) + fadeIn(tween(220, delayMillis = 90)),
                exit = shrinkVertically(tween(280)) + fadeOut(tween(140)),
            ) {
                Text(
                    text = "Every tab is already on your bar.",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

// New tabs slot in just before a trailing Settings (the conventional last position);
// if the user moved Settings elsewhere, simply append.
private fun addTab(keys: List<String>, route: String): List<String> =
    if (keys.lastOrNull() == UserPreferences.NAV_TAB_SETTINGS) {
        keys.dropLast(1) + route + UserPreferences.NAV_TAB_SETTINGS
    } else {
        keys + route
    }

/**
 * The reorderable tab strip, built on the glance editor's interaction model:
 *  - one ROOT-level long-press drag (not per-tile) hit-tests the tile bounds, lifts a
 *    floating, slightly scaled copy under the finger, and hides the original in place;
 *  - the dragged tile moves into the slot under the finger (index move, not adjacent
 *    swap), and the other tiles GLIDE into their new slots via animateBounds;
 *  - removal shrinks the tile out, then its slot collapses and the neighbours flow in
 *    (tiles share the row width equally, so they widen to fill the space);
 *  - newly added tabs spring in with a gentle overshoot.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NavTabsEditor(
    tabs: List<NavDestination>,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberAppHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var order by remember { mutableStateOf(tabs) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    val removing = remember { mutableStateListOf<String>() }
    // Routes dropped locally once their removal animation finished, but not yet gone
    // upstream. Keeps a just-removed tile from flashing back during the prefs round-trip.
    val pendingRemoval = remember { mutableStateListOf<String>() }
    // Routes present when the editor opened — anything added later springs in.
    val initialIds = remember { tabs.map { it.route }.toSet() }
    // Holds animateBounds on for a beat after an add/remove lands, so the slot
    // collapse/expansion glides instead of snapping once `removing` empties.
    var glideHold by remember { mutableIntStateOf(0) }
    fun holdGlide() = scope.launch { glideHold++; delay(450); glideHold-- }

    val currentTabs by rememberUpdatedState(tabs)

    // Resync from upstream when not mid-drag: fold in additions, drop removals — without
    // resurrecting a tile that's mid-removal or whose prefs write hasn't landed yet.
    LaunchedEffect(tabs) {
        if (draggedKey != null) return@LaunchedEffect
        val upstream = tabs.associateBy { it.route }
        pendingRemoval.retainAll { it in upstream }
        val merged = buildList {
            order.forEach { existing ->
                val id = existing.route
                when {
                    id in pendingRemoval -> Unit
                    id in upstream -> add(upstream.getValue(id))
                    id in removing -> add(existing)
                }
            }
            val have = map { it.route }.toSet()
            tabs.forEach { if (it.route !in have && it.route !in pendingRemoval) add(it) }
        }
        if (merged != order) {
            if (merged.size > order.size) holdGlide() // an add landed — glide the others aside
            order = merged
        }
    }

    // Tile bounds in root coordinates for the drag's hit-testing.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        val pressRoot = local + rootOrigin
                        val hit = bounds.entries.firstOrNull { it.value.contains(pressRoot) }?.key
                        if (hit != null && hit !in removing) {
                            draggedKey = hit
                            dragPos = pressRoot
                            haptic.confirm()
                        }
                    },
                    onDragEnd = {
                        if (draggedKey != null) {
                            onReorder(order.map { it.route })
                            draggedKey = null
                        }
                    },
                    onDragCancel = {
                        draggedKey = null
                        order = currentTabs
                    },
                    onDrag = { change, amount ->
                        val key = draggedKey ?: return@detectDragGesturesAfterLongPress
                        change.consume()
                        dragPos += amount
                        // Move the dragged tile into the slot under the finger.
                        val targetKey = bounds.entries
                            .firstOrNull { (k, r) -> k != key && r.contains(dragPos) }?.key
                        if (targetKey != null) {
                            val from = order.indexOfFirst { it.route == key }
                            val to = order.indexOfFirst { it.route == targetKey }
                            if (from in order.indices && to in order.indices && from != to) {
                                order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                haptic.tick()
                            }
                        }
                    },
                )
            },
    ) {
        // Glide only while something is in motion — on at first composition it would
        // make the strip fly in from zero (same gate the glance editor uses).
        val animateSlots = draggedKey != null || removing.isNotEmpty() || glideHold > 0
        LookaheadScope {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                order.forEach { dest ->
                    key(dest.route) {
                        val isDragged = dest.route == draggedKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { bounds[dest.route] = it.boundsInRoot() }
                                .then(
                                    if (animateSlots) Modifier.animateBounds(this@LookaheadScope)
                                    else Modifier
                                )
                                // Dragged tile: hidden here (drawn in the floating overlay)
                                // but still laid out so its slot is reserved.
                                .then(if (isDragged) Modifier.graphicsLayer { alpha = 0f } else Modifier),
                        ) {
                            EditableNavTabTile(
                                dest = dest,
                                locked = dest.route == UserPreferences.NAV_TAB_SETTINGS,
                                isNew = dest.route !in initialIds,
                                removing = dest.route in removing,
                                onRemove = {
                                    if (dest.route !in removing) {
                                        haptic.confirm()
                                        removing.add(dest.route)
                                    }
                                },
                                onRemoved = {
                                    removing.remove(dest.route)
                                    pendingRemoval.add(dest.route)
                                    order = order.filterNot { it.route == dest.route }
                                    bounds.remove(dest.route)
                                    holdGlide() // neighbours widen into the freed slot
                                    onRemove(dest.route)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Floating copy of the dragged tile — follows the finger above the strip.
        val key = draggedKey
        val rect = key?.let { bounds[it] }
        val dest = key?.let { k -> order.firstOrNull { it.route == k } }
        if (key != null && rect != null && dest != null) {
            val left = dragPos.x - rootOrigin.x - rect.width / 2f
            val top = dragPos.y - rootOrigin.y - rect.height / 2f
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                    .zIndex(10f)
                    .graphicsLayer { scaleX = 1.06f; scaleY = 1.06f },
            ) {
                NavTabTileBody(
                    dest = dest,
                    locked = dest.route == UserPreferences.NAV_TAB_SETTINGS,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EditableNavTabTile(
    dest: NavDestination,
    locked: Boolean,
    isNew: Boolean,
    removing: Boolean,
    onRemove: () -> Unit,
    onRemoved: () -> Unit,
) {
    // Tiles present when the editor opens show immediately; added ones spring in with a
    // soft overshoot; removal is a quick shrink + fade before the slot collapses.
    val appear = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(removing) {
        if (removing) {
            appear.animateTo(0f, tween(durationMillis = 140))
            onRemoved()
        } else {
            appear.animateTo(
                1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }

    Box(
        modifier = Modifier.graphicsLayer {
            alpha = appear.value.coerceIn(0f, 1f)
            scaleX = 0.7f + 0.3f * appear.value
            scaleY = 0.7f + 0.3f * appear.value
        },
    ) {
        NavTabTileBody(dest = dest, locked = locked, modifier = Modifier.fillMaxWidth())
        // Badge is a sibling ABOVE the body (glance-editor rule) — × to remove, or the
        // lock on the mandatory Settings tile.
        if (locked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Settings can’t be removed",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        } else {
            Tap(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Hide ${dest.label} tab",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabTileBody(dest: NavDestination, locked: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerHigh)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = dest.selectedIcon,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = dest.label,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HiddenTabRow(dest: NavDestination, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onAdd, modifier = modifier) {
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
                modifier = Modifier.size(40.dp).clip(CircleShape).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = dest.icon,
                    contentDescription = null,
                    tint = cs.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dest.label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TAB_DESCRIPTIONS[dest.route]?.let { description ->
                    Text(
                        text = description,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(cs.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Add ${dest.label} tab",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
