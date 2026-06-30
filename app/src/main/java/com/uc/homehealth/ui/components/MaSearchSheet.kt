package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uc.homehealth.data.MaEnqueueMode
import com.uc.homehealth.data.MaMediaType
import com.uc.homehealth.data.MaSearchItem
import com.uc.homehealth.data.MaSearchResults
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

// Music Assistant search — opened from the media card's search pill (MA players only).
// Type-to-search with a debounce, media-type filter pills, and grouped results.
// Tap a result to play it now; the queue button appends it instead. Everything runs
// through the MA integration's HA services, so there is no separate MA connection.
@Composable
fun MaSearchSheet(
    visible: Boolean,
    playerName: String,
    hazeState: HazeState? = null,
    search: suspend (query: String, type: MaMediaType?, libraryOnly: Boolean) -> MaSearchResults,
    onPlay: (item: MaSearchItem, mode: MaEnqueueMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<MaMediaType?>(null) }
    var libraryOnly by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<MaSearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }
    // uri+mode of the most recent play/queue action — flips that row's button to a
    // check for a beat so the tap visibly landed.
    var confirmedAction by remember { mutableStateOf<String?>(null) }

    // Fresh sheet every open.
    LaunchedEffect(visible) {
        if (visible) {
            query = ""
            filter = null
            libraryOnly = false
            results = null
            searching = false
            confirmedAction = null
        }
    }

    // Debounced type-to-search: every keystroke/filter change restarts this effect,
    // so the delay() acts as the debounce window.
    LaunchedEffect(visible, query, filter, libraryOnly) {
        if (!visible) return@LaunchedEffect
        if (query.isBlank()) {
            results = null
            searching = false
            return@LaunchedEffect
        }
        searching = true
        delay(450)
        results = search(query.trim(), filter, libraryOnly)
        searching = false
    }

    LaunchedEffect(confirmedAction) {
        if (confirmedAction != null) {
            delay(1400)
            confirmedAction = null
        }
    }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.9f) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Search music",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Music Assistant · plays on $playerName",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(14.dp))
            MaSearchField(
                value = query,
                onValueChange = { query = it },
                searching = searching,
            )

            Spacer(Modifier.height(10.dp))
            MaFilterRow(
                filter = filter,
                onFilterChange = { filter = it },
                libraryOnly = libraryOnly,
                onLibraryOnlyChange = { libraryOnly = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        val phase = when {
            query.isBlank() -> MaSheetPhase.PROMPT
            searching && results == null -> MaSheetPhase.LOADING
            results?.isEmpty == true -> MaSheetPhase.EMPTY
            results != null -> MaSheetPhase.RESULTS
            else -> MaSheetPhase.LOADING
        }
        AnimatedContent(
            targetState = phase,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                fadeIn(tween(220)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            label = "ma_search_phase",
        ) { p ->
            when (p) {
                MaSheetPhase.PROMPT -> MaCenteredHint(
                    icon = Icons.Outlined.Search,
                    line = "Search artists, songs, albums,\nplaylists & radio",
                )
                MaSheetPhase.LOADING -> MaResultSkeleton()
                MaSheetPhase.EMPTY -> MaCenteredHint(
                    icon = Icons.Outlined.MusicNote,
                    line = "No results for “${query.trim()}”",
                )
                MaSheetPhase.RESULTS -> {
                    val res = results ?: MaSearchResults()
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 12.dp),
                    ) {
                        res.sections().forEach { (type, items) ->
                            item(key = "header_${type.haValue}") {
                                Text(
                                    text = type.label.uppercase(),
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.2.sp,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
                                )
                            }
                            items(items.size, key = { i -> items[i].uri }) { i ->
                                val item = items[i]
                                MaResultRow(
                                    item = item,
                                    confirmed = confirmedAction?.startsWith(item.uri) == true,
                                    onPlayNow = {
                                        haptic.confirm()
                                        confirmedAction = "${item.uri}|play"
                                        onPlay(item, MaEnqueueMode.PLAY)
                                    },
                                    onAddToQueue = {
                                        haptic.tick()
                                        confirmedAction = "${item.uri}|add"
                                        onPlay(item, MaEnqueueMode.ADD)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

private enum class MaSheetPhase { PROMPT, LOADING, EMPTY, RESULTS }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MaSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    searching: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 14.sp, color = cs.onSurface),
        placeholder = {
            Text(
                text = "Song, artist, album…",
                fontFamily = MontserratFamily,
                fontSize = 14.sp,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = {
            AnimatedVisibility(visible = searching, enter = fadeIn(), exit = fadeOut()) {
                ContainedLoadingIndicator(modifier = Modifier.padding(end = 8.dp).size(32.dp))
            }
        },
        shape = RoundedCornerShape(16.dp),
        // Results stream in as you type; the IME action just closes the keyboard.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = OutlinedTextFieldDefaults.colors(),
    )
}

// Single-select media-type pills + a separate "Library" toggle (MA's library_only flag).
@Composable
private fun MaFilterRow(
    filter: MaMediaType?,
    onFilterChange: (MaMediaType?) -> Unit,
    libraryOnly: Boolean,
    onLibraryOnlyChange: (Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
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
            onClick = { haptic.tick(); onFilterChange(null) },
        )
        MaMediaType.values().forEach { type ->
            MaFilterPill(
                label = type.label,
                selected = filter == type,
                onClick = { haptic.tick(); onFilterChange(type) },
            )
        }
        // Visual divider between the what-kind filter and the where-from toggle.
        Box(
            Modifier
                .width(1.dp)
                .height(18.dp)
                .background(cs.onSurface.copy(alpha = 0.12f))
        )
        MaFilterPill(
            label = "Library",
            selected = libraryOnly,
            onClick = { haptic.tick(); onLibraryOnlyChange(!libraryOnly) },
        )
    }
}

@Composable
internal fun MaFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (selected) cs.primary else cs.surfaceContainerHigh,
        label = "ma_filter_pill",
    )
    val content = if (selected) cs.onPrimary else cs.onSurface
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(container)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = content,
            )
        }
    }
}

// Shared result/library row — used by this sheet and the Music page. The sheet's
// edge-to-edge list supplies its own gutter via [horizontalPadding]; containers
// that already pad horizontally pass 0.
@Composable
internal fun MaResultRow(
    item: MaSearchItem,
    confirmed: Boolean,
    onPlayNow: () -> Unit,
    onAddToQueue: () -> Unit,
    horizontalPadding: Dp = 20.dp,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onPlayNow, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MaArtwork(item = item)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle.ifBlank { item.mediaType.singularLabel },
                    fontFamily = MontserratFamily,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            // Queue button (tap-the-row plays now). Flips to a check right after either action.
            Tap(onClick = onAddToQueue) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(PillShape)
                        .background(if (confirmed) cs.primary else cs.onSurface.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (confirmed) Icons.Outlined.Check else Icons.AutoMirrored.Outlined.PlaylistAdd,
                        contentDescription = if (confirmed) "Done" else "Add to queue",
                        tint = if (confirmed) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MaArtwork(item: MaSearchItem) {
    val cs = MaterialTheme.colorScheme
    // Artists read better as circles (faces); everything else keeps album-art corners.
    val shape = if (item.mediaType == MaMediaType.ARTIST) PillShape else MaterialTheme.shapes.small
    if (item.imageUrl != null) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(46.dp)
                .clip(shape)
                .background(cs.surfaceContainerHigh),
        )
    } else {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(shape)
                .background(cs.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.mediaType.fallbackIcon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

internal val MaMediaType.fallbackIcon: ImageVector
    get() = when (this) {
        MaMediaType.ARTIST -> Icons.Outlined.Person
        MaMediaType.ALBUM -> Icons.Outlined.Album
        MaMediaType.TRACK -> Icons.Outlined.MusicNote
        MaMediaType.PLAYLIST -> Icons.AutoMirrored.Outlined.QueueMusic
        MaMediaType.RADIO -> Icons.Outlined.Radio
    }

// Row sublabel when MA sends no artist line — the item's kind, singular.
internal val MaMediaType.singularLabel: String
    get() = when (this) {
        MaMediaType.ARTIST -> "Artist"
        MaMediaType.ALBUM -> "Album"
        MaMediaType.TRACK -> "Track"
        MaMediaType.PLAYLIST -> "Playlist"
        MaMediaType.RADIO -> "Radio"
    }

@Composable
private fun MaCenteredHint(icon: ImageVector, line: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Text(
            text = line,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun MaResultSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(4) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBox(modifier = Modifier.size(46.dp), shape = MaterialTheme.shapes.small)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.width(160.dp).height(14.dp), shape = PillShape)
                    SkeletonBox(modifier = Modifier.width(96.dp).height(10.dp), shape = PillShape)
                }
            }
        }
    }
}
