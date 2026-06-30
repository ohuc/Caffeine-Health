package com.uc.homehealth.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.HaNotification
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.ActivityUiState
import com.uc.homehealth.ui.viewmodel.ActivityViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val filters = listOf("All", "Lights", "Climate", "Scenes", "Other")

private fun matchesFilter(kind: String, filter: String): Boolean = when (filter) {
    "All" -> true
    "Lights" -> kind == "light"
    "Climate" -> kind == "climate"
    "Scenes" -> kind == "scene"
    "Other" -> kind !in setOf("light", "climate", "scene")
    else -> true
}

// kind → (accent, icon key). Accents come from the theme (CustomColors / primary) so the
// icon tint and its wash stay legible in both light and dark themes.
@Composable
private fun kindStyle(kind: String): Pair<Color, String> {
    val custom = MaterialTheme.customColors
    return when (kind) {
        "light"   -> custom.sand to "bulb"
        "scene"   -> custom.lavender to "sparkle"
        "climate" -> custom.coral to "thermo"
        "motion"  -> MaterialTheme.colorScheme.primary to "pulse"
        "door"    -> custom.sky to "door"
        "energy"  -> custom.sand to "energy"
        "auto"    -> custom.lavender to "sparkle"
        "update"  -> custom.mint to "settings"
        "media"   -> custom.cyan to "speaker"
        else      -> MaterialTheme.colorScheme.primary to "pulse"
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("MMM d")
private val zone: ZoneId = ZoneId.systemDefault()

private fun relativeTime(ts: Long, now: Long): String {
    val d = now - ts
    return when {
        d < 60_000L -> "just now"
        d < 3_600_000L -> "${d / 60_000L}m ago"
        d < 24 * 3_600_000L -> "${d / 3_600_000L}h ago"
        d < 48 * 3_600_000L -> "Yesterday"
        d < 7 * 24 * 3_600_000L -> "${d / (24 * 3_600_000L)}d ago"
        else -> Instant.ofEpochMilli(ts).atZone(zone).format(dateFormatter)
    }
}

private fun bucketFor(ts: Long, now: Long): String {
    if (now - ts < 5 * 60_000L) return "Just now"
    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(ts).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
    )
    return when {
        days == 0L -> "Earlier today"
        days == 1L -> "Yesterday"
        days < 7L -> "This week"
        else -> "Older"
    }
}

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    ActivityScreenContent(
        uiState = uiState,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ActivityScreenContent(
    uiState: ActivityUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onDelete: (Long) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    var activeFilter by remember { mutableStateOf("All") }
    val now = remember(uiState.notifications) { System.currentTimeMillis() }

    val filtered = uiState.notifications.filter { matchesFilter(it.kind, activeFilter) }
    val groups: List<Pair<String, List<HaNotification>>> = remember(filtered, now) {
        filtered
            .sortedByDescending { it.timestamp }
            .groupBy { bucketFor(it.timestamp, now) }
            .toList()
    }

    val last24h = uiState.notifications.count { now - it.timestamp < 24 * 3_600_000L }
    val subtitle = when {
        uiState.notifications.isEmpty() -> "No actions yet"
        else -> "$last24h ${if (last24h == 1) "event" else "events"} in the last 24 hours"
    }

    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxWidth(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
    ) {
        // Header
        Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 14.dp)) {
            Text(
                text = "Activity",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 40.sp,
                color = cs.onBackground,
            )
            Text(
                text = subtitle,
                fontFamily = MontserratFamily,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Filter chips — connected M3 Expressive button group
        @OptIn(ExperimentalMaterial3ExpressiveApi::class)
        ButtonGroup(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ml)
                .padding(bottom = 14.dp),
        ) {
            filters.forEachIndexed { index, filter ->
                ToggleButton(
                    checked = filter == activeFilter,
                    onCheckedChange = { checked ->
                        if (checked && filter != activeFilter) {
                            haptics.toggle(true)
                            activeFilter = filter
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        text = filter,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            EmptyActivity(
                title = if (uiState.notifications.isEmpty()) "Nothing here yet" else "No matches",
                subtitle = if (uiState.notifications.isEmpty())
                    "Actions you take in this app will show up here."
                else
                    "Try a different filter.",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = Spacing.ml,
                end = Spacing.ml,
                bottom = 130.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            groups.forEach { (label, items) ->
                item(key = "h-$label") { GroupHeader(label, Modifier.animateItem()) }
                itemsIndexed(items, key = { _, n -> n.id }) { index, n ->
                    SwipeableNotificationRow(
                        n = n,
                        now = now,
                        shape = notificationRowShape(index, items.size),
                        onDelete = onDelete,
                        // Spring the rows below up to close the gap when one is removed —
                        // the same list-removal motion Gmail / Google Messages use.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun GroupHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        fontFamily = MontserratFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 8.dp, horizontal = 6.dp),
    )
}

// Material 3 Expressive grouped-list shape: rows in the same time bucket read as a
// single container — large corners on the group's outer edges, small corners where
// rows meet (see m3.material.io/components/lists, expressive grouped lists).
private val GroupCornerLarge = 22.dp
private val GroupCornerSmall = 6.dp

private fun notificationRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(GroupCornerLarge)
    index == 0 -> RoundedCornerShape(
        topStart = GroupCornerLarge, topEnd = GroupCornerLarge,
        bottomStart = GroupCornerSmall, bottomEnd = GroupCornerSmall,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = GroupCornerSmall, topEnd = GroupCornerSmall,
        bottomStart = GroupCornerLarge, bottomEnd = GroupCornerLarge,
    )
    else -> RoundedCornerShape(GroupCornerSmall)
}

// Slide-to-remove (Material 3 SwipeToDismissBox). Either direction deletes — swipe the
// card off whichever way feels natural — and the library's default 56.dp positional
// threshold + fling physics mean a moderate swipe-and-release auto-completes the dismiss
// (the same feel as Gmail / Google Messages). The reveal behind the row is a flat
// errorContainer panel with the trash icon pinned to the side you're swiping toward,
// clipped to the row's own grouped-list shape so the rounded corners stay consistent.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNotificationRow(
    n: HaNotification,
    now: Long,
    shape: Shape,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberAppHaptics()
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        onDismiss = { direction ->
            if (direction != SwipeToDismissBoxValue.Settled) {
                haptics.confirm()
                onDelete(n.id)
            }
        },
        backgroundContent = { SwipeDeleteBackground(state, shape) },
    ) {
        NotificationRow(n, now, shape)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteBackground(state: SwipeToDismissBoxState, shape: Shape) {
    val cs = MaterialTheme.colorScheme
    val direction = state.dismissDirection
    val alignment = if (direction == SwipeToDismissBoxValue.StartToEnd) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }
    // Icon grows in as the drag picks up so the action reads before release.
    val iconScale by animateFloatAsState(
        targetValue = if (direction == SwipeToDismissBoxValue.Settled) 0.6f else 1f,
        label = "swipeTrashScale",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(cs.errorContainer)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment,
    ) {
        Icon(
            imageVector = haIconFor("trash"),
            contentDescription = "Delete",
            tint = cs.onErrorContainer,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
    }
}

@Composable
private fun NotificationRow(n: HaNotification, now: Long, shape: Shape) {
    val cs = MaterialTheme.colorScheme
    val (accent, iconKey) = kindStyle(n.kind)

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
                imageVector = haIconFor(iconKey),
                contentDescription = n.kind,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = n.title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = (-0.1).sp,
                color = cs.onSurface,
            )
            Text(
                text = n.body,
                fontFamily = MontserratFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // One time representation per row — the group header already carries the bucket.
        Text(
            text = relativeTime(n.timestamp, now),
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyActivity(title: String, subtitle: String) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ml, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = haIconFor("activity"),
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = title,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = cs.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
