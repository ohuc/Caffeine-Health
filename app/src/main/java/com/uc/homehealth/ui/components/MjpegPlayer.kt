package com.uc.homehealth.ui.components

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// ── MJPEG live player ─────────────────────────────────────────────────────────
// Plays Home Assistant's `/api/camera_proxy_stream/<entity>?token=…` feed: a
// `multipart/x-mixed-replace` stream of JPEG frames. This is the universal fallback
// HA's own frontend uses, and unlike HLS it's a plain continuous HTTP push — no
// LL-HLS blocking-playlist reloads, no UDP — so it survives a Cloudflare tunnel that
// stalls ExoPlayer's HLS (the snapshot uses the same path and works, so this does too).
//
// We don't parse multipart headers; we scan the byte stream for JPEG start (FF D8) and
// end (FF D9) markers, which self-delimit each frame. Each frame is decoded on a
// dedicated reader thread and pushed to Compose. Any failure (or no frame within the
// watchdog) drives onFailed so the detail sheet shows the error.

private const val TAG = "HomeHealth_MJPEG"
private const val MJPEG_CONNECT_TIMEOUT_MS = 8_000L
private const val MJPEG_FIRST_FRAME_TIMEOUT_MS = 8_000L

private val JPEG_SOI = ByteString.of(0xFF.toByte(), 0xD8.toByte()) // start of image
private val JPEG_EOI = ByteString.of(0xFF.toByte(), 0xD9.toByte()) // end of image

@Composable
fun MjpegPlayer(
    url: String,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onRendered: () -> Unit = {},
) {
    var frame by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var rendered by remember(url) { mutableStateOf(false) }
    val currentOnRendered by rememberUpdatedState(onRendered)
    val currentOnFailed by rememberUpdatedState(onFailed)

    // Watchdog: HA accepted the connection but if no JPEG frame decodes within the
    // window (tunnel buffering the multipart stream, camera not producing frames),
    // surface the error rather than spin forever.
    LaunchedEffect(url) {
        delay(MJPEG_FIRST_FRAME_TIMEOUT_MS)
        if (!rendered) currentOnFailed()
    }

    DisposableEffect(url) {
        val mainHandler = Handler(Looper.getMainLooper())
        val client = OkHttpClient.Builder()
            .connectTimeout(MJPEG_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // the stream never "completes"
            .build()
        val call = client.newCall(Request.Builder().url(url).build())
        val active = AtomicBoolean(true)

        // Raw thread (not a coroutine): okio's blocking reads don't honour coroutine
        // cancellation — we unblock them by cancelling the call on dispose.
        val reader = Thread {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (active.get()) mainHandler.post { currentOnFailed() }
                        return@use
                    }
                    val source = resp.body?.source() ?: run {
                        if (active.get()) mainHandler.post { currentOnFailed() }
                        return@use
                    }
                    var frames = 0
                    while (active.get() && !Thread.currentThread().isInterrupted) {
                        val start = source.indexOf(JPEG_SOI)
                        if (start < 0) break
                        source.skip(start) // drop boundary/headers before the SOI
                        val end = source.indexOf(JPEG_EOI, 2) // first EOI after the SOI
                        if (end < 0) break
                        val jpeg = source.readByteArray(end + 2) // include the EOI marker
                        val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: continue
                        val img = bmp.asImageBitmap()
                        frames++
                        if (active.get()) mainHandler.post {
                            frame = img
                            if (!rendered) { rendered = true; currentOnRendered() }
                        }
                    }
                    // Stream ended without us tearing it down — treat as a failure unless
                    // we never even started (the watchdog handles the never-started case).
                    if (active.get() && frames == 0) mainHandler.post { currentOnFailed() }
                }
            } catch (t: Throwable) {
                if (active.get()) {
                    Log.w(TAG, "MJPEG read failed: ${t.message}")
                    mainHandler.post { currentOnFailed() }
                }
            }
        }
        reader.start()

        onDispose {
            active.set(false)
            call.cancel()
            reader.interrupt()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    Box(modifier = modifier) {
        val f = frame
        if (f != null) {
            Image(
                bitmap = f,
                contentDescription = null,
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
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}
