package com.uc.homehealth.energy

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Self-contained solar math for the Energy card — sun position (for the arc + dial),
 * sunrise/sunset, and a minimal PV production model. Pure Kotlin, no dependencies.
 *
 * Sun position uses the NOAA Solar Position Algorithm (the "spreadsheet" variant), good
 * to ~0.1°, which is far beyond what the visualisation needs. Irradiance/cloud forecasts
 * come from Open-Meteo (already cloud-attenuated), so we do NOT reimplement a clear-sky /
 * cloud-attenuation model here — only the panel model that turns GHI into kW.
 */
object Solar {

    /** Sun position in the observer's sky. [azimuthDeg] is clockwise from true north. */
    data class Position(val azimuthDeg: Double, val altitudeDeg: Double) {
        val isUp: Boolean get() = altitudeDeg > 0.0
    }

    private fun Double.rad() = Math.toRadians(this)
    private fun Double.deg() = Math.toDegrees(this)

    /**
     * Sun azimuth + altitude for an instant at a location, via the NOAA SPA.
     * [latDeg] north-positive, [lonDeg] east-positive.
     */
    fun position(instant: Instant, latDeg: Double, lonDeg: Double): Position {
        val jd = instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0

        val l0 = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(m.rad()) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin((2 * m).rad()) * (0.019993 - 0.000101 * t) +
            sin((3 * m).rad()) * 0.000289
        val trueLong = l0 + c
        val appLong = trueLong - 0.00569 - 0.00478 * sin((125.04 - 1934.136 * t).rad())
        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val meanObliq = 23.0 + (26.0 + seconds / 60.0) / 60.0
        val obliqCorr = meanObliq + 0.00256 * cos((125.04 - 1934.136 * t).rad())
        val decl = asin(sin(obliqCorr.rad()) * sin(appLong.rad())).deg()

        val y = tan((obliqCorr / 2).rad()) * tan((obliqCorr / 2).rad())
        val eqTime = 4 * (
            y * sin(2 * l0.rad()) -
                2 * e * sin(m.rad()) +
                4 * e * y * sin(m.rad()) * cos(2 * l0.rad()) -
                0.5 * y * y * sin(4 * l0.rad()) -
                1.25 * e * e * sin(2 * m.rad())
            ).deg()

        val utc = instant.atOffset(ZoneOffset.UTC)
        val minutesOfDay = utc.hour * 60.0 + utc.minute + utc.second / 60.0
        val trueSolarTime = (minutesOfDay + eqTime + 4 * lonDeg).mod(1440.0)
        val hourAngle = if (trueSolarTime / 4 < 0) trueSolarTime / 4 + 180 else trueSolarTime / 4 - 180

        val zenith = acos(
            sin(latDeg.rad()) * sin(decl.rad()) +
                cos(latDeg.rad()) * cos(decl.rad()) * cos(hourAngle.rad())
        ).deg()
        val elevation = 90.0 - zenith

        val azDenom = cos(latDeg.rad()) * sin(zenith.rad())
        val azimuth = if (kotlin.math.abs(azDenom) > 1e-9) {
            val azRaw = ((sin(latDeg.rad()) * cos(zenith.rad()) - sin(decl.rad())) / azDenom)
                .coerceIn(-1.0, 1.0)
            val a = acos(azRaw).deg()
            if (hourAngle > 0) (a + 180).mod(360.0) else (540 - a).mod(360.0)
        } else {
            if (latDeg > 0) 180.0 else 0.0
        }
        return Position(azimuthDeg = azimuth, altitudeDeg = elevation)
    }

    /**
     * Sunrise/sunset as UTC instants for the calendar day containing [instant] (in UTC),
     * using the standard 90.833° zenith (refraction + solar disc). Null for polar
     * day/night where the sun never crosses the horizon.
     */
    fun sunriseSunset(instant: Instant, latDeg: Double, lonDeg: Double): Pair<Instant, Instant>? {
        val dayStart = instant.atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC)
        // Declination + equation of time at local solar noon are accurate enough for markers.
        val noon = dayStart.plusSeconds(((720 - 4 * lonDeg) * 60).toLong())
        val jd = noon.toEpochMilli() / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0
        val l0 = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
        val m = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val cc = sin(m.rad()) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin((2 * m).rad()) * (0.019993 - 0.000101 * t) + sin((3 * m).rad()) * 0.000289
        val appLong = l0 + cc - 0.00569 - 0.00478 * sin((125.04 - 1934.136 * t).rad())
        val seconds = 21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))
        val obliqCorr = 23.0 + (26.0 + seconds / 60.0) / 60.0 + 0.00256 * cos((125.04 - 1934.136 * t).rad())
        val decl = asin(sin(obliqCorr.rad()) * sin(appLong.rad())).deg()
        val y = tan((obliqCorr / 2).rad()) * tan((obliqCorr / 2).rad())
        val eqTime = 4 * (
            y * sin(2 * l0.rad()) - 2 * e * sin(m.rad()) + 4 * e * y * sin(m.rad()) * cos(2 * l0.rad()) -
                0.5 * y * y * sin(4 * l0.rad()) - 1.25 * e * e * sin(2 * m.rad())
            ).deg()

        val cosHa = cos(90.833.rad()) / (cos(latDeg.rad()) * cos(decl.rad())) -
            tan(latDeg.rad()) * tan(decl.rad())
        if (cosHa < -1.0 || cosHa > 1.0) return null // polar day / night
        val haDeg = acos(cosHa).deg()
        val sunriseMin = 720 - 4 * (lonDeg + haDeg) - eqTime
        val sunsetMin = 720 - 4 * (lonDeg - haDeg) - eqTime
        return dayStart.plusSeconds((sunriseMin * 60).toLong()) to dayStart.plusSeconds((sunsetMin * 60).toLong())
    }

    /**
     * Instantaneous PV output (kW) from global horizontal irradiance.
     *
     * P = GHI/1000 · kWp · PR · thermalDerate, where the thermal derate uses the NOCT
     * cell-temperature estimate (Sandia): T_cell ≈ T_air + (NOCT−20)/800 · GHI, with a
     * −0.4 %/°C power coefficient referenced to 25 °C. This is the one piece of Helios
     * fidelity worth keeping — it visibly lowers midday output on hot, bright days.
     */
    fun productionKw(
        ghiWm2: Float,
        peakKwp: Float,
        airTempC: Float = 20f,
        performanceRatio: Float = 0.80f,
        noctC: Float = 45f,
        powerCoeffPerC: Float = -0.004f,
    ): Float {
        if (ghiWm2 <= 0f || peakKwp <= 0f) return 0f
        val cellTemp = airTempC + (noctC - 20f) / 800f * ghiWm2
        val thermalDerate = (1f + powerCoeffPerC * (cellTemp - 25f)).coerceIn(0f, 1.2f)
        return (ghiWm2 / 1000f) * peakKwp * performanceRatio * thermalDerate
    }
}
