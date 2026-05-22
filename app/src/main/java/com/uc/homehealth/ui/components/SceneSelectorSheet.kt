package com.uc.homehealth.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaScene
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// Bottom sheet listing every HA scene as a SceneTile; tap to add to quick scenes.
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun SceneSelectorSheet(
    visible: Boolean,
    allScenes: List<HaScene>,
    existingIds: Set<String>,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onPick: (HaScene) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val available = allScenes.filter { it.id !in existingIds }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 8.dp),
        ) {
            Text(
                text = "Add quick scene",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = if (available.isEmpty()) "No scenes available" else "${available.size} scene${if (available.size == 1) "" else "s"} from Home Assistant",
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (available.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Connect to Home Assistant to load your scenes.",
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(available, key = { it.id }) { scene ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SceneTile(scene = scene, onTap = { onPick(scene) })
                }
            }
        }
    }
}
