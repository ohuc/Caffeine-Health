package com.uc.homehealth.data

import android.content.Context
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

/**
 * Process-wide WebRTC bootstrap. Initializes the native library exactly once and owns
 * the shared [EglBase] + [PeerConnectionFactory]. The camera detail sheet is the only
 * caller; everything is created lazily on first use so the rest of the app pays nothing
 * for the native lib until a camera is actually opened.
 */
object WebRtcCore {

    /** Shared GL context — also handed to each [org.webrtc.SurfaceViewRenderer]. */
    val eglBase: EglBase by lazy { EglBase.create() }

    @Volatile private var initialized = false

    private fun ensureInitialized(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context.applicationContext)
                    .createInitializationOptions()
            )
            initialized = true
        }
    }

    private val factoryDelegate: PeerConnectionFactory by lazy {
        PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .createPeerConnectionFactory()
    }

    /** The shared factory; initializes the native lib on first call. */
    fun factory(context: Context): PeerConnectionFactory {
        ensureInitialized(context)
        return factoryDelegate
    }
}
