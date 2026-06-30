package com.uc.homehealth.ui.components.energy

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.CloudGrid
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.data.HomeCoords
import com.uc.homehealth.data.SolarForecast
import androidx.compose.ui.text.TextStyle
import com.uc.homehealth.energy.Solar
import com.uc.homehealth.energy.SolarSeries
import com.uc.homehealth.ui.components.RollingNumberText
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.glanceInkOn
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import com.uc.homehealth.ui.theme.customColors
import java.time.Instant
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// The camera state the map reports: the home's ground position, the point just above its
// roof (for the floating pin), and the camera orientation the sun projection needs.
private data class HeroCam(
    val anchor: Offset?,
    val pinAnchor: Offset?,
    val bearingDeg: Float,
    val pitchDeg: Float,
)

/**
 * The Energy hero, rebuilt to match the Helios card: an interactive 3D map of the home
 * (orbitable by gestures), with the sun's **real trajectory** — computed from azimuth and
 * altitude and projected around the home in screen space — drawn over it. The arc is stroked
 * thicker where it faces the camera and as faint dots below the horizon (Helios `nearness`);
 * a dashed incidence ray flows from the sun to the home at a speed eased by irradiance; the
 * whole scene is graded by the Helios day/night lighting ramps. Falls back to a flat sky
 * gradient when no home location is available.
 */
@Composable
fun SunArcHero(
    home: HomeCoords?,
    instant: Instant,
    forecast: SolarForecast?,
    peakKwp: Float,
    solar: HaEntityValue?,
    batterySoc: HaEntityValue?,
    batteryPower: HaEntityValue?,
    grid: HaEntityValue?,
    modifier: Modifier = Modifier,
    cloudGrid: CloudGrid? = null,
    onRequestClouds: () -> Unit = {},
    onHomeTap: () -> Unit = {},
) {
    val custom = MaterialTheme.customColors
    // Chrome ink for lines drawn over the basemap — the map follows the theme, so the
    // overlay ink must too (white-on-light-map was invisible in light theme).
    val chromeInk = MaterialTheme.colorScheme.onSurface
    val dark = isSystemInDarkTheme()
    // Weather mode (Helios): camera flips top-down over the area, the solar HUD fades out,
    // and the low/mid/high cloud-cover field renders over the basemap.
    var weatherMode by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val lat = home?.latitude ?: 40.0
    val lon = home?.longitude ?: 0.0

    val minuteBucket = instant.epochSecond / 60
    val sunNow = remember(home, minuteBucket) { Solar.position(instant, lat, lon) }
    val sunAlt = sunNow.altitudeDeg.toFloat()
    val irradiance = SolarSeries.ghiAt(forecast, instant.epochSecond)

    // The day's full trajectory (az/alt every 15 min), recomputed only when the date changes.
    val date = remember(minuteBucket) { instant.atZone(zone).toLocalDate() }
    val arcSamples = remember(home, date) {
        val dayStart = date.atStartOfDay(zone).toInstant()
        (0..96).map { i -> Solar.position(dayStart.plusSeconds(i * 900L), lat, lon) }
    }

    val restBearing = if (lat >= 0) 180f else 0f
    var cam by remember(home) { mutableStateOf(HeroCam(null, null, restBearing, 55f)) }

    // Incidence ray dash flow: speed eased by live irradiance (Helios flowDuration).
    val rayPeriodMs = remember(irradiance == null, (irradiance ?: 0f).toInt() / 50) {
        HeliosLighting.flowDurationMs(rate = (irradiance ?: 0f) / 1000f)
    }
    val rayPhase by rememberInfiniteTransition(label = "ray").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(rayPeriodMs, easing = LinearEasing), RepeatMode.Restart),
        label = "ray_phase",
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            // App shape token `medium` — same radius as every other card on the page.
            .clip(MaterialTheme.shapes.medium),
    ) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()

        // The HUD (night shade, sun arc, chips, pin, weather toggle) waits for the map to
        // reveal: composing it over the bare shimmer skeleton reads as broken UI. With no
        // home there is no map — the gradient-sky fallback shows the HUD immediately.
        var mapReady by remember(home == null) { mutableStateOf(home == null) }
        val hudAlpha by animateFloatAsState(if (mapReady) 1f else 0f, tween(450), label = "hero_hud_reveal")

        if (home != null) {
            EnergyHomeMap(
                home = home,
                sunAzimuthDeg = sunNow.azimuthDeg.toFloat(),
                sunAltitudeDeg = sunAlt,
                darkTheme = dark,
                weatherMode = weatherMode,
                cloudGrid = cloudGrid,
                onCamera = { anchor, pinAnchor, bearing, pitch ->
                    cam = HeroCam(anchor, pinAnchor, bearing, pitch)
                },
                onReady = { mapReady = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val dayFactor = (sunAlt / 60f).coerceIn(0f, 1f)
            val skyTop = lerp(Color(0xFF11131B), Color(0xFF1C2A48), dayFactor)
            val skyBottom = lerp(Color(0xFF15171F), Color(0xFF223049), dayFactor * 0.7f)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(skyTop, skyBottom))))
        }

        if (hudAlpha > 0.01f) Box(Modifier.fillMaxSize().alpha(hudAlpha)) {

        // Helios night-shade: a solar-altitude-keyed tint that dims the scene through dusk,
        // night, and dawn, clearing entirely in daylight.
        val shade = HeliosLighting.nightShade(sunAlt)
        if (shade.alpha > 0.005f) {
            Box(Modifier.fillMaxSize().background(shade))
        }

        val homePx = cam.anchor ?: Offset(wPx * 0.5f, hPx * 0.58f)
        val homeFx = (homePx.x / wPx).coerceIn(0f, 1f)
        val homeFy = (homePx.y / hPx).coerceIn(0f, 1f)

        // Project a sun position (azimuth/altitude) to screen space around the home, honouring
        // the camera: direction = azimuth relative to map bearing; the ground circle is
        // foreshortened by pitch; altitude lifts the point along the screen-vertical.
        val radius = hPx * 0.62f
        val bearingRad = Math.toRadians(cam.bearingDeg.toDouble())
        val pitchRad = Math.toRadians(cam.pitchDeg.toDouble())
        fun project(azDeg: Double, altDeg: Double): Offset {
            val theta = Math.toRadians(azDeg) - bearingRad
            val altRad = Math.toRadians(altDeg.coerceAtLeast(-10.0))
            val ground = radius * cos(altRad)
            val height = radius * sin(altRad)
            return Offset(
                (homePx.x + ground * sin(theta)).toFloat(),
                (homePx.y - ground * cos(theta) * cos(pitchRad) - height * sin(pitchRad)).toFloat(),
            )
        }
        // 1 facing the camera (screen-bottom side), 0 behind the scene (Helios `nearness`).
        fun nearness(azDeg: Double): Float {
            val theta = Math.toRadians(azDeg) - bearingRad
            return ((1.0 - cos(theta)) / 2.0).toFloat()
        }

        val sunUp = sunNow.altitudeDeg > 0
        val sunPt = project(sunNow.azimuthDeg, sunNow.altitudeDeg)

        if (!weatherMode) Canvas(modifier = Modifier.fillMaxSize()) {
            // Daily trajectory: above-horizon stroked with nearness-scaled width, below as dots.
            for (i in 0 until arcSamples.size - 1) {
                val a = arcSamples[i]
                val b = arcSamples[i + 1]
                val pa = project(a.azimuthDeg, a.altitudeDeg)
                val pb = project(b.azimuthDeg, b.altitudeDeg)
                if (a.isUp || b.isUp) {
                    val near = (nearness(a.azimuthDeg) + nearness(b.azimuthDeg)) / 2f
                    drawLine(
                        color = SunGold.copy(alpha = 0.45f + 0.40f * near),
                        start = pa,
                        end = pb,
                        strokeWidth = (1.2f + 2.2f * near).dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                } else if (i % 2 == 0) {
                    drawCircle(chromeInk.copy(alpha = 0.15f), radius = 1.4.dp.toPx(), center = pa)
                }
            }

            if (sunUp) {
                // Incidence ray: dashed sun→home line whose dashes flow with irradiance.
                val dashPx = 5.dp.toPx()
                drawLine(
                    color = SunGold.copy(alpha = 0.55f),
                    start = sunPt,
                    end = homePx,
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(dashPx, dashPx * 1.4f),
                        -rayPhase * dashPx * 2.4f,
                    ),
                )

                // Sun disc + irradiance halo (the sanctioned glow): alpha √(GHI/1000)·0.55.
                val haloAlpha = (sqrt(((irradiance ?: 0f) / 1000f).coerceIn(0f, 1f)) * 0.55f).coerceAtLeast(0.14f)
                val haloR = 58.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(0f to SunAmber.copy(alpha = haloAlpha), 1f to Color.Transparent),
                        center = sunPt,
                        radius = haloR,
                    ),
                    radius = haloR,
                    center = sunPt,
                )
                drawCircle(SunGold, radius = 11.dp.toPx(), center = sunPt)
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 5.dp.toPx(), center = sunPt)
            } else {
                drawCircle(chromeInk.copy(alpha = 0.25f), radius = 5.dp.toPx(), center = sunPt)
            }

            // Leader lines from the metric chips to the home.
            val leader = chromeInk.copy(alpha = 0.30f)
            listOf(
                Offset(homeFx * size.width, (homeFy - 0.17f) * size.height),
                Offset((homeFx - 0.27f) * size.width, (homeFy - 0.02f) * size.height),
                Offset((homeFx + 0.27f) * size.width, (homeFy - 0.02f) * size.height),
            ).forEach { p ->
                drawLine(leader, start = p, end = homePx, strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
            }
        }

        // ── Compose overlays, centred at fractional anchors via BiasAlignment ──
        if (!weatherMode && sunUp && irradiance != null) {
            val sunFx = (sunPt.x / wPx).coerceIn(0.10f, 0.90f)
            val sunFy = (sunPt.y / hPx - 0.075f).coerceIn(0.04f, 0.90f)
            HeroPill(text = "${irradiance.toInt()} W/m²", modifier = Modifier.align(bias(sunFx, sunFy)))
        }

        // Floating map pin: a chip + pointer stem hovering just above the building's roof
        // (Helios-style), instead of an icon pasted flat onto the home's base.
        val density = androidx.compose.ui.platform.LocalDensity.current
        val pinPx = cam.pinAnchor ?: Offset(homePx.x, homePx.y - hPx * 0.05f)
        val pinLift = with(density) { 30.dp.toPx() } // gap above roof + half the pin's height
        val pinFx = (pinPx.x / wPx).coerceIn(0.05f, 0.95f)
        val pinFy = ((pinPx.y - pinLift) / hPx).coerceIn(0.04f, 0.92f)
        HomePin(accent = custom.sky, onTap = onHomeTap, modifier = Modifier.align(bias(pinFx, pinFy)))

        if (!weatherMode) {
            val pvF = Offset(homeFx, (homeFy - 0.17f).coerceIn(0.06f, 0.94f))
            val batF = Offset((homeFx - 0.27f).coerceIn(0.10f, 0.90f), (homeFy - 0.02f).coerceIn(0.06f, 0.94f))
            val gridF = Offset((homeFx + 0.27f).coerceIn(0.10f, 0.90f), (homeFy - 0.02f).coerceIn(0.06f, 0.94f))
            EnergyChip(chipText(solar, forceKw = true), custom.sand, Modifier.align(bias(pvF.x, pvF.y)))
            EnergyChip(chipText(batterySoc), custom.mint, Modifier.align(bias(batF.x, batF.y)))
            EnergyChip(chipText(grid), custom.cyan, Modifier.align(bias(gridF.x, gridF.y)))
        }

        // Weather-mode toggle (Helios's weather chip) — top-right over the map.
        if (home != null) {
            val haptic = rememberAppHaptics()
            Tap(
                onClick = {
                    haptic.toggle(!weatherMode)
                    if (!weatherMode) onRequestClouds()
                    weatherMode = !weatherMode
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (weatherMode) Icons.Outlined.ViewInAr else Icons.Outlined.Cloud,
                        contentDescription = if (weatherMode) "3D view" else "Weather view",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        } // end HUD reveal gate
    }
}

private fun bias(xFrac: Float, yFrac: Float) =
    BiasAlignment(horizontalBias = xFrac * 2f - 1f, verticalBias = yFrac * 2f - 1f)

/**
 * Map-pin marker: accent chip with the home glyph and a pointer stem aiming at the roof.
 * Tapping it opens the sun-&-sky detail sheet.
 */
@Composable
private fun HomePin(accent: Color, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberAppHaptics()
    Tap(onClick = { haptic.navigation(); onTap() }, modifier = modifier) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Home",
                tint = glanceInkOn(accent),
                modifier = Modifier.size(19.dp),
            )
        }
            Canvas(Modifier.size(width = 12.dp, height = 8.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = accent)
            }
        }
    }
}

@Composable
private fun EnergyChip(value: String, accent: Color, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(cs.surfaceContainerHigh.copy(alpha = 0.92f), PillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(accent, CircleShape))
        Spacer(Modifier.width(7.dp))
        RollingNumberText(
            text = value,
            style = TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurface,
            ),
            labelPrefix = "hero_chip",
        )
    }
}

@Composable
private fun HeroPill(text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .background(cs.surfaceContainerHigh.copy(alpha = 0.92f), PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(SunGold, CircleShape))
        Spacer(Modifier.width(6.dp))
        RollingNumberText(
            text = text,
            style = TextStyle(
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurface,
            ),
            labelPrefix = "hero_pill",
        )
    }
}

/** Compact chip text: "1.89 kW", "56 %", "0 W", or "—" when unavailable. */
private fun chipText(value: HaEntityValue?, forceKw: Boolean = false): String {
    if (value == null) return "—"
    val num = value.numeric ?: return value.state
    val unit = value.unit?.trim().orEmpty()
    return when {
        unit.equals("W", ignoreCase = true) && (forceKw || kotlin.math.abs(num) >= 1000f) ->
            "${trimZeros(num / 1000f)} kW"
        unit.equals("W", ignoreCase = true) -> "${num.toInt()} W"
        unit == "%" -> "${num.toInt()} %"
        else -> "${trimZeros(num)}${if (unit.isNotEmpty()) " $unit" else ""}"
    }
}

private fun trimZeros(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.2f", v)
