package com.uc.homehealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.HaNotification
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.Spacing
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

// kind → (bg alpha color, icon tint color, icon key)
private fun kindStyle(kind: String): Triple<Color, Color, String> = when (kind) {
    "light"   -> Triple(Color(0x23FFD9A8), Color(0xFFFFD9A8), "bulb")
    "scene"   -> Triple(Color(0x23B8A8E8), Color(0xFFB8A8E8), "sparkle")
    "climate" -> Triple(Color(0x23F2725C), Color(0xFFF2725C), "thermo")
    "motion"  -> Triple(Color(0x23E8B4D6), Color(0xFFE8B4D6), "pulse")
    "door"    -> Triple(Color(0x239CB6E8), Color(0xFF9CB6E8), "door")
    "energy"  -> Triple(Color(0x23E8C99B), Color(0xFFE8C99B), "energy")
    "auto"    -> Triple(Color(0x23B8A8E8), Color(0xFFB8A8E8), "sparkle")
    "update"  -> Triple(Color(0x239DD8A8), Color(0xFF9DD8A8), "settings")
    "media"   -> Triple(Color(0x237DD3D8), Color(0xFF7DD3D8), "speaker")
    else      -> Triple(Color(0x23E8B4D6), Color(0xFFE8B4D6), "pulse")
}

private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
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

private fun clockTime(ts: Long): String =
    Instant.ofEpochMilli(ts).atZone(zone).format(timeFormatter)

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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActivityScreenContent(
    uiState: ActivityUiState,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
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

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth(),
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(bottom = 130.dp),
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
                fontFamily = InterFamily,
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
                    onCheckedChange = { if (it) activeFilter = filter },
                    modifier = Modifier.weight(1f),
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        filters.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    Text(
                        text = filter,
                        fontFamily = InterFamily,
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
            contentPadding = PaddingValues(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groups.forEach { (label, items) ->
                item(key = "h-$label") { GroupHeader(label) }
                items(items, key = { it.id }) { n -> NotificationRow(n, now) }
            }
        }
    }
    }
}

@Composable
private fun GroupHeader(label: String) {
    Text(
        text = label.uppercase(),
        fontFamily = InterFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
    )
}

@Composable
private fun NotificationRow(n: HaNotification, now: Long) {
    val cs = MaterialTheme.colorScheme
    val (bgColor, iconColor, iconKey) = kindStyle(n.kind)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(bgColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = haIconFor(iconKey),
                contentDescription = n.kind,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = n.title,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = (-0.1).sp,
                color = cs.onSurface,
            )
            Text(
                text = n.body,
                fontFamily = InterFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = relativeTime(n.timestamp, now),
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = clockTime(n.timestamp),
                fontFamily = InterFamily,
                fontSize = 10.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
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
                .background(cs.surfaceContainerHigh, RoundedCornerShape(20.dp)),
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
            fontFamily = InterFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = cs.onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle,
            fontFamily = InterFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
