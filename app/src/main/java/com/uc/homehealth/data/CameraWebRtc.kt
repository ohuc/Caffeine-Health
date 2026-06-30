package com.uc.homehealth.data

// Framework-agnostic WebRTC signaling models. These intentionally carry NO
// `org.webrtc` types so the data layer (WS client + repository) stays decoupled
// from the native WebRTC library — only the UI player layer touches `org.webrtc`.

/** A single ICE candidate in HA's `RTCIceCandidateInit` shape. */
data class WebRtcIceCandidate(
    val candidate: String,
    val sdpMid: String?,
    val sdpMLineIndex: Int,
)

/**
 * One STUN/TURN server from HA's WebRTC client configuration
 * (`camera/webrtc/get_client_config` → `configuration.iceServers[]`). HA hands these to
 * the client so the PeerConnection uses the *server's* configured relays — crucially the
 * TURN relay needed to traverse a remote link (Nabu Casa Cloud, or a user-configured
 * `web_rtc: ice_servers:` in configuration.yaml). Carries no `org.webrtc` types so the
 * data layer stays decoupled; the player maps it to `PeerConnection.IceServer`.
 */
data class WebRtcIceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

/**
 * Messages streamed back from HA's `camera/webrtc/offer` subscription. HA's wire
 * format has varied across versions, so the WS client parses defensively into
 * these and the player falls back to HLS on [Error] (or a timeout).
 */
sealed interface WebRtcSignal {
    /** SDP answer from the camera's WebRTC provider (go2rtc et al.). */
    data class Answer(val sdp: String) : WebRtcSignal
    /** A remote ICE candidate to add to the local peer connection. */
    data class Candidate(val candidate: WebRtcIceCandidate) : WebRtcSignal
    /** Server-assigned session id — required to post our own candidates back. */
    data class Session(val id: String) : WebRtcSignal
    /** Signaling failed; caller should fall back to HLS. */
    data class Error(val message: String) : WebRtcSignal
}
