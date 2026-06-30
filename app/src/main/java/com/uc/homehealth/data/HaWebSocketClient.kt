package com.uc.homehealth.data

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HaWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) : DefaultLifecycleObserver {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _status = MutableStateFlow(WsConnectionStatus.DISCONNECTED)
    val status: StateFlow<WsConnectionStatus> = _status.asStateFlow()

    // HA's own reason string from the auth_invalid message (e.g. "Invalid access token or
    // password" vs a local-only/inactive-user restriction). Surfaced in Settings so an
    // auth failure is diagnosable without server log access. Null while not in AUTH_INVALID.
    private val _lastAuthError = MutableStateFlow<String?>(null)
    val lastAuthError: StateFlow<String?> = _lastAuthError.asStateFlow()

    private val _areas = MutableStateFlow<List<HaArea>>(emptyList())
    val areas: StateFlow<List<HaArea>> = _areas.asStateFlow()

    private val _devices = MutableStateFlow<List<HaDeviceEntry>>(emptyList())
    val devices: StateFlow<List<HaDeviceEntry>> = _devices.asStateFlow()

    private val _entities = MutableStateFlow<List<HaEntityEntry>>(emptyList())
    val entities: StateFlow<List<HaEntityEntry>> = _entities.asStateFlow()

    private val _states = MutableStateFlow<Map<String, HaState>>(emptyMap())
    val states: StateFlow<Map<String, HaState>> = _states.asStateFlow()

    @Volatile private var webSocket: WebSocket? = null
    private val idGen = AtomicInteger(0)

    // HA requires WS message ids to be SENT in strictly increasing order. Concurrent
    // callService calls (e.g. "Update all") would otherwise interleave incrementAndGet()
    // and send() and trip HA's "id_reuse / Identifier values have to increase" error.
    // Hold this only for the atomic id-assign + enqueue; the response await stays outside.
    private val sendMutex = Mutex()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    // Long-lived subscription commands (e.g. camera/webrtc/offer): HA replies with one
    // `result` then streams multiple `event` messages on the same id. Each entry maps a
    // command id to a handler fed the inner `event` JSON object until unsubscribed.
    private val subscriptions = ConcurrentHashMap<Int, (JsonObject) -> Unit>()

    private var areaCmd = -1
    private var deviceCmd = -1
    private var entityCmd = -1
    private var statesCmd = -1
    // @Volatile: written by the repository collector (IO thread) and read from OkHttp's
    // reader thread (auth_required handler) and the main thread (onStart) — without it
    // the JMM permits a stale token to be sent in the WS auth message.
    @Volatile private var token = ""
    @Volatile private var currentHaUrl = ""

    /**
     * Set by the repository: returns a currently-valid access token, refreshing the OAuth
     * pair first if the stored one is expired or about to expire. Consulted right before
     * every socket open so reconnects never present a stale token — HA logs each rejected
     * WS auth as a failed login and counts it toward its ip_ban threshold.
     */
    @Volatile var tokenProvider: (suspend () -> String?)? = null

    // Serializes socket creation so racing connect paths (credential change on the IO
    // thread vs onStart on the main thread vs the reconnect loop) can't open two sockets.
    private val connectMutex = Mutex()

    @Volatile private var appInForeground = false
    // Set by the update foreground service: keep the socket connected even while the app is
    // backgrounded, so installs keep dispatching and progress keeps flowing until done.
    @Volatile private var keepAlive = false
    private var reconnectJob: Job? = null

    // The socket should be open/reconnecting whenever the app is foreground OR an install
    // is keeping it alive in the background.
    private val shouldStayConnected: Boolean get() = appInForeground || keepAlive

    /**
     * Toggle background keep-alive. While enabled, the socket stays connected (and keeps
     * reconnecting) even when the app is backgrounded. Enabling it opens the socket
     * immediately if needed; disabling it closes the socket when the app is in the background.
     */
    fun setKeepAlive(enabled: Boolean) {
        if (keepAlive == enabled) return
        keepAlive = enabled
        Log.d("HomeHealth_WS", "setKeepAlive($enabled)")
        if (enabled) {
            if (webSocket == null && currentHaUrl.isNotBlank() && token.isNotBlank() &&
                _status.value != WsConnectionStatus.AUTH_INVALID && _status.value != WsConnectionStatus.IP_BANNED
            ) {
                _status.value = WsConnectionStatus.CONNECTING
                openSocket()
            }
        } else if (!appInForeground) {
            cancelReconnect()
            closeSocket()
            setIdleStatus()
        }
    }

    // Mark the socket as intentionally closed, but keep AUTH_INVALID / IP_BANNED sticky:
    // overwriting them with DISCONNECTED would let the next foreground silently retry the
    // same rejected token, and every retry is another failed login in HA's log and another
    // tick of its ip_ban counter. A new token, a manual reconnect, or a successful OAuth
    // refresh still clears the gate.
    private fun setIdleStatus() {
        if (_status.value != WsConnectionStatus.AUTH_INVALID && _status.value != WsConnectionStatus.IP_BANNED) {
            _status.value = WsConnectionStatus.DISCONNECTED
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Store credentials. Connects immediately if app is in foreground; otherwise waits for onStart. */
    fun setCredentials(haUrl: String, accessToken: String) {
        val sameCreds = haUrl == currentHaUrl && accessToken == token
        currentHaUrl = haUrl
        token = accessToken
        if (sameCreds && webSocket != null) {
            Log.d("HomeHealth_WS", "setCredentials: unchanged, keeping existing socket")
            return
        }
        closeSocket()
        // If the same token already failed auth or got us IP-banned, retrying it just
        // adds another failed login attempt to HA's ban counter. Wait for a new token
        // (paste, refresh, re-login) before talking to HA again.
        if (sameCreds && (_status.value == WsConnectionStatus.AUTH_INVALID || _status.value == WsConnectionStatus.IP_BANNED)) {
            Log.d("HomeHealth_WS", "setCredentials: skipping reconnect — status=${_status.value}, token unchanged")
            return
        }
        if (shouldStayConnected && haUrl.isNotBlank() && accessToken.isNotBlank()) {
            runHttpDiagnostic(haUrl)
            _status.value = WsConnectionStatus.CONNECTING
            openSocket()
        } else {
            Log.d("HomeHealth_WS", "setCredentials: deferring socket open (foreground=$appInForeground, keepAlive=$keepAlive)")
        }
    }

    // One-shot UNAUTHENTICATED probe so failures can be distinguished as "Android blocked
    // LAN entirely" vs WebSocket-specific. Runs only on credential change, not on every
    // reconnect. /auth/providers requires no auth, so this can never be logged by HA as a
    // failed login or tick its ip_ban counter (a Bearer probe that 401s would do both) —
    // and the token never travels on a side channel. A banned IP still surfaces as 403
    // because HA's ip_ban middleware rejects every request before routing, letting us
    // abort the WS connect instead of adding another ban-counter tick.
    private fun runHttpDiagnostic(haUrl: String) {
        scope.launch {
            try {
                val probeUrl = "${haUrl.trimEnd('/')}/auth/providers"
                Log.d("HomeHealth_WS", "HTTP diagnostic → GET $probeUrl (unauthenticated)")
                val req = Request.Builder().url(probeUrl).build()
                okHttpClient.newCall(req).execute().use { resp ->
                    if (resp.code == 403) {
                        Log.e("HomeHealth_WS", "HTTP diagnostic ✗ 403 — IP banned by HA. Aborting WS connect.")
                        closeSocket()
                        _status.value = WsConnectionStatus.IP_BANNED
                    } else {
                        Log.d("HomeHealth_WS", "HTTP diagnostic ✓ status=${resp.code} — HA reachable")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeHealth_WS", "HTTP diagnostic ✗ FAILED: ${e.message} — Android cannot reach HA at all")
            }
        }
    }

    /**
     * User-initiated reconnect. Cancels any pending backoff, drops the existing socket,
     * and immediately reopens it if credentials are present and the app is foregrounded.
     * No-op if credentials are missing.
     */
    fun reconnectNow() {
        if (currentHaUrl.isBlank() || token.isBlank()) {
            Log.d("HomeHealth_WS", "reconnectNow: no credentials, ignoring")
            return
        }
        cancelReconnect()
        closeSocket()
        // User-initiated retry — clear sticky AUTH_INVALID/IP_BANNED gates so the
        // attempt actually goes through. If the underlying problem is unresolved,
        // the same status will come back on its own.
        if (_status.value == WsConnectionStatus.AUTH_INVALID || _status.value == WsConnectionStatus.IP_BANNED) {
            _status.value = WsConnectionStatus.DISCONNECTED
        }
        if (shouldStayConnected) {
            _status.value = WsConnectionStatus.CONNECTING
            openSocket()
        }
    }

    /** Clear credentials and tear down the socket. */
    fun clearCredentials() {
        currentHaUrl = ""
        token = ""
        cancelReconnect()
        closeSocket()
        _status.value = WsConnectionStatus.DISCONNECTED
        _lastAuthError.value = null
        clearData()
    }

    // ── Lifecycle observer ────────────────────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        appInForeground = true
        if (currentHaUrl.isNotBlank() && token.isNotBlank() && webSocket == null) {
            if (_status.value == WsConnectionStatus.AUTH_INVALID || _status.value == WsConnectionStatus.IP_BANNED) {
                Log.d("HomeHealth_WS", "App foreground but status=${_status.value} — not opening socket")
                return
            }
            Log.d("HomeHealth_WS", "App foreground → opening socket")
            _status.value = WsConnectionStatus.CONNECTING
            openSocket()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        appInForeground = false
        if (keepAlive) {
            // An install is in progress — keep the socket open in the background so progress
            // keeps flowing and pending calls aren't cancelled. The FGS clears keepAlive when done.
            Log.d("HomeHealth_WS", "App background but keepAlive active → keeping socket open")
            return
        }
        Log.d("HomeHealth_WS", "App background → closing socket, suspending reconnect loop")
        cancelReconnect()
        closeSocket()
        // Keep credentials. Status goes DISCONNECTED while backgrounded so any observer
        // that re-subscribes sees an accurate "not connected" until we resume — but
        // AUTH_INVALID / IP_BANNED stay sticky (see setIdleStatus).
        setIdleStatus()
    }

    // ── Socket management ─────────────────────────────────────────────────────

    // Async on purpose: before touching the network we ask the repository for a
    // currently-valid token (refreshing the OAuth pair if it expired while we were
    // backgrounded/disconnected). Presenting a stale token would get auth_invalid,
    // which HA logs as a failed login and counts toward its ip_ban threshold.
    // Callers set status=CONNECTING first; that invariant doubles as the abort check.
    private fun openSocket() {
        scope.launch {
            connectMutex.withLock {
                if (webSocket != null) return@withLock   // another path already connected
                if (!shouldStayConnected) return@withLock
                if (_status.value != WsConnectionStatus.CONNECTING) return@withLock // torn down while queued
                val freshToken = tokenProvider
                    ?.let { provider -> runCatching { provider() }.getOrNull() }
                    ?.takeIf { it.isNotBlank() }
                    ?: token
                if (currentHaUrl.isBlank() || freshToken.isBlank()) {
                    Log.w("HomeHealth_WS", "openSocket: missing URL or token — not connecting")
                    _status.value = WsConnectionStatus.DISCONNECTED
                    return@withLock
                }
                token = freshToken
                val base = currentHaUrl.trimEnd('/')
                // Prefix-only scheme swap; a URL without a scheme defaults to TLS rather
                // than silently falling back to cleartext.
                val wsUrl = when {
                    base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
                    base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
                    base.startsWith("wss://", ignoreCase = true) ||
                        base.startsWith("ws://", ignoreCase = true) -> base
                    else -> "wss://$base"
                } + "/api/websocket"
                Log.d("HomeHealth_WS", "Opening WebSocket → $wsUrl")
                runCatching {
                    okHttpClient.newWebSocket(Request.Builder().url(wsUrl).build(), Listener())
                }.onSuccess { newWs ->
                    webSocket = newWs
                }.onFailure { e ->
                    // Malformed URL — deterministic, so no reconnect loop.
                    Log.e("HomeHealth_WS", "openSocket: invalid HA URL '$wsUrl': ${e.message}")
                    _status.value = WsConnectionStatus.ERROR
                }
            }
        }
    }

    private fun closeSocket() {
        webSocket?.close(1000, null)
        webSocket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
        // Drop any open WebRTC (or other) subscriptions — their ids belong to the
        // socket we're tearing down and would otherwise leak handlers across reconnects.
        subscriptions.clear()
    }

    private fun clearData() {
        _areas.value = emptyList()
        _devices.value = emptyList()
        _entities.value = emptyList()
        _states.value = emptyMap()
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    private fun scheduleReconnect() {
        if (!shouldStayConnected) {
            Log.d("HomeHealth_WS", "Skipping reconnect — app is in background")
            return
        }
        if (_status.value == WsConnectionStatus.AUTH_INVALID || _status.value == WsConnectionStatus.IP_BANNED) {
            Log.d("HomeHealth_WS", "Skipping reconnect — status=${_status.value}")
            return
        }
        val url = currentHaUrl
        val tok = token
        if (url.isBlank() || tok.isBlank()) return
        cancelReconnect()
        reconnectJob = scope.launch {
            Log.d("HomeHealth_WS", "Reconnecting in 10s to $url")
            delay(10_000L)
            if (!shouldStayConnected) {
                Log.d("HomeHealth_WS", "Reconnect canceled — app went to background during wait")
                return@launch
            }
            if (currentHaUrl == url && token == tok && webSocket == null) {
                _status.value = WsConnectionStatus.CONNECTING
                openSocket()
            }
        }
    }

    /**
     * Wait (up to 6s) for a socket that is safe to send commands on: the auth handshake
     * completed and the initial state load finished (READY). HA treats ANY non-auth
     * message during the login handshake as a protocol violation — it replies
     * auth_invalid ("Auth message incorrectly formatted") and drops the connection,
     * which the app then misreports as a rejected token ("Session expired"). The race
     * only manifests on high-latency remote links where the handshake is slow enough
     * for an eager command (get_config, energy/get_prefs, …) to jump the queue.
     * Returns null when READY doesn't arrive in time or the connection is in a state
     * that won't recover on its own.
     */
    private suspend fun awaitReadySocket(tag: String): WebSocket? {
        var waited = 0L
        while (_status.value != WsConnectionStatus.READY && waited < 6_000L) {
            val s = _status.value
            if (s == WsConnectionStatus.AUTH_INVALID || s == WsConnectionStatus.IP_BANNED) break
            delay(200)
            waited += 200
        }
        val ws = webSocket
        if (ws == null || _status.value != WsConnectionStatus.READY) {
            Log.w("HomeHealth_WS", "$tag: not ready (status=${_status.value}) after ${waited}ms — command not sent")
            return null
        }
        return ws
    }

    suspend fun getAreaEntities(areaId: String): Set<String> {
        val ws = awaitReadySocket("getAreaEntities($areaId)") ?: return emptySet()
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "search/related", "item_type" to "area", "item_id" to areaId)))
        return try {
            val msg = withTimeout(5_000L) { deferred.await() }
            if (msg["success"]?.asBoolean != true) return emptySet()
            msg.getAsJsonObject("result")?.getAsJsonArray("entity")
                ?.mapNotNull { it.asString }?.toSet() ?: emptySet()
        } catch (_: Exception) {
            pending.remove(id)
            emptySet()
        }
    }

    /**
     * Fetch the Energy dashboard's source wiring (`energy/get_prefs`) — solar/grid/battery
     * statistic + rate + SoC entities. Returns null on failure or when the user never set
     * the Energy dashboard up. Parses both the flat per-source fields and grid's
     * flow_from/flow_to arrays, plus `power_config.stat_rate` where present.
     */
    suspend fun getEnergyPrefs(): HaEnergyPrefs? {
        val ws = awaitReadySocket("getEnergyPrefs") ?: return null
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "energy/get_prefs")))
        return try {
            val msg = withTimeout(5_000L) { deferred.await() }
            if (msg["success"]?.asBoolean != true) return null
            val result = msg.getAsJsonObject("result") ?: return null
            val sources = result.getAsJsonArray("energy_sources") ?: return null

            fun str(o: JsonObject, key: String): String? =
                o[key]?.takeIf { it.isJsonPrimitive }?.runCatching { asString }?.getOrNull()?.takeIf { it.isNotBlank() }

            fun rateOf(o: JsonObject): String? =
                str(o, "stat_rate")
                    ?: o["power_config"]?.takeIf { it.isJsonObject }?.asJsonObject?.let { str(it, "stat_rate") }

            val prefs = HaEnergyPrefs(
                solarEnergyFrom = mutableListOf(), solarRate = mutableListOf(),
                gridEnergyFrom = mutableListOf(), gridEnergyTo = mutableListOf(), gridRate = mutableListOf(),
                batteryEnergyFrom = mutableListOf(), batteryEnergyTo = mutableListOf(),
                batterySoc = mutableListOf(), batteryRate = mutableListOf(),
            )
            for (el in sources) {
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                when (str(o, "type")) {
                    "solar" -> {
                        str(o, "stat_energy_from")?.let { (prefs.solarEnergyFrom as MutableList) += it }
                        rateOf(o)?.let { (prefs.solarRate as MutableList) += it }
                    }
                    "battery" -> {
                        str(o, "stat_energy_from")?.let { (prefs.batteryEnergyFrom as MutableList) += it }
                        str(o, "stat_energy_to")?.let { (prefs.batteryEnergyTo as MutableList) += it }
                        str(o, "stat_soc")?.let { (prefs.batterySoc as MutableList) += it }
                        rateOf(o)?.let { (prefs.batteryRate as MutableList) += it }
                    }
                    "grid" -> {
                        // Modern shape: flow_from/flow_to arrays (one entry per tariff).
                        o["flow_from"]?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { f ->
                            val fo = f.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                            str(fo, "stat_energy_from")?.let { (prefs.gridEnergyFrom as MutableList) += it }
                            rateOf(fo)?.let { (prefs.gridRate as MutableList) += it }
                        }
                        o["flow_to"]?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { f ->
                            val fo = f.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                            str(fo, "stat_energy_to")?.let { (prefs.gridEnergyTo as MutableList) += it }
                            rateOf(fo)?.let { (prefs.gridRate as MutableList) += it }
                        }
                        // Flat fallback shape.
                        str(o, "stat_energy_from")?.let { (prefs.gridEnergyFrom as MutableList) += it }
                        str(o, "stat_energy_to")?.let { (prefs.gridEnergyTo as MutableList) += it }
                        rateOf(o)?.let { (prefs.gridRate as MutableList) += it }
                    }
                }
            }
            prefs.takeUnless { it.isEmpty }
        } catch (_: Exception) {
            pending.remove(id)
            null
        }
    }

    /**
     * Fetch HA's core config (`get_config`) for the home's latitude/longitude/elevation.
     * Used by the Energy card to centre its 3D map and drive solar-position math. Returns
     * null on failure / older HA; callers fall back to `zone.home` coordinates.
     */
    suspend fun getHaConfig(): HaCoreConfig? {
        val ws = awaitReadySocket("getHaConfig") ?: return null
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "get_config")))
        return try {
            val msg = withTimeout(5_000L) { deferred.await() }
            if (msg["success"]?.asBoolean != true) return null
            val r = msg.getAsJsonObject("result") ?: return null
            fun num(key: String) = r[key]?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()
            HaCoreConfig(
                latitude = num("latitude"),
                longitude = num("longitude"),
                elevation = num("elevation"),
                timeZone = r["time_zone"]?.takeIf { !it.isJsonNull }?.asString,
            )
        } catch (_: Exception) {
            pending.remove(id)
            null
        }
    }

    /**
     * List the installed TTS engines via the WS `tts/engine/list` command. Each provider
     * carries its engine_id (the entity_id used as the `tts.speak` target), a friendly
     * name, and the languages it supports (needed to query its voices). Returns empty on
     * failure or older HA. Used by the Settings voice picker + the announce composer.
     */
    suspend fun listTtsEngines(): List<HaTtsEngine> {
        val ws = awaitReadySocket("listTtsEngines") ?: return emptyList()
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "tts/engine/list")))
        return try {
            val msg = withTimeout(5_000L) { deferred.await() }
            if (msg["success"]?.asBoolean != true) return emptyList()
            msg.getAsJsonObject("result")?.getAsJsonArray("providers")
                ?.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val engineId = str(o, "engine_id") ?: return@mapNotNull null
                    val langs = o["supported_languages"]?.takeIf { it.isJsonArray }?.asJsonArray
                        ?.mapNotNull { l -> l.takeIf { !it.isJsonNull }?.asString } ?: emptyList()
                    HaTtsEngine(
                        engineId = engineId,
                        name = str(o, "name") ?: engineId,
                        supportedLanguages = langs,
                        deprecated = o["deprecated"]?.takeIf { !it.isJsonNull }?.asBoolean == true,
                    )
                } ?: emptyList()
        } catch (_: Exception) {
            pending.remove(id)
            emptyList()
        }
    }

    /**
     * List the voices a TTS engine offers for [language] via the WS `tts/engine/voices`
     * command. Returns empty for engines without per-voice selection (e.g. Google Translate)
     * or on failure. [voiceId] feeds `options.voice` of `tts.speak`.
     */
    suspend fun listTtsVoices(engineId: String, language: String): List<HaTtsVoice> {
        val ws = awaitReadySocket("listTtsVoices($engineId)") ?: return emptyList()
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "tts/engine/voices", "engine_id" to engineId, "language" to language)))
        return try {
            val msg = withTimeout(5_000L) { deferred.await() }
            if (msg["success"]?.asBoolean != true) return emptyList()
            msg.getAsJsonObject("result")?.getAsJsonArray("voices")
                ?.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val vid = str(o, "voice_id") ?: return@mapNotNull null
                    HaTtsVoice(voiceId = vid, name = str(o, "name") ?: vid)
                } ?: emptyList()
        } catch (_: Exception) {
            pending.remove(id)
            emptyList()
        }
    }

    /**
     * Resolve a camera entity to an HLS stream URL via the WS `camera/stream` command.
     * Returned URL is the relative HA path (e.g. `/api/hls/<token>/master.m3u8`) — caller
     * must prefix with the HA base URL. The path includes a signed token so no further
     * auth header is needed when fetching the playlist or segments.
     */
    suspend fun getCameraStreamUrl(entityId: String): String? {
        val ws = awaitReadySocket("getCameraStreamUrl($entityId)") ?: return null
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val payload = gson.toJson(mapOf("id" to id, "type" to "camera/stream", "entity_id" to entityId))
        ws.send(payload)
        return try {
            // 15s: a cold camera's stream worker can take several seconds to produce
            // its first HLS playlist, especially over a remote link.
            val resp = withTimeout(15_000L) { deferred.await() }
            if (resp["success"]?.asBoolean != true) {
                Log.w("HomeHealth_WS", "camera/stream($entityId) refused by HA: $resp")
                return null
            }
            val url = resp.getAsJsonObject("result")?.get("url")?.asString
            Log.d("HomeHealth_WS", "camera/stream($entityId) → $url")
            url
        } catch (e: Exception) {
            Log.w("HomeHealth_WS", "camera/stream($entityId) errored: ${e.message}")
            pending.remove(id)
            null
        }
    }

    /**
     * Fetch HA's WebRTC client configuration for [entityId] via the WS
     * `camera/webrtc/get_client_config` command. HA returns `configuration.iceServers[]`
     * — the STUN/TURN servers the client should use (incl. the TURN relay from Nabu Casa
     * Cloud or a configured `web_rtc: ice_servers:`). Returns null on failure / older HA,
     * in which case the player falls back to a bare public STUN server. The `urls` field
     * of each server may be a single string or a JSON array — both are handled.
     */
    suspend fun getCameraWebRtcClientConfig(entityId: String): List<WebRtcIceServer>? {
        val ws = awaitReadySocket("getCameraWebRtcClientConfig($entityId)") ?: return null
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        ws.send(gson.toJson(mapOf("id" to id, "type" to "camera/webrtc/get_client_config", "entity_id" to entityId)))
        return try {
            val resp = withTimeout(5_000L) { deferred.await() }
            if (resp["success"]?.asBoolean != true) {
                Log.w("HomeHealth_WS", "get_client_config($entityId) refused by HA: $resp")
                return null
            }
            val iceServers = resp.getAsJsonObject("result")
                ?.getAsJsonObject("configuration")
                ?.getAsJsonArray("iceServers")
                ?: return emptyList()
            val servers = iceServers.mapNotNull { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val urlsEl = o["urls"] ?: o["url"] ?: return@mapNotNull null
                val urls = when {
                    urlsEl.isJsonArray -> urlsEl.asJsonArray.mapNotNull { it.takeIf { u -> !u.isJsonNull }?.asString }
                    !urlsEl.isJsonNull -> listOf(urlsEl.asString)
                    else -> emptyList()
                }.filter { it.isNotBlank() }
                if (urls.isEmpty()) null else WebRtcIceServer(
                    urls = urls,
                    username = o["username"]?.takeIf { !it.isJsonNull }?.asString,
                    credential = o["credential"]?.takeIf { !it.isJsonNull }?.asString,
                )
            }
            servers
        } catch (e: Exception) {
            Log.w("HomeHealth_WS", "get_client_config($entityId) errored: ${e.message}")
            pending.remove(id)
            null
        }
    }

    /**
     * Open a WebRTC offer subscription for [entityId]. Sends our SDP [offerSdp] and
     * routes the streamed `answer` / `candidate` / `session` / `error` events to
     * [onSignal] (parsed into [WebRtcSignal]). Returns the subscription id — pass it to
     * [unsubscribeCameraWebRtc] to tear the session down — or null if the socket isn't
     * READY. Falls back to HLS at the call site if this returns null or surfaces an error.
     */
    suspend fun cameraWebRtcOffer(
        entityId: String,
        offerSdp: String,
        onSignal: (WebRtcSignal) -> Unit,
    ): Int? {
        val ws = awaitReadySocket("cameraWebRtcOffer($entityId)") ?: return null
        val id = idGen.incrementAndGet()
        subscriptions[id] = { event -> parseWebRtcSignal(event)?.let(onSignal) }
        val payload = gson.toJson(
            mapOf("id" to id, "type" to "camera/webrtc/offer", "entity_id" to entityId, "offer" to offerSdp)
        )
        val sent = ws.send(payload)
        if (!sent) {
            Log.w("HomeHealth_WS", "cameraWebRtcOffer($entityId): ws.send() returned false")
            subscriptions.remove(id)
            return null
        }
        Log.d("HomeHealth_WS", "cameraWebRtcOffer($entityId) → subscription id=$id")
        return id
    }

    /** Post a locally-gathered ICE candidate to HA for an active WebRTC session. */
    fun cameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate) {
        // A candidate is only meaningful for a session on the CURRENT, authed socket; a
        // late one arriving during a reconnect's auth handshake would kill the connection.
        if (_status.value != WsConnectionStatus.READY) return
        val ws = webSocket ?: return
        val candMap = buildMap<String, Any> {
            put("candidate", candidate.candidate)
            candidate.sdpMid?.let { put("sdpMid", it) }
            put("sdpMLineIndex", candidate.sdpMLineIndex)
        }
        val payload = gson.toJson(
            mapOf(
                "id" to idGen.incrementAndGet(),
                "type" to "camera/webrtc/candidate",
                "entity_id" to entityId,
                "session_id" to sessionId,
                "candidate" to candMap,
            )
        )
        ws.send(payload)
    }

    /** Close a WebRTC (or other) subscription opened via [cameraWebRtcOffer]. */
    fun unsubscribeCameraWebRtc(subscriptionId: Int) {
        subscriptions.remove(subscriptionId) ?: return
        // Subscriptions die with their socket (closeSocket clears them), so there is
        // nothing to unsubscribe on a not-yet-authed reconnect socket — and sending
        // during the auth handshake would kill the connection.
        if (_status.value != WsConnectionStatus.READY) return
        val ws = webSocket ?: return
        ws.send(
            gson.toJson(
                mapOf(
                    "id" to idGen.incrementAndGet(),
                    "type" to "unsubscribe_events",
                    "subscription" to subscriptionId,
                )
            )
        )
    }

    // Parse one HA WebRTC subscription event into a WebRtcSignal. Defensive about
    // version differences: candidate may be a JSON object or a bare string, and the
    // session id arrives under "id" (current) or "session_id" (older builds).
    private fun parseWebRtcSignal(event: JsonObject): WebRtcSignal? =
        when (event["type"]?.asString) {
            "answer" -> event["answer"]?.takeIf { !it.isJsonNull }?.asString
                ?.let { WebRtcSignal.Answer(it) }
            "candidate" -> {
                val c = event["candidate"]
                when {
                    c == null || c.isJsonNull -> null
                    c.isJsonObject -> {
                        val o = c.asJsonObject
                        o["candidate"]?.takeIf { !it.isJsonNull }?.asString?.let { s ->
                            WebRtcSignal.Candidate(
                                WebRtcIceCandidate(
                                    candidate = s,
                                    sdpMid = o["sdpMid"]?.takeIf { !it.isJsonNull }?.asString,
                                    sdpMLineIndex = o["sdpMLineIndex"]?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                                )
                            )
                        }
                    }
                    else -> WebRtcSignal.Candidate(WebRtcIceCandidate(c.asString, null, 0))
                }
            }
            "session" -> (event["id"] ?: event["session_id"])?.takeIf { !it.isJsonNull }?.asString
                ?.let { WebRtcSignal.Session(it) }
            "error" -> WebRtcSignal.Error(
                event["message"]?.takeIf { !it.isJsonNull }?.asString ?: "WebRTC error"
            )
            else -> null
        }

    /** Current HA base URL (for building camera snapshot URLs from the UI side). */
    fun baseUrl(): String = currentHaUrl

    suspend fun callService(domain: String, service: String, serviceData: Map<String, Any>): Boolean =
        callServiceResult(domain, service, serviceData).success

    /** Like [callService] but also returns HA's error message on failure (for surfacing to the UI). */
    suspend fun callServiceResult(domain: String, service: String, serviceData: Map<String, Any>): ServiceCallResult {
        // READY-gate like every other command: a call fired mid-reconnect used to be sent
        // during the auth handshake, which HA rejects as a malformed auth message and
        // kills the whole connection. Waiting also means a tap during a reconnect now
        // goes through once the link is back instead of being dropped.
        val ws = awaitReadySocket("callService($domain.$service)")
        if (ws == null) {
            Log.w("HomeHealth_WS", "callService($domain.$service) DROPPED — not connected (status=${_status.value})")
            return ServiceCallResult(false, "Not connected to Home Assistant")
        }
        val deferred = CompletableDeferred<JsonObject>()
        // Assign id + enqueue atomically so concurrent calls send in id order (see sendMutex).
        val id = sendMutex.withLock {
            val newId = idGen.incrementAndGet()
            pending[newId] = deferred
            val payload = gson.toJson(mapOf("id" to newId, "type" to "call_service", "domain" to domain, "service" to service, "service_data" to serviceData))
            Log.d("HomeHealth_WS", "callService → $payload")
            val sent = ws.send(payload)
            if (!sent) Log.w("HomeHealth_WS", "callService($domain.$service id=$newId) — ws.send() returned false (queue full or closed)")
            newId
        }
        return try {
            val resp = withTimeout(8_000L) { deferred.await() }
            val ok = resp["success"]?.asBoolean == true
            val err = if (ok) null else
                resp.getAsJsonObject("error")?.get("message")?.takeIf { !it.isJsonNull }?.asString
            Log.d("HomeHealth_WS", "callService($domain.$service id=$id) result: success=$ok | $resp")
            ServiceCallResult(ok, err)
        } catch (_: TimeoutCancellationException) {
            Log.w("HomeHealth_WS", "callService($domain.$service id=$id) TIMED OUT after 8s — no response from HA")
            pending.remove(id)
            ServiceCallResult(false, "No response from Home Assistant")
        } catch (e: Exception) {
            Log.e("HomeHealth_WS", "callService($domain.$service id=$id) FAILED: ${e.message}")
            ServiceCallResult(false, e.message)
        }
    }

    /**
     * Call a service that returns data (`return_response: true`, e.g.
     * `music_assistant.search`) and hand back the `result.response` object — null on
     * failure or timeout. Longer timeout than [callService]: a search may fan out to
     * slow streaming providers before HA answers.
     */
    suspend fun callServiceWithResponse(
        domain: String,
        service: String,
        serviceData: Map<String, Any>,
    ): JsonObject? {
        val ws = awaitReadySocket("callServiceWithResponse($domain.$service)") ?: return null
        val deferred = CompletableDeferred<JsonObject>()
        val id = sendMutex.withLock {
            val newId = idGen.incrementAndGet()
            pending[newId] = deferred
            ws.send(
                gson.toJson(
                    mapOf(
                        "id" to newId,
                        "type" to "call_service",
                        "domain" to domain,
                        "service" to service,
                        "service_data" to serviceData,
                        "return_response" to true,
                    )
                )
            )
            newId
        }
        return try {
            val resp = withTimeout(15_000L) { deferred.await() }
            if (resp["success"]?.asBoolean != true) {
                val err = resp.getAsJsonObject("error")?.get("message")?.takeIf { !it.isJsonNull }?.asString
                Log.w("HomeHealth_WS", "callServiceWithResponse($domain.$service id=$id) failed: $err")
                return null
            }
            resp.getAsJsonObject("result")?.get("response")?.takeIf { it.isJsonObject }?.asJsonObject
        } catch (_: TimeoutCancellationException) {
            Log.w("HomeHealth_WS", "callServiceWithResponse($domain.$service id=$id) TIMED OUT")
            pending.remove(id)
            null
        } catch (e: Exception) {
            Log.e("HomeHealth_WS", "callServiceWithResponse($domain.$service id=$id) FAILED: ${e.message}")
            pending.remove(id)
            null
        }
    }

    private inner class Listener : WebSocketListener() {
        // Callbacks from a WebSocket we've already replaced fire asynchronously.
        // Drop them — they refer to the *previous* connection and must not touch
        // the current webSocket reference or status.
        private fun isCurrent(ws: WebSocket): Boolean = ws === webSocket

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket)) return
            val msg = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull() ?: return
            when (msg["type"]?.asString) {
                "auth_required" -> {
                    Log.d("HomeHealth_WS", "Auth required — sending token")
                    webSocket.send(gson.toJson(mapOf("type" to "auth", "access_token" to token)))
                }
                "auth_ok" -> {
                    Log.d("HomeHealth_WS", "Auth OK")
                    _lastAuthError.value = null
                    onAuthOk(webSocket)
                }
                "auth_invalid" -> {
                    val reason = msg["message"]?.takeIf { !it.isJsonNull }?.asString
                    closeSocket()
                    if (reason?.startsWith("Auth message incorrectly formatted") == true) {
                        // NOT a credential rejection: a command slipped into the auth
                        // handshake (protocol race — should be prevented by the READY
                        // gates, but never say never). HA doesn't count this as a failed
                        // login, the token is fine — reconnect instead of stranding the
                        // app on "Session expired" and demanding a needless re-login.
                        Log.e("HomeHealth_WS", "Auth-phase protocol error: \"$reason\" — reconnecting")
                        _status.value = WsConnectionStatus.ERROR
                        scheduleReconnect()
                    } else {
                        // HA counts every credential-level auth_invalid as a failed login
                        // attempt against its ip_ban threshold. Mark the state distinctly
                        // so (a) the UI can prompt for re-auth, (b) the repository can
                        // attempt an OAuth refresh, and (c) scheduleReconnect /
                        // setCredentials stop hammering with the same bad token. Keep HA's
                        // reason string — it tells a bad token apart from a user
                        // restricted to local-network logins.
                        Log.e("HomeHealth_WS", "Auth INVALID — HA said: \"$reason\" — suspending reconnects")
                        _lastAuthError.value = reason
                        _status.value = WsConnectionStatus.AUTH_INVALID
                    }
                }
                "result" -> onResult(msg)
                "event" -> onEvent(msg)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket)) {
                Log.d("HomeHealth_WS", "Stale onFailure ignored: ${t.message}")
                return
            }
            val code = response?.code
            Log.e("HomeHealth_WS", "WS failure: ${t.message} | response=$code")
            this@HaWebSocketClient.webSocket = null
            if (code == 403) {
                // HA's ip_ban middleware refused the WebSocket upgrade — every retry just
                // resets the ban-clear timer on the server side.
                Log.e("HomeHealth_WS", "WS upgrade 403 — IP banned by HA, not reconnecting")
                _status.value = WsConnectionStatus.IP_BANNED
            } else {
                _status.value = WsConnectionStatus.ERROR
                scheduleReconnect()
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket)) {
                Log.d("HomeHealth_WS", "Stale onClosed ignored ($code)")
                return
            }
            Log.d("HomeHealth_WS", "WS closed unexpectedly ($code) — scheduling reconnect")
            this@HaWebSocketClient.webSocket = null
            _status.value = WsConnectionStatus.ERROR
            scheduleReconnect()
        }
    }

    private fun onAuthOk(ws: WebSocket) {
        Log.d("HomeHealth_WS", "Sending area/device/entity/state requests")
        clearData()
        areaCmd = idGen.incrementAndGet()
        ws.send(gson.toJson(mapOf("id" to areaCmd, "type" to "config/area_registry/list")))
        deviceCmd = idGen.incrementAndGet()
        ws.send(gson.toJson(mapOf("id" to deviceCmd, "type" to "config/device_registry/list")))
        entityCmd = idGen.incrementAndGet()
        ws.send(gson.toJson(mapOf("id" to entityCmd, "type" to "config/entity_registry/list")))
        statesCmd = idGen.incrementAndGet()
        ws.send(gson.toJson(mapOf("id" to statesCmd, "type" to "get_states")))
        val subId = idGen.incrementAndGet()
        ws.send(gson.toJson(mapOf("id" to subId, "type" to "subscribe_events", "event_type" to "state_changed")))
    }

    private fun onResult(msg: JsonObject) {
        val id = msg["id"]?.asInt ?: return
        pending.remove(id)?.complete(msg)
        if (msg["success"]?.asBoolean != true) {
            // A subscription command HA refuses outright (e.g. camera/webrtc/offer on an
            // older HA, or an unsupported camera) never streams events. Synthesize an
            // error so the caller falls back to HLS immediately instead of waiting out
            // the timeout.
            subscriptions[id]?.let { handler ->
                val message = msg.getAsJsonObject("error")
                    ?.get("message")?.takeIf { !it.isJsonNull }?.asString
                handler(JsonObject().apply {
                    addProperty("type", "error")
                    addProperty("message", message ?: "command refused")
                })
            }
            return
        }
        val result = msg["result"] ?: return
        if (!result.isJsonArray) return
        val arr = result.asJsonArray
        when (id) {
            areaCmd -> {
                _areas.value = arr.mapNotNull { parseArea(it.asJsonObject) }
                Log.d("HomeHealth_WS", "Areas loaded: ${_areas.value.size} → ${_areas.value.map { it.name }}")
            }
            deviceCmd -> {
                _devices.value = arr.mapNotNull { parseDevice(it.asJsonObject) }
                Log.d("HomeHealth_WS", "Devices loaded: ${_devices.value.size}")
            }
            entityCmd -> {
                _entities.value = arr.mapNotNull { parseEntityEntry(it.asJsonObject) }
                val lights = _entities.value.filter { it.entity_id.startsWith("light.") }
                Log.d("HomeHealth_WS", "Entities loaded: ${_entities.value.size} total, ${lights.size} lights → ${lights.take(10).map { it.entity_id }}")
            }
            statesCmd -> {
                _states.value = arr.mapNotNull { elem ->
                    runCatching {
                        val obj = elem.asJsonObject
                        obj["entity_id"].asString to parseState(obj)
                    }.getOrNull()
                }.toMap()
                _status.value = WsConnectionStatus.READY
                val lightStates = _states.value.keys.filter { it.startsWith("light.") }
                Log.d("HomeHealth_WS", "States loaded: ${_states.value.size} total, ${lightStates.size} light states → ${lightStates.take(10)}")
            }
        }
    }

    private fun onEvent(msg: JsonObject) {
        // Route subscription events (camera/webrtc/offer etc.) to their handler by id
        // first; only the global state_changed subscription falls through below.
        val subId = msg["id"]?.asInt
        if (subId != null) {
            subscriptions[subId]?.let { handler ->
                msg["event"]?.takeIf { it.isJsonObject }?.asJsonObject?.let(handler)
                return
            }
        }
        val event = msg["event"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        if (event["event_type"]?.asString != "state_changed") return
        val data = event["data"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val entityId = data["entity_id"]?.asString ?: return
        // new_state is JsonNull when an entity is removed.
        val newState = data["new_state"]?.takeIf { it.isJsonObject }?.asJsonObject
        _states.value = if (newState != null) {
            _states.value + (entityId to parseState(newState))
        } else {
            _states.value - entityId
        }
    }

    private fun str(obj: JsonObject, key: String): String? =
        obj[key]?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }

    private fun parseArea(obj: JsonObject) = runCatching {
        HaArea(area_id = obj["area_id"].asString, name = obj["name"].asString, icon = str(obj, "icon"))
    }.getOrNull()

    private fun parseDevice(obj: JsonObject) = runCatching {
        HaDeviceEntry(id = obj["id"].asString, area_id = str(obj, "area_id"), name = str(obj, "name") ?: str(obj, "name_by_user"))
    }.getOrNull()

    private fun parseEntityEntry(obj: JsonObject) = runCatching {
        HaEntityEntry(
            entity_id = obj["entity_id"].asString,
            area_id = str(obj, "area_id"),
            device_id = str(obj, "device_id"),
            name = str(obj, "name"),
            original_name = str(obj, "original_name"),
            platform = str(obj, "platform"),
            disabled_by = str(obj, "disabled_by"),
            config_entry_id = str(obj, "config_entry_id"),
        )
    }.getOrNull()

    private fun parseState(obj: JsonObject): HaState {
        // HA sometimes emits "attributes": null or omits it; treat both as empty.
        val attrs = obj["attributes"]?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        return HaState(
            entity_id = obj["entity_id"]?.asString ?: "",
            state = obj["state"]?.asString ?: "unknown",
            attributes = HaStateAttributes(
                friendly_name = str(attrs, "friendly_name"),
                brightness = attrs["brightness"]?.takeIf { !it.isJsonNull }?.asFloat,
                // rgb_color can be JsonNull for non-color lights — getAsJsonArray() would throw.
                rgb_color = attrs["rgb_color"]?.takeIf { it.isJsonArray }?.asJsonArray?.map { it.asInt },
                color_temp_kelvin = attrs["color_temp_kelvin"]?.takeIf { !it.isJsonNull }?.asInt,
                supported_color_modes = attrs["supported_color_modes"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { runCatching { it.asString }.getOrNull() },
                current_temperature = attrs["current_temperature"]?.takeIf { !it.isJsonNull }?.asFloat,
                temperature = attrs["temperature"]?.takeIf { !it.isJsonNull }?.asFloat,
                current_humidity = attrs["current_humidity"]?.takeIf { !it.isJsonNull }?.asInt,
                humidity = attrs["humidity"]?.takeIf { !it.isJsonNull }?.asInt,
                device_class = str(attrs, "device_class"),
                unit_of_measurement = str(attrs, "unit_of_measurement"),
                hvac_action = str(attrs, "hvac_action"),
                hvac_modes = attrs["hvac_modes"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList(),
                fan_mode = str(attrs, "fan_mode"),
                fan_modes = attrs["fan_modes"]?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { runCatching { it.asString }.getOrNull() } ?: emptyList(),
                target_temp_step = attrs["target_temp_step"]?.takeIf { !it.isJsonNull }?.asFloat,
                min_temp = attrs["min_temp"]?.takeIf { !it.isJsonNull }?.asFloat,
                max_temp = attrs["max_temp"]?.takeIf { !it.isJsonNull }?.asFloat,
                raw = attrs,
            ),
            last_changed = obj["last_changed"]?.takeIf { !it.isJsonNull }?.asString,
            last_updated = obj["last_updated"]?.takeIf { !it.isJsonNull }?.asString,
        )
    }
}
