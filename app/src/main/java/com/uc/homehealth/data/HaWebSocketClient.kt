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
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    private var areaCmd = -1
    private var deviceCmd = -1
    private var entityCmd = -1
    private var statesCmd = -1
    private var token = ""
    private var currentHaUrl = ""

    @Volatile private var appInForeground = false
    private var reconnectJob: Job? = null

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
        if (appInForeground && haUrl.isNotBlank() && accessToken.isNotBlank()) {
            runHttpDiagnostic(haUrl, accessToken)
            _status.value = WsConnectionStatus.CONNECTING
            openSocket()
        } else {
            Log.d("HomeHealth_WS", "setCredentials: deferring socket open (foreground=$appInForeground)")
        }
    }

    // One-shot REST ping so failures can be distinguished as "Android blocked LAN entirely"
    // vs WebSocket-specific. Runs only on credential change, not on every reconnect.
    // Also surfaces HA's 403 (IP banned by ip_ban middleware) before the WS even tries to
    // authenticate, so we can abort the WS connect and avoid adding another ban-counter tick.
    private fun runHttpDiagnostic(haUrl: String, accessToken: String) {
        scope.launch {
            try {
                val restUrl = "${haUrl.trimEnd('/')}/api/"
                Log.d("HomeHealth_WS", "HTTP diagnostic → GET $restUrl")
                val req = Request.Builder().url(restUrl)
                    .header("Authorization", "Bearer $accessToken")
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                val code = resp.code
                resp.close()
                if (code == 403) {
                    Log.e("HomeHealth_WS", "HTTP diagnostic ✗ 403 — IP banned by HA. Aborting WS connect.")
                    closeSocket()
                    _status.value = WsConnectionStatus.IP_BANNED
                } else {
                    Log.d("HomeHealth_WS", "HTTP diagnostic ✓ status=$code — REST API reachable")
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
        if (appInForeground) {
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
        Log.d("HomeHealth_WS", "App background → closing socket, suspending reconnect loop")
        cancelReconnect()
        closeSocket()
        // Keep credentials. Status stays DISCONNECTED while backgrounded so any
        // observer that re-subscribes sees an accurate "not connected" until we resume.
        _status.value = WsConnectionStatus.DISCONNECTED
    }

    // ── Socket management ─────────────────────────────────────────────────────

    private fun openSocket() {
        val wsUrl = currentHaUrl.trimEnd('/')
            .replace("https://", "wss://")
            .replace("http://", "ws://") + "/api/websocket"
        Log.d("HomeHealth_WS", "Opening WebSocket → $wsUrl")
        val newWs = okHttpClient.newWebSocket(Request.Builder().url(wsUrl).build(), Listener())
        webSocket = newWs
    }

    private fun closeSocket() {
        webSocket?.close(1000, null)
        webSocket = null
        pending.values.forEach { it.cancel() }
        pending.clear()
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
        if (!appInForeground) {
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
            if (!appInForeground) {
                Log.d("HomeHealth_WS", "Reconnect canceled — app went to background during wait")
                return@launch
            }
            if (currentHaUrl == url && token == tok && webSocket == null) {
                _status.value = WsConnectionStatus.CONNECTING
                openSocket()
            }
        }
    }

    suspend fun getAreaEntities(areaId: String): Set<String> {
        val ws = webSocket ?: return emptySet()
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
     * Resolve a camera entity to an HLS stream URL via the WS `camera/stream` command.
     * Returned URL is the relative HA path (e.g. `/api/hls/<token>/master.m3u8`) — caller
     * must prefix with the HA base URL. The path includes a signed token so no further
     * auth header is needed when fetching the playlist or segments.
     */
    suspend fun getCameraStreamUrl(entityId: String): String? {
        val ws = webSocket ?: return null
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val payload = gson.toJson(mapOf("id" to id, "type" to "camera/stream", "entity_id" to entityId))
        ws.send(payload)
        return try {
            val resp = withTimeout(8_000L) { deferred.await() }
            if (resp["success"]?.asBoolean != true) return null
            resp.getAsJsonObject("result")?.get("url")?.asString
        } catch (_: Exception) {
            pending.remove(id)
            null
        }
    }

    /** Current HA base URL (for building camera snapshot URLs from the UI side). */
    fun baseUrl(): String = currentHaUrl

    suspend fun callService(domain: String, service: String, serviceData: Map<String, Any>): Boolean {
        val ws = webSocket
        if (ws == null) {
            Log.w("HomeHealth_WS", "callService($domain.$service) DROPPED — webSocket is null (status=${_status.value})")
            return false
        }
        val id = idGen.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val payload = gson.toJson(mapOf("id" to id, "type" to "call_service", "domain" to domain, "service" to service, "service_data" to serviceData))
        Log.d("HomeHealth_WS", "callService → $payload")
        val sent = ws.send(payload)
        if (!sent) Log.w("HomeHealth_WS", "callService($domain.$service id=$id) — ws.send() returned false (queue full or closed)")
        return try {
            val resp = withTimeout(8_000L) { deferred.await() }
            val ok = resp["success"]?.asBoolean == true
            Log.d("HomeHealth_WS", "callService($domain.$service id=$id) result: success=$ok | $resp")
            ok
        } catch (_: TimeoutCancellationException) {
            Log.w("HomeHealth_WS", "callService($domain.$service id=$id) TIMED OUT after 8s — no response from HA")
            pending.remove(id)
            false
        } catch (e: Exception) {
            Log.e("HomeHealth_WS", "callService($domain.$service id=$id) FAILED: ${e.message}")
            false
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
                    onAuthOk(webSocket)
                }
                "auth_invalid" -> {
                    // HA counts every WS auth_invalid as a failed login attempt against its
                    // ip_ban threshold. Mark the state distinctly so (a) the UI can prompt
                    // for re-auth, (b) the repository can attempt one OAuth refresh, and
                    // (c) scheduleReconnect / setCredentials stop hammering with the same
                    // bad token. The repository observes this status and decides whether
                    // to refresh the access token.
                    Log.e("HomeHealth_WS", "Auth INVALID — token rejected by HA, suspending reconnects")
                    closeSocket()
                    _status.value = WsConnectionStatus.AUTH_INVALID
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
        if (msg["success"]?.asBoolean != true) return
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
                target_temp_step = attrs["target_temp_step"]?.takeIf { !it.isJsonNull }?.asFloat,
                min_temp = attrs["min_temp"]?.takeIf { !it.isJsonNull }?.asFloat,
                max_temp = attrs["max_temp"]?.takeIf { !it.isJsonNull }?.asFloat,
                raw = attrs,
            ),
        )
    }
}
