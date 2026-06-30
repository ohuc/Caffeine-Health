package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Thermostat
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.data.RoomSensorOverride
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState

private enum class EditorView { Main, PickTemp, PickHumidity }

@Composable
fun RoomSensorEditorSheet(
    room: HaRoom?,
    override: RoomSensorOverride?,
    allEntities: List<HaEntitySummary>,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onSetTempSensor: (String) -> Unit,
    onSetHumiditySensor: (String) -> Unit,
) {
    val visible = room != null
    var view by remember(room?.id) { mutableStateOf(EditorView.Main) }
    var query by remember(room?.id) { mutableStateOf("") }
    LaunchedEffect(view) { if (view == EditorView.Main) query = "" }

    val handleDismiss: () -> Unit = {
        if (view != EditorView.Main) view = EditorView.Main else onDismiss()
    }

    AppBottomSheet(
        visible = visible,
        onDismiss = handleDismiss,
        hazeState = hazeState,
        maxHeightFraction = 0.92f,
    ) {
        AnimatedContent(
            targetState = view,
            transitionSpec = {
                val forward = targetState != EditorView.Main
                if (forward) {
                    (slideInHorizontally(tween(260)) { it } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(160)))
                } else {
                    (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(260)) { it } + fadeOut(tween(160)))
                }
            },
            label = "room_sensor_editor_view",
        ) { current ->
            when (current) {
                EditorView.Main -> MainView(
                    room = room,
                    override = override,
                    allEntities = allEntities,
                    onPickTemp = { view = EditorView.PickTemp },
                    onPickHumidity = { view = EditorView.PickHumidity },
                )
                EditorView.PickTemp -> SensorPickerView(
                    title = "Pick temperature sensor",
                    subtitle = "Numeric sensors only",
                    allEntities = allEntities,
                    query = query,
                    onQueryChange = { query = it },
                    onBack = { view = EditorView.Main },
                    onPick = { entity ->
                        onSetTempSensor(entity.entityId)
                        view = EditorView.Main
                    },
                )
                EditorView.PickHumidity -> SensorPickerView(
                    title = "Pick humidity sensor",
                    subtitle = "Numeric sensors only",
                    allEntities = allEntities,
                    query = query,
                    onQueryChange = { query = it },
                    onBack = { view = EditorView.Main },
                    onPick = { entity ->
                        onSetHumiditySensor(entity.entityId)
                        view = EditorView.Main
                    },
                )
            }
        }
    }
}

@Composable
private fun MainView(
    room: HaRoom?,
    override: RoomSensorOverride?,
    allEntities: List<HaEntitySummary>,
    onPickTemp: () -> Unit,
    onPickHumidity: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val byId = remember(allEntities) { allEntities.associateBy { it.entityId } }
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 12.dp),
        ) {
            Text(
                text = "Edit sensors",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = room?.let { "Pick the sensors used for ${it.name}." }
                    ?: "Pick the sensors used for this room.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SensorSlot(
            icon = Icons.Outlined.Thermostat,
            label = "Temperature sensor",
            entityId = override?.tempEntityId.orEmpty(),
            entity = override?.tempEntityId?.let { byId[it] },
            onPick = onPickTemp,
        )
        Spacer(Modifier.height(10.dp))
        SensorSlot(
            icon = Icons.Outlined.Opacity,
            label = "Humidity sensor",
            entityId = override?.humidityEntityId.orEmpty(),
            entity = override?.humidityEntityId?.let { byId[it] },
            onPick = onPickHumidity,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SensorSlot(
    icon: ImageVector,
    label: String,
    entityId: String,
    entity: HaEntitySummary?,
    onPick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onPick, modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(cs.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
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
                    text = label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    letterSpacing = 0.5.sp,
                )
                val title = when {
                    entity != null -> entity.friendlyName
                    entityId.isNotBlank() -> entityId
                    else -> "Not set · auto-detect"
                }
                Text(
                    text = title,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (entity != null) {
                    Text(
                        text = entity.entityId,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(cs.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "Edit",
                    tint = cs.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SensorPickerView(
    title: String,
    subtitle: String,
    allEntities: List<HaEntitySummary>,
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onPick: (HaEntitySummary) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val pool = remember(allEntities) {
        allEntities.filter { it.domain == "sensor" && it.state.toFloatOrNull() != null }
    }
    val filtered = remember(pool, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) pool
        else pool.filter { it.friendlyName.lowercase().contains(q) || it.entityId.lowercase().contains(q) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tap(onClick = onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceContainerHigh, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SearchField(value = query, onValueChange = onQueryChange)
        }

        if (pool.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No numeric sensor entities found.",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.entityId }) { entity ->
                SensorRow(
                    entity = entity,
                    onPick = { onPick(entity) },
                    modifier = Modifier.animateItem(),
                )
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
                    Tap(onClick = { onValueChange("") }) {
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
            }
        },
    )
}

@Composable
private fun SensorRow(
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
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
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
