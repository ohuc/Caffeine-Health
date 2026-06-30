package com.uc.homehealth.ui.screens

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.GlanceTile
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.ui.components.GlanceTilesEditor
import com.uc.homehealth.ui.components.SettingToggleCard
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.glanceDomainLabel
import com.uc.homehealth.ui.components.glanceIconKey
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing

// Domains shown first in the catalog (most glance-worthy on top).
private val DOMAIN_PRIORITY = listOf(
    "sensor", "binary_sensor", "light", "switch", "climate",
    "media_player", "lock", "cover", "fan", "number", "input_number",
    "person", "device_tracker",
)

/**
 * Full-screen editor for the "at a glance" tiles. The top shows the current tiles
 * as a reorderable strip (drag to reorder, tap × to remove — both animated); below,
 * an "Add tiles" catalog grouped by entity type opens a domain-scoped entity picker.
 */
@Composable
fun GlanceEditScreen(
    tiles: List<GlanceTile>,
    allEntities: List<HaEntitySummary>,
    smartGlanceEnabled: Boolean,
    onSmartGlanceChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onRemoveTile: (String) -> Unit,
    onReorderTiles: (List<GlanceTile>) -> Unit,
    onPickCategory: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val categories = remember(allEntities) {
        val byDomain = allEntities.groupBy { it.domain }
        val ordered = DOMAIN_PRIORITY.filter { byDomain.containsKey(it) } +
            byDomain.keys.filterNot { it in DOMAIN_PRIORITY }.sorted()
        ordered.map { it to (byDomain[it]?.size ?: 0) }
    }

    SettingsPageScaffold(
        title = "At a glance",
        subtitle = "Swipe between pages · drag to reorder · tap × to remove",
        onBack = onBack,
        backLabel = "Home",
    ) {
        // ── Smart cards toggle — controls the auto-surfaced alert/insight/delight cards ──
        SettingToggleCard(
            iconKey = "sparkle",
            title = "Smart cards",
            description = "Auto-surface alerts, live activity, and home insights in “At a glance” alongside the tiles below.",
            checked = smartGlanceEnabled,
            onCheckedChange = onSmartGlanceChange,
            modifier = Modifier.padding(start = Spacing.ml, end = Spacing.ml, bottom = 16.dp),
        )

        // ── Current tiles — live preview of the real layout, reorderable + animated ──
        if (tiles.isEmpty()) {
            Text(
                text = "No tiles yet — add some below.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = 8.dp),
            )
        } else {
            GlanceTilesEditor(
                tiles = tiles,
                onReorder = onReorderTiles,
                onRemove = onRemoveTile,
                modifier = Modifier.padding(horizontal = Spacing.ml),
            )
        }

        // ── Add tiles ─────────────────────────────────────────────────────────
        Text(
            text = "Add tiles",
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 26.sp,
            letterSpacing = (-0.4).sp,
            lineHeight = 30.sp,
            color = cs.onBackground,
            modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 28.dp, bottom = 10.dp),
        )

        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { (domain, count) ->
                CategoryRow(
                    domain = domain,
                    count = count,
                    onClick = { onPickCategory(domain) },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    domain: String,
    count: Int,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick) {
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
                    imageVector = haIconFor(glanceIconKey(domain)),
                    contentDescription = null,
                    tint = cs.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = glanceDomainLabel(domain),
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (count == 1) "1 entity" else "$count entities",
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
                    contentDescription = "Add ${glanceDomainLabel(domain)} tile",
                    tint = cs.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
