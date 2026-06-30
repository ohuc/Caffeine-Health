package com.uc.homehealth.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.WebRtcCore
import com.uc.homehealth.data.WebRtcIceCandidate
import com.uc.homehealth.data.WebRtcIceServer
import com.uc.homehealth.data.WebRtcSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.RendererCommon
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import java.util.concurrent.Executors

// ── WebRTC live camera player ─────────────────────────────────────────────────
// Drives a receive-only WebRTC session against Home Assistant's unified WebRTC WS
// API (HA 2024.11+ / go2rtc) and renders the remote video into a SurfaceViewRenderer.
// All PeerConnection interaction is confined to a single background thread; the WS
// signaling callbacks hop onto it too. Any failure (or the caller's timeout) drives
// the [WebRtcPlayerState.Failed] state so the detail sheet can fall back to HLS.

/** Thin signaling surface the player needs — implemented over the repository/ViewModel. */
interface CameraWebRtcClient {
    suspend fun start(entityId: String, offerSdp: String, onSignal: (WebRtcSignal) -> Unit): Int?
    fun sendCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate)
    fun stop(subscriptionId: Int)
    /** HA's configured STUN/TURN servers (incl. the relay needed for remote links). */
    suspend fun getClientConfig(entityId: String): List<WebRtcIceServer>?
}

sealed interface WebRtcPlayerState {
    data object Connecting : WebRtcPlayerState
    data class Connected(val track: VideoTrack) : WebRtcPlayerState
    data object Failed : WebRtcPlayerState
}

private const val TAG = "HomeHealth_WebRTC"
// Shorter than before (was 8s): over a remote link with no TURN relay, WebRTC can never
// connect, so the sooner we give up the sooner the detail sheet falls back to HLS. On the
// LAN (or with a TURN relay) a connection is established well within this window.
private const val CONNECT_TIMEOUT_MS = 6_000L

// Public STUN used only when HA returns no client config (older HA / get_client_config
// unavailable). STUN alone gets you LAN + simple NATs; the TURN relay that lets WebRTC
// traverse a Cloudflare tunnel comes from HA's own config when present.
private const val FALLBACK_STUN = "stun:stun.l.google.com:19302"

private class WebRtcController(
    appContext: Context,
    private val entityId: String,
    private val client: CameraWebRtcClient,
) {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow<WebRtcPlayerState>(WebRtcPlayerState.Connecting)
    val state: StateFlow<WebRtcPlayerState> = _state.asStateFlow()

    private val factory = WebRtcCore.factory(appContext)
    private var pc: PeerConnection? = null

    // Set once HA returns the session id; gate trickling our local candidates on it.
    private var sessionId: String? = null
    private val pendingLocalCandidates = mutableListOf<IceCandidate>()

    // Remote candidates can race ahead of the answer; buffer until the remote
    // description is applied or WebRTC will drop them.
    private var remoteDescriptionSet = false
    private val pendingRemoteCandidates = mutableListOf<IceCandidate>()

    private var subscriptionId: Int? = null
    // Read from the WebRTC signaling thread (onAddTrack/onConnectionChange) and the
    // PC dispatcher, written from the main thread on dispose — keep it visible.
    @Volatile private var closed = false

    fun start() {
        // Watchdog: a silent stall (no answer / never connects) won't fire an error,
        // so give up after the timeout and let the detail sheet fall back to HLS.
        scope.launch {
            delay(CONNECT_TIMEOUT_MS)
            if (_state.value is WebRtcPlayerState.Connecting) {
                Log.w(TAG, "WebRTC connect timed out for $entityId")
                fail()
            }
        }
        scope.launch {
            // Ask HA which STUN/TURN servers to use. With Nabu Casa Cloud (or a configured
            // `web_rtc: ice_servers:`) this includes a TURN relay, which is the only way a
            // WebRTC session can traverse a remote/Cloudflare link. Falls back to a bare
            // public STUN server (LAN-only) if HA doesn't provide a config.
            val haServers = try {
                client.getClientConfig(entityId)
            } catch (t: Throwable) {
                Log.w(TAG, "getClientConfig($entityId) threw: ${t.message}"); null
            }
            val iceServers = haServers?.takeIf { it.isNotEmpty() }?.map { s ->
                PeerConnection.IceServer.builder(s.urls).apply {
                    if (!s.username.isNullOrEmpty()) setUsername(s.username)
                    if (!s.credential.isNullOrEmpty()) setPassword(s.credential)
                }.createIceServer()
            } ?: listOf(PeerConnection.IceServer.builder(FALLBACK_STUN).createIceServer())

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            }

            val connection = factory.createPeerConnection(rtcConfig, pcObserver)
            if (connection == null) {
                Log.w(TAG, "createPeerConnection returned null for $entityId")
                fail()
                return@launch
            }
            pc = connection

            // Receive-only video + audio — we never publish a track.
            val recvOnly = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
            connection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, recvOnly)
            connection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, recvOnly)

            connection.createOffer(object : SdpObserver by NoopSdp {
                override fun onCreateSuccess(desc: SessionDescription) {
                    connection.setLocalDescription(NoopSdp, desc)
                    scope.launch {
                        val subId = try {
                            client.start(entityId, desc.description, ::onSignal)
                        } catch (t: Throwable) {
                            Log.w(TAG, "start($entityId) threw: ${t.message}")
                            null
                        }
                        if (subId == null) fail() else subscriptionId = subId
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.w(TAG, "createOffer failed for $entityId: $error")
                    fail()
                }
            }, MediaConstraints())
        }
    }

    // Handle one signaling message from HA. Always marshaled onto the PC thread.
    private fun onSignal(signal: WebRtcSignal) {
        scope.launch {
            val connection = pc ?: return@launch
            when (signal) {
                is WebRtcSignal.Answer -> connection.setRemoteDescription(
                    object : SdpObserver by NoopSdp {
                        override fun onSetSuccess() {
                            scope.launch {
                                remoteDescriptionSet = true
                                pendingRemoteCandidates.forEach { connection.addIceCandidate(it) }
                                pendingRemoteCandidates.clear()
                            }
                        }

                        override fun onSetFailure(error: String?) {
                            Log.w(TAG, "setRemoteDescription failed for $entityId: $error")
                            fail()
                        }
                    },
                    SessionDescription(SessionDescription.Type.ANSWER, signal.sdp),
                )

                is WebRtcSignal.Candidate -> {
                    val c = signal.candidate
                    val ice = IceCandidate(c.sdpMid, c.sdpMLineIndex, c.candidate)
                    if (remoteDescriptionSet) connection.addIceCandidate(ice)
                    else pendingRemoteCandidates += ice
                }

                is WebRtcSignal.Session -> {
                    sessionId = signal.id
                    pendingLocalCandidates.forEach { client.sendCandidate(entityId, signal.id, it.toModel()) }
                    pendingLocalCandidates.clear()
                }

                is WebRtcSignal.Error -> {
                    Log.w(TAG, "WebRTC signaling error for $entityId: ${signal.message}")
                    fail()
                }
            }
        }
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) {
            scope.launch {
                val sid = sessionId
                if (sid != null) client.sendCandidate(entityId, sid, candidate.toModel())
                else pendingLocalCandidates += candidate
            }
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track()
            if (track is VideoTrack) {
                Log.d(TAG, "Remote video track received for $entityId")
                if (!closed) _state.value = WebRtcPlayerState.Connected(track)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (newState == PeerConnection.PeerConnectionState.FAILED) {
                Log.w(TAG, "PeerConnection FAILED for $entityId")
                fail()
            }
        }

        // Unused observer callbacks.
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: MediaStream?) {}
        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
        override fun onRenegotiationNeeded() {}
    }

    private fun fail() {
        if (!closed) _state.value = WebRtcPlayerState.Failed
    }

    fun close() {
        if (closed) return
        closed = true
        scope.launch {
            runCatching { subscriptionId?.let { client.stop(it) } }
            runCatching { pc?.dispose() }
            pc = null
        }.invokeOnCompletion {
            scope.cancel()
            dispatcher.close()
        }
    }

    private fun IceCandidate.toModel() = WebRtcIceCandidate(sdp, sdpMid, sdpMLineIndex)
}

// SdpObserver with empty defaults; `by NoopSdp` lets call sites override only what
// they care about (org.webrtc's SdpObserver has no default methods).
private val NoopSdp = object : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}

/**
 * Plays the WebRTC live stream for [entityId]. Calls [onConnected] once the remote
 * video starts rendering (so the caller can drop its spinner) and [onFailed] (once) if
 * the session can't be established (so the caller can fall back to HLS). Renders
 * nothing while connecting — the detail sheet shows its snapshot/spinner underneath
 * until video arrives, at which point a LIVE badge appears.
 */
@Composable
fun WebRtcPlayer(
    entityId: String,
    client: CameraWebRtcClient,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offset: Offset = Offset.Zero,
    onConnected: () -> Unit = {},
) {
    val appContext = LocalContext.current.applicationContext
    val controller = remember(entityId) { WebRtcController(appContext, entityId, client) }

    DisposableEffect(controller) {
        controller.start()
        onDispose { controller.close() }
    }

    val state by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        if (state is WebRtcPlayerState.Failed) onFailed()
    }

    // One renderer for the lifetime of this entity; released on dispose.
    val renderer = remember(entityId) {
        SurfaceViewRenderer(appContext).apply {
            init(WebRtcCore.eglBase.eglBaseContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }
    DisposableEffect(renderer) {
        onDispose { renderer.release() }
    }

    // Attach the remote track to the renderer once connected; detach on track change.
    val track = (state as? WebRtcPlayerState.Connected)?.track
    DisposableEffect(track) {
        if (track != null) {
            track.addSink(renderer)
            onConnected()
        }
        onDispose { track?.removeSink(renderer) }
    }

    Box(modifier = modifier) {
        if (track != null) {
            AndroidView(
                factory = { renderer },
                // Digital pinch-zoom: a Compose graphicsLayer can't move the renderer's
                // separate SurfaceView surface, so transform the View itself.
                update = { r ->
                    r.scaleX = scale
                    r.scaleY = scale
                    r.translationX = offset.x
                    r.translationY = offset.y
                },
                modifier = Modifier.fillMaxSize(),
            )
            LiveBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
            )
        }
    }
}
