@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.uc.homehealth.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class HaHomeRepository @Inject constructor(
    private val authPreferences: AuthPreferences,
    private val userPreferences: UserPreferences,
    private val wsClient: HaWebSocketClient,
    private val fakeRepo: FakeHomeRepository,
    private val activityLog: ActivityLog,
    private val okHttpClient: OkHttpClient,
    private val gson: Gson,
    private val networkLocator: NetworkLocator,
    private val authManager: HaAuthManager,
    private val pulseHistory: PulseHistoryStore,
) : HomeRepository {

    @Volatile private var refreshInFlight = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isAuthenticated: Flow<Boolean> = authPreferences.authState.map { it.accessToken.isNotEmpty() }

    // Optimistic overlay: entity_ids the user just tapped "install" on, so the card shows
    // "installing" the instant they tap — before HA echoes in_progress=true (which can lag
    // a beat, or never arrive for fast integrations). Cleared once HA reports progress /
    // completion, or after a safety timeout (see installUpdate). Bridges the gap only.
    private val installingUpdates = MutableStateFlow<Set<String>>(emptySet())
    // entity_id → last install error (e.g. "requires HA 2026.5.0"), surfaced on the card.
    private val installErrors = MutableStateFlow<Map<String, String>>(emptyMap())

    /** SSID-resolved URL for one-shot reads (REST history, camera URLs, etc.). */
    private suspend fun activeUrl(): String {
        val auth = authPreferences.authState.first()
        return auth.activeUrl(networkLocator.currentSsid.value)
    }

    init {
        // Consulted by HaWebSocketClient right before every socket open (foreground
        // reconnect, backoff reconnect, keep-alive) so the WS auth message never carries
        // an expired OAuth access token — HA logs each rejected auth as a failed login
        // and counts it toward its ip_ban threshold. Long-lived tokens (no refresh_token)
        // pass through unchanged.
        wsClient.tokenProvider = { freshAccessToken().takeIf { it.isNotBlank() } }
        // Pulse connectivity history: count only UNEXPECTED drops (READY → ERROR).
        // Deliberate closes (logout/background) land on DISCONNECTED and don't count.
        scope.launch {
            var prev: WsConnectionStatus? = null
            wsClient.status.collect { status ->
                if (prev == WsConnectionStatus.READY && status == WsConnectionStatus.ERROR) {
                    pulseHistory.recordDrop()
                }
                prev = status
            }
        }
        scope.launch {
            combine(authPreferences.authState, networkLocator.currentSsid) { auth, ssid -> auth to ssid }
                .collect { (auth, ssid) ->
                    if (auth.accessToken.isEmpty()) {
                        Log.d("HomeHealth_Repo", "No auth — clearing WS credentials, using fake data")
                        wsClient.clearCredentials()
                        return@collect
                    }
                    // Proactive OAuth refresh: if the access token has expired (or is within
                    // 60s of expiry) and we have a refresh_token, swap it out *before* we hand
                    // an expired token to HA. Saving the new pair re-emits authState and this
                    // collector runs again with the fresh token.
                    if (needsRefresh(auth)) {
                        val refreshed = attemptTokenRefresh()
                        if (refreshed) return@collect  // new authState about to arrive — wait for it
                        // Refresh failed (network, revoked refresh_token, etc.). Falling through
                        // means HA will reject the WS auth → status=AUTH_INVALID → UI prompts re-login.
                    }
                    val url = auth.activeUrl(ssid)
                    val choice = when {
                        url == auth.localUrl && url.isNotBlank() -> "LOCAL"
                        url == auth.remoteUrl && url.isNotBlank() -> "REMOTE"
                        else -> "FALLBACK"
                    }
                    Log.i(
                        "HomeHealth_Repo",
                        "Routing → $choice url=$url · ssid=$ssid · homeSsids=${auth.homeSsids} · " +
                            "match=${auth.isHomeSsid(ssid)} · localBlank=${auth.localUrl.isBlank()}",
                    )
                    wsClient.setCredentials(url, auth.accessToken)
                }
        }
        // Reactive refresh: when HA rejects the WS auth, refresh the OAuth pair. Success →
        // saveAuth re-emits authState → collector above reconnects with the new token.
        // Transient failures (network blip, proxy hiccup — the request never produced a
        // failed-login entry on HA) get a couple of spaced retries so a flaky remote link
        // doesn't strand the app on "Session expired". A definitive rejection (HTTP 4xx
        // from /auth/token: refresh token revoked) stops immediately — retrying would only
        // add failed logins to HA's log and tick its ip_ban counter.
        scope.launch {
            wsClient.status.collect { status ->
                if (status != WsConnectionStatus.AUTH_INVALID) return@collect
                val auth = authPreferences.authState.first()
                if (auth.refreshToken.isBlank()) {
                    Log.w("HomeHealth_Repo", "AUTH_INVALID with no refresh_token (long-lived token rejected/revoked) — needs re-login")
                    return@collect
                }
                for (delayMs in listOf(0L, 5_000L, 20_000L)) {
                    if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
                    if (wsClient.status.value != WsConnectionStatus.AUTH_INVALID) break
                    val outcome = attemptTokenRefreshOutcome()
                    if (outcome != RefreshOutcome.FAILED_TRANSIENT) break
                }
            }
        }
    }

    private fun needsRefresh(auth: AuthState): Boolean =
        auth.refreshToken.isNotBlank() &&
            auth.tokenExpiry > 0L &&
            auth.tokenExpiry < System.currentTimeMillis() + 60_000L

    /**
     * The stored access token, refreshed first when expired/near expiry (OAuth sessions).
     * Use this instead of reading `authState.accessToken` directly for anything that is
     * about to present the token to HA — a stale token gets a 401/auth_invalid, which HA
     * logs as a failed login attempt.
     */
    private suspend fun freshAccessToken(): String {
        val auth = authPreferences.authState.first()
        if (!needsRefresh(auth)) return auth.accessToken
        attemptTokenRefresh()
        return authPreferences.authState.first().accessToken
    }

    private enum class RefreshOutcome { SUCCESS, FAILED_TRANSIENT, FAILED_DEFINITIVE }

    private suspend fun attemptTokenRefresh(): Boolean =
        attemptTokenRefreshOutcome() == RefreshOutcome.SUCCESS

    private suspend fun attemptTokenRefreshOutcome(): RefreshOutcome {
        if (refreshInFlight) return RefreshOutcome.FAILED_TRANSIENT
        refreshInFlight = true
        return try {
            val auth = authPreferences.authState.first()
            if (auth.refreshToken.isBlank()) {
                Log.w("HomeHealth_Repo", "Token refresh skipped — no refresh_token saved")
                return RefreshOutcome.FAILED_DEFINITIVE
            }
            val url = auth.activeUrl(networkLocator.currentSsid.value)
            if (url.isBlank()) {
                Log.w("HomeHealth_Repo", "Token refresh skipped — no HA URL configured")
                return RefreshOutcome.FAILED_TRANSIENT
            }
            Log.i("HomeHealth_Repo", "Refreshing OAuth access token at $url")
            val tokens = authManager.refreshToken(url, auth.refreshToken)
            authPreferences.saveAuth(
                localUrl = auth.localUrl,
                remoteUrl = auth.remoteUrl,
                homeSsids = auth.homeSsids,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn,
            )
            Log.i("HomeHealth_Repo", "Token refresh OK — new access token valid for ${tokens.expiresIn}s")
            RefreshOutcome.SUCCESS
        } catch (e: HaAuthHttpException) {
            if (e.isDefinitiveRejection) {
                Log.e("HomeHealth_Repo", "Token refresh REJECTED by HA (${e.code}) — refresh token revoked, re-login required")
                RefreshOutcome.FAILED_DEFINITIVE
            } else {
                Log.e("HomeHealth_Repo", "Token refresh failed with HTTP ${e.code} — treating as transient")
                RefreshOutcome.FAILED_TRANSIENT
            }
        } catch (e: Exception) {
            Log.e("HomeHealth_Repo", "Token refresh failed (transient): ${e.message}")
            RefreshOutcome.FAILED_TRANSIENT
        } finally {
            refreshInFlight = false
        }
    }

    override fun getAllRooms(): Flow<List<HaRoom>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getAllRooms()
        combine(
            wsClient.areas, wsClient.devices, wsClient.entities, wsClient.states,
            userPreferences.roomSensorOverrides,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            mapRooms(
                args[0] as List<HaArea>,
                args[1] as List<HaDeviceEntry>,
                args[2] as List<HaEntityEntry>,
                args[3] as Map<String, HaState>,
                args[4] as Map<String, RoomSensorOverride>,
            )
        }
    }

    override fun getRooms(): Flow<List<HaRoom>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getRooms()
        combine(
            wsClient.areas, wsClient.devices, wsClient.entities, wsClient.states,
            userPreferences.roomSensorOverrides,
        ) { args ->
            @Suppress("UNCHECKED_CAST")
            mapRooms(
                args[0] as List<HaArea>,
                args[1] as List<HaDeviceEntry>,
                args[2] as List<HaEntityEntry>,
                args[3] as Map<String, HaState>,
                args[4] as Map<String, RoomSensorOverride>,
            )
        }
    }

    override fun getScenes(): Flow<List<HaScene>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getScenes()
        combine(userPreferences.quickSceneIds, wsClient.states, wsClient.entities) { ids, states, entities ->
            ids.mapNotNull { id -> buildScene(id, states, entities) }
        }
    }

    override fun getAllScenes(): Flow<List<HaScene>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getAllScenes()
        combine(wsClient.states, wsClient.entities) { states, entities ->
            states.values
                .filter { it.entity_id.startsWith("scene.") }
                .mapNotNull { state -> buildScene(state.entity_id, states, entities) }
                .sortedBy { it.name.lowercase() }
        }
    }

    override fun getFavorites(): Flow<List<HaFavorite>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getFavorites()
        combine(
            userPreferences.favoriteEntityIds,
            wsClient.states,
            wsClient.entities,
            wsClient.devices,
            wsClient.areas,
        ) { ids, states, entities, devices, areas ->
            val deviceAreaMap = devices.associate { it.id to it.area_id }
            ids.mapNotNull { id -> buildFavorite(id, states, entities, deviceAreaMap, areas) }
        }
    }

    override fun getAllEntities(): Flow<List<HaEntitySummary>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getAllEntities()
        combine(
            wsClient.entities,
            wsClient.states,
            wsClient.devices,
            wsClient.areas,
        ) { entities, states, devices, areas ->
            val deviceAreaMap = devices.associate { it.id to it.area_id }
            val areaMap = areas.associate { it.area_id to it.name }
            entities
                .filter { it.disabled_by == null && !it.entity_id.startsWith("scene.") }
                .mapNotNull { entry ->
                    val state = states[entry.entity_id] ?: return@mapNotNull null
                    val areaId = effectiveAreaId(entry, deviceAreaMap)
                    val domain = entry.entity_id.substringBefore('.')
                    // CameraEntityFeature.STREAM == 2; cleared ⇒ snapshot-only.
                    val supportsStream = domain == "camera" &&
                        ((state.attributes.raw?.get("supported_features")
                            ?.takeIf { !it.isJsonNull }?.asInt ?: 0) and 2) != 0
                    // person/device_tracker entities only get a map when they report coords.
                    val hasLocation = (domain == "person" || domain == "device_tracker") &&
                        coordsOf(state) != null
                    HaEntitySummary(
                        entityId = entry.entity_id,
                        friendlyName = entry.name ?: state.attributes.friendly_name ?: entry.entity_id,
                        domain = domain,
                        areaName = areaId?.let { areaMap[it] } ?: "Unassigned",
                        state = state.state,
                        supportsStream = supportsStream,
                        hasLocation = hasLocation,
                    )
                }
                .sortedBy { it.friendlyName.lowercase() }
        }
    }

    override fun getAutomations(): Flow<List<HaAutomation>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getAutomations()
        wsClient.states.map { states ->
            states.entries
                .filter { it.key.startsWith("automation.") }
                .map { (id, state) ->
                    HaAutomation(
                        entityId = id,
                        friendlyName = state.attributes.friendly_name
                            ?: id.substringAfter('.').replace('_', ' ')
                                .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                        isEnabled = state.state == "on",
                    )
                }
                .sortedBy { it.friendlyName.lowercase() }
        }
    }

    override fun getNotifications(): Flow<List<HaNotification>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) fakeRepo.getNotifications() else activityLog.events
    }

    // Operate directly on the on-device Room store — no HA round-trip, and works
    // regardless of auth so leftover history can be cleared after signing out.
    override suspend fun deleteNotification(id: Long) {
        activityLog.delete(id)
    }

    override suspend fun clearNotifications() {
        activityLog.clear()
    }

    override suspend fun runScene(sceneId: String) {
        Log.d("HomeHealth_Repo", "runScene($sceneId)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("scene", "turn_on", mapOf("entity_id" to sceneId))
        activityLog.record("scene", "Scene activated", friendlyName(sceneId))
    }

    override suspend fun toggleEntity(entityId: String) {
        Log.d("HomeHealth_Repo", "toggleEntity($entityId)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        val domain = entityId.substringBefore('.')
        val name = friendlyName(entityId)
        when (domain) {
            "light", "switch", "fan", "input_boolean", "automation", "humidifier", "siren" -> {
                val wasOn = wsClient.states.value[entityId]?.state == "on"
                wsClient.callService(domain, "toggle", mapOf("entity_id" to entityId))
                activityLog.record(kindForDomain(domain), "$name turned ${if (wasOn) "off" else "on"}", domainLabel(domain))
            }
            "media_player" -> {
                wsClient.callService("media_player", "media_play_pause", mapOf("entity_id" to entityId))
                activityLog.record("media", "$name play / pause", "Media")
            }
            "cover" -> {
                val isOpen = wsClient.states.value[entityId]?.state == "open"
                wsClient.callService("cover", if (isOpen) "close_cover" else "open_cover", mapOf("entity_id" to entityId))
                activityLog.record("door", "$name ${if (isOpen) "closed" else "opened"}", "Cover")
            }
            "lock" -> {
                val isLocked = wsClient.states.value[entityId]?.state == "locked"
                wsClient.callService("lock", if (isLocked) "unlock" else "lock", mapOf("entity_id" to entityId))
                activityLog.record("door", "$name ${if (isLocked) "unlocked" else "locked"}", "Lock")
            }
            "scene", "script" -> {
                wsClient.callService(domain, "turn_on", mapOf("entity_id" to entityId))
                activityLog.record("scene", if (domain == "scene") "Scene activated" else "Script ran", name)
            }
            else -> Log.d("HomeHealth_Repo", "toggleEntity: domain '$domain' not togglable — skipped")
        }
    }

    override suspend fun pressEntity(entityId: String) {
        Log.d("HomeHealth_Repo", "pressEntity($entityId)")
        if (entityId.isBlank()) return
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        val domain = entityId.substringBefore('.')
        // PTZ moves are momentary: buttons get `press`, switches/scripts/scenes get
        // `turn_on`. We don't write to the activity log — PTZ is held/tapped rapidly
        // and would otherwise flood it.
        val service = when (domain) {
            "button", "input_button" -> "press"
            "switch", "script", "scene", "light", "input_boolean" -> "turn_on"
            else -> "press"
        }
        wsClient.callService(domain, service, mapOf("entity_id" to entityId))
    }

    private fun friendlyName(entityId: String): String {
        val entry = wsClient.entities.value.firstOrNull { it.entity_id == entityId }
        val state = wsClient.states.value[entityId]
        return entry?.name
            ?: state?.attributes?.friendly_name
            ?: entityId.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun kindForDomain(domain: String): String = when (domain) {
        "light" -> "light"
        "switch", "input_boolean", "fan", "humidifier" -> "light"
        "automation" -> "auto"
        "siren" -> "auto"
        else -> "auto"
    }

    private fun domainLabel(domain: String): String = when (domain) {
        "light" -> "Light"
        "switch" -> "Switch"
        "fan" -> "Fan"
        "input_boolean" -> "Helper"
        "automation" -> "Automation"
        "humidifier" -> "Humidifier"
        "siren" -> "Siren"
        else -> domain.replaceFirstChar { it.uppercase() }
    }

    private fun buildScene(
        sceneId: String,
        states: Map<String, HaState>,
        entities: List<HaEntityEntry>,
    ): HaScene? {
        val state = states[sceneId] ?: return null
        if (!sceneId.startsWith("scene.")) return null
        val entry = entities.find { it.entity_id == sceneId }
        val name = entry?.name ?: state.attributes.friendly_name
            ?: sceneId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
        return HaScene(
            id = sceneId,
            name = name,
            emoji = emojiForScene(name),
            isActive = false,
        )
    }

    private fun emojiForScene(name: String): String {
        val n = name.lowercase()
        return when {
            "morning" in n || "wake" in n || "sunrise" in n -> "☀"
            "focus" in n || "work" in n -> "🎯"
            "movie" in n || "cinema" in n || "theater" in n -> "🎬"
            "sleep" in n || "night" in n || "bedtime" in n -> "🌙"
            "away" in n || "vacation" in n -> "✈"
            "dinner" in n || "eat" in n -> "🍽"
            "party" in n -> "🎉"
            "relax" in n || "chill" in n -> "🛋"
            "read" in n -> "📖"
            "kids" in n || "child" in n -> "🧸"
            else -> "✨"
        }
    }

    private fun buildFavorite(
        entityId: String,
        states: Map<String, HaState>,
        entities: List<HaEntityEntry>,
        deviceAreaMap: Map<String, String?>,
        areas: List<HaArea>,
    ): HaFavorite? {
        val state = states[entityId] ?: return null
        val entry = entities.find { it.entity_id == entityId }
        val domain = entityId.substringBefore('.')
        val attrs = state.attributes
        val name = entry?.name ?: attrs.friendly_name ?: entityId
        val areaId = entry?.let { effectiveAreaId(it, deviceAreaMap) }
        val areaName = areaId?.let { id -> areas.find { it.area_id == id }?.name } ?: "Whole home"
        val isOn = when (state.state) {
            "on", "playing", "open", "unlocked", "home", "heat", "cool", "auto", "fan_only", "dry", "heat_cool" -> true
            else -> false
        }
        val kind = when (domain) {
            "light" -> "light"
            "switch", "input_boolean", "fan" -> "light"
            "climate" -> "climate"
            "lock" -> "lock"
            "media_player" -> "media"
            "sensor", "binary_sensor" -> if ("energy" in entityId || "power" in entityId) "energy" else "climate"
            else -> "light"
        }
        val value: Float? = when (domain) {
            "sensor" -> state.state.toFloatOrNull()
            "climate" -> attrs.temperature ?: attrs.current_temperature
            "light" -> if (isOn) attrs.brightness?.div(255f)?.times(100f) else null
            else -> null
        }
        val unit: String? = attrs.unit_of_measurement?.takeIf { value != null }
            ?: when (domain) {
                "climate" -> if (value != null) "°" else null
                "light" -> if (value != null) "%" else null
                else -> null
            }
        val readableState = when (domain) {
            "light", "switch", "fan", "input_boolean" -> if (isOn) "On" else "Off"
            "lock" -> if (isOn) "Unlocked" else "Locked"
            "media_player" -> if (isOn) "Playing" else state.state.replaceFirstChar { it.uppercase() }
            "climate" -> attrs.hvac_action?.replaceFirstChar { it.uppercase() }
                ?: state.state.replaceFirstChar { it.uppercase() }
            "scene", "script", "automation" -> "Tap to run"
            "sensor", "binary_sensor" -> attrs.friendly_name?.let { "Live" } ?: state.state.replaceFirstChar { it.uppercase() }
            else -> state.state.replaceFirstChar { it.uppercase() }
        }
        return HaFavorite(
            id = entityId,
            kind = kind,
            name = name,
            value = value,
            unit = unit,
            state = readableState,
            isOn = isOn,
            room = areaName,
        )
    }

    override fun getLightsForRoom(areaId: String): Flow<List<HaLight>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getLightsForRoom(areaId)
        flow {
            // Phase 1: entity registry matching (instant — uses cached StateFlow values)
            val entitiesNow = wsClient.entities.value
            val devicesNow = wsClient.devices.value
            val statesNow = wsClient.states.value
            Log.d("HomeHealth_Repo", "getLightsForRoom($areaId): entities=${entitiesNow.size} devices=${devicesNow.size} states=${statesNow.size}")

            val registryLights = mapLightsForRoom(areaId, devicesNow, entitiesNow, statesNow)
            Log.d("HomeHealth_Repo", "Phase1 registry match: ${registryLights.size} lights")

            if (registryLights.isNotEmpty()) {
                emitAll(combine(wsClient.devices, wsClient.entities, wsClient.states) { d, e, s ->
                    mapLightsForRoom(areaId, d, e, s)
                })
                return@flow
            }

            // Phase 2: entity registry returned nothing — use HA's search/related command.
            Log.d("HomeHealth_Repo", "Phase1 empty — calling search/related for area=$areaId")
            val areaEntityIds = wsClient.getAreaEntities(areaId)
            val lightEntityIds = areaEntityIds.filter { it.startsWith("light.") }.toSet()
            Log.d("HomeHealth_Repo", "search/related: ${areaEntityIds.size} total entities, ${lightEntityIds.size} lights → $lightEntityIds")

            if (lightEntityIds.isNotEmpty()) {
                emitAll(wsClient.states.map { states -> mapLightsFromEntityIds(lightEntityIds, states) })
            } else {
                // Nothing found either way — keep watching registry in case WS data arrives late
                Log.d("HomeHealth_Repo", "No lights found via either method — falling back to live registry watch")
                emitAll(combine(wsClient.devices, wsClient.entities, wsClient.states) { d, e, s ->
                    mapLightsForRoom(areaId, d, e, s)
                })
            }
        }
    }

    override suspend fun toggleLight(entityId: String, isOn: Boolean) {
        Log.d("HomeHealth_Repo", "toggleLight($entityId, isOn=$isOn)")
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            Log.w("HomeHealth_Repo", "toggleLight skipped — no auth token")
            return
        }
        wsClient.callService("light", if (isOn) "turn_on" else "turn_off", mapOf("entity_id" to entityId))
        activityLog.record("light", "${friendlyName(entityId)} turned ${if (isOn) "on" else "off"}", "Light")
    }

    override suspend fun setLightBrightness(entityId: String, brightness: Int) {
        Log.d("HomeHealth_Repo", "setLightBrightness($entityId, $brightness%)")
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            Log.w("HomeHealth_Repo", "setLightBrightness skipped — no auth token")
            return
        }
        val haBrightness = (brightness / 100f * 255).toInt().coerceIn(1, 255)
        wsClient.callService("light", "turn_on", mapOf("entity_id" to entityId, "brightness" to haBrightness))
        activityLog.record("light", "${friendlyName(entityId)} dimmed", "Brightness set to $brightness%")
    }

    override suspend fun setLightColor(entityId: String, r: Int, g: Int, b: Int) {
        Log.d("HomeHealth_Repo", "setLightColor($entityId, rgb=$r,$g,$b)")
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            Log.w("HomeHealth_Repo", "setLightColor skipped — no auth token")
            return
        }
        wsClient.callService("light", "turn_on", mapOf(
            "entity_id" to entityId,
            "rgb_color" to listOf(r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255)),
        ))
        activityLog.record("light", "${friendlyName(entityId)} color set", "RGB $r, $g, $b")
    }

    override suspend fun setLightColorTemp(entityId: String, kelvin: Int) {
        Log.d("HomeHealth_Repo", "setLightColorTemp($entityId, ${kelvin}K)")
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            Log.w("HomeHealth_Repo", "setLightColorTemp skipped — no auth token")
            return
        }
        // HA's `light.turn_on` no longer accepts the deprecated `kelvin` field;
        // `color_temp_kelvin` is the canonical parameter name. Sending it alone
        // switches the light into color_temp mode if it was in colour mode.
        wsClient.callService("light", "turn_on", mapOf(
            "entity_id" to entityId,
            "color_temp_kelvin" to kelvin.coerceIn(1000, 10000),
        ))
        activityLog.record("light", "${friendlyName(entityId)} temperature", "${kelvin}K")
    }

    override fun getClimateForRoom(areaId: String): Flow<HaClimate?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getClimateForRoom(areaId)
        combine(wsClient.devices, wsClient.entities, wsClient.states) { devices, entities, states ->
            mapClimateForRoom(areaId, devices, entities, states)
        }
    }

    override fun getClimate(entityId: String): Flow<HaClimate?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getClimate(entityId)
        combine(wsClient.entities, wsClient.states) { entities, states ->
            val entry = entities.firstOrNull { it.entity_id == entityId && it.disabled_by == null }
            mapClimate(entry, states)
        }
    }

    override fun getLight(entityId: String): Flow<HaLight?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getLight(entityId)
        wsClient.states.map { states -> mapLightsFromEntityIds(setOf(entityId), states).firstOrNull() }
    }

    override suspend fun setClimateTemperature(entityId: String, temperature: Float) {
        Log.d("HomeHealth_Repo", "setClimateTemperature($entityId, $temperature)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("climate", "set_temperature",
            mapOf("entity_id" to entityId, "temperature" to temperature))
        val pretty = if (temperature % 1f == 0f) temperature.toInt().toString() else "%.1f".format(temperature)
        activityLog.record("climate", "${friendlyName(entityId)} target set", "$pretty°")
    }

    override suspend fun setClimateHvacMode(entityId: String, mode: String) {
        Log.d("HomeHealth_Repo", "setClimateHvacMode($entityId, $mode)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("climate", "set_hvac_mode",
            mapOf("entity_id" to entityId, "hvac_mode" to mode))
        activityLog.record("climate", "${friendlyName(entityId)} mode", "Set to $mode")
    }

    override suspend fun setClimateFanMode(entityId: String, fanMode: String) {
        Log.d("HomeHealth_Repo", "setClimateFanMode($entityId, $fanMode)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("climate", "set_fan_mode",
            mapOf("entity_id" to entityId, "fan_mode" to fanMode))
        activityLog.record("climate", "${friendlyName(entityId)} fan", "Set to $fanMode")
    }

    private fun mapClimateForRoom(
        areaId: String,
        devices: List<HaDeviceEntry>,
        entities: List<HaEntityEntry>,
        states: Map<String, HaState>,
    ): HaClimate? {
        val deviceAreaMap = devices.associate { it.id to it.area_id }
        val entry = entities.firstOrNull {
            it.entity_id.startsWith("climate.") &&
                it.disabled_by == null &&
                effectiveAreaId(it, deviceAreaMap) == areaId
        } ?: return null
        return mapClimate(entry, states)
    }

    // Maps a single climate registry entry + the live state cache into an HaClimate.
    // Shared by the per-room lookup and the per-entity [getClimate] for climate widgets.
    private fun mapClimate(entry: HaEntityEntry?, states: Map<String, HaState>): HaClimate? {
        if (entry == null) return null
        val state = states[entry.entity_id] ?: return null
        val attrs = state.attributes
        return HaClimate(
            id = entry.entity_id,
            name = entry.name ?: attrs.friendly_name ?: entry.entity_id,
            currentTemp = attrs.current_temperature,
            targetTemp = attrs.temperature,
            mode = state.state,
            action = attrs.hvac_action,
            supportedModes = attrs.hvac_modes,
            tempStep = attrs.target_temp_step ?: 0.5f,
            minTemp = attrs.min_temp ?: 7f,
            maxTemp = attrs.max_temp ?: 35f,
            fanMode = attrs.fan_mode,
            fanModes = attrs.fan_modes,
            isAvailable = state.state != "unavailable",
        )
    }

    override fun connectionStatus(): Flow<WsConnectionStatus> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) flowOf(WsConnectionStatus.DISCONNECTED) else wsClient.status
    }

    override fun authError(): Flow<String?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) flowOf(null) else wsClient.lastAuthError
    }

    override fun reconnectNow() {
        wsClient.reconnectNow()
    }

    override fun getTrackedFlights(): Flow<List<HaFlight>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getTrackedFlights()
        wsClient.states.map { states ->
            val raw = states[Fr24Entities.TRACKED]?.attributes?.raw ?: return@map emptyList()
            val arr = raw["flights"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@map emptyList()
            arr.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let { HaFlightJson.parse(it) } }
        }
    }

    override fun isFlightRadar24Available(): Flow<Boolean> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.isFlightRadar24Available()
        wsClient.states.map { states ->
            // While the WS state cache is still warming up the map is empty —
            // treat that as available so the install prompt doesn't flash before
            // real data arrives. A disabled integration leaves the entity in
            // the registry with state "unavailable" (or "unknown"), so treat
            // those as not-available too.
            if (states.isEmpty()) return@map true
            val s = states[Fr24Entities.TRACKED] ?: return@map false
            s.state !in setOf("unavailable", "unknown", "none")
        }
    }

    override suspend fun addTrackedFlight(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            fakeRepo.addTrackedFlight(q)
            return
        }
        wsClient.callService(
            "text", "set_value",
            mapOf("entity_id" to Fr24Entities.ADD, "value" to q),
        )
        activityLog.record("auto", "Tracking flight $q", "FlightRadar24")
    }

    override suspend fun removeTrackedFlight(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            fakeRepo.removeTrackedFlight(q)
            return
        }
        wsClient.callService(
            "text", "set_value",
            mapOf("entity_id" to Fr24Entities.REMOVE, "value" to q),
        )
        activityLog.record("auto", "Untracking flight $q", "FlightRadar24")
    }

    override fun getEntityState(entityId: String): Flow<HaEntityValue?> {
        if (entityId.isBlank()) return flowOf(null)
        return isAuthenticated.flatMapLatest { authed ->
            if (!authed) fakeRepo.getEntityState(entityId)
            else wsClient.states.map { states ->
                val s = states[entityId] ?: return@map null
                HaEntityValue(
                    entityId = entityId,
                    state = s.state,
                    unit = s.attributes.unit_of_measurement,
                    friendlyName = s.attributes.friendly_name,
                )
            }
        }
    }

    override fun getPersonLocation(entityId: String): Flow<HaPersonLocation?> {
        if (entityId.isBlank()) return flowOf(null)
        return isAuthenticated.flatMapLatest { authed ->
            if (!authed) fakeRepo.getPersonLocation(entityId)
            else wsClient.states.map { states ->
                val s = states[entityId] ?: return@map null
                personLocationFrom(entityId, s)
            }
        }
    }

    // Pull latitude/longitude out of a state's raw attributes, or null if either is
    // missing (presence-only tracker). HA stores them as numbers under the state's
    // `attributes`, which parseState preserves verbatim in `raw`.
    private fun coordsOf(s: HaState): Pair<Double, Double>? {
        val raw = s.attributes.raw ?: return null
        val lat = raw["latitude"]?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()
        val lng = raw["longitude"]?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()
        return if (lat != null && lng != null) lat to lng else null
    }

    private fun personLocationFrom(entityId: String, s: HaState): HaPersonLocation {
        val coords = coordsOf(s)
        val accuracy = s.attributes.raw?.get("gps_accuracy")
            ?.takeIf { !it.isJsonNull }?.runCatching { asDouble }?.getOrNull()?.toInt()
        val lastMs = s.last_changed?.let {
            runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
        }
        return HaPersonLocation(
            entityId = entityId,
            friendlyName = s.attributes.friendly_name ?: prettifyEntityId(entityId),
            latitude = coords?.first,
            longitude = coords?.second,
            gpsAccuracyMeters = accuracy,
            zone = s.state,
            lastUpdatedEpochMs = lastMs,
        )
    }

    private fun prettifyEntityId(entityId: String): String =
        entityId.substringAfterLast('.').replace('_', ' ')
            .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    // ── Updates ────────────────────────────────────────────────────────────────
    // UpdateEntityFeature.BACKUP bit (HA homeassistant/components/update/const.py).
    private val UPDATE_FEATURE_BACKUP = 8

    private val systemUpdateIds = setOf(
        "update.home_assistant_core_update",
        "update.home_assistant_operating_system_update",
        "update.home_assistant_supervisor_update",
    )

    override fun getPulse(): Flow<PulseReport> = combine(
        wsClient.states,
        getUpdates(),
        pulseHistory.drops,
    ) { states, updates, drops ->
        PulseAnalyzer.analyze(
            states = states,
            pendingUpdates = updates.filter { !it.isSkipped && !it.inProgress }.map { it.title },
            dropTimesMs = drops,
            nowMs = System.currentTimeMillis(),
        )
    }.distinctUntilChanged()

    override fun getUpdates(): Flow<List<HaUpdate>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getUpdates()
        // Two client-side overlays folded into one source to stay within combine's 5-arg arity.
        val overlays = combine(installingUpdates, installErrors) { installing, errors -> installing to errors }
        combine(
            wsClient.states,
            wsClient.entities,
            authPreferences.authState,
            networkLocator.currentSsid,
            overlays,
        ) { states, entities, auth, ssid, overlay ->
            val (installing, errors) = overlay
            val base = auth.activeUrl(ssid).trimEnd('/')
            val platformById = entities.associate { it.entity_id to it.platform }
            states.entries
                .filter { (id, s) ->
                    // `on` = update available; in-progress / just-tapped keep the installing
                    // card alive; a skipped update flips to `off` but keeps skipped_version,
                    // so include it too — the UI files it under the collapsed "Skipped" group.
                    // An entity with a recent error stays visible so the user sees why it failed.
                    id.startsWith("update.") &&
                        (s.state == "on" || updateInProgress(s) || id in installing ||
                            updateSkipped(s) || id in errors)
                }
                .map { (id, s) -> mapUpdate(id, s, platformById[id], base, id in installing, errors[id]) }
                .sortedWith(compareBy({ it.category.ordinal }, { it.title.lowercase() }))
        }
    }

    private fun mapUpdate(
        entityId: String,
        s: HaState,
        platform: String?,
        baseUrl: String,
        optimisticInstalling: Boolean,
        errorMessage: String?,
    ): HaUpdate {
        val raw = s.attributes.raw
        fun str(key: String): String? =
            raw?.get(key)?.takeIf { !it.isJsonNull }?.runCatching { asString }?.getOrNull()?.takeIf { it.isNotBlank() }
        fun int(key: String): Int? =
            raw?.get(key)?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull()

        val features = int("supported_features") ?: 0
        val pictureRaw = str("entity_picture_local") ?: str("entity_picture")
        val pictureUrl = pictureRaw?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
        return HaUpdate(
            entityId = entityId,
            title = str("title") ?: s.attributes.friendly_name ?: prettifyEntityId(entityId),
            installedVersion = str("installed_version"),
            latestVersion = str("latest_version"),
            inProgress = optimisticInstalling || updateInProgress(s),
            updatePercentage = int("update_percentage"),
            releaseSummary = str("release_summary"),
            releaseUrl = str("release_url"),
            entityPictureUrl = pictureUrl,
            skippedVersion = str("skipped_version"),
            supportsBackup = (features and UPDATE_FEATURE_BACKUP) != 0,
            category = classifyUpdate(entityId, platform, s.attributes.device_class),
            errorMessage = errorMessage,
        )
    }

    private fun updateSkipped(s: HaState): Boolean =
        s.attributes.raw?.get("skipped_version")
            ?.takeIf { !it.isJsonNull }?.runCatching { asString }?.getOrNull()?.isNotBlank() == true

    // `in_progress` is normally a boolean, but tolerate the legacy numeric form (% as int).
    private fun updateInProgress(s: HaState): Boolean {
        val el = s.attributes.raw?.get("in_progress")?.takeIf { !it.isJsonNull } ?: return false
        return el.runCatching { asBoolean }.getOrNull()
            ?: el.runCatching { asInt }.getOrNull()?.let { it != 0 }
            ?: false
    }

    private fun classifyUpdate(entityId: String, platform: String?, deviceClass: String?): UpdateCategory {
        val obj = entityId.substringAfter("update.")
        return when {
            entityId in systemUpdateIds ||
                obj.startsWith("home_assistant_core") ||
                obj.startsWith("home_assistant_operating_system") ||
                obj.startsWith("home_assistant_supervisor") -> UpdateCategory.SYSTEM
            platform == "hacs" -> UpdateCategory.HACS
            platform == "hassio" -> UpdateCategory.ADDON
            deviceClass == "firmware" -> UpdateCategory.FIRMWARE
            else -> UpdateCategory.FIRMWARE
        }
    }

    override suspend fun installUpdate(entityId: String, backup: Boolean) {
        Log.d("HomeHealth_Repo", "installUpdate($entityId, backup=$backup)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        installErrors.update { it - entityId }          // clear any stale error on retry
        installingUpdates.update { it + entityId }
        val data = buildMap<String, Any> {
            put("entity_id", entityId)
            if (backup) put("backup", true)
        }
        val result = wsClient.callServiceResult("update", "install", data)
        if (!result.success) {
            // HA rejected the install (e.g. version too old). Revert immediately and surface
            // the reason on the card instead of spinning until the watcher times out.
            installingUpdates.update { it - entityId }
            installErrors.update { it + (entityId to (result.error ?: "Update failed")) }
            activityLog.record("update", "${friendlyName(entityId)} update failed", result.error ?: "Update failed")
            return
        }
        activityLog.record("update", "${friendlyName(entityId)} update started", "Update")
        // Drop the optimistic overlay once HA takes over (reports its own progress) or the
        // update finishes/clears, with a safety timeout so a stuck install reverts to idle.
        scope.launch {
            withTimeoutOrNull(120_000L) {
                wsClient.states.first { states ->
                    val st = states[entityId]
                    st == null || st.state != "on" || updateInProgress(st)
                }
            }
            installingUpdates.update { it - entityId }
        }
    }

    override suspend fun skipUpdate(entityId: String) {
        Log.d("HomeHealth_Repo", "skipUpdate($entityId)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("update", "skip", mapOf("entity_id" to entityId))
    }

    override suspend fun clearSkippedUpdate(entityId: String) {
        Log.d("HomeHealth_Repo", "clearSkippedUpdate($entityId)")
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("update", "clear_skipped", mapOf("entity_id" to entityId))
    }

    override suspend fun suggestedGlanceEntityIds(): List<String> {
        if (authPreferences.authState.first().accessToken.isEmpty()) {
            return fakeRepo.suggestedGlanceEntityIds()
        }
        val states = wsClient.states.value
        fun isNumeric(id: String) = states[id]?.state?.toFloatOrNull() != null
        // Prefer the kinds of sensors people actually want at a glance, in this order.
        val priorityClasses = listOf(
            "temperature", "humidity", "aqi", "pm25", "pm10",
            "carbon_dioxide", "power", "energy", "battery", "illuminance",
        )
        val picked = priorityClasses.flatMap { dc ->
            states.entries
                .filter {
                    it.key.startsWith("sensor.") &&
                        it.value.attributes.device_class == dc &&
                        it.value.state.toFloatOrNull() != null
                }
                .map { it.key }
                .sorted()
        }.distinct().take(6)
        if (picked.isNotEmpty()) return picked
        // Fallback: any numeric sensors at all, so the section still seeds.
        return states.keys
            .filter { it.startsWith("sensor.") && isNumeric(it) }
            .sorted()
            .take(4)
    }

    override fun getTempHistory(areaId: String): Flow<List<Float>> = flow {
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) { emit(emptyList()); return@flow }

        val deviceAreaMap = wsClient.devices.value.associate { it.id to it.area_id }
        val tempEntityId = wsClient.entities.value.firstOrNull { entity ->
            effectiveAreaId(entity, deviceAreaMap) == areaId &&
                entity.entity_id.startsWith("sensor.") &&
                entity.disabled_by == null &&
                wsClient.states.value[entity.entity_id]?.attributes?.device_class == "temperature"
        }?.entity_id

        if (tempEntityId == null) { emit(emptyList()); return@flow }

        emit(fetchRestHistory(auth.activeUrl(networkLocator.currentSsid.value), freshAccessToken(), tempEntityId, valueRange = -50f..100f))
    }

    override fun getEntityHistory(entityId: String): Flow<List<Float>> = flow {
        if (entityId.isBlank()) { emit(emptyList()); return@flow }
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) { emitAll(fakeRepo.getEntityHistory(entityId)); return@flow }
        emit(fetchRestHistory(auth.activeUrl(networkLocator.currentSsid.value), freshAccessToken(), entityId))
    }

    // ── Energy ───────────────────────────────────────────────────────────────────
    // Live value for a single configured energy sensor, mirroring getEntityState but
    // exposed as its own method so the Energy card can subscribe per-chip.
    private fun energyValue(entityId: String): Flow<HaEntityValue?> {
        if (entityId.isBlank()) return flowOf(null)
        return isAuthenticated.flatMapLatest { authed ->
            // Demo routing is handled by DemoAwareHomeRepository; when unauthenticated here
            // there's no HA state to read, so emit null rather than a wrong-role demo value.
            if (!authed) flowOf(null)
            else wsClient.states.map { states ->
                val s = states[entityId] ?: return@map null
                HaEntityValue(
                    entityId = entityId,
                    state = s.state,
                    unit = s.attributes.unit_of_measurement,
                    friendlyName = s.attributes.friendly_name,
                )
            }
        }
    }

    override fun getSolarProduction(entityId: String): Flow<HaEntityValue?> = energyValue(entityId)
    override fun getBatterySoc(entityId: String): Flow<HaEntityValue?> = energyValue(entityId)
    override fun getBatteryPower(entityId: String): Flow<HaEntityValue?> = energyValue(entityId)
    override fun getGridPower(entityId: String): Flow<HaEntityValue?> = energyValue(entityId)

    override fun getEnergyHistory(entityId: String): Flow<List<Float>> = getEntityHistory(entityId)

    // ── Energy dashboard auto-wiring (Helios `energy-prefs.ts`) ───────────────────
    // One `energy/get_prefs` fetch per connection; the live values resolve like Helios:
    // a wired power sensor reads directly, a cumulative kWh meter is differentiated.

    private val energyPrefs: Flow<HaEnergyPrefs?> = connectionStatus().flatMapLatest { status ->
        if (status != WsConnectionStatus.READY) flowOf(null)
        else flow { emit(wsClient.getEnergyPrefs()) }
    }

    override fun getAutoEnergy(): Flow<AutoEnergy?> = energyPrefs.flatMapLatest { prefs ->
        if (prefs == null) return@flatMapLatest flowOf<AutoEnergy?>(null)
        val solar = wattsValue(
            ids = prefs.solarRate.ifEmpty { prefs.solarEnergyFrom },
            name = "Solar Production",
        )
        val grid = if (prefs.gridRate.isNotEmpty()) {
            wattsValue(prefs.gridRate, "Grid")
        } else {
            combine(sumWatts(prefs.gridEnergyFrom), sumWatts(prefs.gridEnergyTo)) { imp, exp ->
                // Canonical Energy-dashboard convention: positive = importing.
                if (imp == null && exp == null) null
                else HaEntityValue("auto:grid", ((imp ?: 0.0) - (exp ?: 0.0)).roundToInt().toString(), "W", "Grid")
            }
        }
        val batteryPower = if (prefs.batteryRate.isNotEmpty()) {
            wattsValue(prefs.batteryRate, "Battery Power")
        } else {
            combine(sumWatts(prefs.batteryEnergyTo), sumWatts(prefs.batteryEnergyFrom)) { charge, discharge ->
                // Positive = charging (energy flowing INTO the battery).
                if (charge == null && discharge == null) null
                else HaEntityValue("auto:battery_power", ((charge ?: 0.0) - (discharge ?: 0.0)).roundToInt().toString(), "W", "Battery Power")
            }
        }
        val soc = if (prefs.batterySoc.isEmpty()) {
            flowOf(null)
        } else {
            wsClient.states.map { states ->
                val values = prefs.batterySoc.mapNotNull { states[it]?.state?.toDoubleOrNull() }.filter { it.isFinite() }
                if (values.isEmpty()) null
                else HaEntityValue("auto:battery_soc", values.average().coerceIn(0.0, 100.0).roundToInt().toString(), "%", "Battery")
            }
        }
        combine(solar, soc, batteryPower, grid) { s, b, bp, g ->
            if (s == null && b == null && bp == null && g == null) null else AutoEnergy(s, b, bp, g)
        }
    }

    /** Sum of [livePowerWatts] across [ids]; null until any id produces a reading. */
    private fun sumWatts(ids: List<String>): Flow<Double?> =
        if (ids.isEmpty()) flowOf(null)
        else combine(ids.map { livePowerWatts(it) }) { arr ->
            arr.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
        }

    private fun wattsValue(ids: List<String>, name: String): Flow<HaEntityValue?> =
        sumWatts(ids).map { w ->
            w?.let { HaEntityValue("auto:$name", it.roundToInt().toString(), "W", name) }
        }

    /**
     * Live watts for one entity, Helios-style: instantaneous power sensors (W/kW/MW) read
     * directly; cumulative energy meters (Wh/kWh/MWh or device_class energy) are
     * differentiated between consecutive state changes — `Δt < 10 s` is ignored as noise
     * and a negative delta (counter reset) reads as 0 W.
     */
    private fun livePowerWatts(entityId: String): Flow<Double?> {
        data class Acc(val lastKwh: Double?, val lastMs: Long, val watts: Double?)
        return wsClient.states
            .map { it[entityId] }
            .distinctUntilChanged { a, b -> a?.state == b?.state }
            .scan(Acc(null, 0L, null)) { acc, s ->
                if (s == null) return@scan Acc(null, 0L, null)
                val unit = s.attributes.unit_of_measurement?.trim()?.lowercase() ?: ""
                val v = s.state.toDoubleOrNull() ?: return@scan acc
                val cumulative = s.attributes.device_class == "energy" ||
                    unit == "wh" || unit == "kwh" || unit == "mwh"
                if (!cumulative) {
                    val mult = when (unit) {
                        "kw" -> 1000.0
                        "mw" -> 1_000_000.0
                        else -> 1.0
                    }
                    Acc(null, 0L, v * mult)
                } else {
                    val kwh = when (unit) {
                        "wh" -> v / 1000.0
                        "mwh" -> v * 1000.0
                        else -> v
                    }
                    val now = System.currentTimeMillis()
                    val prevKwh = acc.lastKwh
                    when {
                        prevKwh == null -> Acc(kwh, now, acc.watts ?: 0.0)
                        now - acc.lastMs < 10_000 -> acc
                        kwh < prevKwh -> Acc(kwh, now, 0.0)
                        else -> Acc(kwh, now, (kwh - prevKwh) * 3_600_000_000.0 / (now - acc.lastMs))
                    }
                }
            }
            .map { it.watts }
    }

    override fun getHomeCoords(): Flow<HomeCoords?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getHomeCoords()
        userPreferences.energyConfig.flatMapLatest { cfg ->
            val override = if (cfg.homeLatOverride != null && cfg.homeLonOverride != null) {
                HomeCoords(cfg.homeLatOverride, cfg.homeLonOverride)
            } else null
            if (override != null) {
                flowOf(override)
            } else {
                // zone.home always exists in HA and mirrors core config — reactive + cheap.
                wsClient.states
                    .map { states -> states["zone.home"]?.let { coordsOf(it) }?.let { HomeCoords(it.first, it.second) } }
                    .distinctUntilChanged()
                    .flatMapLatest { fromZone ->
                        if (fromZone != null) flowOf<HomeCoords?>(fromZone)
                        else flow<HomeCoords?> {
                            // Fallback for instances where zone.home isn't in the state stream.
                            val core = wsClient.getHaConfig()
                            val lat = core?.latitude; val lon = core?.longitude
                            emit(if (lat != null && lon != null) HomeCoords(lat, lon) else null)
                        }
                    }
            }
        }
    }

    override fun getCameraSnapshotUrl(entityId: String): Flow<String?> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) flowOf(null)
        else wsClient.states.map { states ->
            val s = states[entityId] ?: return@map null
            val accessToken = s.attributes.raw?.get("access_token")?.asString ?: return@map null
            val base = activeUrl().trimEnd('/')
            "$base/api/camera_proxy/$entityId?token=$accessToken"
        }
    }

    override suspend fun getCameraStreamUrl(entityId: String): String? {
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) return null
        val relative = wsClient.getCameraStreamUrl(entityId)
        if (relative == null) {
            Log.w("HomeHealth_Repo", "getCameraStreamUrl($entityId): WS returned null")
            return null
        }
        val base = auth.activeUrl(networkLocator.currentSsid.value).trimEnd('/')
        val full = if (relative.startsWith("http")) relative else "$base$relative"
        Log.d("HomeHealth_Repo", "getCameraStreamUrl($entityId) → $full")
        return full
    }

    override suspend fun startCameraWebRtc(
        entityId: String,
        offerSdp: String,
        onSignal: (WebRtcSignal) -> Unit,
    ): Int? {
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) return null
        return wsClient.cameraWebRtcOffer(entityId, offerSdp, onSignal)
    }

    override fun sendCameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate) =
        wsClient.cameraWebRtcCandidate(entityId, sessionId, candidate)

    override fun stopCameraWebRtc(subscriptionId: Int) = wsClient.unsubscribeCameraWebRtc(subscriptionId)

    override suspend fun getCameraWebRtcConfig(entityId: String): List<WebRtcIceServer>? {
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) return null
        return wsClient.getCameraWebRtcClientConfig(entityId)
    }

    // ── Media player ─────────────────────────────────────────────────────────
    // Subscribes to wsClient.states for live updates. Falls back to FakeHomeRepository's
    // static demo snapshot when not authenticated (demo mode). Returns null when
    // authenticated but the entity has no state yet.
    override fun getMediaPlayer(entityId: String): Flow<HaMedia?> {
        if (entityId.isBlank()) return flowOf(null)
        return authPreferences.authState.flatMapLatest { auth ->
            if (auth.accessToken.isEmpty()) fakeRepo.getMediaPlayer(entityId)
            // Registry entities ride along so the card knows the player's platform
            // (Music Assistant players get extra controls).
            else combine(wsClient.states, wsClient.entities) { states, entities ->
                val s = states[entityId] ?: return@combine null
                buildMedia(
                    entityId, s,
                    auth.activeUrl(networkLocator.currentSsid.value).trimEnd('/'),
                    isMusicAssistant = entities.firstOrNull { it.entity_id == entityId }
                        ?.platform == MUSIC_ASSISTANT_PLATFORM,
                )
            }
        }
    }

    private fun buildMedia(entityId: String, state: HaState, baseUrl: String, isMusicAssistant: Boolean): HaMedia {
        val attrs = state.attributes
        val raw = attrs.raw
        val rawState = state.state
        val isOff = rawState == "off" || rawState == "unavailable" || rawState == "standby"
        val isPlaying = rawState == "playing"
        val title = raw?.get("media_title")?.takeIf { !it.isJsonNull }?.asString
            ?: raw?.get("media_content_id")?.takeIf { !it.isJsonNull }?.asString?.let { mediaContentTitle(it) }
            ?: "Nothing playing"
        val friendlyName = attrs.friendly_name
            ?: entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
        val sourceLabel = buildMediaSourceLabel(raw)
        val volume = raw?.get("volume_level")?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        val muted = raw?.get("is_volume_muted")?.takeIf { !it.isJsonNull }?.asBoolean == true
        val shuffle = raw?.get("shuffle")?.takeIf { !it.isJsonNull }?.asBoolean == true
        val repeat = MediaRepeatMode.fromHa(raw?.get("repeat")?.takeIf { !it.isJsonNull }?.asString)
        val duration = raw?.get("media_duration")?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        val position = raw?.get("media_position")?.takeIf { !it.isJsonNull }?.asFloat ?: 0f
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val elapsed = if (duration > 0f) formatMediaTime(position.toLong()) else "0:00"
        val remaining = if (duration > 0f) "-" + formatMediaTime((duration - position).coerceAtLeast(0f).toLong()) else "-0:00"
        val pictureRaw = raw?.get("entity_picture_local")?.takeIf { !it.isJsonNull }?.asString
            ?: raw?.get("entity_picture")?.takeIf { !it.isJsonNull }?.asString
        val pictureUrl = pictureRaw?.let { if (it.startsWith("http")) it else "$baseUrl$it" }
        return HaMedia(
            entityId = entityId,
            friendlyName = friendlyName,
            title = title,
            source = sourceLabel,
            isPlaying = isPlaying,
            isOff = isOff,
            progress = progress,
            elapsedLabel = elapsed,
            remainingLabel = remaining,
            volume = if (muted) 0f else volume.coerceIn(0f, 1f),
            shuffleOn = shuffle,
            repeatMode = repeat,
            entityPictureUrl = pictureUrl,
            isMusicAssistant = isMusicAssistant,
        )
    }

    private fun mediaContentTitle(contentId: String): String {
        // Strip query string, take last path segment.
        val noQuery = contentId.substringBefore('?')
        val lastSeg = noQuery.substringAfterLast('/')
        return if (lastSeg.length > 40) lastSeg.take(37) + "…" else lastSeg.ifBlank { "Stream" }
    }

    private fun buildMediaSourceLabel(raw: com.google.gson.JsonObject?): String {
        if (raw == null) return ""
        val appName = raw["app_name"]?.takeIf { !it.isJsonNull }?.asString
        val source = raw["source"]?.takeIf { !it.isJsonNull }?.asString
        val contentId = raw["media_content_id"]?.takeIf { !it.isJsonNull }?.asString
        val host = contentId?.let {
            runCatching { java.net.URI(it).host }.getOrNull()
        }
        val mediaContentType = raw["media_content_type"]?.takeIf { !it.isJsonNull }?.asString
        return listOfNotNull(
            appName ?: source ?: mediaContentType,
            host,
        ).joinToString(" · ").ifBlank { "" }
    }

    private fun formatMediaTime(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    override suspend fun mediaPlayPause(entityId: String) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("media_player", "media_play_pause", mapOf("entity_id" to entityId))
        activityLog.record("media", "${friendlyName(entityId)} play / pause", "Media")
    }

    override suspend fun mediaSkipNext(entityId: String) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("media_player", "media_next_track", mapOf("entity_id" to entityId))
    }

    override suspend fun mediaSkipPrev(entityId: String) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("media_player", "media_previous_track", mapOf("entity_id" to entityId))
    }

    override suspend fun mediaSetVolume(entityId: String, volume: Float) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService(
            "media_player", "volume_set",
            mapOf("entity_id" to entityId, "volume_level" to volume.coerceIn(0f, 1f)),
        )
    }

    override suspend fun mediaSetShuffle(entityId: String, on: Boolean) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService(
            "media_player", "shuffle_set",
            mapOf("entity_id" to entityId, "shuffle" to on),
        )
    }

    override suspend fun mediaSetRepeat(entityId: String, mode: MediaRepeatMode) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService(
            "media_player", "repeat_set",
            mapOf("entity_id" to entityId, "repeat" to MediaRepeatMode.toHa(mode)),
        )
    }

    override suspend fun mediaSeek(entityId: String, progress: Float) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        val state = wsClient.states.value[entityId] ?: return
        val duration = state.attributes.raw?.get("media_duration")?.takeIf { !it.isJsonNull }?.asFloat ?: return
        val seekSeconds = (duration * progress.coerceIn(0f, 1f)).toDouble()
        wsClient.callService(
            "media_player", "media_seek",
            mapOf("entity_id" to entityId, "seek_position" to seekSeconds),
        )
    }

    override suspend fun mediaTurnOff(entityId: String) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService("media_player", "turn_off", mapOf("entity_id" to entityId))
        activityLog.record("media", "${friendlyName(entityId)} turned off", "Media")
    }

    // ── Music Assistant ──────────────────────────────────────────────────────
    // `music_assistant.search` is scoped to a config entry, not a player. The MA
    // player's own registry entry carries that id, so search "just works" for any
    // MA player with zero extra configuration (mass-search-card needs the same id
    // but makes the user dig it out of /config/integrations).
    override suspend fun searchMusicAssistant(
        entityId: String,
        query: String,
        mediaType: MaMediaType?,
        libraryOnly: Boolean,
    ): MaSearchResults {
        if (query.isBlank()) return MaSearchResults()
        if (authPreferences.authState.first().accessToken.isEmpty()) return MaSearchResults()
        val configEntryId = wsClient.entities.value
            .firstOrNull { it.entity_id == entityId }?.config_entry_id
        if (configEntryId.isNullOrBlank()) {
            Log.w("HomeHealth_Repo", "searchMusicAssistant: no config_entry_id for $entityId")
            return MaSearchResults()
        }
        val data = buildMap<String, Any> {
            put("config_entry_id", configEntryId)
            put("name", query)
            // Per-type cap: generous for a single-type search, smaller for the fan-out.
            put("limit", if (mediaType != null) 20 else 8)
            if (mediaType != null) put("media_type", listOf(mediaType.haValue))
            if (libraryOnly) put("library_only", true)
        }
        val response = wsClient.callServiceWithResponse("music_assistant", "search", data)
            ?: return MaSearchResults()
        fun items(key: String, type: MaMediaType): List<MaSearchItem> =
            response[key]?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let { parseMaItem(it, type) } }
                ?: emptyList()
        return MaSearchResults(
            artists = items("artists", MaMediaType.ARTIST),
            albums = items("albums", MaMediaType.ALBUM),
            tracks = items("tracks", MaMediaType.TRACK),
            playlists = items("playlists", MaMediaType.PLAYLIST),
            radio = items("radio", MaMediaType.RADIO),
        )
    }

    private fun parseMaItem(obj: com.google.gson.JsonObject, type: MaMediaType): MaSearchItem? {
        fun str(key: String) = obj[key]?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
        val uri = str("uri") ?: return null
        val name = str("name") ?: return null
        val artists = obj["artists"]?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { el ->
                el.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("name")?.takeIf { !it.isJsonNull }?.asString
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        // Artwork only when MA hands back an absolute URL — local-library items may
        // carry MA-internal image paths the phone can't fetch; model those as null
        // (fallback art) instead of broken images.
        val image = str("image")?.takeIf { it.startsWith("http") }
        return MaSearchItem(
            uri = uri,
            name = name,
            mediaType = type,
            subtitle = artists.joinToString(", "),
            imageUrl = image,
        )
    }

    override suspend fun playMusicAssistantMedia(entityId: String, item: MaSearchItem, mode: MaEnqueueMode) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService(
            "music_assistant", "play_media",
            mapOf(
                "entity_id" to entityId,
                "media_id" to item.uri,
                "media_type" to item.mediaType.haValue,
                "enqueue" to mode.haValue,
            ),
        )
        val verb = if (mode == MaEnqueueMode.ADD) "Queued" else "Playing"
        activityLog.record("media", "$verb ${item.name} on ${friendlyName(entityId)}", "Music Assistant")
    }

    override fun getMusicAssistantPlayers(): Flow<List<HaMedia>> =
        authPreferences.authState.flatMapLatest { auth ->
            if (auth.accessToken.isEmpty()) fakeRepo.getMusicAssistantPlayers()
            else combine(wsClient.states, wsClient.entities) { states, entities ->
                val baseUrl = auth.activeUrl(networkLocator.currentSsid.value).trimEnd('/')
                entities
                    .filter {
                        it.platform == MUSIC_ASSISTANT_PLATFORM &&
                            it.entity_id.startsWith("media_player.") &&
                            it.disabled_by == null
                    }
                    .mapNotNull { entry ->
                        states[entry.entity_id]?.let { s ->
                            buildMedia(entry.entity_id, s, baseUrl, isMusicAssistant = true)
                        }
                    }
                    // Playing players first — they're what the Music page wants selected.
                    .sortedWith(compareByDescending<HaMedia> { it.isPlaying }.thenBy { it.friendlyName.lowercase() })
            }
        }

    override suspend fun getMaQueue(entityId: String): MaQueue? {
        if (authPreferences.authState.first().accessToken.isEmpty()) return null
        val response = wsClient.callServiceWithResponse(
            "music_assistant", "get_queue", mapOf("entity_id" to entityId),
        ) ?: return null
        // Response is keyed by entity_id; be tolerant of a flat payload from older builds.
        val q = response[entityId]?.takeIf { it.isJsonObject }?.asJsonObject
            ?: response.takeIf { it.has("queue_id") || it.has("items") }
            ?: return null
        fun item(key: String): Pair<String?, String?> {
            val obj = q[key]?.takeIf { it.isJsonObject }?.asJsonObject ?: return null to null
            val media = obj["media_item"]?.takeIf { it.isJsonObject }?.asJsonObject
            val title = media?.get("name")?.takeIf { !it.isJsonNull }?.asString
                ?: obj["name"]?.takeIf { !it.isJsonNull }?.asString
            val artist = media?.get("artists")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { el ->
                    el.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("name")?.takeIf { !it.isJsonNull }?.asString
                }
                ?.joinToString(", ")
                ?.takeIf { it.isNotBlank() }
            return title to artist
        }
        val (curTitle, curArtist) = item("current_item")
        val (nextTitle, nextArtist) = item("next_item")
        return MaQueue(
            currentTitle = curTitle,
            currentArtist = curArtist,
            nextTitle = nextTitle,
            nextArtist = nextArtist,
            itemCount = q["items"]?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull() ?: 0,
            currentIndex = q["current_index"]?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull(),
        )
    }

    override suspend fun getMaLibrary(
        entityId: String,
        mediaType: MaMediaType,
        favoritesOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<MaSearchItem> {
        if (authPreferences.authState.first().accessToken.isEmpty()) return emptyList()
        val configEntryId = wsClient.entities.value
            .firstOrNull { it.entity_id == entityId }?.config_entry_id
        if (configEntryId.isNullOrBlank()) return emptyList()
        val data = buildMap<String, Any> {
            put("config_entry_id", configEntryId)
            put("media_type", mediaType.haValue)
            put("limit", limit)
            put("offset", offset)
            if (favoritesOnly) put("favorite", true)
        }
        val response = wsClient.callServiceWithResponse("music_assistant", "get_library", data)
            ?: return emptyList()
        val items = response["items"]?.takeIf { it.isJsonArray }?.asJsonArray
            // Older builds keyed the list by the plural media type instead of "items".
            ?: response["${mediaType.haValue}s"]?.takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyList()
        return items.mapNotNull { el ->
            el.takeIf { it.isJsonObject }?.asJsonObject?.let { parseMaItem(it, mediaType) }
        }
    }

    override suspend fun transferMaQueue(fromEntityId: String, toEntityId: String) {
        if (authPreferences.authState.first().accessToken.isEmpty()) return
        wsClient.callService(
            "music_assistant", "transfer_queue",
            mapOf("entity_id" to toEntityId, "source_player" to fromEntityId),
        )
        activityLog.record(
            "media",
            "Moved music from ${friendlyName(fromEntityId)} to ${friendlyName(toEntityId)}",
            "Music Assistant",
        )
    }

    // ── Text-to-speech ─────────────────────────────────────────────────────────
    // Echo devices are detected by the media_player's registry `platform`: the official
    // "Alexa Devices" integration reports `alexa_devices`, the "Alexa Media Player" custom
    // integration reports `alexa_media`. Both speak in Amazon's own voice via a notify flow,
    // so the composer hides the TTS-engine controls for them.
    override fun getTtsTarget(entityId: String): Flow<TtsTarget> {
        if (entityId.isBlank()) return flowOf(TtsTarget(entityId, "", isEcho = false))
        return authPreferences.authState.flatMapLatest { auth ->
            if (auth.accessToken.isEmpty()) fakeRepo.getTtsTarget(entityId)
            else combine(wsClient.entities, wsClient.states) { entities, states ->
                val entry = entities.firstOrNull { it.entity_id == entityId }
                val name = entry?.name
                    ?: states[entityId]?.attributes?.friendly_name
                    ?: entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
                TtsTarget(
                    entityId = entityId,
                    friendlyName = name,
                    isEcho = entry?.platform == ALEXA_MEDIA_PLATFORM || entry?.platform == ALEXA_DEVICES_PLATFORM,
                )
            }
        }
    }

    override suspend fun getTtsEngines(): List<HaTtsEngine> {
        if (authPreferences.authState.first().accessToken.isEmpty()) return emptyList()
        return wsClient.listTtsEngines().filterNot { it.deprecated }.sortedBy { it.name.lowercase() }
    }

    override suspend fun getTtsVoices(engineId: String, language: String): List<HaTtsVoice> {
        if (engineId.isBlank() || language.isBlank()) return emptyList()
        if (authPreferences.authState.first().accessToken.isEmpty()) return emptyList()
        return wsClient.listTtsVoices(engineId, language).sortedBy { it.name.lowercase() }
    }

    override suspend fun sendTts(
        mediaPlayerEntityId: String,
        message: String,
        announce: Boolean,
        engineId: String?,
        voiceId: String?,
        language: String?,
    ) {
        Log.d("HomeHealth_Repo", "sendTts($mediaPlayerEntityId, announce=$announce, engine=$engineId)")
        if (mediaPlayerEntityId.isBlank() || message.isBlank()) return
        if (authPreferences.authState.first().accessToken.isEmpty()) return

        val entry = wsClient.entities.value.firstOrNull { it.entity_id == mediaPlayerEntityId }
        val ok = when (entry?.platform) {
            // "Alexa Media Player" custom integration: notify.alexa_media accepts the
            // media_player entity_id as a target (robust against entity renames). data.type
            // picks plain TTS vs the chime-prefixed announcement.
            ALEXA_MEDIA_PLATFORM -> wsClient.callService(
                "notify", "alexa_media",
                mapOf(
                    "message" to message,
                    "target" to listOf(mediaPlayerEntityId),
                    "data" to mapOf("type" to if (announce) "announce" else "tts"),
                ),
            )
            // Official "Alexa Devices" integration: a notify.<device>_speak / *_announce
            // entity exists on the same device. notify.send_message targets it directly.
            ALEXA_DEVICES_PLATFORM -> {
                val target = alexaDevicesNotifyEntity(entry, announce)
                if (target == null) {
                    Log.w("HomeHealth_Repo", "sendTts: no alexa_devices notify entity for $mediaPlayerEntityId")
                    false
                } else {
                    wsClient.callService(
                        "notify", "send_message",
                        mapOf("entity_id" to target, "message" to message),
                    )
                }
            }
            // Everything else: tts.speak with the configured (or overridden) engine + voice.
            else -> {
                val defaults = userPreferences.ttsDefaults.first()
                val engine = engineId?.takeIf { it.isNotBlank() } ?: defaults.engineId
                if (engine.isBlank()) {
                    Log.w("HomeHealth_Repo", "sendTts: no TTS engine configured — aborting")
                    return
                }
                val voice = voiceId ?: defaults.voiceId
                val lang = language?.takeIf { it.isNotBlank() } ?: defaults.language.takeIf { it.isNotBlank() }
                val data = buildMap<String, Any> {
                    put("entity_id", engine)                          // the tts.* engine entity
                    put("media_player_entity_id", mediaPlayerEntityId)
                    put("message", message)
                    if (lang != null) put("language", lang)
                    if (voice.isNotBlank()) put("options", mapOf("voice" to voice))
                }
                wsClient.callService("tts", "speak", data)
            }
        }
        if (ok) {
            activityLog.record("media", "Announced to ${friendlyName(mediaPlayerEntityId)}", "Text to speech")
        }
    }

    // Find the notify entity to use for an alexa_devices media player. Prefer the
    // announce/speak entity matching the requested mode, on the same device; fall back
    // to whichever notify entity that integration created for the device.
    private fun alexaDevicesNotifyEntity(mediaEntry: HaEntityEntry, announce: Boolean): String? {
        val deviceId = mediaEntry.device_id?.takeIf { it.isNotBlank() }
        val notifies = wsClient.entities.value.filter {
            it.entity_id.startsWith("notify.") &&
                it.disabled_by == null &&
                (deviceId != null && it.device_id == deviceId)
        }
        if (notifies.isEmpty()) return null
        val preferredSuffix = if (announce) "_announce" else "_speak"
        val otherSuffix = if (announce) "_speak" else "_announce"
        return notifies.firstOrNull { it.entity_id.endsWith(preferredSuffix) }?.entity_id
            ?: notifies.firstOrNull { it.entity_id.endsWith(otherSuffix) }?.entity_id
            ?: notifies.first().entity_id
    }

    // ── Area matching ─────────────────────────────────────────────────────────

    private fun effectiveAreaId(entity: HaEntityEntry, deviceAreaMap: Map<String, String?>): String? {
        // Blank strings from HA must be treated as null (some HA versions return "" for unset fields)
        val entityArea = entity.area_id?.takeIf { it.isNotBlank() }
        if (entityArea != null) return entityArea
        val deviceId = entity.device_id?.takeIf { it.isNotBlank() } ?: return null
        return deviceAreaMap[deviceId]?.takeIf { it.isNotBlank() }
    }

    // Friendly label for an entity using the data already in scope (no global lookup).
    private fun entityFriendlyName(entry: HaEntityEntry, state: HaState?): String =
        entry.name
            ?: state?.attributes?.friendly_name
            ?: entry.entity_id.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    // ── Room mapping ──────────────────────────────────────────────────────────

    private fun mapRooms(
        areas: List<HaArea>,
        devices: List<HaDeviceEntry>,
        entities: List<HaEntityEntry>,
        states: Map<String, HaState>,
        overrides: Map<String, RoomSensorOverride>,
    ): List<HaRoom> {
        val deviceAreaMap = devices.associate { it.id to it.area_id }
        val activeEntities = entities.filter { it.disabled_by == null }
        return areas.mapIndexed { index, area ->
            val areaEntities = activeEntities.filter { effectiveAreaId(it, deviceAreaMap) == area.area_id }
            val areaStates = areaEntities.mapNotNull { states[it.entity_id] }
            val lightAndSwitchStates = areaStates.filter { it.entity_id.startsWith("light.") || it.entity_id.startsWith("switch.") }
            val activeCount = lightAndSwitchStates.count { it.state == "on" }

            val climateState = areaStates.firstOrNull { it.entity_id.startsWith("climate.") }
            val override = overrides[area.area_id]
            val overrideTemp = override?.tempEntityId
                ?.takeIf { it.isNotBlank() }
                ?.let { states[it]?.state?.toFloatOrNull() }
            val overrideHumidity = override?.humidityEntityId
                ?.takeIf { it.isNotBlank() }
                ?.let { states[it]?.state?.toFloatOrNull()?.toInt() }
            // Leave null when nothing resolves — the card/sheet then omit the reading
            // rather than rendering a misleading 0° / 0%.
            val temp = overrideTemp
                ?: climateState?.attributes?.current_temperature
                ?: areaStates.firstOrNull { it.attributes.device_class == "temperature" }?.state?.toFloatOrNull()
                ?: areaStates.firstOrNull { it.entity_id.startsWith("sensor.") && "temp" in it.entity_id.lowercase() }?.state?.toFloatOrNull()
            val humidity = overrideHumidity
                ?: climateState?.attributes?.current_humidity
                ?: areaStates.firstOrNull { it.attributes.device_class == "humidity" }?.state?.toIntOrNull()
                ?: areaStates.firstOrNull { it.entity_id.startsWith("sensor.") && "humid" in it.entity_id.lowercase() }?.state?.toIntOrNull()

            // Only flag a room for "needs attention" when an actual controllable device is
            // unreachable. Diagnostic/noise domains (update, button, scene, sensor, …) are
            // routinely "unavailable" in HA and would otherwise make nearly every room alarm.
            val alerts = areaEntities.mapNotNull { entry ->
                val st = states[entry.entity_id] ?: return@mapNotNull null
                if (entry.entity_id.substringBefore('.') !in ALERT_DOMAINS) return@mapNotNull null
                when (st.state) {
                    "unavailable" -> "${entityFriendlyName(entry, st)} — unreachable"
                    "unknown" -> "${entityFriendlyName(entry, st)} — not responding"
                    else -> null
                }
            }

            val (colorHex, inkHex) = AREA_PALETTE[index % AREA_PALETTE.size]
            HaRoom(
                id = area.area_id,
                name = area.name,
                icon = roomIconKey(area.icon, area.name),
                colorHex = colorHex,
                inkHex = inkHex,
                deviceCount = areaEntities.size,
                activeCount = activeCount,
                temp = temp,
                humidity = humidity,
                alerts = alerts,
            )
        }
    }

    // ── Light mapping ─────────────────────────────────────────────────────────

    private fun mapLightsForRoom(
        areaId: String,
        devices: List<HaDeviceEntry>,
        entities: List<HaEntityEntry>,
        states: Map<String, HaState>,
    ): List<HaLight> {
        val deviceAreaMap = devices.associate { it.id to it.area_id }
        val allLightEntities = entities.filter { it.entity_id.startsWith("light.") && it.disabled_by == null }
        val matchedLights = allLightEntities.filter { effectiveAreaId(it, deviceAreaMap) == areaId }
        Log.d("HomeHealth_Repo", "mapLightsForRoom($areaId): ${allLightEntities.size} total lights in registry, ${matchedLights.size} matched to area | sample areas: ${allLightEntities.take(3).map { "${it.entity_id}→area=${it.area_id},dev=${it.device_id}→devArea=${deviceAreaMap[it.device_id]}" }}")
        return matchedLights
            .mapNotNull { entry ->
                val state = states[entry.entity_id] ?: return@mapNotNull null
                val available = state.state != "unavailable"
                val isOn = state.state == "on"
                val brightness = if (isOn) ((state.attributes.brightness ?: 255f) / 255f * 100).toInt() else 0
                val color = rgbToHex(state.attributes.rgb_color) ?: if (isOn) "#FFD9A8" else "#555555"
                HaLight(
                    id = entry.entity_id,
                    name = entry.name ?: state.attributes.friendly_name ?: entry.entity_id,
                    brightness = brightness,
                    colorHex = color,
                    isOn = isOn,
                    isAvailable = available,
                    supportsColor = supportsColor(state.attributes.supported_color_modes),
                    supportsColorTemp = supportsColorTemp(state.attributes.supported_color_modes),
                    colorTempKelvin = state.attributes.color_temp_kelvin,
                    minColorTempKelvin = state.attributes.min_color_temp_kelvin,
                    maxColorTempKelvin = state.attributes.max_color_temp_kelvin,
                )
            }
    }

    private fun mapLightsFromEntityIds(lightEntityIds: Set<String>, states: Map<String, HaState>): List<HaLight> =
        lightEntityIds.mapNotNull { entityId ->
            val state = states[entityId] ?: return@mapNotNull null
            val available = state.state != "unavailable"
            val isOn = state.state == "on"
            HaLight(
                id = entityId,
                name = state.attributes.friendly_name
                    ?: entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() },
                brightness = if (isOn) ((state.attributes.brightness ?: 255f) / 255f * 100).toInt() else 0,
                colorHex = rgbToHex(state.attributes.rgb_color) ?: if (isOn) "#FFD9A8" else "#555555",
                isOn = isOn,
                isAvailable = available,
                supportsColor = supportsColor(state.attributes.supported_color_modes),
                supportsColorTemp = supportsColorTemp(state.attributes.supported_color_modes),
                colorTempKelvin = state.attributes.color_temp_kelvin,
                minColorTempKelvin = state.attributes.min_color_temp_kelvin,
                maxColorTempKelvin = state.attributes.max_color_temp_kelvin,
            )
        }.sortedBy { it.name }

    private fun supportsColor(modes: List<String>?): Boolean {
        if (modes == null) return false
        val colorModes = setOf("hs", "xy", "rgb", "rgbw", "rgbww")
        return modes.any { it.lowercase() in colorModes }
    }

    private fun supportsColorTemp(modes: List<String>?): Boolean {
        if (modes == null) return false
        return modes.any { it.lowercase() == "color_temp" }
    }

    // ── REST history fetch ────────────────────────────────────────────────────

    private suspend fun fetchRestHistory(
        haUrl: String,
        token: String,
        entityId: String,
        valueRange: ClosedFloatingPointRange<Float>? = null,
    ): List<Float> =
        withContext(Dispatchers.IO) {
            try {
                val startTime = java.time.Instant.now().minusSeconds(86_400L).toString()
                val url = "${haUrl.trimEnd('/')}/api/history/period/$startTime" +
                    "?filter_entity_id=$entityId&minimal_response=true&no_attributes=true&significant_changes_only=false"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                val body = okHttpClient.newCall(req).execute().body?.string() ?: return@withContext emptyList()
                val outer = gson.fromJson(body, JsonArray::class.java)
                if (outer.size() == 0 || !outer[0].isJsonArray) return@withContext emptyList()
                val inner = outer[0].asJsonArray
                val parsed = inner.mapNotNull { it.asJsonObject["state"]?.asString?.toFloatOrNull() }
                val all = if (valueRange != null) parsed.filter { it in valueRange } else parsed
                // Downsample to max 60 points for the graph
                if (all.size <= 60) all else {
                    val step = all.size / 60
                    all.filterIndexed { i, _ -> i % step == 0 }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun rgbToHex(rgb: List<Int>?): String? {
        if (rgb == null || rgb.size < 3) return null
        return "#%02x%02x%02x".format(rgb[0], rgb[1], rgb[2])
    }

    // Resolve a room icon: prefer HA's own mdi icon, but most areas leave it unset (or
    // use an icon we don't map), which previously made every card fall back to the
    // generic "home" glyph. So when the mdi lookup is inconclusive, infer from the
    // area name (Bedroom → bed, Kitchen → cooking, Hall → door, …).
    private fun roomIconKey(mdiIcon: String?, areaName: String): String {
        val fromMdi = mdiToIconKey(mdiIcon)
        if (fromMdi != "home") return fromMdi
        val n = areaName.lowercase()
        return when {
            "living" in n || "lounge" in n || "family" in n || "den" in n || "sitting" in n -> "sofa"
            "bed" in n || "master" in n || "guest" in n || "nursery" in n || "kid" in n -> "bed"
            "kitchen" in n || "pantry" in n || "dining" in n -> "cooking"
            "office" in n || "study" in n || "desk" in n || "work" in n -> "desk"
            "bath" in n || "shower" in n || "toilet" in n || "wash" in n || "laundry" in n || "wc" in n -> "water"
            "entry" in n || "hall" in n || "foyer" in n || "garage" in n || "door" in n || "porch" in n || "mud" in n || "corridor" in n -> "door"
            else -> "home"
        }
    }

    private fun mdiToIconKey(mdiIcon: String?): String {
        val name = mdiIcon?.removePrefix("mdi:") ?: return "home"
        return when {
            "sofa" in name || "couch" in name -> "sofa"
            "bed" in name -> "bed"
            "kitchen" in name || "stove" in name || "pot" in name || "silverware" in name || "food" in name -> "cooking"
            "desk" in name || "laptop" in name || "monitor" in name || "computer" in name -> "desk"
            "shower" in name || "bath" in name || "toilet" in name || "water" in name -> "water"
            "door" in name || "gate" in name || "entry" in name || "garage" in name -> "door"
            else -> "home"
        }
    }

    companion object {
        private val AREA_PALETTE = listOf(
            "#E8B4D6" to "#3a1a2c", "#9DD8A8" to "#0f3a1a",
            "#F2A65E" to "#3a1d0a", "#9CB6E8" to "#0f1f3a",
            "#7DD3D8" to "#0a2f30", "#C77DBA" to "#2c0f25",
            "#E8C99B" to "#3a2a0f", "#F2725C" to "#3a1510",
        )
        // Registry `platform` values for Amazon Echo media players — the two integrations
        // that need the Alexa notify TTS flow instead of `tts.speak`.
        private const val ALEXA_MEDIA_PLATFORM = "alexa_media"     // HACS "Alexa Media Player"
        private const val ALEXA_DEVICES_PLATFORM = "alexa_devices" // official "Alexa Devices"

        // Registry `platform` of Music Assistant media players — unlocks the media
        // card's MA search & play controls.
        private const val MUSIC_ASSISTANT_PLATFORM = "music_assistant"

        // Domains that represent real, user-controllable devices. An "unavailable" entity in one
        // of these is a genuine problem worth surfacing; everything else (update, button, scene,
        // sensor, device_tracker, automation, …) is excluded to avoid false "needs attention".
        private val ALERT_DOMAINS = setOf(
            "light", "switch", "climate", "cover", "lock", "fan",
            "media_player", "vacuum", "humidifier", "water_heater",
            "alarm_control_panel", "valve", "lawn_mower", "camera", "siren",
        )
    }
}
