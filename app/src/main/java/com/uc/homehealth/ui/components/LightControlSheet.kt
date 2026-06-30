package com.uc.homehealth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.uc.homehealth.data.HaLight
import dev.chrisbanes.haze.HazeState

/**
 * Bottom-sheet wrapper around the room sheet's expanded [LightTile], opened by entity_id
 * when a glance tile for a light is tapped — so a glance light gets the exact same controls
 * (toggle, brightness, colour, temperature) as inside a room. Keeps a local optimistic copy
 * so the toggle/slider track the finger without waiting for the WS round-trip, and holds the
 * last value so the panel still renders while the sheet slides out.
 */
@Composable
fun LightControlSheetOverlay(
    light: HaLight?,
    onDismiss: () -> Unit,
    onToggle: (entityId: String, isOn: Boolean) -> Unit,
    onBrightnessChange: (entityId: String, brightness: Int) -> Unit,
    onBrightnessChangeFinished: (entityId: String, brightness: Int) -> Unit,
    onColorChange: (entityId: String, r: Int, g: Int, b: Int) -> Unit,
    onColorChangeFinished: (entityId: String, r: Int, g: Int, b: Int) -> Unit,
    onColorTempChange: (entityId: String, kelvin: Int) -> Unit,
    onColorTempChangeFinished: (entityId: String, kelvin: Int) -> Unit,
    hazeState: HazeState? = null,
) {
    var shown by remember { mutableStateOf(light) }
    LaunchedEffect(light) { if (light != null) shown = light }

    AppBottomSheet(visible = light != null, onDismiss = onDismiss, hazeState = hazeState) {
        val l = shown ?: return@AppBottomSheet
        LightTile(
            light = l,
            expanded = true,
            onExpand = {},
            onToggle = {
                val next = !l.isOn
                shown = l.copy(isOn = next, brightness = if (next) (l.brightness.takeIf { it > 0 } ?: 70) else 0)
                onToggle(l.id, next)
            },
            onBrightnessChange = { bri ->
                shown = l.copy(brightness = bri, isOn = bri > 0)
                onBrightnessChange(l.id, bri)
            },
            onBrightnessChangeFinished = { bri ->
                shown = l.copy(brightness = bri, isOn = bri > 0)
                onBrightnessChangeFinished(l.id, bri)
            },
            onColorChange = { r, g, b -> onColorChange(l.id, r, g, b) },
            onColorChangeFinished = { r, g, b -> onColorChangeFinished(l.id, r, g, b) },
            onColorTempChange = { k -> onColorTempChange(l.id, k) },
            onColorTempChangeFinished = { k -> onColorTempChangeFinished(l.id, k) },
            onOpenColorPicker = {},
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
