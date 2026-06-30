package com.uc.homehealth.energy

import com.uc.homehealth.data.ForecastPoint
import com.uc.homehealth.data.SolarForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One forecast hour resolved to a local time-of-day plus the modelled PV output. */
data class DaySample(
    val epochSec: Long,
    val hourOfDay: Float,   // 0..24 local
    val ghiWm2: Float,
    val cloudPct: Float,
    val productionKw: Float,
)

/**
 * Derives the display series the Energy visualisations need (today's curve, 5-day curve,
 * per-day kWh totals, interpolated irradiance) from an Open-Meteo [SolarForecast] plus the
 * installation size. Pure — no Android/Compose deps.
 */
object SolarSeries {

    /** Linearly-interpolated GHI (W/m²) at an arbitrary instant, or null if no forecast. */
    fun ghiAt(forecast: SolarForecast?, epochSec: Long): Float? =
        interpolate(forecast, epochSec) { it.ghiWm2 }

    /** Linearly-interpolated cloud cover (%) at an arbitrary instant, or null if no forecast. */
    fun cloudAt(forecast: SolarForecast?, epochSec: Long): Float? =
        interpolate(forecast, epochSec) { it.cloudPct }

    private inline fun interpolate(
        forecast: SolarForecast?,
        epochSec: Long,
        value: (ForecastPoint) -> Float,
    ): Float? {
        val pts = forecast?.points ?: return null
        if (pts.isEmpty()) return null
        if (epochSec <= pts.first().epochSec) return value(pts.first())
        if (epochSec >= pts.last().epochSec) return value(pts.last())
        for (i in 1 until pts.size) {
            if (pts[i].epochSec >= epochSec) {
                val a = pts[i - 1]; val b = pts[i]
                val span = (b.epochSec - a.epochSec).toFloat().coerceAtLeast(1f)
                val f = (epochSec - a.epochSec) / span
                return value(a) + (value(b) - value(a)) * f
            }
        }
        return value(pts.last())
    }

    /** Hourly samples that fall on [date] (local), modelled to kW with the panel size. */
    fun samplesForDate(
        forecast: SolarForecast?,
        peakKwp: Float,
        date: LocalDate,
        zone: ZoneId,
    ): List<DaySample> {
        val pts = forecast?.points ?: return emptyList()
        return pts.mapNotNull { p ->
            val zdt = Instant.ofEpochSecond(p.epochSec).atZone(zone)
            if (zdt.toLocalDate() != date) return@mapNotNull null
            DaySample(
                epochSec = p.epochSec,
                hourOfDay = zdt.hour + zdt.minute / 60f,
                ghiWm2 = p.ghiWm2,
                cloudPct = p.cloudPct,
                productionKw = Solar.productionKw(p.ghiWm2, peakKwp, p.airTempC),
            )
        }
    }

    /** Predicted kWh for [date] = Σ hourly kW (samples are hourly, so kW·1h = kWh). */
    fun dayKwh(forecast: SolarForecast?, peakKwp: Float, date: LocalDate, zone: ZoneId): Float =
        samplesForDate(forecast, peakKwp, date, zone).fold(0f) { acc, s -> acc + s.productionKw }

    /** The 5 local dates covered by the forecast (2 past + today + 2 ahead), ascending. */
    fun coveredDates(forecast: SolarForecast?, zone: ZoneId): List<LocalDate> {
        val pts = forecast?.points ?: return emptyList()
        return pts.map { Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate() }
            .distinct()
            .sorted()
    }
}
