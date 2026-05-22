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
import androidx.compose.material.icons.outlined.Videocam
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
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState

@Composable
fun CameraEntityPickerSheet(
    visible: Boolean,
    allEntities: List<HaEntitySummary>,
    existingIds: Set<String>,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onPick: (HaEntitySummary) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    LaunchedEffect(visible) { if (!visible) query = "" }

    val pool = remember(allEntities, existingIds) {
        allEntities.filter { it.domain == "camera" && it.entityId !in existingIds }
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
                text = "Pick a camera",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Choose a Home Assistant camera entity",
                fontFamily = InterFamily,
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
            SearchField(value = query, onValueChange = { query = it })
        }

        if (pool.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No camera entities found.",
                    fontFamily = InterFamily,
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
                CameraEntityRow(
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
        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 14.sp, color = cs.onSurface),
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
                            fontFamily = InterFamily,
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
private fun CameraEntityRow(
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
                    imageVector = Icons.Outlined.Videocam,
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
                    fontFamily = InterFamily,
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
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
