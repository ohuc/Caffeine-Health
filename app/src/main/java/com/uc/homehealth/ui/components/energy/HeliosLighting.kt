package com.uc.homehealth.ui.components.energy

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow

/**
 * Day/night lighting math ported from Helios (src/engine/lighting.ts) so the map hero
 * matches the reference card's mood at every solar altitude.
 */
internal object HeliosLighting {

    private val NightBlue = Color(0xFF02040C)
    private val CivilBlue = Color(0xFF0A1240)
    private val DawnAmber = Color(0xFF3A1408)
    private val NightIndigo = Color(0xFF0A0E1A)
    private val DuskPurple = Color(0xFF2A2540)
    private val WarmBrown = Color(0xFF5A3220)

    /**
     * Tinted overlay that dims the basemap by solar altitude (Helios `nightShadeForAltitude`):
     * astronomical night `#02040c`@0.68 → twilight blues → sunrise amber → clear by +20°.
     */
    fun nightShade(altDeg: Float): Color = when {
        altDeg < -12f -> NightBlue.copy(alpha = 0.68f)
        altDeg < -6f -> NightBlue.copy(alpha = lerpF(0.68f, 0.50f, (altDeg + 12f) / 6f))
        altDeg < 0f -> CivilBlue.copy(alpha = lerpF(0.50f, 0.30f, (altDeg + 6f) / 6f))
        altDeg < 6f -> DawnAmber.copy(alpha = lerpF(0.30f, 0.10f, altDeg / 6f))
        altDeg < 20f -> DawnAmber.copy(alpha = lerpF(0.10f, 0f, (altDeg - 6f) / 14f))
        else -> DawnAmber.copy(alpha = 0f)
    }

    /**
     * Building tint by solar altitude (Helios `buildingColorForAltitude`): deep night blends
     * 85% toward indigo, twilight toward dusk purple, sunrise toward warm brown, day = base.
     */
    fun buildingColor(base: Color, altDeg: Float): Color = when {
        altDeg < -6f -> lerp(base, NightIndigo, 0.85f)
        altDeg < 0f -> lerp(lerp(base, NightIndigo, 0.85f), lerp(base, DuskPurple, 0.55f), (altDeg + 6f) / 6f)
        altDeg < 6f -> lerp(lerp(base, DuskPurple, 0.55f), lerp(base, WarmBrown, 0.35f), altDeg / 6f)
        altDeg < 20f -> lerp(lerp(base, WarmBrown, 0.35f), base, (altDeg - 6f) / 14f)
        else -> base
    }

    /** MapLibre light polar angle from sun altitude (Helios `sunLightPolarFromAltitude`). */
    fun lightPolar(altDeg: Float): Float =
        if (altDeg > 0f) (90f - altDeg).coerceIn(0f, 89f) else 89f

    /**
     * Incidence-ray dash period from a production/irradiance rate (Helios `flowDuration`):
     * ease-out cubic from 30 s (idle) down to [minMs] at saturation.
     */
    fun flowDurationMs(rate: Float, saturation: Float = 1f, minMs: Int = 400): Int {
        if (rate <= 0f || saturation <= 0f) return 30_000
        val f = (rate / saturation).coerceAtMost(1f)
        val eased = 1f - (1f - f).pow(3)
        return (30_000 - (30_000 - minMs) * eased).toInt()
    }

    private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
