package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import com.uc.homehealth.data.GlanceTile
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

// ─── Shared "at a glance" tile UI ─────────────────────────────────────────────
// Used by the dashboard (view) and the edit subpage (with remove badges).

// Picks a legible ink (near-black or white) for text/icons drawn on a filled accent.
internal fun glanceInkOn(color: Color): Color =
    if (color.luminance() > 0.5f) Color(0xFF15141A) else Color.White

// Renders a Material expressive RoundedPolygon (MaterialShapes) as a Compose Shape,
// scaled to fill the target box. One shape used consistently for every tile blob.
private class MaterialPolygonShape(private val polygon: RoundedPolygon) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath().asComposePath()
        val b = polygon.calculateBounds(FloatArray(4))
        val left = b[0]; val top = b[1]
        val span = max(b[2] - left, b[3] - top)
        val matrix = Matrix()
        matrix.scale(size.width / span, size.height / span)
        matrix.translate(-left, -top)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberTileBlobShape(): Shape =
    remember { MaterialPolygonShape(MaterialShapes.Cookie9Sided) }

// Rotating accent palette so adjacent tiles read as distinct without per-tile config.
@Composable
internal fun rememberGlancePalette(): List<Color> {
    val c = MaterialTheme.customColors
    return listOf(c.sky, c.sand, c.mint, c.cyan, c.lavender, c.coral)
}

internal fun glanceIconKey(domain: String): String = when (domain) {
    "light", "switch", "input_boolean" -> "bulb"
    "climate" -> "thermo"
    "lock" -> "lock"
    "media_player" -> "speaker"
    "sensor", "binary_sensor" -> "pulse"
    "fan" -> "energy"
    "cover" -> "door"
    else -> "sparkle"
}

internal fun glanceDomainLabel(domain: String): String = when (domain) {
    "sensor" -> "Sensors"
    "binary_sensor" -> "Binary sensors"
    "light" -> "Lights"
    "switch" -> "Switches"
    "climate" -> "Climate"
    "media_player" -> "Media players"
    "lock" -> "Locks"
    "cover" -> "Covers"
    "fan" -> "Fans"
    "number", "input_number" -> "Numbers"
    "person" -> "People"
    "device_tracker" -> "Device trackers"
    else -> domain.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun trimNum(s: String): String {
    val f = s.toFloatOrNull() ?: return s
    return if (f == f.toInt().toFloat()) f.toInt().toString() else s
}

// HA "no value" states. Rendered as a short em-dash so they never blow out the big
// featured number (e.g. "Unavailable" at 60sp used to overflow the tile).
private val GLANCE_NULL_STATES = setOf("unavailable", "unknown", "none", "nan", "null", "")

private fun normalizeState(state: String): String =
    if (state.trim().lowercase() in GLANCE_NULL_STATES) "—"
    else state.replaceFirstChar { it.uppercase() }

// Compact value with unit, e.g. "31°", "56 µg/m³", "1503", or a capitalized word.
private fun tileValue(tile: GlanceTile): String {
    val num = Regex("""-?\d+(?:\.\d+)?""").find(tile.state)?.value
    val unit = tile.unit?.takeIf { it.isNotBlank() }
    return when {
        num == null -> normalizeState(tile.state)
        unit == "°" -> "${trimNum(num)}°"
        unit != null -> "${trimNum(num)} $unit"
        else -> trimNum(num)
    }
}

// Big featured number: numeric part with a degree sign when relevant, else a normalized word.
private fun bigValue(tile: GlanceTile): String {
    val num = Regex("""-?\d+(?:\.\d+)?""").find(tile.state)?.value
        ?: return normalizeState(tile.state)
    return if (tile.unit == "°") "${trimNum(num)}°" else trimNum(num)
}

// Featured value font size: numbers are short, but word states ("Unavailable", "Heating")
// must shrink to fit the tile. Numbers always render at the big size via the digit branch.
private fun featuredFontSize(text: String) = when {
    text.length <= 2 -> 52.sp
    text.length <= 4 -> 40.sp
    text.length <= 7 -> 30.sp
    else -> 22.sp
}

// Featured-tile value: digits roll at the large display size; non-numeric words drop to a
// size that fits on one line (with ellipsis as a final safety net) so nothing overflows.
@Composable
private fun FeaturedValueText(value: String, color: Color, valueKey: String) {
    if (value.any { it.isDigit() }) {
        RollingNumberText(
            text = value,
            style = TextStyle(
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 60.sp,
                lineHeight = 56.sp,
                color = color,
            ),
            labelPrefix = "glance_$valueKey",
        )
    } else {
        Text(
            text = value,
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = featuredFontSize(value),
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// One featured tile + up to three mini tiles make up a single carousel page.
private const val TILES_PER_PAGE = 4
private val GLANCE_PAGE_HEIGHT = 224.dp

/**
 * The single, self-curating "At a glance" surface. [cards] arrives already prioritized
 * (alert → pinned stats → live insight → delight); the hero (featured, large) tile is the
 * most important thing right now and the supporting tiles fill in by relevance. Beyond one
 * page (1 featured + 3 supporting) it spills into a swipeable carousel — no tiles are lost.
 *
 * When the *set* (or order) of cards changes — something became relevant or stopped being —
 * the affected page crossfades, so tiles "fade and come"; value-only ticks keep the same
 * keys and update in place with no flash. Empty → the add-tiles prompt. See
 * docs/material3-expressive.md (one contained group, one clear hero, motion for change).
 */
@Composable
fun SmartGlance(
    cards: List<GlanceCard>,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
) {
    if (cards.isEmpty()) {
        if (onAdd != null) EmptyTilesCard(onAddTile = onAdd, modifier = modifier.fillMaxWidth())
        return
    }
    if (cards.size <= TILES_PER_PAGE) {
        // Single page: crossfade as the set/relevance changes (there's no swipe to fight).
        AnimatedGlancePage(cards = cards, modifier = modifier.fillMaxWidth().height(GLANCE_PAGE_HEIGHT))
        return
    }
    // More than one page's worth: a plain swipeable carousel. NOT wrapped in AnimatedContent
    // per page — that transition layer fought the pager's drag/fling and made swiping janky.
    val pages = cards.chunked(TILES_PER_PAGE)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(GLANCE_PAGE_HEIGHT),
        ) { page ->
            GlanceCardPage(cards = pages.getOrNull(page) ?: emptyList(), modifier = Modifier.fillMaxSize())
        }
        GlancePageDots(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp),
        )
    }
}

// Crossfades a glance page only when its set/order of cards changes (keyed by the card
// keys), so relevance changes animate while live value ticks update silently in place.
@Composable
private fun AnimatedGlancePage(cards: List<GlanceCard>, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = cards,
        contentKey = { page -> page.joinToString(",") { it.key } },
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(spring(stiffness = Spring.StiffnessMediumLow), initialScale = 0.96f)) togetherWith
                fadeOut(tween(180))
        },
        label = "glance_page",
        modifier = modifier,
    ) { page ->
        if (page.isNotEmpty()) GlanceCardPage(cards = page, modifier = Modifier.fillMaxSize())
    }
}

// One page of the surface: the first card is the hero (large, left, full width when alone);
// the next up to three fill the supporting column on the right. Each card carries its own
// accent, so no palette threading is needed. Height comes from [modifier].
@Composable
private fun GlanceCardPage(cards: List<GlanceCard>, modifier: Modifier = Modifier) {
    if (cards.isEmpty()) return
    if (cards.size == 1) {
        FeaturedTile(card = cards[0], modifier = modifier)
        return
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeaturedTile(card = cards[0], modifier = Modifier.weight(1f).fillMaxHeight())
        val supporting = cards.drop(1).take(3)
        if (supporting.isNotEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                supporting.forEach { card ->
                    GlanceMetricTile(card = card, modifier = Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}

// Page indicator for the glance carousel: the active page is a stretched pill.
@Composable
private fun GlancePageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == current
            val dotWidth by animateDpAsState(
                targetValue = if (active) 18.dp else 6.dp,
                label = "glance_dot_$i",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(
                        if (active) cs.onSurface
                        else cs.onSurfaceVariant.copy(alpha = 0.30f)
                    )
            )
        }
    }
}

@Composable
private fun FeaturedTile(card: GlanceCard, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = rememberTileBlobShape()
    val ink = glanceInkOn(card.accent)
    val haptic = rememberAppHaptics()
    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(shape)
                    .background(if (card.filled) ink.copy(alpha = 0.16f) else card.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor(card.icon),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column {
                FeaturedValueText(
                    value = card.featuredValue,
                    color = if (card.filled) ink else cs.onSurface,
                    valueKey = card.key,
                )
                Text(
                    text = card.name,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = if (card.filled) ink.copy(alpha = 0.82f) else cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
    val base = modifier
        .clip(MaterialTheme.shapes.large)
        .background(if (card.filled) card.accent else card.accent.copy(alpha = 0.20f))
    val tap = card.onTap
    // Tap's built-in press haptic is a faint CONTEXT_CLICK; add a firmer navigation buzz on
    // the tap itself (matching room cards) so opening a control/running a scene is felt.
    if (tap != null) Tap(onClick = { haptic.navigation(); tap() }, modifier = base) { body() }
    else Box(modifier = base) { body() }
}

@Composable
private fun GlanceMetricTile(card: GlanceCard, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = rememberTileBlobShape()
    val ink = glanceInkOn(card.accent)
    val haptic = rememberAppHaptics()
    val body: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large)
                .background(if (card.filled) card.accent else card.accent.copy(alpha = 0.20f))
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(shape)
                    .background(if (card.filled) ink.copy(alpha = 0.16f) else card.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor(card.icon),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    color = if (card.filled) ink.copy(alpha = 0.82f) else cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = card.miniValue,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    color = if (card.filled) ink else cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val tap = card.onTap
    if (tap != null) Tap(onClick = { haptic.navigation(); tap() }, modifier = modifier) { body() }
    else Box(modifier = modifier) { body() }
}

/**
 * Editable at-a-glance carousel: a swipeable [HorizontalPager] that mirrors the
 * dashboard (1 featured + up to 3 mini tiles per page, with page dots), with a −
 * badge on every tile and full drag-and-drop reordering — *including across pages*.
 *
 * Drag is driven by a single root-level gesture (not per-tile) so it survives the
 * dragged tile being re-chunked onto another page:
 *  - long-press a tile to pick it up — this freezes page-swiping;
 *  - the tile floats under the finger in an overlay while its slot in the grid
 *    follows along, reflowing the others;
 *  - dwell near the left/right edge to auto-advance to the adjacent page, so you can
 *    carry ("throw") a tile from one page to another;
 *  - release to drop it into the slot under the finger.
 * A quick horizontal swipe (no long-press) just changes page.
 */
@Composable
fun GlanceTilesEditor(
    tiles: List<GlanceTile>,
    onReorder: (List<GlanceTile>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberGlancePalette()
    val haptic = rememberAppHaptics()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var order by remember { mutableStateOf(tiles) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    val removing = remember { mutableStateListOf<String>() }
    // Ids dropped locally once their removal animation finished, but not yet gone
    // upstream. Keeps a just-removed tile from flashing back when a live-value
    // emission lands during the prefs round-trip.
    val pendingRemoval = remember { mutableStateListOf<String>() }
    // Resync from upstream when not mid-drag: refresh live tile values, fold in
    // additions, and drop removals — without resurrecting a tile that's mid-removal
    // or whose prefs write hasn't landed yet.
    LaunchedEffect(tiles) {
        if (draggedKey != null) return@LaunchedEffect
        val upstream = tiles.associateBy { it.entityId }
        pendingRemoval.retainAll { it in upstream }
        val merged = buildList {
            order.forEach { existing ->
                val id = existing.entityId
                when {
                    id in pendingRemoval -> Unit                  // awaiting upstream: drop
                    id in upstream -> add(upstream.getValue(id))  // present: latest values
                    id in removing -> add(existing)               // animating out: keep
                }
            }
            val have = map { it.entityId }.toSet()
            tiles.forEach { if (it.entityId !in have && it.entityId !in pendingRemoval) add(it) }
        }
        if (merged != order) order = merged
    }

    // Tile bounds in editor-root coordinates, shared across pages so the drag can
    // hit-test tiles on whichever page is currently visible.
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewportWidth by remember { mutableStateOf(0) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }   // finger, root coords
    var edgeArmed by remember { mutableStateOf(true) }

    val pages = order.chunked(TILES_PER_PAGE)
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val edgePx = with(density) { 56.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                rootOrigin = it.positionInRoot()
                viewportWidth = it.size.width
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { local ->
                        val pressRoot = local + rootOrigin
                        val hit = bounds.entries.firstOrNull { it.value.contains(pressRoot) }?.key
                        if (hit != null) {
                            draggedKey = hit
                            dragPos = pressRoot
                            edgeArmed = true
                            haptic.confirm()
                        }
                    },
                    onDragEnd = { if (draggedKey != null) { onReorder(order); draggedKey = null } },
                    onDragCancel = { draggedKey = null; order = tiles },
                    onDrag = { change, amount ->
                        val key = draggedKey
                        if (key != null) {
                            change.consume()
                            dragPos += amount

                            // Move the dragged tile into the slot under the finger
                            // (works across pages — the index is global).
                            val targetKey = bounds.entries
                                .firstOrNull { (k, r) -> k != key && r.contains(dragPos) }?.key
                            if (targetKey != null) {
                                val from = order.indexOfFirst { it.entityId == key }
                                val to = order.indexOfFirst { it.entityId == targetKey }
                                if (from in order.indices && to in order.indices && from != to) {
                                    order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                    haptic.tick()
                                }
                            }

                            // Dwell near an edge → flip to the adjacent page so the tile
                            // can be carried across. Re-armed once the finger leaves the edge.
                            val localX = dragPos.x - rootOrigin.x
                            val current = pagerState.currentPage
                            val atRight = localX > viewportWidth - edgePx
                            val atLeft = localX < edgePx
                            when {
                                edgeArmed && atRight && current < pages.lastIndex -> {
                                    edgeArmed = false
                                    scope.launch { pagerState.animateScrollToPage(current + 1); edgeArmed = true }
                                }
                                edgeArmed && atLeft && current > 0 -> {
                                    edgeArmed = false
                                    scope.launch { pagerState.animateScrollToPage(current - 1); edgeArmed = true }
                                }
                                !atLeft && !atRight -> edgeArmed = true
                            }
                        }
                    },
                )
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 12.dp,
                // Freeze swiping during a drag; the drag does its own edge auto-advance.
                userScrollEnabled = draggedKey == null,
                modifier = Modifier.fillMaxWidth().height(GLANCE_PAGE_HEIGHT),
            ) { pageIndex ->
                val pageTiles = pages.getOrNull(pageIndex) ?: emptyList()
                GlanceEditPage(
                    pageTiles = pageTiles,
                    startIndex = pageIndex * TILES_PER_PAGE,
                    palette = palette,
                    draggedKey = draggedKey,
                    isRemoving = { it in removing },
                    onBoundsChange = { id, r -> bounds[id] = r },
                    onBeginRemove = { keyId ->
                        if (keyId !in removing) { haptic.confirm(); removing.add(keyId) }
                    },
                    onRemoved = { keyId ->
                        removing.remove(keyId)
                        // Mark pending and drop it from the local order so the slot
                        // collapses now instead of waiting on the prefs round-trip —
                        // pendingRemoval keeps it from flashing back in the meantime.
                        pendingRemoval.add(keyId)
                        order = order.filterNot { it.entityId == keyId }
                        bounds.remove(keyId)
                        onRemove(keyId)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (pages.size > 1) {
                GlancePageDots(
                    count = pages.size,
                    current = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 14.dp),
                )
            }
        }

        // Floating copy of the dragged tile — drawn above the pager so it stays visible
        // even while pages scroll underneath. Sized to its current slot's bounds.
        val key = draggedKey
        val rect = key?.let { bounds[it] }
        val tile = key?.let { k -> order.firstOrNull { it.entityId == k } }
        if (key != null && rect != null && tile != null) {
            val idx = order.indexOfFirst { it.entityId == key }.coerceAtLeast(0)
            val big = idx % TILES_PER_PAGE == 0
            val accent = palette[idx % palette.size]
            val left = dragPos.x - rootOrigin.x - rect.width / 2f
            val top = dragPos.y - rootOrigin.y - rect.height / 2f
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                    .zIndex(10f)
                    .graphicsLayer { scaleX = 1.05f; scaleY = 1.05f },
            ) {
                if (big) FeaturedTile(card = tile.toCard(accent), modifier = Modifier.fillMaxSize())
                else GlanceMetricTile(card = tile.toCard(accent), modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// One editable page: same geometry as the read-only GlancePage, with each tile
// reporting its root-space bounds for the parent's drag hit-testing and showing a −
// badge. The tile currently being dragged renders invisibly here (it's drawn in the
// parent's floating overlay) but keeps its slot so the others reflow around it.
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GlanceEditPage(
    pageTiles: List<GlanceTile>,
    startIndex: Int,
    palette: List<Color>,
    draggedKey: String?,
    isRemoving: (String) -> Boolean,
    onBoundsChange: (String, Rect) -> Unit,
    onBeginRemove: (String) -> Unit,
    onRemoved: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animate tiles between slots only while a drag is in progress (reflow around the
    // dragged tile) or a removal is collapsing (neighbours slide up). Off during plain
    // page swiping — otherwise animateBounds on a freshly-composed page makes its tiles
    // fly in from zero, leaving a big empty gap mid-swipe.
    val animateSlots = draggedKey != null || pageTiles.any { isRemoving(it.entityId) }
    LookaheadScope {
        SinglePageEditLayout(tileCount = pageTiles.size, modifier = modifier) {
            pageTiles.forEachIndexed { localIndex, tile ->
                val keyId = tile.entityId
                key(keyId) {
                    val isDragged = keyId == draggedKey
                    val frameMod = Modifier
                        .onGloballyPositioned { onBoundsChange(keyId, it.boundsInRoot()) }
                        .then(
                            when {
                                // Dragged tile: hidden here (shown in the overlay) but still
                                // laid out so its slot is reserved.
                                isDragged -> Modifier.graphicsLayer { alpha = 0f }
                                animateSlots -> Modifier.animateBounds(this@LookaheadScope)
                                else -> Modifier
                            }
                        )
                    EditableGlanceTile(
                        tile = tile,
                        big = localIndex == 0,
                        accent = palette[(startIndex + localIndex) % palette.size],
                        removing = isRemoving(keyId),
                        onRemove = { onBeginRemove(keyId) },
                        onRemoved = { onRemoved(keyId) },
                        frameModifier = frameMod,
                    )
                }
            }
        }
    }
}

// Single page geometry matching the read-only GlancePage: slot 0 is the featured tile
// (full width when alone, else left half), slots 1.. are pills filling the right
// column. Measured by slot so a tile resizes when it changes slot.
@Composable
private fun SinglePageEditLayout(
    tileCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gap = 12.dp.roundToPx()
        val half = ((w - gap) / 2).coerceAtLeast(1)
        val numPills = (tileCount - 1).coerceAtLeast(0)
        val featuredW = if (numPills == 0) w else half
        val pillH = if (numPills > 0) ((h - (numPills - 1) * gap) / numPills).coerceAtLeast(1) else 0

        val placeables = measurables.mapIndexed { i, m ->
            if (i == 0) m.measure(Constraints.fixed(featuredW, h))
            else m.measure(Constraints.fixed(half, pillH))
        }
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                if (i == 0) p.place(0, 0)
                else p.place(half + gap, (i - 1) * (pillH + gap))
            }
        }
    }
}

@Composable
private fun EditableGlanceTile(
    tile: GlanceTile,
    big: Boolean,
    accent: Color,
    removing: Boolean,
    onRemove: () -> Unit,
    onRemoved: () -> Unit,
    frameModifier: Modifier = Modifier,
    gestureModifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = rememberTileBlobShape()

    // Tiles are present from the moment the editor opens — no entrance pop-in.
    // animateFloatAsState seeds its initial value from the first target (1f), so a
    // tile shows at full alpha/scale immediately; only removal animates out (a quick
    // shrink + fade) before the slot collapses.
    val appear by animateFloatAsState(
        targetValue = if (removing) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "tile_appear_${tile.entityId}",
        finishedListener = { v -> if (removing && v == 0f) onRemoved() },
    )

    Box(
        modifier = frameModifier
            .graphicsLayer {
                alpha = appear
                scaleX = 0.88f + 0.12f * appear
                scaleY = 0.88f + 0.12f * appear
            }
            .clip(MaterialTheme.shapes.large)
            .background(accent.copy(alpha = 0.20f)),
    ) {
        // Tile body holds the drag gesture; the remove badge is a sibling above it.
        Box(modifier = Modifier.fillMaxSize().then(gestureModifier)) {
        if (big) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(shape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = haIconFor(glanceIconKey(tile.domain)),
                        contentDescription = null,
                        tint = glanceInkOn(accent),
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column {
                    FeaturedValueText(value = bigValue(tile), color = cs.onSurface, valueKey = tile.entityId)
                    Text(
                        text = tile.name,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(shape).background(accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = haIconFor(glanceIconKey(tile.domain)),
                        contentDescription = null,
                        tint = glanceInkOn(accent),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tile.name,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp,
                        letterSpacing = 0.4.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = tileValue(tile),
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 26.sp,
                        lineHeight = 28.sp,
                        color = cs.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        }
        RemoveBadge(onClick = onRemove)
    }
}

@Composable
private fun BoxScope.RemoveBadge(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tap(
        onClick = onClick,
        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).clip(CircleShape).background(cs.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Remove,
                contentDescription = "Remove tile",
                tint = cs.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyTilesCard(
    onAddTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onAddTile, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add tiles",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = "Pick the entities you want at a glance",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ─── Unified card model ───────────────────────────────────────────────────────
// Both user-pinned entity tiles and app-surfaced "smart" tiles (alerts/insights/delights)
// map to GlanceCard, so one carousel can show them together and reorder them by relevance.
// [filled] gives a card (used for alerts) a solid-accent, high-emphasis treatment.
data class GlanceCard(
    val key: String,               // stable identity for crossfade keying
    val icon: String,              // haIconFor key
    val name: String,              // label (Montserrat)
    val featuredValue: String,     // big serif value/phrase for the hero slot
    val miniValue: String,         // compact value for the supporting slots
    val accent: Color,
    val filled: Boolean = false,
    val onTap: (() -> Unit)? = null,
)

// A user-pinned entity tile rendered as a card — reuses the existing big/compact value
// formatting and domain→icon mapping so pinned tiles look exactly as before.
internal fun GlanceTile.toCard(accent: Color, onTap: (() -> Unit)? = null): GlanceCard = GlanceCard(
    key = entityId,
    icon = glanceIconKey(domain),
    name = name,
    featuredValue = bigValue(this),
    miniValue = tileValue(this),
    accent = accent,
    onTap = onTap,
)
