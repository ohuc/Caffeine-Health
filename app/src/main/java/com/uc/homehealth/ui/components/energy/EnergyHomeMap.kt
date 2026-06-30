package com.uc.homehealth.ui.components.energy

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uc.homehealth.data.CloudGrid
import com.uc.homehealth.data.HomeCoords
import com.uc.homehealth.ui.components.MapShimmer
import com.uc.homehealth.ui.components.ensureMapLibre
import com.uc.homehealth.ui.components.rememberAppHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.light.Position
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt

// OpenFreeMap styles, matching Helios (`helios-engine.ts` `_resolveMapStyle`): Liberty for
// light themes, Fiord (muted dark slate-blue) for dark.
const val OPENFREEMAP_LIBERTY = "https://tiles.openfreemap.org/styles/liberty"
const val OPENFREEMAP_FIORD = "https://tiles.openfreemap.org/styles/fiord"

private const val ZOOM_MIN = 15.5
private const val ZOOM_MAX = 18.5
private const val TILT_MIN = 20.0
private const val TILT_MAX = 60.0
private const val REST_ZOOM = 17.0
private const val REST_TILT = 55.0
// Weather mode: top-down, north-up, zoomed out so the ±1° cloud grid fills the view.
private const val WEATHER_ZOOM = 7.8

// Home-highlight wireframe palette (Helios's glassy focal-building look).
private val HomeEdge = Color(0xFFDCE9FF)
private val HomeGlow = Color(0xFF9FC2F5)

/**
 * The hero's 3D map, modeled on Helios's engine: an **orbit camera** pinned to the home.
 * Native MapLibre gestures are fully disabled; instead a Compose gesture layer implements
 * Helios's own interaction (`helios-engine.ts`): **single-finger drag rotates (horizontal)
 * and pitches (vertical)** around the home, pinch zooms within a tight band, a two-finger
 * twist also rotates, and a double-tap animates back to the resting pose. Driving the camera
 * ourselves also keeps the surrounding scrollable screen from stealing the drag.
 *
 * Buildings are extruded from the OpenMapTiles `building` layer and tinted by solar altitude;
 * the style's light position follows the real sun. The home's screen point + camera
 * bearing/pitch are reported via [onCamera] on every camera change so the sun-arc overlay
 * can project the sun's trajectory around it.
 */
@Composable
fun EnergyHomeMap(
    home: HomeCoords,
    sunAzimuthDeg: Float,
    sunAltitudeDeg: Float,
    darkTheme: Boolean,
    // anchor = the home's ground position; pinAnchor = the point just above its roof
    // (height lifted through the camera pitch) where the floating map pin belongs.
    onCamera: (anchor: Offset, pinAnchor: Offset, bearingDeg: Float, pitchDeg: Float) -> Unit,
    modifier: Modifier = Modifier,
    weatherMode: Boolean = false,
    cloudGrid: CloudGrid? = null,
    // Fires once the basemap has rendered and the shimmer handoff begins. The hero gates
    // its HUD (sun arc, chips, pin) on this so nothing floats over the bare skeleton.
    onReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = rememberAppHaptics()
    val homeLatLng = remember(home) { LatLng(home.latitude, home.longitude) }
    // The home building's extrusion height (m): placeholder until the real footprint loads.
    val homeHeightM = remember { mutableStateOf(10f) }
    // The home's footprint ring (real building once tiles render, square until then) +
    // its roof height — drawn as the glowing screen-space wireframe, Helios-style.
    var homeOutline by remember(home) {
        mutableStateOf(
            homeSquare(home.latitude, home.longitude, 6.0).coordinates().first().dropLast(1) to 10f,
        )
    }
    // Building footprints harvested ONCE per zoom band for the shadow pass — querying the
    // renderer on every rebuild is what made daytime unusable.
    val shadowCache = remember { ShadowCache() }
    var shadowDataVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // Hemisphere-aware rest bearing (Helios): face the sun's path — south-up in the northern
    // hemisphere, north-up in the southern.
    val restBearing = if (home.latitude >= 0) 180.0 else 0.0
    val initialCamera = remember(home) {
        CameraPosition.Builder().target(homeLatLng).zoom(REST_ZOOM).tilt(REST_TILT).bearing(restBearing).build()
    }
    val styleUrl = if (darkTheme) OPENFREEMAP_FIORD else OPENFREEMAP_LIBERTY

    val mapView = remember {
        ensureMapLibre(context)
        val opts = MapLibreMapOptions.createFromAttributes(context).textureMode(true).camera(initialCamera)
        MapView(context, opts).apply { onCreate(null) }
    }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    DisposableEffect(LocalLifecycleOwner.current, mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Bumped on every camera frame so the cloud canvas re-projects while the camera animates.
    var camStamp by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    fun push(map: MapLibreMap) {
        val p = map.projection.toScreenLocation(homeLatLng)
        camStamp++
        // Roof point: the building height converted to screen px through the pitch, so the
        // pin floats just above the extrusion instead of sitting flat on its base.
        val mpp = map.projection.getMetersPerPixelAtLatitude(home.latitude)
        val tiltRad = Math.toRadians(map.cameraPosition.tilt)
        val roofPx = if (mpp > 0) (homeHeightM.value / mpp * kotlin.math.sin(tiltRad)).toFloat() else 0f
        onCamera(
            Offset(p.x, p.y),
            Offset(p.x, p.y - roofPx),
            map.cameraPosition.bearing.toFloat(),
            map.cameraPosition.tilt.toFloat(),
        )
    }

    // Harvest the visible building footprints into the cache (main thread — required by
    // queryRenderedFeatures) — but only when empty or the zoom band changed; the camera is
    // pinned to the home, so the building set is otherwise stable.
    fun harvestShadowBuildings(map: MapLibreMap) {
        val band = (map.cameraPosition.zoom * 2).toInt()
        if (shadowCache.buildings != null && shadowCache.zoomBand == band) return
        if (mapView.width <= 0 || mapView.height <= 0) return
        val features = runCatching {
            map.queryRenderedFeatures(
                android.graphics.RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat()),
                BUILDINGS_LAYER,
            )
        }.getOrNull() ?: return
        if (features.isEmpty()) return
        val out = ArrayList<ShadowBuilding>()
        for (f in features) {
            if (out.size >= 250) break
            val g = f.geometry()
            val rings = when (g) {
                is Polygon -> listOfNotNull(g.coordinates().firstOrNull())
                is org.maplibre.geojson.MultiPolygon -> g.coordinates().mapNotNull { it.firstOrNull() }
                else -> emptyList()
            }
            val h = runCatching { f.getNumberProperty("render_height")?.toDouble() }.getOrNull() ?: 8.0
            for (ring in rings) {
                if (ring.size < 3) continue
                // Simplify long rings — shadow hulls don't need vertex-exact footprints.
                val step = (ring.size / 20).coerceAtLeast(1)
                out.add(ShadowBuilding(ring.filterIndexed { i, _ -> i % step == 0 }, h))
            }
        }
        // Tile borders duplicate buildings across fragments; cheap dedupe by anchor vertex.
        shadowCache.buildings = out.distinctBy {
            Triple(it.ring.first().longitude(), it.ring.first().latitude(), it.ring.size)
        }
        shadowCache.zoomBand = band
        shadowDataVersion++
    }

    // Hide the GL clear + tile pop-in behind a fade, like the location widget does —
    // with the same shimmering map skeleton underneath while tiles stream in.
    var ready by remember(mapView) { mutableStateOf(false) }
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(380), label = "energy_map_reveal")
    val currentOnReady by androidx.compose.runtime.rememberUpdatedState(onReady)
    LaunchedEffect(ready) { if (ready) currentOnReady() }
    Box(modifier = modifier) {
        if (alpha < 1f) MapShimmer(dark = darkTheme, modifier = Modifier.fillMaxSize())
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize().alpha(alpha))

        // Helios-style orbit gestures. Consuming the drag here is also what stops the
        // scrollable Energy screen from hijacking it. In weather mode the camera is a fixed
        // top-down frame, so the orbit gestures stand down (and the page can scroll again).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(homeLatLng, weatherMode) {
                    if (weatherMode) return@pointerInput
                    detectTransformGestures { _, pan, zoom, twist ->
                        val map = mapRef ?: return@detectTransformGestures
                        val cur = map.cameraPosition
                        val dpX = pan.x / density
                        val dpY = pan.y / density
                        val bearing = cur.bearing - dpX * 0.45 + twist
                        val tilt = (cur.tilt - dpY * 0.30).coerceIn(TILT_MIN, TILT_MAX)
                        val zoomed = (cur.zoom + log2(zoom.toDouble())).coerceIn(ZOOM_MIN, ZOOM_MAX)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(homeLatLng)
                            .bearing(bearing)
                            .tilt(tilt)
                            .zoom(zoomed)
                            .build()
                        push(map)
                    }
                }
                .pointerInput(homeLatLng, weatherMode) {
                    if (weatherMode) return@pointerInput
                    detectTapGestures(
                        // Bracket every map touch with the gesture haptics the rest of the
                        // app uses, so orbiting the home has physical feedback.
                        onPress = {
                            haptic.gestureStart()
                            tryAwaitRelease()
                            haptic.gestureEnd()
                        },
                        onDoubleTap = {
                            haptic.confirm()
                            mapRef?.animateCamera(CameraUpdateFactory.newCameraPosition(initialCamera), 650)
                        },
                    )
                },
        )

        // Home highlight (Helios's focal-building treatment): the real footprint traced as
        // a glowing screen-space wireframe — bright roof ring, dimmer ground ring, faint
        // vertical edges — over a soft pool of light under the building. Projected through
        // the live camera every frame, so it stays welded to the building while orbiting.
        if (!weatherMode) {
            // Fades in WITH the map: drawing the wireframe + under-glow over the shimmer
            // skeleton (before any building exists on screen) reads as broken UI.
            Canvas(Modifier.fillMaxSize().alpha(alpha)) {
                @Suppress("UNUSED_EXPRESSION") camStamp
                val map = mapRef ?: return@Canvas
                val (ring, heightM) = homeOutline
                if (ring.size < 3) return@Canvas
                val mpp = map.projection.getMetersPerPixelAtLatitude(home.latitude)
                if (mpp <= 0) return@Canvas
                val lift = (heightM / mpp * kotlin.math.sin(Math.toRadians(map.cameraPosition.tilt))).toFloat()
                val base = ring.map { p ->
                    val s = map.projection.toScreenLocation(LatLng(p.latitude(), p.longitude()))
                    Offset(s.x, s.y)
                }
                // Skip entirely when the building is off-screen (e.g. mid fly-out).
                if (base.none { it.x in -200f..size.width + 200f && it.y in -200f..size.height + 200f }) return@Canvas
                val top = base.map { Offset(it.x, it.y - lift) }

                // Soft pool of light under the footprint.
                val centroid = Offset(base.sumOf { it.x.toDouble() }.toFloat() / base.size,
                    base.sumOf { it.y.toDouble() }.toFloat() / base.size)
                val spread = base.maxOf {
                    kotlin.math.hypot((it.x - centroid.x).toDouble(), (it.y - centroid.y).toDouble())
                }.toFloat()
                val glowR = spread * 1.8f + 24.dp.toPx()
                drawCircle(
                    brush = androidx.compose.ui.graphics.Brush.radialGradient(
                        colorStops = arrayOf(0f to HomeGlow.copy(alpha = 0.20f), 1f to Color.Transparent),
                        center = centroid,
                        radius = glowR,
                    ),
                    radius = glowR,
                    center = centroid,
                )

                fun ringPath(points: List<Offset>): androidx.compose.ui.graphics.Path =
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                        close()
                    }
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3.dp.toPx())
                // Verticals faintest, ground ring mid, roof ring brightest — reads as glass.
                base.forEachIndexed { i, b ->
                    drawLine(HomeEdge.copy(alpha = 0.28f), b, top[i], strokeWidth = 1.dp.toPx())
                }
                drawPath(ringPath(base), color = HomeEdge.copy(alpha = 0.45f), style = stroke)
                drawPath(
                    ringPath(top),
                    color = HomeEdge.copy(alpha = 0.95f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx()),
                )
            }
        }

        // Weather-mode cloud field: the real Open-Meteo coverage grid baked into a
        // noise-carved texture (see CloudField.kt, ported from Helios's cloud shader) and
        // stretched over the grid's geographic bounds. Fades in as the camera flattens to
        // the top-down weather pose so it never smears across the 3D transition.
        if (weatherMode && cloudGrid != null) {
            val cloudImage by produceState<ImageBitmap?>(initialValue = null, cloudGrid) {
                value = withContext(Dispatchers.Default) { buildCloudField(cloudGrid) }
            }
            val bounds = remember(cloudGrid) {
                val lats = cloudGrid.cells.map { it.lat }
                val lons = cloudGrid.cells.map { it.lon }
                doubleArrayOf(lats.min(), lats.max(), lons.min(), lons.max())
            }
            Canvas(Modifier.fillMaxSize()) {
                @Suppress("UNUSED_EXPRESSION") camStamp
                val img = cloudImage ?: return@Canvas
                val map = mapRef ?: return@Canvas
                val flatness = (1.0 - map.cameraPosition.tilt / 30.0).coerceIn(0.0, 1.0).toFloat()
                if (flatness <= 0.02f) return@Canvas
                val nw = map.projection.toScreenLocation(LatLng(bounds[1], bounds[2]))
                val se = map.projection.toScreenLocation(LatLng(bounds[0], bounds[3]))
                val wPx = se.x - nw.x
                val hPx = se.y - nw.y
                if (wPx <= 0f || hPx <= 0f) return@Canvas
                drawImage(
                    image = img,
                    dstOffset = IntOffset(nw.x.toInt(), nw.y.toInt()),
                    dstSize = IntSize(wPx.toInt(), hPx.toInt()),
                    alpha = flatness,
                )
            }
        }
    }

    // Weather-mode camera: relax the orbit constraints and fly top-down over the area;
    // restore the 3D pose (and only then the constraints, so the zoom doesn't snap) on exit.
    LaunchedEffect(mapView, weatherMode) {
        mapView.getMapAsync { map ->
            if (weatherMode) {
                map.setMinZoomPreference(3.0)
                map.setMinPitchPreference(0.0)
                val pos = CameraPosition.Builder()
                    .target(homeLatLng).zoom(WEATHER_ZOOM).tilt(0.0).bearing(0.0).build()
                map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 900)
            } else {
                map.animateCamera(
                    CameraUpdateFactory.newCameraPosition(initialCamera),
                    900,
                    object : MapLibreMap.CancelableCallback {
                        override fun onCancel() = Unit
                        override fun onFinish() {
                            map.setMinZoomPreference(ZOOM_MIN)
                            map.setMinPitchPreference(TILT_MIN)
                        }
                    },
                )
            }
        }
    }

    // Style + camera — runs once per home/style; lighting updates live elsewhere so the
    // style is never reloaded on a clock tick.
    LaunchedEffect(mapView, home, styleUrl) {
        mapView.addOnDidBecomeIdleListener { ready = true }
        mapView.getMapAsync { map ->
            mapRef = map
            map.uiSettings.apply {
                setAllGesturesEnabled(false) // the Compose layer above owns interaction
                isCompassEnabled = false
                isAttributionEnabled = false
                isLogoEnabled = false
            }
            map.setMinZoomPreference(ZOOM_MIN)
            map.setMaxZoomPreference(ZOOM_MAX)
            map.setMinPitchPreference(TILT_MIN)
            map.setMaxPitchPreference(TILT_MAX)
            map.cameraPosition = initialCamera

            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                // Declutter (Helios `_suppressMapLayers`): POIs/transit/housenumbers off.
                // The basemap's flat building fills/outlines are hidden by their SOURCE
                // LAYER (not id guessing — Fiord names them differently than Liberty);
                // leaving them visible doubles every building with a ground-level shadow
                // copy offset from the tilted extrusions, which reads as broken shadows.
                style.layers.forEach { layer ->
                    if (layer.id.startsWith("hh-")) return@forEach
                    val id = layer.id.lowercase()
                    val sourceLayer = when (layer) {
                        is org.maplibre.android.style.layers.FillLayer -> layer.sourceLayer
                        is org.maplibre.android.style.layers.LineLayer -> layer.sourceLayer
                        is FillExtrusionLayer -> layer.sourceLayer
                        else -> null
                    }
                    val suppress = "poi" in id || "transit" in id || "airport" in id ||
                        "aerodrome" in id || "housenumber" in id ||
                        id.startsWith("building") || sourceLayer == "building"
                    if (suppress) {
                        runCatching { layer.setProperties(PropertyFactory.visibility(Property.NONE)) }
                    }
                }
                // Context buildings, extruded from the OpenMapTiles `building` source-layer.
                runCatching {
                    style.addLayer(
                        FillExtrusionLayer(BUILDINGS_LAYER, "openmaptiles")
                            .withSourceLayer("building")
                            .withProperties(
                                PropertyFactory.fillExtrusionHeight(Expression.get("render_height")),
                                PropertyFactory.fillExtrusionBase(Expression.get("render_min_height")),
                                PropertyFactory.fillExtrusionColor(baseBuildingColor(darkTheme).toArgb()),
                                PropertyFactory.fillExtrusionOpacity(0.92f),
                            ),
                    )
                }
                // Ground shadows cast by the buildings (Helios `projectExtrusionShadows`):
                // a fill layer under the extrusions, rebuilt from the live sun angle.
                runCatching {
                    style.addSource(GeoJsonSource(SHADOW_SRC))
                    style.addLayerBelow(
                        org.maplibre.android.style.layers.FillLayer(SHADOW_LAYER, SHADOW_SRC).withProperties(
                            PropertyFactory.fillColor(android.graphics.Color.BLACK),
                            PropertyFactory.fillOpacity(0.25f),
                        ),
                        BUILDINGS_LAYER,
                    )
                }
                // The home as a translucent accent envelope (Helios keeps the home looking
                // like a building, just highlighted — never an opaque colored block).
                // Seeded with a square (a circle extrudes into a weird cylinder); upgraded
                // below to the REAL footprint once tiles render.
                runCatching {
                    style.addSource(GeoJsonSource(HOME_SRC, homeSquare(home.latitude, home.longitude, 6.0)))
                    style.addLayer(
                        FillExtrusionLayer(HOME_LAYER, HOME_SRC).withProperties(
                            PropertyFactory.fillExtrusionHeight(10f),
                            // Glassy ghost body (Helios): the highlight is carried by the
                            // wireframe + under-glow overlay, not a colored block.
                            PropertyFactory.fillExtrusionColor(android.graphics.Color.parseColor("#D9E6FA")),
                            PropertyFactory.fillExtrusionOpacity(0.12f),
                            PropertyFactory.fillExtrusionVerticalGradient(true),
                        ),
                    )
                }
            }

            // Once the basemap has actually rendered, swap the placeholder square for the
            // real building footprint under the pin (Helios highlights the true polygon).
            // The highlight is scaled up ~8% and made slightly taller so it fully ENVELOPS
            // the context extrusion — coplanar walls z-fight and read as broken geometry.
            var homeFootprintSet = false
            mapView.addOnDidBecomeIdleListener {
                if (homeFootprintSet) return@addOnDidBecomeIdleListener
                map.getStyle { style ->
                    val p = map.projection.toScreenLocation(homeLatLng)
                    val features = runCatching { map.queryRenderedFeatures(p, BUILDINGS_LAYER) }.getOrNull()
                    val feature = features?.firstOrNull() ?: return@getStyle
                    val src = style.getSource(HOME_SRC) as? GeoJsonSource ?: return@getStyle
                    runCatching {
                        val envelope = scaleGeometry(feature.geometry(), 1.08) ?: return@runCatching
                        src.setGeoJson(org.maplibre.geojson.Feature.fromGeometry(envelope))
                        val renderHeight = runCatching { feature.getNumberProperty("render_height")?.toFloat() }
                            .getOrNull() ?: 10f
                        val envelopeHeight = renderHeight.coerceAtLeast(6f) + 1.2f
                        (style.getLayer(HOME_LAYER) as? FillExtrusionLayer)?.setProperties(
                            PropertyFactory.fillExtrusionHeight(envelopeHeight),
                        )
                        homeHeightM.value = envelopeHeight
                        // The wireframe traces the REAL footprint at the building's own
                        // roof height (simplified — edge strokes don't need every vertex).
                        val realRing = when (val g = feature.geometry()) {
                            is Polygon -> g.coordinates().firstOrNull()
                            is org.maplibre.geojson.MultiPolygon ->
                                g.coordinates().mapNotNull { it.firstOrNull() }.maxByOrNull { it.size }
                            else -> null
                        }
                        if (realRing != null && realRing.size >= 4) {
                            val step = (realRing.size / 40).coerceAtLeast(1)
                            homeOutline = realRing.dropLast(1).filterIndexed { i, _ -> i % step == 0 } to
                                renderHeight.coerceAtLeast(6f)
                        }
                        homeFootprintSet = true
                        push(map)
                    }
                }
            }

            // Keep the overlay glued during animations (double-tap reset, fling settle).
            // Shadows are GEOGRAPHIC — camera motion never rebuilds them; idle only
            // refreshes the footprint cache when the zoom band changed.
            map.addOnCameraIdleListener {
                push(map)
                harvestShadowBuildings(map)
            }
            map.addOnCameraMoveListener { push(map) }
            mapView.addOnDidBecomeIdleListener {
                push(map)
                harvestShadowBuildings(map)
            }
        }
        kotlinx.coroutines.delay(1500)
        ready = true
    }

    // Live lighting (Helios `lighting.ts`): tint the context buildings and steer the style's
    // light by the sun, updating layer paint in place — no style reload, no tile refetch.
    val litBuildingArgb = remember(darkTheme, (sunAltitudeDeg * 2).roundToInt()) {
        HeliosLighting.buildingColor(baseBuildingColor(darkTheme), sunAltitudeDeg).toArgb()
    }
    val lightAzimuth = remember(sunAzimuthDeg.roundToInt()) { sunAzimuthDeg }
    LaunchedEffect(mapView, litBuildingArgb, lightAzimuth) {
        mapView.getMapAsync { map ->
            map.getStyle { style ->
                (style.getLayer(BUILDINGS_LAYER) as? FillExtrusionLayer)
                    ?.setProperties(PropertyFactory.fillExtrusionColor(litBuildingArgb))
                runCatching {
                    style.light?.apply {
                        // MAP anchor = azimuth in compass degrees, so extrusion faces shade
                        // from the sun's true direction regardless of camera bearing.
                        anchor = Property.ANCHOR_MAP
                        position = Position(1.15f, lightAzimuth, HeliosLighting.lightPolar(sunAltitudeDeg))
                    }
                }
            }
        }
    }

    // Shadow pass: hulls computed OFF the main thread from the cached footprints, debounced
    // so scrubbing the time slider only recomputes when the drag pauses, and bucketed (2°
    // azimuth / 1° altitude) so the 30s clock tick rarely retriggers it. The only main-thread
    // work left is handing the prebuilt GeoJSON string to the source.
    val shadowAzBucket = (sunAzimuthDeg / 2f).roundToInt()
    val shadowAltBucket = sunAltitudeDeg.roundToInt()
    LaunchedEffect(mapRef, shadowAzBucket, shadowAltBucket, weatherMode, shadowDataVersion) {
        val map = mapRef ?: return@LaunchedEffect
        kotlinx.coroutines.delay(180)
        val style = map.style ?: return@LaunchedEffect
        val src = style.getSource(SHADOW_SRC) as? GeoJsonSource ?: return@LaunchedEffect
        if (weatherMode || sunAltitudeDeg < 2f) {
            src.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
            return@LaunchedEffect
        }
        val buildings = shadowCache.buildings ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) {
            buildShadowJson(buildings, sunAzimuthDeg, sunAltitudeDeg)
        }
        src.setGeoJson(json)
    }
}

/** One simplified building footprint kept for the shadow pass. */
private class ShadowBuilding(val ring: List<Point>, val heightM: Double)

private class ShadowCache {
    var buildings: List<ShadowBuilding>? = null
    var zoomBand: Int = -1
}

/**
 * Pure CPU shadow projection (safe off the main thread): each footprint swept opposite the
 * sun's azimuth by `height / tan(altitude)`, hulled, and serialized to a GeoJSON string.
 */
private fun buildShadowJson(buildings: List<ShadowBuilding>, sunAzDeg: Float, sunAltDeg: Float): String {
    val altRad = Math.toRadians(sunAltDeg.toDouble())
    val castBearing = Math.toRadians((sunAzDeg + 180.0).mod(360.0))
    val out = ArrayList<org.maplibre.geojson.Feature>(buildings.size)
    for (b in buildings) {
        val length = (b.heightM / kotlin.math.tan(altRad)).coerceAtMost(90.0)
        if (length < 0.5) continue
        val lat0 = b.ring[0].latitude()
        val dLat = kotlin.math.cos(castBearing) * length / 111_320.0
        val dLon = kotlin.math.sin(castBearing) * length / (111_320.0 * cos(Math.toRadians(lat0)))
        val swept = ArrayList<Point>(b.ring.size * 2)
        b.ring.forEach { pt ->
            swept.add(pt)
            swept.add(Point.fromLngLat(pt.longitude() + dLon, pt.latitude() + dLat))
        }
        val hull = convexHull(swept)
        if (hull.size >= 3) {
            out.add(org.maplibre.geojson.Feature.fromGeometry(Polygon.fromLngLats(listOf(hull + hull.first()))))
        }
    }
    return org.maplibre.geojson.FeatureCollection.fromFeatures(out).toJson()
}

/** Monotone-chain convex hull over lng/lat points. */
private fun convexHull(points: List<Point>): List<Point> {
    val pts = points
        .distinctBy { it.longitude() to it.latitude() }
        .sortedWith(compareBy({ it.longitude() }, { it.latitude() }))
    if (pts.size < 3) return pts
    fun cross(o: Point, a: Point, b: Point): Double =
        (a.longitude() - o.longitude()) * (b.latitude() - o.latitude()) -
            (a.latitude() - o.latitude()) * (b.longitude() - o.longitude())
    val lower = ArrayList<Point>()
    for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
            lower.removeAt(lower.size - 1)
        }
        lower.add(p)
    }
    val upper = ArrayList<Point>()
    for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
            upper.removeAt(upper.size - 1)
        }
        upper.add(p)
    }
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    return lower + upper
}

private fun baseBuildingColor(dark: Boolean): Color =
    if (dark) Color(0xFF5A6378) else Color(0xFFB9BDC9)

/** Uniformly scale a (Multi)Polygon about its centroid — used to envelop the home building. */
private fun scaleGeometry(geometry: org.maplibre.geojson.Geometry?, factor: Double): org.maplibre.geojson.Geometry? {
    fun scaleRing(ring: List<Point>): List<Point> {
        if (ring.isEmpty()) return ring
        val cx = ring.sumOf { it.longitude() } / ring.size
        val cy = ring.sumOf { it.latitude() } / ring.size
        return ring.map { Point.fromLngLat(cx + (it.longitude() - cx) * factor, cy + (it.latitude() - cy) * factor) }
    }
    return when (geometry) {
        is Polygon -> Polygon.fromLngLats(geometry.coordinates().map { scaleRing(it) })
        is org.maplibre.geojson.MultiPolygon ->
            org.maplibre.geojson.MultiPolygon.fromLngLats(geometry.coordinates().map { poly -> poly.map { scaleRing(it) } })
        else -> null
    }
}

/** Building-shaped placeholder footprint: a square of ±[halfMeters] around the home. */
private fun homeSquare(lat: Double, lon: Double, halfMeters: Double): Polygon {
    val dLat = halfMeters / 111_320.0
    val dLon = halfMeters / (111_320.0 * cos(Math.toRadians(lat)))
    val ring = listOf(
        Point.fromLngLat(lon - dLon, lat - dLat),
        Point.fromLngLat(lon + dLon, lat - dLat),
        Point.fromLngLat(lon + dLon, lat + dLat),
        Point.fromLngLat(lon - dLon, lat + dLat),
        Point.fromLngLat(lon - dLon, lat - dLat),
    )
    return Polygon.fromLngLats(listOf(ring))
}

private const val BUILDINGS_LAYER = "hh-buildings"
private const val HOME_SRC = "hh-home-src"
private const val HOME_LAYER = "hh-home"
private const val SHADOW_SRC = "hh-shadow-src"
private const val SHADOW_LAYER = "hh-shadows"
