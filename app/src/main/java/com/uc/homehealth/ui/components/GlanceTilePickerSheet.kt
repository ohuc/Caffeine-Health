package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

private fun glanceIconKeyForDomain(domain: String): String = when (domain) {
    "light", "switch", "input_boolean" -> "bulb"
    "climate" -> "thermo"
    "lock" -> "lock"
    "media_player" -> "speaker"
    "sensor", "binary_sensor" -> "pulse"
    "fan" -> "energy"
    "cover" -> "door"
    else -> "sparkle"
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlanceTilePickerSheet(
    visible: Boolean,
    allEntities: List<HaEntitySummary>,
    existingIds: Set<String>,
    domain: String? = null,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onPick: (HaEntitySummary) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    LaunchedEffect(visible) { if (!visible) query = "" }

    // Keep the last opened domain so the list + title don't visibly change to the
    // unfiltered/"null" state while the sheet is animating closed.
    var lastDomain by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(domain) { if (domain != null) lastDomain = domain }
    val activeDomain = domain ?: lastDomain

    val pool = remember(allEntities, existingIds, activeDomain) {
        allEntities.filter { it.entityId !in existingIds && (activeDomain == null || it.domain == activeDomain) }
    }
    val filtered = remember(pool, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) pool
        else pool.filter { it.friendlyName.lowercase().contains(q) || it.entityId.lowercase().contains(q) }
    }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.92f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Add tile",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = activeDomain?.let { "Choose from ${glanceDomainLabel(it)}" }
                    ?: "Pick a Home Assistant entity to glance at",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            GlanceSearchField(
                value = query,
                onValueChange = { query = it },
            )
        }

        if (pool.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Connect to Home Assistant to load your entities.",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            return@AppBottomSheet
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.entityId }) { entity ->
                GlanceEntityRow(
                    entity = entity,
                    onPick = { onPick(entity) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun GlanceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 14.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
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
                Box(modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Search by name or entity id",
                            fontFamily = MontserratFamily,
                            fontSize = 14.sp,
                            color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 4.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun GlanceEntityRow(
    entity: HaEntitySummary,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onPick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surface, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(cs.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor(glanceIconKeyForDomain(entity.domain)),
                    contentDescription = null,
                    tint = cs.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = entity.friendlyName,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = "${entity.entityId} · ${entity.areaName}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = entity.state,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
