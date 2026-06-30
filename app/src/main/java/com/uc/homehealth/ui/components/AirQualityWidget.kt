package com.uc.homehealth.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.AirQualityBand
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily

// VINDRIKTNING traffic-light colours, tuned to read on the dark surface.
private val AqCleanColor = Color(0xFF5BCB7C)     // green  · ≤35 µg/m³
private val AqModerateColor = Color(0xFFF2B33D)  // amber  · 36–85
private val AqPoorColor = Color(0xFFF2725C)      // red    · ≥86

@Composable
private fun bandColor(band: AirQualityBand): Color = when (band) {
    AirQualityBand.CLEAN -> AqCleanColor
    AirQualityBand.MODERATE -> AqModerateColor
    AirQualityBand.POOR -> AqPoorColor
    AirQualityBand.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
}

/**
 * Air-quality "art piece": a slowly-rotating Material expressive blob holding the live
 * PM2.5 reading (coloured by the IKEA VINDRIKTNING band), above a proportional
 * "time in each band" bar built from the clean / moderate / poor duration sensors.
 * Purely informational — removal/drag are handled by the room sheet's widget column.
 */
@Composable
fun AirQualityWidget(
    baseState: HaEntityValue?,
    cleanState: HaEntityValue?,
    moderateState: HaEntityValue?,
    poorState: HaEntityValue?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val pm = baseState?.state?.toFloatOrNull()
    val band = AirQualityBand.fromPm25(pm)
    val color = bandColor(band)
    val unit = baseState?.unit?.takeIf { it.isNotBlank() } ?: "µg/m³"
    val name = baseState?.friendlyName?.takeIf { it.isNotBlank() } ?: "Air quality"
    val hasDurations = cleanState != null || moderateState != null || poorState != null

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                BlobHero(pm = pm, color = color)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name.uppercase(),
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = band.label,
                        fontFamily = InstrumentSerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 34.sp,
                        lineHeight = 36.sp,
                        color = color,
                    )
                    Text(
                        text = if (pm != null) "PM2.5 · ${fmt(pm)} $unit" else "Sensor unavailable",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (hasDurations) TimeSection(cleanState, moderateState, poorState)
        }
    }
}

@Composable
private fun BlobHero(pm: Float?, color: Color) {
    val shape = rememberTileBlobShape()
    // Slow continuous spin gives the expressive shape a living, art-object feel without
    // distracting motion. The reading sits still on top.
    val transition = rememberInfiniteTransition(label = "aq_blob")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26_000, easing = LinearEasing)),
        label = "aq_rotation",
    )
    Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle }
                .clip(shape)
                .background(color),
        )
        Text(
            text = pm?.let { fmt(it) } ?: "—",
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 40.sp,
            color = glanceInkOn(color),
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeSection(
    cleanState: HaEntityValue?,
    moderateState: HaEntityValue?,
    poorState: HaEntityValue?,
) {
    val cs = MaterialTheme.colorScheme
    val clean = parseDuration(cleanState)
    val moderate = parseDuration(moderateState)
    val poor = parseDuration(poorState)
    val total = clean + moderate + poor

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "TIME IN EACH BAND",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            color = cs.onSurfaceVariant,
        )

        // Proportional stacked bar with rounded segment ends (M3 expressive active-track).
        Row(
            modifier = Modifier.fillMaxWidth().height(16.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (total <= 0f) {
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(cs.surfaceVariant))
            } else {
                if (clean > 0f) Box(Modifier.weight(clean).fillMaxHeight().clip(CircleShape).background(AqCleanColor))
                if (moderate > 0f) Box(Modifier.weight(moderate).fillMaxHeight().clip(CircleShape).background(AqModerateColor))
                if (poor > 0f) Box(Modifier.weight(poor).fillMaxHeight().clip(CircleShape).background(AqPoorColor))
            }
        }

        LegendRow(AqCleanColor, "Clean", durationLabel(cleanState))
        LegendRow(AqModerateColor, "Moderate", durationLabel(moderateState))
        LegendRow(AqPoorColor, "Poor", durationLabel(poorState))
    }
}

@Composable
private fun LegendRow(color: Color, name: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(10.dp))
        Text(
            text = name,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = cs.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            fontFamily = InstrumentSerifFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 17.sp,
            color = cs.onSurface,
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private val AQ_NULL_STATES = setOf("unavailable", "unknown", "none", "nan", "")

private fun fmt(f: Float): String =
    if (f == f.toInt().toFloat()) f.toInt().toString() else "%.1f".format(f)

// Numeric magnitude of a duration sensor, used for the bar proportions. Handles a plain
// number ("2.5") or a clock string ("1:23:45" → seconds). Returns 0 when unparseable.
private fun parseDuration(v: HaEntityValue?): Float {
    val s = v?.state?.trim() ?: return 0f
    if (s.lowercase() in AQ_NULL_STATES) return 0f
    s.toFloatOrNull()?.let { return it }
    val parts = s.split(':').mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600f + parts[1] * 60f + parts[2]
        2 -> parts[0] * 60f + parts[1]
        1 -> parts[0].toFloat()
        else -> 0f
    }
}

// Human label for the legend: "<value> <unit>" when numeric, the raw string otherwise.
private fun durationLabel(v: HaEntityValue?): String {
    val raw = v?.state?.trim() ?: return "—"
    if (raw.lowercase() in AQ_NULL_STATES) return "—"
    val unit = v.unit?.takeIf { it.isNotBlank() }
    val num = raw.toFloatOrNull()
    return when {
        num != null && unit != null -> "${fmt(num)} $unit"
        num != null -> fmt(num)
        else -> raw
    }
}
