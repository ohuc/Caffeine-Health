package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.GlanceConfig
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

// Bottom sheet that hosts the "At a glance" configuration form previously embedded
// directly inside SettingsScreen. Opens via a button on the settings page; matches
// the room sheet visual language (rounded top, drag-to-dismiss, scrim).
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlanceConfigSheet(
    visible: Boolean,
    glance: GlanceConfig,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onOutsideTempChange: (String) -> Unit,
    onInsideTempChange: (String) -> Unit,
    onDoorbellChange: (String) -> Unit,
    onLightsOnChange: (String) -> Unit,
    onAqiChange: (String) -> Unit,
    onTemplateChange: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                text = "At a glance",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Plug in the Home Assistant entity IDs used in the dashboard greeting.",
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EntitySlot(
                label = "Outside temperature · {outside_temp}",
                value = glance.entityOutsideTemp,
                onChange = onOutsideTempChange,
                placeholder = "sensor.outside_temperature",
            )
            EntitySlot(
                label = "Inside temperature · {inside_temp}",
                value = glance.entityInsideTemp,
                onChange = onInsideTempChange,
                placeholder = "sensor.indoor_temperature",
            )
            EntitySlot(
                label = "Doorbell count · {doorbell}",
                value = glance.entityDoorbell,
                onChange = onDoorbellChange,
                placeholder = "sensor.doorbell_count",
            )
            EntitySlot(
                label = "Lights on · {lights_on}",
                value = glance.entityLightsOn,
                onChange = onLightsOnChange,
                placeholder = "sensor.lights_on_count",
            )
            EntitySlot(
                label = "Air quality · {aqi}",
                value = glance.entityAqi,
                onChange = onAqiChange,
                placeholder = "sensor.aqi",
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "GLANCE PHRASE",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                )
                SheetTextField(
                    value = glance.template,
                    onValueChange = onTemplateChange,
                    placeholder = GlanceConfig.DEFAULT_TEMPLATE,
                    singleLine = false,
                    minHeight = 90.dp,
                )
                Text(
                    text = "Available placeholders: {outside_temp} {inside_temp} {doorbell} {lights_on} {aqi}",
                    fontFamily = InterFamily,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun EntitySlot(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = cs.onSurfaceVariant,
        )
        SheetTextField(value = value, onValueChange = onChange, placeholder = placeholder)
    }
}

@Composable
private fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
    minHeight: Dp = 0.dp,
) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        keyboardActions = KeyboardActions.Default,
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (minHeight > 0.dp) Modifier.heightIn(min = minHeight) else Modifier)
                    .background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        fontFamily = InterFamily,
                        fontSize = 13.sp,
                        color = cs.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                inner()
            }
        },
    )
}
