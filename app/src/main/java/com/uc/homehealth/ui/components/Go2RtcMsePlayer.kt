package com.uc.homehealth.ui.components

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.InterruptedIOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// ── go2rtc MSE live player ────────────────────────────────────────────────────
// Connects to go2rtc's WebSocket API (/api/ws?src=) and asks for MSE: the server
// streams a fragmented-MP4 init segment (ftyp+moov) followed by media fragments
// (moof+mdat) as binary frames. Browsers feed those to MediaSource; Android has no
// MediaSource, so we pipe the concatenated byte stream into ExoPlayer via a custom
// live DataSource forced to the FragmentedMp4Extractor. WebSocket transport survives
// Cloudflare's long-lived-connection handling, unlike a chunked stream.mp4. Any
// failure drives onFailed so the detail sheet falls back to HLS.

private const val TAG = "HomeHealth_MSE"
// Matches the WebRTC connect timeout — give up quickly so the detail sheet can fall back
// to HLS rather than leaving the user on a spinner.
private const val MSE_CONNECT_TIMEOUT_MS = 6_000L

// Codecs we tell go2rtc we can decode (intersected server-side with the stream).
// Limited to what Android MediaCodec reliably handles in fMP4: H.264/H.265 + AAC.
private const val MSE_CODECS =
    "avc1.640029,avc1.64002A,avc1.4D401F,avc1.42E01E,hvc1.1.6.L153.B0,hev1.1.6.L153.B0,mp4a.40.2"

@UnstableApi
@Composable
fun Go2RtcMsePlayer(
    go2rtcUrl: String,
    src: String,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onConnected: () -> Unit = {},
) {
    val context = LocalContext.current

    // One playback pipeline per (url, src). Rebuilt if either changes.
    val wsUrl = remember(go2rtcUrl, src) { buildWsUrl(go2rtcUrl, src) }

    var isPlaying by remember(wsUrl) { mutableStateOf(false) }
    var failed by remember(wsUrl) { mutableStateOf(false) }

    val player = remember(wsUrl) {
        // Low-latency-ish buffering: start as soon as a little is buffered so we don't
        // sit behind a fat buffer; modest min/max so transient jitter doesn't rebuffer.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_000, 4_000, 300, 800)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build()
    }

    val dataSource = remember(wsUrl) { WsByteStreamDataSource() }

    DisposableEffect(wsUrl) {
        val mainHandler = Handler(Looper.getMainLooper())
        fun failOnMain() = mainHandler.post { if (!failed) { failed = true; onFailed() } }

        // Force fMP4 parsing — sniffing the ftyp+moov init segment can otherwise pick the
        // non-fragmented Mp4Extractor, which then chokes on the first moof.
        val extractorsFactory = ExtractorsFactory { arrayOf(FragmentedMp4Extractor()) }
        val mediaSource = ProgressiveMediaSource.Factory(
            DataSource.Factory { dataSource }, extractorsFactory,
        ).createMediaSource(MediaItem.fromUri("go2rtc://mse/$src"))

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                if (playing) isPlaying = true
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "ExoPlayer error for $src: ${error.errorCodeName}", error)
                failOnMain()
            }
        }
        player.addListener(listener)
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true

        // Dedicated client: no read timeout (the stream never "completes") and a ping so
        // idle-detection middleboxes keep the socket open between fragments.
        val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
        Log.d(TAG, "Opening go2rtc MSE WS → $wsUrl")
        val ws = client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("{\"type\":\"mse\",\"value\":\"$MSE_CODECS\"}")
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    // JSON control frames: {"type":"mse","value":"video/mp4; codecs=..."} or
                    // {"type":"error",...}. We don't need the codec string (ExoPlayer sniffs),
                    // but surface server errors as a failure.
                    if (text.contains("\"type\":\"error\"")) {
                        Log.w(TAG, "go2rtc error for $src: $text")
                        failOnMain()
                    }
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    dataSource.enqueue(bytes.toByteArray())
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "go2rtc MSE WS failed for $src: ${t.message}")
                    dataSource.signalEnd()
                    failOnMain()
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    dataSource.signalEnd()
                }
            },
        )

        onDispose {
            player.removeListener(listener)
            player.release()
            ws.cancel()
            dataSource.signalEnd()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    // Connected = video actually playing → let the sheet drop its spinner.
    LaunchedEffect(isPlaying) { if (isPlaying) onConnected() }

    // Watchdog: if nothing is playing by the timeout, fall back to HLS.
    LaunchedEffect(wsUrl) {
        kotlinx.coroutines.delay(MSE_CONNECT_TIMEOUT_MS)
        if (!isPlaying && !failed) {
            Log.w(TAG, "go2rtc MSE connect timed out for $src")
            failed = true
            onFailed()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    this.player = player
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            // Digital pinch-zoom: transform the player View (a Compose graphicsLayer can't
            // move the underlying SurfaceView's separate surface).
            update = { view ->
                view.scaleX = scale
                view.scaleY = scale
                view.translationX = offset.x
                view.translationY = offset.y
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

// Builds wss(s)://host/api/ws?src=<encoded>. Accepts a base with or without scheme.
private fun buildWsUrl(go2rtcUrl: String, src: String): String {
    val base = go2rtcUrl.trim().trimEnd('/')
    val wsBase = when {
        base.startsWith("https://") -> "wss://" + base.removePrefix("https://")
        base.startsWith("http://") -> "ws://" + base.removePrefix("http://")
        base.startsWith("wss://") || base.startsWith("ws://") -> base
        else -> "wss://$base" // assume TLS when scheme omitted (typical for a public host)
    }
    return "$wsBase/api/ws?src=" + URLEncoder.encode(src, "UTF-8")
}

// ExoPlayer DataSource backed by the WebSocket's binary frames. Live + unbounded:
// open() returns LENGTH_UNSET, read() blocks for the next bytes, and END_OF_INPUT is
// returned once the socket ends. Confined access via a monitor — enqueue() runs on the
// OkHttp thread, read() on ExoPlayer's loader thread.
@UnstableApi
private class WsByteStreamDataSource : BaseDataSource(/* isNetwork = */ true) {
    private val lock = Object()
    private val chunks = ArrayDeque<ByteArray>()
    private var current: ByteArray? = null
    private var currentPos = 0
    private var endOfInput = false
    private var opened = false
    private var uri: Uri? = null

    fun enqueue(data: ByteArray) {
        if (data.isEmpty()) return
        synchronized(lock) {
            if (endOfInput) return
            chunks.addLast(data)
            lock.notifyAll()
        }
    }

    fun signalEnd() {
        synchronized(lock) {
            endOfInput = true
            lock.notifyAll()
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        synchronized(lock) { opened = true }
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val cur: ByteArray
        synchronized(lock) {
            while (current == null && chunks.isEmpty() && !endOfInput) {
                try {
                    lock.wait()
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException()
                }
            }
            if (current == null) {
                if (chunks.isNotEmpty()) {
                    current = chunks.removeFirst()
                    currentPos = 0
                } else {
                    return C.RESULT_END_OF_INPUT
                }
            }
            cur = current!!
        }
        val toCopy = minOf(cur.size - currentPos, length)
        System.arraycopy(cur, currentPos, buffer, offset, toCopy)
        currentPos += toCopy
        if (currentPos >= cur.size) current = null
        bytesTransferred(toCopy)
        return toCopy
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        signalEnd()
        synchronized(lock) {
            chunks.clear()
            current = null
        }
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
