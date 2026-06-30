package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MusicOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SpeakerGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.MaEnqueueMode
import com.uc.homehealth.data.MaMediaType
import com.uc.homehealth.data.MaQueue
import com.uc.homehealth.data.MaSearchItem
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.ui.components.MaFilterPill
import com.uc.homehealth.ui.components.MaResultRow
import com.uc.homehealth.ui.components.MaSearchField
import com.uc.homehealth.ui.components.MediaCardHero
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.SkeletonBox
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Music — the Music Assistant tab ─────────────────────────────────────────
// ONE entry in the bottom bar; everything else lives behind it on an internal
// NavDisplay (same pattern as Settings): the main page carries players + the
// now-playing hero + the queue, and Library / Search are full subpages so the
// main page stays uncluttered. All data flows through the HA integration's MA
// services — the app never connects to the MA server directly.

private const val LIBRARY_PAGE_SIZE = 24

private enum class MusicDestination : NavKey { Main, Library, Search }

// List-area phases for the Library/Search subpages — content crossfades + grows
// between these instead of snapping (same pattern as Voice settings / the sheet).
private enum class MaListPhase { PROMPT, LOADING, EMPTY, LIST }

private fun musicSlideSpring() = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

private fun musicExpandSpring() = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntSize.VisibilityThreshold,
)

@Composable
fun MusicScreen(
    viewModel: MusicViewModel = hiltViewModel(),
    onAnnounce: (entityId: String) -> Unit = {},
) {
    val backStack = rememberNavBackStack(MusicDestination.Main)

    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        modifier = Modifier.fillMaxSize(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        // Same expressive spring slides as the Settings sub-navigation.
        transitionSpec = {
            slideInHorizontally(animationSpec = musicSlideSpring()) { it } togetherWith
                (slideOutHorizontally(animationSpec = musicSlideSpring()) { -it / 4 } +
                    fadeOut(animationSpec = tween(220)))
        },
        popTransitionSpec = {
            (slideInHorizontally(animationSpec = musicSlideSpring()) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = musicSlideSpring()) { it }
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(animationSpec = musicSlideSpring()) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = musicSlideSpring()) { it }
        },
        entryProvider = entryProvider {
            entry<MusicDestination> { destination ->
                when (destination) {
                    MusicDestination.Main -> MusicMainPage(
                        viewModel = viewModel,
                        onAnnounce = onAnnounce,
                        onOpenLibrary = { backStack.add(MusicDestination.Library) },
                        onOpenSearch = { backStack.add(MusicDestination.Search) },
                    )
                    MusicDestination.Library -> MusicLibraryPage(
                        viewModel = viewModel,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    MusicDestination.Search -> MusicSearchPage(
                        viewModel = viewModel,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                }
            }
        },
    )
}

// ─── Main page ───────────────────────────────────────────────────────────────

@Composable
private fun MusicMainPage(
    viewModel: MusicViewModel,
    onAnnounce: (entityId: String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val players by viewModel.players.collectAsStateWithLifecycle()
    val playersLoading by viewModel.playersLoading.collectAsStateWithLifecycle()
    val selectedPlayerId by viewModel.selectedPlayerId.collectAsStateWithLifecycle()
    val media by viewModel.selectedMedia.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        // Header — same voice as Pulse/Energy/Activity.
        Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 14.dp)) {
            Text(
                text = "Music",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 40.sp,
                color = cs.onBackground,
            )
            Text(
                text = when {
                    players.isEmpty() -> "Music Assistant"
                    players.size == 1 -> "Music Assistant · 1 player"
                    else -> "Music Assistant · ${players.size} players"
                },
                fontFamily = MontserratFamily,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Loading is its own state: an empty list mid-connect must never flash the
        // "no players" empty state (the fake-empty-state rule from the M3 doc).
        if (playersLoading) {
            MusicMainSkeleton()
            Spacer(Modifier.height(130.dp))
            return@Column
        }
        if (players.isEmpty()) {
            NoPlayersCard()
            Spacer(Modifier.height(130.dp))
            return@Column
        }

        // A single player needs no selector — the subtitle and hero already name it.
        if (players.size > 1) {
            PlayerRail(
                players = players,
                selectedId = selectedPlayerId,
                onSelect = { viewModel.selectPlayer(it) },
            )
            Spacer(Modifier.height(14.dp))
        }

        // Now playing + queue are ONE concept, so they render as one grouped surface:
        // big outer corners, small kissing corners where the two cards meet (the same
        // grouped-list language Pulse and Activity use).
        val activeQueue = queue?.takeIf { it.itemCount > 0 }
        media?.let { m ->
            MediaCardHero(
                media = m,
                onPlayPause = { viewModel.playPause() },
                onSkipPrev = { viewModel.skipPrev() },
                onSkipNext = { viewModel.skipNext() },
                onToggleShuffle = { viewModel.setShuffle(!m.shuffleOn) },
                onCycleRepeat = { viewModel.setRepeat(MediaRepeatMode.next(m.repeatMode)) },
                onSeek = { viewModel.seek(it) },
                onVolumeChange = { viewModel.setVolume(it) },
                onAnnounce = { onAnnounce(m.entityId) },
                onSearchMusic = onOpenSearch,
                modifier = Modifier.padding(horizontal = Spacing.ml),
                shape = if (activeQueue != null) RoundedCornerShape(
                    topStart = 32.dp, topEnd = 32.dp, bottomStart = 8.dp, bottomEnd = 8.dp,
                ) else MaterialTheme.shapes.large,
            )
        }

        activeQueue?.let { q ->
            Spacer(Modifier.height(3.dp))
            UpNextCard(
                queue = q,
                otherPlayers = players.filter { it.entityId != selectedPlayerId },
                onTransfer = { viewModel.transferQueue(it) },
                shape = RoundedCornerShape(
                    topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp,
                ),
            )
        }

        Spacer(Modifier.height(Spacing.l))

        // ── Explore — entry points into the subpages. Distinct brand accents per
        // row (customColors, like Pulse's categories) instead of primary everywhere.
        val custom = MaterialTheme.customColors
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            ExploreRow(
                icon = Icons.Outlined.LibraryMusic,
                accent = custom.lavender,
                title = "Library",
                summary = "Artists, albums, tracks, playlists & radio",
                shape = exploreRowShape(0, 2),
                onClick = onOpenLibrary,
            )
            ExploreRow(
                icon = Icons.Outlined.Search,
                accent = custom.cyan,
                title = "Search",
                summary = "Find something to play across your providers",
                shape = exploreRowShape(1, 2),
                onClick = onOpenSearch,
            )
        }

        Spacer(Modifier.height(130.dp))
    }
}

// Grouped-list shape (22/6) — the same pattern Pulse and Activity use.
private fun exploreRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
    index == 0 -> androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp,
    )
    index == count - 1 -> androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp,
    )
    else -> androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
}

@Composable
private fun ExploreRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    summary: String,
    shape: Shape,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    Tap(onClick = { haptics.navigation(); onClick() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, shape)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = summary,
                    fontFamily = MontserratFamily,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Icon(
                imageVector = haIconFor("chevron-right"),
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─── Player rail ─────────────────────────────────────────────────────────────

@Composable
private fun PlayerRail(
    players: List<HaMedia>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ml),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        players.forEach { player ->
            val selected = player.entityId == selectedId
            // Spring the fill/ink between states so selection reads as a response,
            // not a snap (same vocabulary as the media card's toggles).
            val container by animateColorAsState(
                targetValue = if (selected) cs.primary else cs.surfaceContainerHigh,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "music_player_pill_${player.entityId}",
            )
            val ink by animateColorAsState(
                targetValue = if (selected) cs.onPrimary else cs.onSurface,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "music_player_ink_${player.entityId}",
            )
            Tap(onClick = { haptics.tick(); onSelect(player.entityId) }) {
                Row(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(container)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    if (player.isPlaying) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(if (selected) cs.onPrimary else custom.mint, CircleShape)
                        )
                    }
                    Text(
                        text = player.friendlyName,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = ink,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ─── Up next ─────────────────────────────────────────────────────────────────

@Composable
private fun UpNextCard(
    queue: MaQueue,
    otherPlayers: List<HaMedia>,
    onTransfer: (entityId: String) -> Unit,
    shape: Shape = MaterialTheme.shapes.large,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var transferOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ml)
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(cs.primary.copy(alpha = 0.14f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UP NEXT",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = queue.nextTitle ?: "End of queue",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                queue.nextArtist?.let {
                    Text(
                        text = it,
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            queue.currentIndex?.let { index ->
                Text(
                    text = "${index + 1} of ${queue.itemCount}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            if (otherPlayers.isNotEmpty()) {
                Tap(onClick = { haptics.tick(); transferOpen = !transferOpen }) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(PillShape)
                            .background(if (transferOpen) cs.primary else cs.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SpeakerGroup,
                            contentDescription = "Move music to another player",
                            tint = if (transferOpen) cs.onPrimary else cs.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // Inline hand-off targets: pick a player, the queue moves there and the
        // page follows it. Springs, not tweens — it's a real state change.
        AnimatedVisibility(
            visible = transferOpen,
            enter = expandVertically(musicExpandSpring()) + fadeIn(),
            exit = shrinkVertically(musicExpandSpring()) + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "MOVE MUSIC TO",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    otherPlayers.forEach { player ->
                        MaFilterPill(
                            label = player.friendlyName,
                            selected = false,
                            onClick = {
                                haptics.confirm()
                                transferOpen = false
                                onTransfer(player.entityId)
                            },
                        )
                    }
                }
            }
        }
    }
}

// ─── Library subpage ─────────────────────────────────────────────────────────

@Composable
private fun MusicLibraryPage(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val selectedPlayerId by viewModel.selectedPlayerId.collectAsStateWithLifecycle()

    var libType by rememberSaveable { mutableStateOf(MaMediaType.ARTIST) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var items by remember { mutableStateOf<List<MaSearchItem>?>(null) }
    var endReached by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var confirmedAction by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedPlayerId, libType, favoritesOnly) {
        if (selectedPlayerId == null) {
            items = emptyList()
            return@LaunchedEffect
        }
        items = null
        val page = viewModel.loadLibrary(libType, favoritesOnly, LIBRARY_PAGE_SIZE, 0)
        items = page
        endReached = page.size < LIBRARY_PAGE_SIZE
    }

    LaunchedEffect(confirmedAction) {
        if (confirmedAction != null) {
            delay(1400)
            confirmedAction = null
        }
    }

    SettingsPageScaffold(
        title = "Library",
        subtitle = "Your Music Assistant collection",
        onBack = onBack,
        backLabel = "Music",
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaMediaType.values().forEach { type ->
                    MaFilterPill(
                        label = type.label,
                        selected = libType == type,
                        onClick = { haptics.tick(); libType = type },
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(cs.onSurface.copy(alpha = 0.12f))
                )
                MaFilterPill(
                    label = "Favorites",
                    selected = favoritesOnly,
                    onClick = { haptics.tick(); favoritesOnly = !favoritesOnly },
                )
            }

            Spacer(Modifier.height(8.dp))

            val phase = when {
                items == null -> MaListPhase.LOADING
                items.orEmpty().isEmpty() -> MaListPhase.EMPTY
                else -> MaListPhase.LIST
            }
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                },
                label = "music_library_phase",
            ) { p ->
                when (p) {
                MaListPhase.LOADING, MaListPhase.PROMPT -> MaRowSkeleton()
                MaListPhase.EMPTY -> Text(
                    text = if (favoritesOnly) "No favorite ${libType.label.lowercase()} yet"
                    else "Nothing in your library here yet",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                MaListPhase.LIST -> Column {
                    items.orEmpty().forEach { item ->
                        MaResultRow(
                            item = item,
                            confirmed = confirmedAction?.startsWith(item.uri) == true,
                            onPlayNow = {
                                haptics.confirm()
                                confirmedAction = "${item.uri}|play"
                                viewModel.playItem(item, MaEnqueueMode.PLAY)
                            },
                            onAddToQueue = {
                                haptics.tick()
                                confirmedAction = "${item.uri}|add"
                                viewModel.playItem(item, MaEnqueueMode.ADD)
                            },
                            horizontalPadding = 0.dp,
                        )
                    }
                    if (!endReached) {
                        Spacer(Modifier.height(6.dp))
                        Tap(onClick = {
                            if (loadingMore) return@Tap
                            haptics.tick()
                            loadingMore = true
                            val nextOffset = items.orEmpty().size
                            scope.launch {
                                val page = viewModel.loadLibrary(libType, favoritesOnly, LIBRARY_PAGE_SIZE, nextOffset)
                                items = items.orEmpty() + page
                                endReached = page.size < LIBRARY_PAGE_SIZE
                                loadingMore = false
                            }
                        }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(PillShape)
                                    .background(cs.surfaceContainerHigh)
                                    .padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (loadingMore) "Loading…" else "Show more",
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = cs.onSurface,
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

// ─── Search subpage ──────────────────────────────────────────────────────────

@Composable
private fun MusicSearchPage(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val selectedPlayerId by viewModel.selectedPlayerId.collectAsStateWithLifecycle()

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<MaMediaType?>(null) }
    var libraryOnly by rememberSaveable { mutableStateOf(false) }
    var sections by remember { mutableStateOf<List<Pair<MaMediaType, List<MaSearchItem>>>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var confirmedAction by remember { mutableStateOf<String?>(null) }

    // Debounced type-to-search, same contract as the quick-search sheet.
    LaunchedEffect(selectedPlayerId, query, filter, libraryOnly) {
        if (selectedPlayerId == null || query.isBlank()) {
            sections = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        val results = viewModel.search(query.trim(), filter, libraryOnly)
        sections = results.sections()
        searching = false
    }

    LaunchedEffect(confirmedAction) {
        if (confirmedAction != null) {
            delay(1400)
            confirmedAction = null
        }
    }

    SettingsPageScaffold(
        title = "Search",
        subtitle = "Across all your music providers",
        onBack = onBack,
        backLabel = "Music",
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
            MaSearchField(value = query, onValueChange = { query = it }, searching = searching)

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaFilterPill(
                    label = "All",
                    selected = filter == null,
                    onClick = { haptics.tick(); filter = null },
                )
                MaMediaType.values().forEach { type ->
                    MaFilterPill(
                        label = type.label,
                        selected = filter == type,
                        onClick = { haptics.tick(); filter = type },
                    )
                }
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(cs.onSurface.copy(alpha = 0.12f))
                )
                MaFilterPill(
                    label = "Library",
                    selected = libraryOnly,
                    onClick = { haptics.tick(); libraryOnly = !libraryOnly },
                )
            }

            Spacer(Modifier.height(8.dp))

            val phase = when {
                query.isBlank() -> MaListPhase.PROMPT
                searching && sections == null -> MaListPhase.LOADING
                sections.orEmpty().isEmpty() && !searching -> MaListPhase.EMPTY
                else -> MaListPhase.LIST
            }
            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                },
                label = "music_search_phase",
            ) { p ->
                when (p) {
                MaListPhase.PROMPT -> Text(
                    text = "Search artists, songs, albums, playlists & radio",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                )
                MaListPhase.LOADING -> MaRowSkeleton()
                MaListPhase.EMPTY -> Text(
                    text = "No results for “${query.trim()}”",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
                MaListPhase.LIST -> Column {
                    sections.orEmpty().forEach { (type, typeItems) ->
                        Text(
                            text = type.label.uppercase(),
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                        typeItems.forEach { item ->
                            MaResultRow(
                                item = item,
                                confirmed = confirmedAction?.startsWith(item.uri) == true,
                                onPlayNow = {
                                    haptics.confirm()
                                    confirmedAction = "${item.uri}|play"
                                    viewModel.playItem(item, MaEnqueueMode.PLAY)
                                },
                                onAddToQueue = {
                                    haptics.tick()
                                    confirmedAction = "${item.uri}|add"
                                    viewModel.playItem(item, MaEnqueueMode.ADD)
                                },
                                horizontalPadding = 0.dp,
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

// ─── Shared bits ─────────────────────────────────────────────────────────────

// Mirrors the loaded layout — rail pills, the grouped hero + queue surfaces — so
// content lands in place instead of reflowing.
@Composable
private fun MusicMainSkeleton() {
    Column(modifier = Modifier.padding(horizontal = Spacing.ml)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(132.dp, 110.dp, 124.dp).forEach { width ->
                SkeletonBox(modifier = Modifier.width(width).height(36.dp), shape = PillShape)
            }
        }
        Spacer(Modifier.height(14.dp))
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().height(286.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 8.dp, bottomEnd = 8.dp),
        )
        Spacer(Modifier.height(3.dp))
        SkeletonBox(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
        )
    }
}

@Composable
private fun MaRowSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        repeat(5) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(modifier = Modifier.size(46.dp), shape = MaterialTheme.shapes.small)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.width(170.dp).height(14.dp), shape = PillShape)
                    SkeletonBox(modifier = Modifier.width(90.dp).height(10.dp), shape = PillShape)
                }
            }
        }
    }
}

@Composable
private fun NoPlayersCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ml)
            .clip(MaterialTheme.shapes.large)
            .background(cs.surfaceContainerHigh)
            .padding(vertical = 36.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.MusicOff,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = "No Music Assistant players",
            fontFamily = InstrumentSerifFamily,
            fontSize = 24.sp,
            color = cs.onSurface,
        )
        Text(
            text = "Install the Music Assistant integration in Home Assistant and add a player — this page lights up automatically.",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
