package com.uc.homehealth.ui.components.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.uc.homehealth.ui.components.RollingNumberText
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HomeCoords
import com.uc.homehealth.data.SolarForecast
import com.uc.homehealth.energy.DaySample
import com.uc.homehealth.energy.Solar
import com.uc.homehealth.energy.SolarSeries
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

private data class SundialModel(
    val samples: List<DaySample>,
    val peakRing: Float,
    val useProduction: Boolean,
    val sunriseHour: Float?,
    val sunsetHour: Float?,
    val nowHour: Float,
)

/**
 * Map an hour-of-day (0..24) to screen angle. **Noon at the top** (as on the Helios dial),
 * midnight at the bottom, so the daylight production fan spans the upper half.
 */
private fun hourDegrees(hour: Float): Float = -90f + ((hour - 12f + 24f) % 24f) / 24f * 360f
private fun hourAngle(hour: Float): Double = Math.toRadians(hourDegrees(hour).toDouble())

/**
 * A 24-hour annular "sundial" for the selected day: an amber production ring whose radius
 * tracks hourly PV output, hour ticks, a sunrise→sunset daylight arc, and a live time hand,
 * with the headline metric in the hub. The signature Helios instrument, in Compose Canvas.
 */
@Composable
fun RadialSundial(
    home: HomeCoords?,
    instant: Instant,
    forecast: SolarForecast?,
    peakKwp: Float,
    liveProductionKw: Float?,
    modifier: Modifier = Modifier,
    // True in the home-detail sheet: adds the compass-style numbered hour ring (the page's
    // inline dial stays clean without it).
    showHourNumerals: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()
    // Dial chrome (ticks, numerals, hand, hub) keys off the theme so the instrument stays
    // legible on a light sheet in light theme; only the sun itself stays gold.
    val cs = MaterialTheme.colorScheme
    val ink = cs.onSurface
    val hubColor = cs.surfaceContainerLowest
    val minuteBucket = instant.epochSecond / 60
    val model = remember(home, forecast, peakKwp, minuteBucket) {
        buildSundial(home, instant, forecast, peakKwp)
    }

    // Headline metric: live production if we have it, else today's predicted total.
    val todayKwh = remember(model) { model.samples.fold(0f) { a, s -> a + s.productionKw } }
    val (metricValue, metricUnit) = if (liveProductionKw != null) powerValueUnit(liveProductionKw) else kwhValueUnit(todayKwh)
    val metricLabel = if (liveProductionKw != null) "now" else "today"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = size.minDimension / 2f - 6.dp.toPx()
            // With the numeral ring the ticks and production bars pull inward to make room.
            val tickR = outerR - (if (showHourNumerals) 26.dp.toPx() else 8.dp.toPx())
            val ringInner = outerR * 0.40f
            val ringMax = outerR * (if (showHourNumerals) 0.72f else 0.86f)
            val center = Offset(cx, cy)

            if (showHourNumerals) {
                for (h in 0 until 24 step 3) {
                    val a = hourAngle(h.toFloat())
                    val r = outerR - 13.dp.toPx()
                    val layout = textMeasurer.measure(
                        AnnotatedString(h.toString()),
                        TextStyle(fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    )
                    drawText(
                        textLayoutResult = layout,
                        color = cs.onSurfaceVariant,
                        topLeft = Offset(
                            cx + r * cos(a).toFloat() - layout.size.width / 2f,
                            cy + r * sin(a).toFloat() - layout.size.height / 2f,
                        ),
                    )
                }
            }

            // Daylight arc (sunrise → sunset) on the rim; full faint ring underneath.
            drawCircle(ink.copy(alpha = 0.08f), radius = outerR, center = center, style = Stroke(width = 2.dp.toPx()))
            if (model.sunriseHour != null && model.sunsetHour != null) {
                val start = hourDegrees(model.sunriseHour)
                val sweep = (model.sunsetHour - model.sunriseHour) / 24f * 360f
                drawArc(
                    color = SunGold.copy(alpha = 0.55f),
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - outerR, cy - outerR),
                    size = Size(outerR * 2, outerR * 2),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Hour ticks (every hour; longer at the quarters).
            for (h in 0 until 24) {
                val a = hourAngle(h.toFloat())
                val major = h % 6 == 0
                val r1 = tickR - if (major) 9.dp.toPx() else 4.dp.toPx()
                drawLine(
                    color = ink.copy(alpha = if (major) 0.45f else 0.18f),
                    start = Offset(cx + (r1) * cos(a).toFloat(), cy + (r1) * sin(a).toFloat()),
                    end = Offset(cx + tickR * cos(a).toFloat(), cy + tickR * sin(a).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                )
            }

            // Production as a radial sunburst: one bar per hour, length ∝ output. Robust and
            // legible (no self-intersecting blob), and reads as a sun's rays around the clock.
            if (model.samples.isNotEmpty() && model.peakRing > 0f) {
                val midR = (ringInner + ringMax) / 2f
                val barWidth = (2.0 * Math.PI * midR / 24.0).toFloat() * 0.62f
                model.samples.forEach { s ->
                    val v = ((if (model.useProduction) s.productionKw else s.ghiWm2 / 1000f) / model.peakRing).coerceIn(0f, 1f)
                    if (v <= 0.02f) return@forEach
                    val a = hourAngle(s.hourOfDay)
                    val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
                    val r1 = ringInner + v * (ringMax - ringInner)
                    drawLine(
                        color = androidx.compose.ui.graphics.lerp(SunAmber.copy(alpha = 0.55f), SunGold, v),
                        start = Offset(cx + ringInner * ca, cy + ringInner * sa),
                        end = Offset(cx + r1 * ca, cy + r1 * sa),
                        strokeWidth = barWidth.coerceAtLeast(2f),
                        cap = StrokeCap.Round,
                    )
                }
            }

            // Now hand + tip dot.
            val na = hourAngle(model.nowHour)
            val handR = ringMax
            drawLine(
                color = ink.copy(alpha = 0.9f),
                start = center,
                end = Offset(cx + handR * cos(na).toFloat(), cy + handR * sin(na).toFloat()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(ink, radius = 3.dp.toPx(), center = Offset(cx + handR * cos(na).toFloat(), cy + handR * sin(na).toFloat()))

            // Hub: a solid theme disc so the metric stays legible, ringed by a thin sun line
            // + the sanctioned irradiance glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(0f to SunAmber.copy(alpha = 0.45f), 1f to Color.Transparent),
                    center = center,
                    radius = ringInner,
                ),
                radius = ringInner,
                center = center,
            )
            drawCircle(hubColor, radius = ringInner * 0.78f, center = center)
            drawCircle(SunGold.copy(alpha = 0.5f), radius = ringInner * 0.78f, center = center, style = Stroke(width = 1.5.dp.toPx()))
        }

        // Hub metric — rolling digits, same treatment as the room/glance cards.
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RollingNumberText(
                text = metricValue,
                style = TextStyle(
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 34.sp,
                    // Highest-emphasis role for the hero metric — gold text fails contrast
                    // on the light hub in light theme; the gold lives in the ring instead.
                    color = ink,
                ),
                labelPrefix = "sundial_metric",
            )
            Text(
                text = "$metricUnit · $metricLabel",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun buildSundial(
    home: HomeCoords?,
    instant: Instant,
    forecast: SolarForecast?,
    peakKwp: Float,
): SundialModel {
    val zone = ZoneId.systemDefault()
    val date = instant.atZone(zone).toLocalDate()
    val samples = SolarSeries.samplesForDate(forecast, peakKwp, date, zone)
    val useProduction = peakKwp > 0f
    val peakRing = max(
        0.0001f,
        samples.maxOfOrNull { if (useProduction) it.productionKw else it.ghiWm2 / 1000f } ?: 0f,
    )
    val sun = if (home != null) Solar.sunriseSunset(instant, home.latitude, home.longitude) else null
    fun hourOf(i: Instant): Float {
        val z = i.atZone(zone); return z.hour + z.minute / 60f
    }
    val zdt = instant.atZone(zone)
    return SundialModel(
        samples = samples,
        peakRing = peakRing,
        useProduction = useProduction,
        sunriseHour = sun?.let { hourOf(it.first) },
        sunsetHour = sun?.let { hourOf(it.second) },
        nowHour = zdt.hour + zdt.minute / 60f,
    )
}
