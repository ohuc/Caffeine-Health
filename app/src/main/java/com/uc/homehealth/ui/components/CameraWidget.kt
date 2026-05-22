package com.uc.homehealth.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
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
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

// ── Collapsed tile — square-ish snapshot card with name overlay ──────────────
// Snapshot URL is provided by the repo (uses the entity's rotating access_token
// query param so no auth headers are required for Coil). We re-key the request
// every SNAPSHOT_REFRESH_MS so the user sees a fresh frame without burning the
// whole tile + animation.

private const val SNAPSHOT_REFRESH_MS = 2_000L

@Composable
fun CameraWidgetTile(
    name: String,
    subtitle: String,
    snapshotUrl: String?,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val interaction = remember { MutableInteractionSource() }

    // Bumps every SNAPSHOT_REFRESH_MS to force Coil to refetch the snapshot.
    var refreshTick by remember { mutableStateOf(0) }
    LaunchedEffect(snapshotUrl) {
        if (snapshotUrl == null) return@LaunchedEffect
        while (true) {
            delay(SNAPSHOT_REFRESH_MS)
            refreshTick++
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(22.dp))
            .background(cs.surfaceContainerHigh)
            .then(
                if (enabled) Modifier.combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { haptic.tick(); onClick() },
                    onLongClick = { haptic.confirm(); onLongPress() },
                ) else Modifier
            ),
    ) {
        if (snapshotUrl != null) {
            val context = LocalContext.current
            // Each tick gets a unique memoryCacheKey so Coil treats it as a new image
            // and re-decodes — but placeholderMemoryCacheKey points back to the *previous*
            // tick's bitmap so it stays on-screen during the fetch. End result: smooth
            // frame swap with no flash to background. diskCache is disabled so we don't
            // accumulate one disk entry per tick.
            val req = remember(snapshotUrl, refreshTick) {
                ImageRequest.Builder(context)
                    .data(snapshotUrl)
                    .memoryCacheKey("$snapshotUrl#$refreshTick")
                    .placeholderMemoryCacheKey("$snapshotUrl#${refreshTick - 1}")
                    .diskCachePolicy(CachePolicy.DISABLED)
                    .crossfade(false)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Offline / no snapshot — show camera-off badge.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.VideocamOff,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(32.dp),
                )
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
                        fontFamily = InterFamily,
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

// ── Detail sheet — HLS via ExoPlayer ─────────────────────────────────────────
// The HLS URL is resolved on first open via a suspend `getStreamUrl` callback
// (which round-trips through the WS `camera/stream` command). go2rtc transcodes
// the source on demand and produces a master.m3u8; ExoPlayer handles segment
// fetch, buffering, and recovery. Path includes a signed token so no auth
// headers are needed. While we're resolving, show a spinner; on failure, show a
// soft error and let the user dismiss.

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraDetailSheet(
    visible: Boolean,
    entityId: String,
    name: String,
    snapshotUrl: String?,
    hazeState: HazeState? = null,
    getStreamUrl: suspend () -> String?,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

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

            // produceState handles the suspend resolve + cancels on dispose.
            val streamUrl by produceState<StreamResult>(initialValue = StreamResult.Loading, visible) {
                if (!visible) return@produceState
                value = StreamResult.Loading
                val url = try { getStreamUrl() } catch (_: Throwable) { null }
                value = if (url != null) StreamResult.Ready(url) else StreamResult.Failed
            }

            // Container blends with the sheet — letterbox bars (if ZOOM can't fill
            // perfectly with the camera's native aspect) disappear into the surface.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainerHigh),
            ) {
                // Show the snapshot while the stream is resolving so the box isn't
                // a giant placeholder during the round-trip.
                if (snapshotUrl != null && streamUrl is StreamResult.Loading) {
                    AsyncImage(
                        model = snapshotUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                when (val s = streamUrl) {
                    is StreamResult.Ready -> HlsPlayer(
                        url = s.url,
                        modifier = Modifier.fillMaxSize(),
                    )
                    StreamResult.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // ContainedLoadingIndicator has a visible disc; the bare
                        // LoadingIndicator is just a 38 dp morphing polygon that
                        // disappears against dark snapshots / black placeholders.
                        ContainedLoadingIndicator(
                            containerColor = cs.primaryContainer,
                            indicatorColor = cs.onPrimaryContainer,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    StreamResult.Failed -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VideocamOff,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(32.dp),
                            )
                            Text(
                                text = "Couldn't open stream",
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Small overlay pill: red dot + LIVE label. Shown on top of the player surface
// only while ExoPlayer is actually playing (post-buffering).
@Composable
private fun LiveBadge(modifier: Modifier = Modifier) {
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
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = Color.White,
        )
    }
}

private sealed interface StreamResult {
    data object Loading : StreamResult
    data object Failed : StreamResult
    data class Ready(val url: String) : StreamResult
}

@OptIn(UnstableApi::class)
@Composable
private fun HlsPlayer(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            val src = HlsMediaSource.Factory(DefaultHttpDataSource.Factory())
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(src)
            prepare()
            playWhenReady = true
        }
    }
    // Drives the LIVE badge visibility — only on after the first frame is
    // actually rendered, so we don't claim "live" while still buffering.
    var isPlaying by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // ZOOM fills the box; a sliver of edge crop is preferable to
                    // black pillarbox bars when the camera's native aspect doesn't
                    // match our 16:9 container.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
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
