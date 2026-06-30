package com.uc.homehealth.ui.components

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.uc.homehealth.data.HaPersonLocation
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors
import dev.chrisbanes.haze.HazeState
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

private const val TILE_ZOOM = 14.5
private const val DETAIL_ZOOM = 15.0

// ─── Dashboard tile: static map preview ──────────────────────────────────────
// A non-interactive snapshot (rendered off-screen via MapSnapshotter) keeps the
// More-tab scroll smooth and is the exact path the planned home-screen App Widget
// will reuse. Tapping opens LocationDetailSheet with the live, pannable map.
@Composable
fun LocationWidgetTile(
    name: String,
    subtitle: String,
    location: HaPersonLocation?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }
    val displayName = location?.friendlyName?.takeIf { it.isNotBlank() } ?: name

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .then(
                if (enabled) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptic.tick(); onClick() },
                    onLongClick = { haptic.confirm(); onLongPress() },
                ) else Modifier
            )
            .padding(12.dp),
    ) {
        LocationHeader(name = displayName, location = location)
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (location != null && location.hasMap) {
                MapPreview(location = location)
            } else {
                NoLocationPlaceholder(present = location != null)
            }
        }
    }
}

@Composable
private fun MapPreview(location: HaPersonLocation) {
    val context = LocalContext.current
    val custom = MaterialTheme.customColors
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val fillArgb = custom.cyan.toArgb()
    val inkArgb = glanceInkOn(custom.cyan).toArgb()
    val initial = location.friendlyName.firstOrNull()?.toString() ?: "?"

    var size by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    val inspection = LocalInspectionMode.current

    // Re-snapshot when the position, tile size, or theme changes.
    androidx.compose.runtime.LaunchedEffect(location.latitude, location.longitude, size, dark) {
        val lat = location.latitude
        val lng = location.longitude
        if (inspection || lat == null || lng == null || size.width == 0 || size.height == 0) return@LaunchedEffect
        val pinPx = (size.height * 0.16f).toInt().coerceIn(48, 120)
        val pin = buildPersonPin(pinPx, initial, fillArgb, inkArgb)
        val bmp = runCatching {
            captureLocationSnapshot(
                context = context,
                widthPx = size.width,
                heightPx = size.height,
                lat = lat,
                lng = lng,
                zoom = TILE_ZOOM,
                styleUrl = mapStyleUrl(dark),
                pin = pin,
            )
        }.getOrNull()
        if (bmp != null) frame = bmp.asImageBitmap()
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { size = it }) {
        Crossfade(targetState = frame, animationSpec = tween(220), label = "map_snapshot") { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = "Map showing ${location.friendlyName}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MapShimmer(dark = dark, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

// Loading skeleton shown while a map snapshot/live map is still rendering — a diagonal
// highlight sweeps across a base tint (matched to the active CARTO style) with a faint
// centered pin, so the area reads as "map loading" rather than blank. Internal: the
// Energy hero's 3D map reuses it so every map in the app loads with the same shimmer.
@Composable
internal fun MapShimmer(dark: Boolean, modifier: Modifier = Modifier) {
    val base = if (dark) Color(0xFF17191B) else Color(0xFFE7E5E0)
    val highlight = if (dark) Color(0xFF282B2E) else Color(0xFFF6F4F0)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "map_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "map_shimmer_x",
    )
    val width = size.width.toFloat().coerceAtLeast(1f)
    val band = width * 0.55f
    val x = progress * (width + band) - band
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(x, 0f),
        end = Offset(x + band, size.height.toFloat()),
    )
    Box(
        modifier = modifier.onSizeChanged { size = it }.background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Map,
            contentDescription = null,
            tint = (if (dark) Color.White else Color.Black).copy(alpha = 0.12f),
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun NoLocationPlaceholder(present: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.LocationOff,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (present) "No GPS coordinates" else "Waiting for location…",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun LocationHeader(name: String, location: HaPersonLocation?) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(CircleShape).background(custom.cyan),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = glanceInkOn(custom.cyan),
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = name,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = cs.onSurface,
            )
            Text(
                text = relativeTime(location?.lastUpdatedEpochMs),
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        location?.let {
            Text(
                text = prettyZone(it.zone),
                fontFamily = InstrumentSerifFamily,
                fontSize = 20.sp,
                color = cs.onSurface,
            )
        }
    }
}

// ─── Detail sheet: live interactive map ──────────────────────────────────────
@Composable
fun LocationDetailSheet(
    visible: Boolean,
    entityId: String,
    location: HaPersonLocation?,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.9f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp),
        ) {
            Text(
                text = location?.friendlyName?.takeIf { it.isNotBlank() } ?: entityId.substringAfterLast('.'),
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = buildString {
                    append(entityId)
                    location?.let { append("  ·  ").append(prettyZone(it.zone)) }
                },
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            val lat = location?.latitude
            val lng = location?.longitude
            if (location != null && lat != null && lng != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh),
                ) {
                    LiveLocationMap(
                        lat = lat,
                        lng = lng,
                        accuracyMeters = location.gpsAccuracyMeters?.toDouble(),
                        initial = location.friendlyName.firstOrNull()?.toString() ?: "?",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(8.dp))
                location.lastUpdatedEpochMs?.let {
                    Text(
                        text = "Updated ${relativeTime(it)}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Tap(
                    onClick = {
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(location.friendlyName)})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            tint = cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Open in Maps",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    NoLocationPlaceholder(present = location != null)
                }
            }
        }
    }
}

@Composable
private fun LiveLocationMap(
    lat: Double,
    lng: Double,
    accuracyMeters: Double?,
    initial: String,
    modifier: Modifier = Modifier,
) {
    val custom = MaterialTheme.customColors
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val styleUrl = mapStyleUrl(dark)
    val fillArgb = custom.cyan.toArgb()
    val inkArgb = glanceInkOn(custom.cyan).toArgb()
    val accuracyArgb = custom.cyan.toArgb()

    // Start the map already centered on the person so the very first rendered frame is at
    // the right place — without this, MapLibre briefly shows the default world view.
    val initialCamera = remember(lat, lng) {
        CameraPosition.Builder().target(LatLng(lat, lng)).zoom(DETAIL_ZOOM).build()
    }
    val mapView = rememberMapViewWithLifecycle(initialCamera)

    // Keep the live map hidden behind a placeholder until it has actually rendered. This
    // hides MapLibre's white GL clear (shown before the style/tiles load) and the world-view
    // first frame; we reveal with a fade once the map reports idle (or a safety timeout).
    var ready by remember(mapView) { mutableStateOf(false) }
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (ready) 1f else 0f,
        animationSpec = tween(380),
        label = "map_reveal",
    )
    Box(modifier = modifier) {
        // Skeleton loader underneath until the live map has faded fully in.
        if (alpha < 1f) MapShimmer(dark = dark, modifier = Modifier.matchParentSize())
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )
    }

    // Configure once per position/style change. Each setStyle() starts from a clean
    // style, so re-adding the same source/layer ids here is safe (no duplicate-id throw);
    // keeping this out of AndroidView.update avoids re-running it on every recomposition.
    androidx.compose.runtime.LaunchedEffect(mapView, lat, lng, accuracyMeters, styleUrl) {
        // Reveal once the map finishes rendering (tiles + camera settled). This rendering
        // event lives on MapView, not MapLibreMap.
        mapView.addOnDidBecomeIdleListener { ready = true }
        mapView.getMapAsync { map ->
            map.cameraPosition = initialCamera
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                // Accuracy halo first so the pin draws on top.
                if (accuracyMeters != null && accuracyMeters > 0) {
                    style.addSource(GeoJsonSource(ACC_SRC, accuracyCircle(lat, lng, accuracyMeters)))
                    style.addLayer(
                        FillLayer(ACC_LAYER, ACC_SRC).withProperties(
                            PropertyFactory.fillColor(accuracyArgb),
                            PropertyFactory.fillOpacity(0.18f),
                            PropertyFactory.fillOutlineColor(accuracyArgb),
                        )
                    )
                }
                style.addImage(PIN_IMAGE, buildPersonPin(96, initial, fillArgb, inkArgb))
                style.addSource(GeoJsonSource(PIN_SRC, Point.fromLngLat(lng, lat)))
                style.addLayer(
                    SymbolLayer(PIN_LAYER, PIN_SRC).withProperties(
                        PropertyFactory.iconImage(PIN_IMAGE),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    )
                )
            }
        }
        // Safety reveal in case idle is slow (e.g. tiles still streaming): by now the
        // camera is preset, so there's no world-view flash to expose.
        kotlinx.coroutines.delay(1500)
        ready = true
    }
}

@Composable
private fun rememberMapViewWithLifecycle(initialCamera: CameraPosition): MapView {
    val context = LocalContext.current
    val mapView = remember {
        ensureMapLibre(context)
        // Render via TextureView, not the default SurfaceView. A SurfaceView sits in its
        // own window layer and punches a hole through the bottom sheet, flashing the white
        // window background during the sheet's enter animation (before the GL surface is
        // composited). TextureView draws inside the view hierarchy, so the map animates
        // and composites together with the sheet. We also preset the camera so the first
        // frame is centered on the person rather than the default world view.
        val options = MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .camera(initialCamera)
        MapView(context, options).apply { onCreate(null) }
    }
    DisposableEffect(LocalLifecycleOwner.current, mapView) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private const val PIN_IMAGE = "person-pin"
private const val PIN_SRC = "person-src"
private const val PIN_LAYER = "person-layer"
private const val ACC_SRC = "accuracy-src"
private const val ACC_LAYER = "accuracy-layer"

private fun prettyZone(zone: String): String = when (zone.lowercase()) {
    "home" -> "Home"
    "not_home", "away" -> "Away"
    "unknown", "unavailable", "" -> "—"
    else -> zone.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun relativeTime(epochMs: Long?): String {
    if (epochMs == null) return ""
    return DateUtils.getRelativeTimeSpanString(
        epochMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
}
