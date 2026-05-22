package com.uc.homehealth.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaClimate
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape

// Mode → accent palette. Falls back to neutral gray for unknown/idle/off.
private data class ClimateAccent(val color: Color, val label: String, val icon: ImageVector)

private fun accentFor(climate: HaClimate): ClimateAccent {
    // Prefer hvac_action (current behavior) over mode (target intent) for the badge.
    return when (climate.action ?: climate.mode) {
        "heating", "heat" -> ClimateAccent(Color(0xFFF2725C), "HEATING", Icons.Outlined.LocalFireDepartment)
        "cooling", "cool" -> ClimateAccent(Color(0xFF9CB6E8), "COOLING", Icons.Outlined.AcUnit)
        "drying", "dry" -> ClimateAccent(Color(0xFFB8A8E8), "DRYING", Icons.Outlined.WaterDrop)
        "fan", "fan_only" -> ClimateAccent(Color(0xFF7DD3D8), "FAN", Icons.Outlined.Air)
        "off" -> ClimateAccent(Color(0xFF8A8A90), "OFF", Icons.Outlined.PowerSettingsNew)
        "idle" -> ClimateAccent(Color(0xFF8A8A90), "IDLE", Icons.Outlined.LocalFireDepartment)
        else -> ClimateAccent(Color(0xFF8A8A90), (climate.mode).uppercase(), Icons.Outlined.LocalFireDepartment)
    }
}

@Composable
fun ClimateCard(
    climate: HaClimate,
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = accentFor(climate)
    val accentSoft = accent.color.copy(alpha = 0.18f)

    Tap(
        onClick = onTap,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Soft accent orb — top-right radial glow approximating the JSX blur 30 / opacity 18% disc.
            Canvas(modifier = Modifier.matchParentSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.color.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(size.width + 30f, -30f),
                        radius = 220f,
                    ),
                )
            }

            Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                // Header row: icon well + action pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = accent.icon,
                            contentDescription = null,
                            tint = accent.color,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(1.dp).fillMaxSize().padding(1.dp))
                    Box(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(accentSoft)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = accent.label,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = accent.color,
                            letterSpacing = 0.4.sp,
                        )
                    }
                }

                Spacer(Modifier.size(14.dp))

                // Body row: Current / Target
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TempBlock(
                        label = "CURRENT",
                        value = climate.currentTemp?.let { "%.1f°".format(it) } ?: "—",
                        valueColor = cs.onSurface,
                        labelColor = cs.onSurfaceVariant,
                    )
                    Box(modifier = Modifier.weight(1f))
                    TempBlock(
                        label = "TARGET",
                        value = climate.targetTemp?.let { "%.1f°".format(it) } ?: "—",
                        valueColor = accent.color,
                        labelColor = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TempBlock(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color,
) {
    Column {
        Text(
            text = label,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = labelColor,
        )
        Text(
            text = value,
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 44.sp,
            lineHeight = 46.sp,
            color = valueColor,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
