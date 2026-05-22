@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.uc.homehealth.data

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

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
) : HomeRepository {

    @Volatile private var refreshInFlight = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isAuthenticated: Flow<Boolean> = authPreferences.authState.map { it.accessToken.isNotEmpty() }

    /** SSID-resolved URL for one-shot reads (REST history, camera URLs, etc.). */
    private suspend fun activeUrl(): String {
        val auth = authPreferences.authState.first()
        return auth.activeUrl(networkLocator.currentSsid.value)
    }

    init {
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
        // Reactive refresh: when HA rejects the WS auth, try one refresh. Success → saveAuth
        // re-emits authState → collector above reconnects with the new token. Failure → leave
        // status=AUTH_INVALID so the UI can prompt for re-login. Single attempt per event
        // (no loop) so we never re-hit HA's ip_ban threshold.
        scope.launch {
            wsClient.status.collect { status ->
                if (status == WsConnectionStatus.AUTH_INVALID && !refreshInFlight) {
                    val auth = authPreferences.authState.first()
                    if (auth.refreshToken.isNotBlank()) {
                        attemptTokenRefresh()
                    } else {
                        Log.w("HomeHealth_Repo", "AUTH_INVALID with no refresh_token (long-lived token revoked) — needs re-login")
                    }
                }
            }
        }
    }

    private fun needsRefresh(auth: AuthState): Boolean =
        auth.refreshToken.isNotBlank() &&
            auth.tokenExpiry > 0L &&
            auth.tokenExpiry < System.currentTimeMillis() + 60_000L

    private suspend fun attemptTokenRefresh(): Boolean {
        if (refreshInFlight) return false
        refreshInFlight = true
        return try {
            val auth = authPreferences.authState.first()
            if (auth.refreshToken.isBlank()) {
                Log.w("HomeHealth_Repo", "Token refresh skipped — no refresh_token saved")
                return false
            }
            val url = auth.activeUrl(networkLocator.currentSsid.value)
            if (url.isBlank()) {
                Log.w("HomeHealth_Repo", "Token refresh skipped — no HA URL configured")
                return false
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
            true
        } catch (e: Exception) {
            Log.e("HomeHealth_Repo", "Token refresh failed: ${e.message}", e)
            false
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
                    HaEntitySummary(
                        entityId = entry.entity_id,
                        friendlyName = entry.name ?: state.attributes.friendly_name ?: entry.entity_id,
                        domain = entry.entity_id.substringBefore('.'),
                        areaName = areaId?.let { areaMap[it] } ?: "Unassigned",
                        state = state.state,
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
            isAvailable = state.state != "unavailable",
        )
    }

    override fun connectionStatus(): Flow<WsConnectionStatus> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) flowOf(WsConnectionStatus.DISCONNECTED) else wsClient.status
    }

    override fun reconnectNow() {
        wsClient.reconnectNow()
    }

    override fun getTrackedFlights(): Flow<List<HaFlight>> = isAuthenticated.flatMapLatest { authed ->
        if (!authed) return@flatMapLatest fakeRepo.getTrackedFlights()
        wsClient.states.map { states ->
            val raw = states[FR24_TRACKED_ENTITY]?.attributes?.raw ?: return@map emptyList()
            val arr = raw["flights"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@map emptyList()
            arr.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let { parseFlight(it) } }
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
            val s = states[FR24_TRACKED_ENTITY] ?: return@map false
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
            mapOf("entity_id" to FR24_ADD_ENTITY, "value" to q),
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
            mapOf("entity_id" to FR24_REMOVE_ENTITY, "value" to q),
        )
        activityLog.record("auto", "Untracking flight $q", "FlightRadar24")
    }

    private fun parseFlight(o: com.google.gson.JsonObject): HaFlight? {
        val id = o["id"]?.takeIf { !it.isJsonNull }?.asString ?: return null
        val flightNumber = o["flight_number"]?.takeIf { !it.isJsonNull }?.asString ?: return null
        fun s(k: String) = o[k]?.takeIf { !it.isJsonNull }?.asString
        fun i(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asInt }?.getOrNull()
        fun l(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asLong }?.getOrNull()
        fun f(k: String) = o[k]?.takeIf { !it.isJsonNull }?.runCatching { asFloat }?.getOrNull()
        return HaFlight(
            id = id,
            flightNumber = flightNumber,
            callsign = s("callsign"),
            aircraftRegistration = s("aircraft_registration"),
            aircraftPhotoSmall = s("aircraft_photo_small"),
            aircraftPhotoMedium = s("aircraft_photo_medium"),
            aircraftPhotoLarge = s("aircraft_photo_large"),
            aircraftModel = s("aircraft_model"),
            airline = s("airline") ?: s("airline_short"),
            airlineIata = s("airline_iata"),
            originCity = s("airport_origin_city"),
            originIata = s("airport_origin_code_iata"),
            originName = s("airport_origin_name"),
            destinationCity = s("airport_destination_city"),
            destinationIata = s("airport_destination_code_iata"),
            destinationName = s("airport_destination_name"),
            scheduledDeparture = l("time_scheduled_departure"),
            scheduledArrival = l("time_scheduled_arrival"),
            realDeparture = l("time_real_departure"),
            realArrival = l("time_real_arrival"),
            estimatedDeparture = l("time_estimated_departure"),
            estimatedArrival = l("time_estimated_arrival"),
            altitudeFt = i("altitude") ?: 0,
            groundSpeedKts = i("ground_speed") ?: 0,
            distanceKm = f("distance") ?: 0f,
            heading = i("heading") ?: 0,
            onGround = (i("on_ground") ?: 0) != 0,
            trackedType = s("tracked_type"),
        )
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

        emit(fetchRestHistory(auth.activeUrl(networkLocator.currentSsid.value), auth.accessToken, tempEntityId, valueRange = -50f..100f))
    }

    override fun getEntityHistory(entityId: String): Flow<List<Float>> = flow {
        if (entityId.isBlank()) { emit(emptyList()); return@flow }
        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) { emitAll(fakeRepo.getEntityHistory(entityId)); return@flow }
        emit(fetchRestHistory(auth.activeUrl(networkLocator.currentSsid.value), auth.accessToken, entityId))
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
        val relative = wsClient.getCameraStreamUrl(entityId) ?: return null
        val base = auth.activeUrl(networkLocator.currentSsid.value).trimEnd('/')
        return if (relative.startsWith("http")) relative else "$base$relative"
    }

    // ── Media player ─────────────────────────────────────────────────────────
    // Subscribes to wsClient.states for live updates. Falls back to FakeHomeRepository's
    // static demo snapshot when not authenticated (demo mode). Returns null when
    // authenticated but the entity has no state yet.
    override fun getMediaPlayer(entityId: String): Flow<HaMedia?> {
        if (entityId.isBlank()) return flowOf(null)
        return authPreferences.authState.flatMapLatest { auth ->
            if (auth.accessToken.isEmpty()) fakeRepo.getMediaPlayer(entityId)
            else wsClient.states.map { states ->
                val s = states[entityId] ?: return@map null
                buildMedia(entityId, s, auth.activeUrl(networkLocator.currentSsid.value).trimEnd('/'))
            }
        }
    }

    private fun buildMedia(entityId: String, state: HaState, baseUrl: String): HaMedia {
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

    // ── Area matching ─────────────────────────────────────────────────────────

    private fun effectiveAreaId(entity: HaEntityEntry, deviceAreaMap: Map<String, String?>): String? {
        // Blank strings from HA must be treated as null (some HA versions return "" for unset fields)
        val entityArea = entity.area_id?.takeIf { it.isNotBlank() }
        if (entityArea != null) return entityArea
        val deviceId = entity.device_id?.takeIf { it.isNotBlank() } ?: return null
        return deviceAreaMap[deviceId]?.takeIf { it.isNotBlank() }
    }

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
            val temp = overrideTemp
                ?: climateState?.attributes?.current_temperature
                ?: areaStates.firstOrNull { it.attributes.device_class == "temperature" }?.state?.toFloatOrNull()
                ?: areaStates.firstOrNull { it.entity_id.startsWith("sensor.") && "temp" in it.entity_id.lowercase() }?.state?.toFloatOrNull()
                ?: 0f
            val humidity = overrideHumidity
                ?: climateState?.attributes?.current_humidity
                ?: areaStates.firstOrNull { it.attributes.device_class == "humidity" }?.state?.toIntOrNull()
                ?: areaStates.firstOrNull { it.entity_id.startsWith("sensor.") && "humid" in it.entity_id.lowercase() }?.state?.toIntOrNull()
                ?: 0

            val (colorHex, inkHex) = AREA_PALETTE[index % AREA_PALETTE.size]
            HaRoom(
                id = area.area_id,
                name = area.name,
                icon = mdiToIconKey(area.icon),
                colorHex = colorHex,
                inkHex = inkHex,
                deviceCount = areaEntities.size,
                activeCount = activeCount,
                temp = temp,
                humidity = humidity,
                hasAlert = areaStates.any { it.state == "unavailable" },
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
        private const val FR24_TRACKED_ENTITY = "sensor.flightradar24_additional_tracked"
        private const val FR24_ADD_ENTITY = "text.flightradar24_add_to_track"
        private const val FR24_REMOVE_ENTITY = "text.flightradar24_remove_from_track"
    }
}
