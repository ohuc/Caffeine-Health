package com.uc.homehealth.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.coroutines.resume
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.suspendCancellableCoroutine

// ─── MapLibre glue for the person-location widget ────────────────────────────
//
// MapLibre Native (open-source Mapbox GL fork) renders vector tiles with no API key.
// We deliberately keep tile/style hosting behind these constants so the source can be
// swapped (or self-hosted) without touching the UI. CARTO's GL styles are public and
// keyless — they're the exact "Dark Matter"/"Positron" basemaps Home Assistant's own
// map card uses, so the result matches what users already know.
//
// The snapshot path (captureLocationSnapshot) renders a map to a Bitmap off-screen. The
// dashboard tile uses it for a smooth, non-interactive preview, and the planned Android
// home-screen App Widget can reuse the same function to fill its RemoteViews ImageView
// (a live GL map can't run inside a widget — only a bitmap can).

/** Dark vector style (CARTO Dark Matter). Swap or self-host by changing this. */
const val MAP_STYLE_DARK = "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"

/** Light vector style (CARTO Positron). */
const val MAP_STYLE_LIGHT = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

fun mapStyleUrl(dark: Boolean): String = if (dark) MAP_STYLE_DARK else MAP_STYLE_LIGHT

@Volatile private var mapLibreReady = false

/** Idempotent MapLibre init. Must run before any MapView/MapSnapshotter is created. */
@Synchronized
fun ensureMapLibre(context: Context) {
    if (mapLibreReady) return
    MapLibre.getInstance(context.applicationContext)
    mapLibreReady = true
}

/**
 * Circular avatar pin (filled disc + white ring + a single initial), drawn natively so it
 * can be composited onto a snapshot bitmap AND registered as a MapLibre symbol image on the
 * live map. [fillColor]/[inkColor] are ARGB ints.
 */
fun buildPersonPin(sizePx: Int, initial: String, fillColor: Int, inkColor: Int): Bitmap {
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val center = sizePx / 2f
    // White ring backing.
    canvas.drawCircle(center, center, center, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
    })
    // Accent-filled disc inset from the ring.
    canvas.drawCircle(center, center, center - sizePx * 0.09f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = fillColor
    })
    // Centered initial.
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inkColor
        textAlign = Paint.Align.CENTER
        textSize = sizePx * 0.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    val fm = text.fontMetrics
    canvas.drawText(initial.take(1).uppercase(), center, center - (fm.ascent + fm.descent) / 2f, text)
    return bmp
}

/**
 * Render an off-screen map snapshot centered on [lat]/[lng], with [pin] composited at the
 * exact center (the snapshot is centered on the person, so center == the marker location).
 * Returns null on error. Cancels the underlying snapshotter if the caller is cancelled.
 */
suspend fun captureLocationSnapshot(
    context: Context,
    widthPx: Int,
    heightPx: Int,
    lat: Double,
    lng: Double,
    zoom: Double,
    styleUrl: String,
    pin: Bitmap?,
): Bitmap? {
    if (widthPx <= 0 || heightPx <= 0) return null
    ensureMapLibre(context)
    return suspendCancellableCoroutine { cont ->
        val options = MapSnapshotter.Options(widthPx, heightPx)
            .withStyleBuilder(Style.Builder().fromUri(styleUrl))
            .withCameraPosition(
                CameraPosition.Builder().target(LatLng(lat, lng)).zoom(zoom).build()
            )
        val snapshotter = MapSnapshotter(context, options)
        cont.invokeOnCancellation { runCatching { snapshotter.cancel() } }
        snapshotter.start({ snapshot ->
            if (!cont.isActive) return@start
            val base = snapshot.bitmap
            val out = if (pin != null) {
                val mutable = base.copy(Bitmap.Config.ARGB_8888, true)
                Canvas(mutable).drawBitmap(
                    pin,
                    (mutable.width - pin.width) / 2f,
                    (mutable.height - pin.height) / 2f,
                    null,
                )
                mutable
            } else {
                base
            }
            cont.resume(out)
        }, { _ ->
            if (cont.isActive) cont.resume(null)
        })
    }
}

/**
 * GeoJSON polygon approximating a circle of [radiusMeters] around [lat]/[lng], used as the
 * GPS-accuracy halo on the live map. [points] segments is plenty for a smooth ring.
 */
fun accuracyCircle(lat: Double, lng: Double, radiusMeters: Double, points: Int = 64): Polygon {
    val earthRadius = 6378137.0
    val ring = ArrayList<Point>(points + 1)
    val latRad = Math.toRadians(lat)
    val lngRad = Math.toRadians(lng)
    val angular = radiusMeters / earthRadius
    for (i in 0..points) {
        val bearing = Math.toRadians(i * 360.0 / points)
        val lat2 = asin(sin(latRad) * cos(angular) + cos(latRad) * sin(angular) * cos(bearing))
        val lng2 = lngRad + atan2(
            sin(bearing) * sin(angular) * cos(latRad),
            cos(angular) - sin(latRad) * sin(lat2),
        )
        ring.add(Point.fromLngLat(Math.toDegrees(lng2), Math.toDegrees(lat2)))
    }
    return Polygon.fromLngLats(listOf(ring))
}
