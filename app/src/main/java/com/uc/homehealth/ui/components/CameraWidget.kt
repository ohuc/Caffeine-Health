package com.uc.homehealth.ui.components

import android.graphics.drawable.BitmapDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.CameraPtz
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.uc.homehealth.R
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

// ── Collapsed tile — square-ish snapshot card with name overlay ──────────────
// Snapshot URL is provided by the repo (uses the entity's rotating access_token
// query param so no auth headers are required for Coil). We re-key the request
// every SNAPSHOT_REFRESH_MS so the user sees a fresh frame without burning the
// whole tile + animation. This tile intentionally polls a still snapshot rather
// than streaming — live HLS is reserved for the opened detail sheet so a wall of
// camera tiles stays light on battery/bandwidth (matters most on remote HA).

// Snapshots are decoded down to the tile's pixel size (see `.size(...)` below) so a
// wall of cameras isn't re-decoding full-res frames every tick — that full-res decode
// (plus a 1 s cadence) was the source of the jank. 2 s + downscale keeps it light.
private const val SNAPSHOT_REFRESH_MS = 2_000L

// HLS fallback: if no decodable video frame is drawn within this window after the playlist
// resolves, move on to the MJPEG fallback. Kept short because ExoPlayer's HLS reliably
// stalls (never leaving BUFFERING) over a remote/Cloudflare link — no point waiting long.
private const val HLS_RENDER_TIMEOUT_MS = 7_000L

// PTZ press-and-hold: fire one nudge on tap; if the button is still held after this
// delay, keep firing every PTZ_REPEAT_MS for continuous, fine-grained panning.
private const val PTZ_HOLD_START_MS = 350L
private const val PTZ_REPEAT_MS = 300L

// Max digital zoom factor for the detail-sheet pinch-to-zoom (and the tile).
private const val MAX_CAMERA_ZOOM = 4f

// ── Digital pinch-to-zoom (detail sheet) ─────────────────────────────────────
// Two-finger transform scales/pans the live view like pinching a photo — purely on the
// client, independent of PTZ. The gesture lives on the player container; we apply the
// resulting scale/offset two ways because the players don't share a surface type: the
// SurfaceView-backed players (WebRTC, go2rtc MSE) take a View transform (a Compose
// graphicsLayer can't move a SurfaceView's separate surface), while the Compose-drawn
// ones (MJPEG, the snapshot underlay) take a graphicsLayer. View translation, graphics-
// layer translation and pointer pan are all in pixels, so the same offset drives both.

// Pinch/pan gesture. The getters read the hoisted zoom state on every callback (the
// pointerInput coroutine is long-lived, so reading through getters avoids stale captures);
// onChange writes the clamped result back. Pan is bounded so the image can't leave frame.
private fun Modifier.cameraZoomGesture(
    scale: () -> Float,
    offset: () -> Offset,
    size: () -> IntSize,
    onChange: (Float, Offset) -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectTransformGestures { _, pan, zoom, _ ->
        val newScale = (scale() * zoom).coerceIn(1f, MAX_CAMERA_ZOOM)
        val newOffset = if (newScale <= 1f) {
            Offset.Zero
        } else {
            val box = size()
            val maxX = (box.width * (newScale - 1f)) / 2f
            val maxY = (box.height * (newScale - 1f)) / 2f
            Offset(
                (offset().x + pan.x).coerceIn(-maxX, maxX),
                (offset().y + pan.y).coerceIn(-maxY, maxY),
            )
        }
        onChange(newScale, newOffset)
    }
}

// Applies the digital zoom to a hosted player View (SurfaceView or TextureView). Scale
// pivots on the View's center by default — matching the graphicsLayer path.
private fun View.applyCameraZoom(scale: Float, offset: Offset) {
    scaleX = scale
    scaleY = scale
    translationX = offset.x
    translationY = offset.y
}

@Composable
fun CameraWidgetTile(
    name: String,
    subtitle: String,
    snapshotUrl: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    ptz: CameraPtz? = null,
    onPtzPress: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }

    // ── Pinch-to-zoom (digital, like pinching a photo) ───────────────────────
    // Two-finger transform scales/pans the snapshot via graphicsLayer. Single-finger
    // taps still reach combinedClickable (a tap has no movement, so the transform
    // detector never consumes it). Pan is clamped so the image can't be dragged off.
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    // Double-buffered snapshot. Each poll decodes off-screen into a bitmap and we only
    // swap the displayed `frame` once it has fully decoded, so the tile never blanks to
    // its background between polls. The Crossfade in the body then dissolves old→new
    // instead of hard-cutting. Together these remove the periodic "blink" the old
    // single-AsyncImage approach produced (it re-keyed Coil's cache every tick, and when
    // the previous frame got evicted mid-fetch the tile flashed to background). `frame`
    // is retained across WebSocket blips, so a disconnect just freezes on the last frame
    // rather than flashing the camera-off badge. This stays a light still-snapshot poll,
    // not a live stream — a wall of cameras must stay easy on battery/bandwidth; live
    // video is reserved for the opened detail sheet.
    val context = LocalContext.current
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(snapshotUrl) {
        val url = snapshotUrl ?: return@LaunchedEffect
        val loader = context.imageLoader
        while (true) {
            val req = ImageRequest.Builder(context)
                .data(url)
                // Decode to the tile's pixel size, not the camera's full resolution —
                // the single biggest win against scroll jank on a wall of cameras.
                .apply { if (boxSize.width > 0 && boxSize.height > 0) size(boxSize.width, boxSize.height) }
                // We hold the decoded frame in state ourselves, so skip Coil's caches
                // entirely (no per-tick memory churn, no disk entries piling up).
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .build()
            val result = loader.execute(req)
            if (result is SuccessResult) {
                (result.drawable as? BitmapDrawable)?.bitmap?.let { frame = it.asImageBitmap() }
            }
            delay(SNAPSHOT_REFRESH_MS)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .onSizeChanged { boxSize = it }
            .then(
                if (enabled && snapshotUrl != null) Modifier.pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale
                        if (newScale <= 1f) {
                            offset = Offset.Zero
                        } else {
                            val maxX = (boxSize.width * (newScale - 1f)) / 2f
                            val maxY = (boxSize.height * (newScale - 1f)) / 2f
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                } else Modifier
            )
            .then(
                if (enabled) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptic.tick(); onClick() },
                    onLongClick = { haptic.confirm(); onLongPress() },
                ) else Modifier
            ),
    ) {
        // Cross-dissolve between snapshot frames. `frame` only changes once a new frame
        // has fully decoded, so this never fades through the background — no blink.
        Crossfade(
            targetState = frame,
            animationSpec = tween(220),
            label = "snapshot",
            modifier = Modifier.fillMaxSize(),
        ) { bmp ->
            if (bmp != null) {
                Image(
                    bitmap = bmp,
                    contentDescription = name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Crop,
                )
            } else {
                // No frame decoded yet (first load / offline before any frame) — badge.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.VideocamOff,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Bottom gradient + caption for legibility over arbitrary scenes.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.White.copy(alpha = 0.14f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                ) {
                    Text(
                        text = name,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }

    }
}

// ── Detail sheet — low-latency live (HLS fallback) ───────────────────────────
// The first attempt depends on configuration:
//   • go2rtc URL set  → MSE (fMP4 over WebSocket) via `Go2RtcMsePlayer`. Best path for
//     remote/Cloudflare since it's pure WS (no UDP/TURN) and low-latency.
//   • otherwise        → Home Assistant's unified WebRTC API via `WebRtcPlayer`
//     (sub-second on LAN; needs TURN to traverse remotely).
// If the chosen path can't establish (unsupported camera, demo mode, or a stall past
// the connect timeout) we fall back to HLS: resolve a `master.m3u8` via the suspend
// `getStreamUrl` callback (WS `camera/stream`) and play it with ExoPlayer. The snapshot
// + spinner cover the connect; on total failure, tap to retry (restarts from the top).

private sealed interface PlayPhase {
    data object Mse : PlayPhase           // Go2RtcMsePlayer mounted (connecting or live)
    data object WebRtc : PlayPhase        // WebRtcPlayer mounted (connecting or live)
    data object Resolving : PlayPhase     // primary gave up; resolving the HLS URL
    data class Hls(val url: String) : PlayPhase
    data object Mjpeg : PlayPhase         // last resort: camera_proxy_stream MJPEG push
    data object Failed : PlayPhase
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraDetailSheet(
    visible: Boolean,
    entityId: String,
    name: String,
    snapshotUrl: String?,
    webRtcClient: CameraWebRtcClient,
    supportsStream: Boolean = true,
    go2rtcUrl: String? = null,
    hazeState: HazeState? = null,
    ptz: CameraPtz? = null,
    onPtzPress: (String) -> Unit = {},
    getStreamUrl: suspend () -> String?,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Digital pinch-to-zoom for the live view (see helpers above). Reset whenever the sheet
    // reopens or the entity changes so each camera starts un-zoomed; the zoom persists
    // across player fallbacks (WebRTC → HLS → MJPEG) within one open session.
    var zoomScale by remember(visible, entityId) { mutableStateOf(1f) }
    var zoomOffset by remember(visible, entityId) { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState, maxHeightFraction = 0.9f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 20.dp),
        ) {
            Text(
                text = name,
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
            Text(
                text = entityId,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            // Snapshot-only camera (HA reports no STREAM feature, e.g. a Reolink
            // `*_snapshots_*` proxy) — there's nothing to connect to, so show the still
            // image with an honest caption instead of a spinner that could only ever
            // fail. The picker steers users toward the LIVE entity, but if one of these
            // is already on the dashboard we still handle it gracefully.
            if (!supportsStream) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cs.surfaceContainerHigh)
                        .onSizeChanged { viewSize = it }
                        .then(
                            if (snapshotUrl != null) Modifier.cameraZoomGesture(
                                scale = { zoomScale },
                                offset = { zoomOffset },
                                size = { viewSize },
                                onChange = { s, o -> zoomScale = s; zoomOffset = o },
                            ) else Modifier
                        ),
                ) {
                    if (snapshotUrl != null) {
                        AsyncImage(
                            model = snapshotUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = zoomOffset.x
                                    translationY = zoomOffset.y
                                },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.VideocamOff,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Text(
                        text = "Snapshot only — this camera has no live stream",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                if (ptz != null && ptz.hasAny) {
                    Spacer(Modifier.height(20.dp))
                    PtzDpad(ptz = ptz, onPtzPress = onPtzPress, modifier = Modifier.fillMaxWidth())
                }
                return@Column
            }

            // Bumping retryKey (retry tap, or a reopen) restarts the whole attempt from
            // the primary path. phase + liveStarted are re-seeded off it (and off
            // visible/entityId). MSE is the primary when a go2rtc URL is configured.
            val useMse = !go2rtcUrl.isNullOrBlank()
            // MJPEG fallback URL: the camera_proxy_stream sibling of the snapshot endpoint,
            // reusing the same signed token. Null when there's no snapshot URL (no token).
            val mjpegUrl = remember(snapshotUrl) {
                snapshotUrl?.replace("/api/camera_proxy/", "/api/camera_proxy_stream/")
                    ?.takeIf { it.contains("/api/camera_proxy_stream/") }
            }
            var retryKey by remember { mutableStateOf(0) }
            var phase by remember(visible, entityId, retryKey) {
                mutableStateOf<PlayPhase>(if (useMse) PlayPhase.Mse else PlayPhase.WebRtc)
            }
            // True once the live video is actually rendering — drops the spinner.
            var liveStarted by remember(visible, entityId, retryKey) { mutableStateOf(false) }

            // Primary path gave up → resolve the HLS fallback URL once.
            LaunchedEffect(phase) {
                if (phase is PlayPhase.Resolving) {
                    val url = try { getStreamUrl() } catch (_: Throwable) { null }
                    phase = if (url != null) PlayPhase.Hls(url) else PlayPhase.Failed
                }
            }

            // Container blends with the sheet — letterbox bars (if ZOOM/FILL can't
            // match the camera's native aspect) disappear into the surface.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainerHigh)
                    .onSizeChanged { viewSize = it },
            ) {
                // Snapshot underlay while there's no moving picture yet (connecting to
                // the live path, or resolving the HLS fallback) so the box isn't blank.
                val showSnapshot = snapshotUrl != null && when (phase) {
                    is PlayPhase.Mse, is PlayPhase.WebRtc, is PlayPhase.Hls, is PlayPhase.Mjpeg -> !liveStarted
                    is PlayPhase.Resolving -> true
                    else -> false
                }
                if (showSnapshot) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            },
                        contentScale = ContentScale.Crop,
                    )
                }

                when (val p = phase) {
                    is PlayPhase.Mse -> {
                        if (visible) Go2RtcMsePlayer(
                            go2rtcUrl = go2rtcUrl.orEmpty(),
                            src = entityId,
                            onConnected = { liveStarted = true },
                            onFailed = { if (phase is PlayPhase.Mse) phase = PlayPhase.Resolving },
                            scale = zoomScale,
                            offset = zoomOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!liveStarted) StreamLoading(cs)
                    }
                    is PlayPhase.WebRtc -> {
                        if (visible) WebRtcPlayer(
                            entityId = entityId,
                            client = webRtcClient,
                            onConnected = { liveStarted = true },
                            onFailed = { if (phase is PlayPhase.WebRtc) phase = PlayPhase.Resolving },
                            scale = zoomScale,
                            offset = zoomOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!liveStarted) StreamLoading(cs)
                    }
                    is PlayPhase.Resolving -> StreamLoading(cs)
                    is PlayPhase.Hls -> {
                        HlsPlayer(
                            url = p.url,
                            onRendered = { liveStarted = true },
                            // ExoPlayer's HLS stalls over a remote/Cloudflare link (stuck
                            // BUFFERING, no segments). Fall through to the MJPEG push, which
                            // uses the same token-signed HTTP path the snapshot already proves
                            // works. No snapshot/token → nothing to build MJPEG from → fail.
                            onFailed = {
                                if (phase is PlayPhase.Hls) {
                                    phase = if (mjpegUrl != null) PlayPhase.Mjpeg else PlayPhase.Failed
                                }
                            },
                            scale = zoomScale,
                            offset = zoomOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!liveStarted) StreamLoading(cs)
                    }
                    is PlayPhase.Mjpeg -> {
                        if (visible && mjpegUrl != null) MjpegPlayer(
                            url = mjpegUrl,
                            onRendered = { liveStarted = true },
                            onFailed = { if (phase is PlayPhase.Mjpeg) phase = PlayPhase.Failed },
                            scale = zoomScale,
                            offset = zoomOffset,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!liveStarted) StreamLoading(cs)
                    }
                    is PlayPhase.Failed -> StreamRetry(cs) { retryKey++ }
                }

                // Pinch-to-zoom capture sits on TOP of the player. The live view is an
                // embedded AndroidView (SurfaceView/PlayerView), and Compose's View interop
                // hands the whole touch stream to that view — so a gesture on the container
                // *behind* it never fires (that's why zoom worked on the tile's Compose image
                // but not here). A transparent overlay above the player is hit first, so the
                // pinch reaches us. Skipped in the Failed state so StreamRetry keeps its tap.
                if (phase !is PlayPhase.Failed) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .cameraZoomGesture(
                                scale = { zoomScale },
                                offset = { zoomOffset },
                                size = { viewSize },
                                onChange = { s, o -> zoomScale = s; zoomOffset = o },
                            ),
                    )
                }
            }

            // PTZ pad below the live view — press-and-hold repeats the nudge for smooth,
            // continuous panning (much finer control than the tile's single-tap edges).
            if (ptz != null && ptz.hasAny) {
                Spacer(Modifier.height(20.dp))
                PtzDpad(ptz = ptz, onPtzPress = onPtzPress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── PTZ pad (detail sheet) ────────────────────────────────────────────────────
// A cross of circular direction buttons under the live view. Each button fires its
// bound entity once on tap and, while held, repeats the nudge — continuous, fine-grained
// panning the tile's single-tap edge zones can't offer. Unbound directions render an
// invisible spacer so the cross stays symmetric.
@Composable
private fun PtzDpad(
    ptz: CameraPtz,
    onPtzPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val tick: (String) -> Unit = { id -> if (id.isNotBlank()) { haptic.tick(); onPtzPress(id) } }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PtzSlot(ptz.up) { PtzButton(Icons.Outlined.KeyboardArrowUp, "Tilt up") { tick(ptz.up) } }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PtzSlot(ptz.left) { PtzButton(Icons.Outlined.KeyboardArrowLeft, "Pan left") { tick(ptz.left) } }
            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp),
                )
            }
            PtzSlot(ptz.right) { PtzButton(Icons.Outlined.KeyboardArrowRight, "Pan right") { tick(ptz.right) } }
        }
        PtzSlot(ptz.down) { PtzButton(Icons.Outlined.KeyboardArrowDown, "Tilt down") { tick(ptz.down) } }
    }
}

// Renders the button when the direction is bound, else a same-size spacer.
@Composable
private fun PtzSlot(entityId: String, content: @Composable () -> Unit) {
    if (entityId.isNotBlank()) content() else Spacer(Modifier.size(56.dp))
}

// One circular PTZ button: tap = a single nudge, press-and-hold = repeat. [onTick] runs
// on each fire (it carries the haptic + the entity press).
@Composable
private fun PtzButton(
    icon: ImageVector,
    description: String,
    onTick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.86f else 1f, label = "ptzPress")
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(cs.secondaryContainer)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            onTick() // immediate nudge on press
                            // Still held past the threshold → repeat until release/cancel.
                            var released = withTimeoutOrNull(PTZ_HOLD_START_MS) { tryAwaitRelease() }
                            while (released == null) {
                                onTick()
                                released = withTimeoutOrNull(PTZ_REPEAT_MS) { tryAwaitRelease() }
                            }
                        } finally {
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = cs.onSecondaryContainer,
            modifier = Modifier.size(30.dp),
        )
    }
}

// Centered loading disc shown while connecting/resolving. ContainedLoadingIndicator
// has a visible disc; the bare LoadingIndicator disappears against dark snapshots.
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StreamLoading(cs: ColorScheme) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ContainedLoadingIndicator(
            containerColor = cs.primaryContainer,
            indicatorColor = cs.onPrimaryContainer,
            modifier = Modifier.size(64.dp),
        )
    }
}

// Soft failure state for the player box — tap anywhere to retry from WebRTC.
// When every path (MSE/WebRTC/HLS) fails, the usual cause is server-side: Home Assistant
// has no live stream for the entity (integration not loaded, camera unavailable/offline, or
// HA replied "Camera not found"). The copy points there instead of implying a network blip,
// since retrying won't help if the camera isn't actually serving in HA.
@Composable
private fun StreamRetry(cs: ColorScheme, onClick: () -> Unit) {
    Tap(onClick = onClick, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VideocamOff,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Home Assistant couldn't open this camera",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = "Check that it's online and plays in Home Assistant. Tap to retry.",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

// Small overlay pill: red dot + LIVE label. Shown on top of the player surface
// (HLS or WebRTC) only while video is actually rendering. Internal so the WebRTC
// player in the sibling file can reuse it.
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(Color(0xFFFF453A), CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "LIVE",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = Color.White,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsPlayer(
    url: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onRendered: () -> Unit = {},
    onFailed: () -> Unit = {},
) {
    val context = LocalContext.current
    val player = remember(url) {
        // Allow cross-protocol redirects — remote HA behind a reverse proxy often bounces
        // http⇄https for the playlist/segments.
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
        // Live buffering: hold a modest window so jitter on a remote link doesn't starve
        // the decoder (too tight a window over Cloudflare = a permanent black surface that
        // never gets its first segment). prioritizeTimeOverSizeThresholds favours recency.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(3_000, 10_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        // Pull toward the live edge: aim ~3s behind live, clamped 1.5–10s. Trims ExoPlayer's
        // conservative default (it holds ~3 segment durations / 6–10s back) without sitting
        // so close to the edge that segments aren't ready yet. HlsMediaSource consumes LL-HLS
        // parts when the playlist advertises them; otherwise this just trims the live delay.
        val liveCfg = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(3_000)
            .setMinOffsetMs(1_500)
            .setMaxOffsetMs(10_000)
            .build()
        val mediaItem = MediaItem.Builder().setUri(url).setLiveConfiguration(liveCfg).build()
        // prepare()/playWhenReady are deferred to the listener-attached DisposableEffect
        // below — calling prepare() here (before the listener is registered) loses the very
        // first IDLE→BUFFERING transition, so a stream that hangs in BUFFERING produces no
        // events at all and looks like an inexplicable black box.
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem))
        }
    }
    // Drives the LIVE badge visibility — only on after the first frame is
    // actually rendered, so we don't claim "live" while still buffering.
    var isPlaying by remember(player) { mutableStateOf(false) }
    // True once a real video frame has been drawn. Until then the sheet keeps the
    // snapshot + spinner up (instead of a black box), and the watchdog below fails
    // out to the honest error if no frame ever arrives.
    var rendered by remember(player) { mutableStateOf(false) }
    val currentOnRendered by rememberUpdatedState(onRendered)
    val currentOnFailed by rememberUpdatedState(onFailed)
    // Watchdog: HA returned a playlist but if no decodable frame shows within the window
    // (segments never load over the remote link — ExoPlayer's HLS stalls there), fall
    // through to the MJPEG path rather than spinning forever.
    LaunchedEffect(player) {
        delay(HLS_RENDER_TIMEOUT_MS)
        if (!rendered) currentOnFailed()
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onRenderedFirstFrame() {
                rendered = true
                currentOnRendered()
            }
            override fun onPlayerError(error: PlaybackException) {
                // The one routinely-recoverable live error: playback fell behind the
                // live window after a stall/network blip. Re-prepare from the live
                // edge. (Matched by errorCode, not cause type, which media3 may wrap.)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                }
            }
        }
        player.addListener(listener)
        // Prepare only after the listener is attached, so every state transition is logged.
        player.playWhenReady = true
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            // Inflated from XML so the PlayerView uses a TextureView surface — a default
            // SurfaceView renders black under the bottom sheet's Haze blur / rounded clip.
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.view_camera_player, null) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            // Apply the digital pinch-zoom transform to the TextureView-backed player.
            update = { it.applyCameraZoom(scale, offset) },
            modifier = Modifier.fillMaxSize(),
        )
        if (isPlaying) {
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}
