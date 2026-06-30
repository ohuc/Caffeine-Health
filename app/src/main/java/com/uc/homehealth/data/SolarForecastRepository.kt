package com.uc.homehealth.data

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** One hour of solar weather: global horizontal irradiance (W/m²), cloud cover %, air temp °C. */
data class ForecastPoint(
    val epochSec: Long,
    val ghiWm2: Float,
    val cloudPct: Float,
    val airTempC: Float,
)

/**
 * A 5-day (2 past + today + 2 forecast) solar-weather forecast for one location, plus the
 * per-day sunrise/sunset instants. [points] are hourly and time-ordered.
 */
data class SolarForecast(
    val points: List<ForecastPoint>,
    val sunriseSunsetEpochSec: List<Pair<Long, Long>>,
)

/** One cell of the weather-mode cloud field: location + current cloud cover per altitude band. */
data class CloudCell(
    val lat: Double,
    val lon: Double,
    val lowPct: Float,
    val midPct: Float,
    val highPct: Float,
)

/** A grid of [CloudCell]s around the home (Helios weather mode's low/mid/high coverage grid). */
data class CloudGrid(val cells: List<CloudCell>)

/**
 * Fetches solar weather from Open-Meteo (free, no API key). Open-Meteo's
 * `shortwave_radiation` is already cloud-attenuated GHI, so callers feed it straight into
 * [com.uc.homehealth.energy.Solar.productionKw] without a separate clear-sky/cloud model.
 *
 * Uses the app's shared OkHttp + Gson directly (same idiom as HaHomeRepository.fetchRestHistory),
 * and caches the last result per location for [CACHE_MS] so the 30s UI clock tick never refetches.
 */
@Singleton
class SolarForecastRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private data class Entry(val lat: Double, val lon: Double, val fetchedAtMs: Long, val forecast: SolarForecast)

    @Volatile private var cached: Entry? = null

    /** Cached forecast for [lat]/[lon]; refetches at most every 30 minutes. Null on first-fetch failure. */
    suspend fun forecast(lat: Double, lon: Double): SolarForecast? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cached?.let { c ->
            if (sameLocation(c.lat, lat) && sameLocation(c.lon, lon) && now - c.fetchedAtMs < CACHE_MS) {
                return@withContext c.forecast
            }
        }
        val fresh = runCatching { fetch(lat, lon) }.getOrNull()
        if (fresh != null) {
            cached = Entry(lat, lon, now, fresh)
            fresh
        } else {
            // On a transient failure keep serving the last good forecast if we have one.
            cached?.forecast
        }
    }

    private fun fetch(lat: Double, lon: Double): SolarForecast? {
        val url = "$BASE?latitude=$lat&longitude=$lon" +
            "&hourly=shortwave_radiation,cloudcover,temperature_2m" +
            "&daily=sunrise,sunset" +
            "&timeformat=unixtime&timezone=auto&past_days=2&forecast_days=3"
        val req = Request.Builder().url(url).build()
        val body = okHttpClient.newCall(req).execute().use { it.body?.string() } ?: return null
        val resp = gson.fromJson(body, OpenMeteoResponse::class.java) ?: return null

        val times = resp.hourly?.time ?: return null
        val ghi = resp.hourly.shortwaveRadiation.orEmptyOf(times.size)
        val cloud = resp.hourly.cloudcover.orEmptyOf(times.size)
        val temp = resp.hourly.temperature2m.orEmptyOf(times.size)
        val points = times.indices.map { i ->
            ForecastPoint(
                epochSec = times[i],
                ghiWm2 = ghi.getOrNull(i)?.coerceAtLeast(0f) ?: 0f,
                cloudPct = cloud.getOrNull(i) ?: 0f,
                airTempC = temp.getOrNull(i) ?: 20f,
            )
        }
        val sunData = resp.daily?.let { d ->
            val rises = d.sunrise.orEmpty(); val sets = d.sunset.orEmpty()
            rises.indices.mapNotNull { i -> sets.getOrNull(i)?.let { rises[i] to it } }
        }.orEmpty()
        return SolarForecast(points = points, sunriseSunsetEpochSec = sunData)
    }

    // ── Weather-mode cloud grid (Helios `ensureWeatherCloudGrid`) ────────────────
    // One multi-location Open-Meteo call: a 9×9 grid (±0.5°, ~±55 km) of current cloud
    // cover split into low/mid/high bands. Cached 30 min per (rounded) home location.
    @Volatile private var cloudCached: Triple<Double, Double, Pair<Long, CloudGrid>>? = null

    suspend fun cloudGrid(lat: Double, lon: Double): CloudGrid? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cloudCached?.let { (cLat, cLon, entry) ->
            if (sameLocation(cLat, lat) && sameLocation(cLon, lon) && now - entry.first < CACHE_MS) {
                return@withContext entry.second
            }
        }
        val fresh = runCatching { fetchCloudGrid(lat, lon) }.getOrNull()
        if (fresh != null) cloudCached = Triple(lat, lon, now to fresh)
        fresh ?: cloudCached?.third?.second
    }

    private fun fetchCloudGrid(lat: Double, lon: Double): CloudGrid? {
        // ±1° (~±110 km) at 0.25° spacing — regional scale like Helios's weather view,
        // comfortably above Open-Meteo's ~11 km cloud-model resolution.
        val n = 9
        val step = 0.25
        val half = (n - 1) / 2
        val lats = ArrayList<Double>(n * n)
        val lons = ArrayList<Double>(n * n)
        for (iy in -half..half) for (ix in -half..half) {
            lats.add(lat + iy * step)
            lons.add(lon + ix * step)
        }
        fun fmt(v: Double) = String.format(java.util.Locale.US, "%.4f", v)
        val url = "$BASE?latitude=${lats.joinToString(",") { fmt(it) }}" +
            "&longitude=${lons.joinToString(",") { fmt(it) }}" +
            "&current=cloud_cover_low,cloud_cover_mid,cloud_cover_high"
        val req = Request.Builder().url(url).build()
        val body = okHttpClient.newCall(req).execute().use { it.body?.string() } ?: return null
        // Multi-location responses are a JSON array; a single location comes back as an object.
        val element = gson.fromJson(body, com.google.gson.JsonElement::class.java) ?: return null
        val items = when {
            element.isJsonArray -> element.asJsonArray.mapNotNull { it.takeIf(com.google.gson.JsonElement::isJsonObject)?.asJsonObject }
            element.isJsonObject -> listOf(element.asJsonObject)
            else -> return null
        }
        if (items.isEmpty()) return null
        // The texture builder needs the FULL n×n lattice in request order — a missing or
        // malformed item becomes a clear cell, never a dropped one (a dropped cell would
        // break the grid's square shape and kill the whole cloud layer).
        val cells = (0 until n * n).map { i ->
            val o = items.getOrNull(i)
            val cur = o?.getAsJsonObject("current")
            fun pct(key: String) = cur?.get(key)?.takeIf { !it.isJsonNull }?.runCatching { asFloat }?.getOrNull() ?: 0f
            CloudCell(
                lat = lats[i],
                lon = lons[i],
                lowPct = pct("cloud_cover_low"),
                midPct = pct("cloud_cover_mid"),
                highPct = pct("cloud_cover_high"),
            )
        }
        return CloudGrid(cells)
    }

    private fun sameLocation(a: Double, b: Double) = kotlin.math.abs(a - b) < 0.001 // ~100 m
    private fun <T> List<T>?.orEmptyOf(size: Int): List<T> = this ?: emptyList()

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
        private const val CACHE_MS = 30 * 60 * 1000L
    }

    // ── Open-Meteo response DTOs (unixtime; nullable lists for defensive parsing) ──
    private data class OpenMeteoResponse(val hourly: Hourly?, val daily: Daily?)
    private data class Hourly(
        val time: List<Long>?,
        @SerializedName("shortwave_radiation") val shortwaveRadiation: List<Float>?,
        val cloudcover: List<Float>?,
        @SerializedName("temperature_2m") val temperature2m: List<Float>?,
    )
    private data class Daily(
        val time: List<Long>?,
        val sunrise: List<Long>?,
        val sunset: List<Long>?,
    )
}
