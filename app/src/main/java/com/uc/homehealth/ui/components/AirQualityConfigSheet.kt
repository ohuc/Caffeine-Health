package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState

private enum class AqSlot(val label: String, val hint: String) {
    BASE("PM2.5 sensor", "Required · the µg/m³ reading"),
    CLEAN("Clean duration", "Optional"),
    MODERATE("Moderate duration", "Optional"),
    POOR("Poor duration", "Optional"),
}

// Configures an air-quality widget: pick the base PM2.5 sensor and the three
// clean/moderate/poor duration sensors. Picking the base auto-suggests the durations by
// the VINDRIKTNING naming convention (sensor.<base>_clean_air_duration, …) when present.
@Composable
fun AirQualityConfigSheet(
    visible: Boolean,
    allEntities: List<HaEntitySummary>,
    hazeState: HazeState? = null,
    onAdd: (base: String, clean: String, moderate: String, poor: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    var base by remember { mutableStateOf("") }
    var clean by remember { mutableStateOf("") }
    var moderate by remember { mutableStateOf("") }
    var poor by remember { mutableStateOf("") }
    var activeSlot by remember { mutableStateOf(AqSlot.BASE) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            base = ""; clean = ""; moderate = ""; poor = ""; activeSlot = AqSlot.BASE; query = ""
        }
    }

    val sensors = remember(allEntities) { allEntities.filter { it.domain == "sensor" } }
    val nameOf = remember(allEntities) { allEntities.associate { it.entityId to it.friendlyName } }
    val filtered = remember(sensors, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) sensors
        else sensors.filter { it.friendlyName.lowercase().contains(q) || it.entityId.lowercase().contains(q) }
    }

    fun selectedFor(slot: AqSlot) = when (slot) {
        AqSlot.BASE -> base; AqSlot.CLEAN -> clean; AqSlot.MODERATE -> moderate; AqSlot.POOR -> poor
    }

    fun firstEmptySlot(): AqSlot =
        AqSlot.entries.firstOrNull { selectedFor(it).isBlank() } ?: AqSlot.BASE

    fun assign(entityId: String) {
        when (activeSlot) {
            AqSlot.BASE -> {
                base = entityId
                // Auto-suggest the three duration sensors from the VINDRIKTNING naming.
                val obj = entityId.substringAfter('.')
                fun derive(suffix: String) =
                    sensors.firstOrNull { it.entityId == "sensor.${obj}_$suffix" }?.entityId ?: ""
                if (clean.isBlank()) clean = derive("clean_air_duration")
                if (moderate.isBlank()) moderate = derive("moderate_air_duration")
                if (poor.isBlank()) poor = derive("poor_air_duration")
            }
            AqSlot.CLEAN -> clean = entityId
            AqSlot.MODERATE -> moderate = entityId
            AqSlot.POOR -> poor = entityId
        }
        query = ""
        activeSlot = firstEmptySlot()
    }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.92f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Air quality",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Pick the PM2.5 sensor — its clean/moderate/poor durations are filled in automatically when found",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Slot chips — tap to choose which entity the list assigns to.
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AqSlot.entries.forEach { slot ->
                SlotRow(
                    label = slot.label,
                    hint = slot.hint,
                    value = selectedFor(slot).takeIf { it.isNotBlank() }?.let { nameOf[it] ?: it },
                    active = slot == activeSlot,
                    onClick = { activeSlot = slot },
                )
            }
        }

        Spacer(Modifier.size(10.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SearchField(value = query, onValueChange = { query = it })
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(filtered, key = { it.entityId }) { entity ->
                SensorRow(
                    entity = entity,
                    selected = entity.entityId == selectedFor(activeSlot),
                    onClick = { assign(entity.entityId) },
                    modifier = Modifier.animateItem(),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            AddButton(enabled = base.isNotBlank(), onClick = { onAdd(base, clean, moderate, poor) })
        }
    }
}

@Composable
private fun SlotRow(
    label: String,
    hint: String,
    value: String?,
    active: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (active) cs.primary.copy(alpha = 0.16f) else cs.surfaceContainerHigh
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface)
                Text(
                    text = value ?: hint,
                    fontFamily = MontserratFamily,
                    fontWeight = if (value != null) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 11.sp,
                    color = if (value != null) cs.primary else cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (value != null) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SensorRow(
    entity: HaEntitySummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.primary.copy(alpha = 0.16f) else cs.surface
    Tap(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entity.friendlyName, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface)
                Text(
                    text = "${entity.entityId} · ${entity.state}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            if (selected) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 14.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    if (value.isEmpty()) {
                        Text("Search sensors", fontFamily = MontserratFamily, fontSize = 14.sp, color = cs.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    inner()
                }
            }
        },
    )
}

@Composable
private fun AddButton(enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container = if (enabled) cs.primary else cs.surfaceVariant
    val content = if (enabled) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.6f)
    Tap(onClick = { if (enabled) onClick() }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, RoundedCornerShape(18.dp))
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add widget",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = content,
            )
        }
    }
}
