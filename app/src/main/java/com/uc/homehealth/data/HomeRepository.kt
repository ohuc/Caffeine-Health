package com.uc.homehealth.data

import kotlinx.coroutines.flow.Flow

// Lightweight value object for arbitrary entity lookups (used by the glance subtitle).
data class HaEntityValue(
    val entityId: String,
    val state: String,
    val unit: String? = null,
    val friendlyName: String? = null,
) {
    // Numeric view of [state] (W, %, kWh, …); null for unavailable/non-numeric states.
    val numeric: Float? get() = state.toFloatOrNull()
}

// Resolved home location for the Energy card's 3D map + solar position math. Resolution
// order is user override → HA core config (get_config) → zone.home → null.
data class HomeCoords(val latitude: Double, val longitude: Double)

// Live energy values auto-wired from HA's Energy dashboard config (Helios-style): used as
// the fallback for any role the user hasn't manually picked a sensor for. Null fields mean
// the dashboard has no source of that kind.
data class AutoEnergy(
    val solar: HaEntityValue? = null,
    val batterySoc: HaEntityValue? = null,
    val batteryPower: HaEntityValue? = null,
    val grid: HaEntityValue? = null,
)

// A person.* (or device_tracker.*) entity resolved into map coordinates + presence.
// [latitude]/[longitude] are non-null ONLY when the entity's active source is GPS-based
// (the HA app, a GPS tracker, …). Router/ping presence trackers report a zone state with
// no coordinates — then [hasMap] is false and the widget shows presence text but no map,
// mirroring how Home Assistant itself only draws a marker when coordinates exist.
data class HaPersonLocation(
    val entityId: String,
    val friendlyName: String,
    val latitude: Double?,
    val longitude: Double?,
    val gpsAccuracyMeters: Int?,
    // Entity state — a zone name ("home"), "not_home", or "unknown".
    val zone: String,
    // Epoch millis of the last state change, for the "x ago" line. Null if unavailable.
    val lastUpdatedEpochMs: Long?,
) {
    val hasMap: Boolean get() = latitude != null && longitude != null
}

// One user-curated "at a glance" tile, resolved from a HA entity's live state.
data class GlanceTile(
    val entityId: String,
    val name: String,
    val state: String,
    val unit: String?,
    val domain: String,
)

// Lightweight entity descriptor for the favorites picker.
data class HaEntitySummary(
    val entityId: String,
    val friendlyName: String,
    val domain: String,
    val areaName: String,
    val state: String,
    // For `camera.*` entities: whether HA reports the STREAM feature (supported_features
    // bit 2). Snapshot-only entities (e.g. Reolink `*_snapshots_*`) report it cleared and
    // can't be streamed via WebRTC or HLS — the picker flags these and the detail sheet
    // shows the still image instead of a futile connect.
    val supportsStream: Boolean = false,
    // For `person.*` / `device_tracker.*` entities: whether the entity currently reports
    // latitude+longitude. The location picker prefers/flags these so the user doesn't add
    // a presence-only tracker that can never draw a map.
    val hasLocation: Boolean = false,
)

interface HomeRepository {
    fun getRooms(): Flow<List<HaRoom>>
    // All HA areas — populates the room picker (includes rooms the user has hidden).
    fun getAllRooms(): Flow<List<HaRoom>>
    // User-curated quick scenes (subset of getAllScenes, in user-chosen order).
    fun getScenes(): Flow<List<HaScene>>
    // All HA scenes available — populates the scene picker.
    fun getAllScenes(): Flow<List<HaScene>>
    // User-curated favorites resolved against live state.
    fun getFavorites(): Flow<List<HaFavorite>>
    // All HA entities — populates the favorite picker.
    fun getAllEntities(): Flow<List<HaEntitySummary>>
    // All HA automation entities — populates the flight automation picker.
    fun getAutomations(): Flow<List<HaAutomation>>
    fun getNotifications(): Flow<List<HaNotification>>
    // Remove one recorded activity event by id — backs swipe-to-delete in the Activity feed.
    suspend fun deleteNotification(id: Long)
    // Wipe all activity history stored on this device. Does NOT touch Home Assistant.
    suspend fun clearNotifications()
    fun getLightsForRoom(areaId: String): Flow<List<HaLight>>
    // Live state for a single light entity_id, regardless of area. Used when a glance tile
    // is tapped to open that light's controls. Null when unknown / not authenticated.
    fun getLight(entityId: String): Flow<HaLight?>
    suspend fun toggleLight(entityId: String, isOn: Boolean)
    suspend fun setLightBrightness(entityId: String, brightness: Int)
    suspend fun setLightColor(entityId: String, r: Int, g: Int, b: Int)
    suspend fun setLightColorTemp(entityId: String, kelvin: Int)
    fun getClimateForRoom(areaId: String): Flow<HaClimate?>
    // Live state for a single climate.* entity_id, regardless of its area. Used by
    // user-added climate widgets. Returns null when the entity is unknown / not authed.
    fun getClimate(entityId: String): Flow<HaClimate?>
    suspend fun setClimateTemperature(entityId: String, temperature: Float)
    suspend fun setClimateHvacMode(entityId: String, mode: String)
    suspend fun setClimateFanMode(entityId: String, fanMode: String)
    fun getTempHistory(areaId: String): Flow<List<Float>>
    // Numeric history (last 24h) for any entity_id. Returns empty for non-numeric
    // entities or when not authenticated. Used by user-added sensor graph widgets.
    fun getEntityHistory(entityId: String): Flow<List<Float>>
    fun connectionStatus(): Flow<WsConnectionStatus>
    // HA's reason string for the last rejected authentication (from the WS auth_invalid
    // message), e.g. a plain bad token vs a user restricted to local-network logins.
    // Null when the connection isn't in AUTH_INVALID. Shown on the Settings card.
    fun authError(): Flow<String?>
    // User-initiated reconnect — drops the existing WS and opens a new one immediately,
    // bypassing the 10s backoff. No-op when not authenticated or when using the fake repo.
    fun reconnectNow()
    // Tracked flights from sensor.flightradar24_additional_tracked.flights[].
    fun getTrackedFlights(): Flow<List<HaFlight>>
    // True when sensor.flightradar24_additional_tracked is present in HA states
    // (i.e. the FlightRadar24 integration is installed). Emits true during demo
    // mode and while initial state load is in flight, so the UI doesn't flash
    // the install prompt before data arrives.
    fun isFlightRadar24Available(): Flow<Boolean>
    // Writes `query` to text.flightradar24_add_to_track; the FR24 integration
    // picks it up and starts tracking. `query` is the flight number (e.g. "DL113").
    suspend fun addTrackedFlight(query: String)
    // Writes `query` to text.flightradar24_remove_from_track; the FR24 integration
    // removes the matching flight on its next refresh.
    suspend fun removeTrackedFlight(query: String)
    // Activate a scene by entity_id (scene.morning etc.)
    suspend fun runScene(sceneId: String)
    // Toggle any togglable entity (light/switch/lock/fan/etc.). No-op for sensors.
    suspend fun toggleEntity(entityId: String)
    // Momentary "press" on a pressable entity — button/input_button get `press`,
    // switch/script/scene get `turn_on`. Used by camera PTZ direction arrows.
    // No-op for other domains and when not authenticated.
    suspend fun pressEntity(entityId: String)
    // Resolve any entity_id to its current value. Returns null if the id is blank
    // or no state is available. Emits updates when the underlying state changes.
    fun getEntityState(entityId: String): Flow<HaEntityValue?>
    // Resolve a person.*/device_tracker.* entity into coordinates + presence for the
    // location widget. Returns null only when the id is blank or no state exists;
    // a non-null result with hasMap == false means "present, but no GPS coordinates".
    fun getPersonLocation(entityId: String): Flow<HaPersonLocation?>
    // Sensible default "at a glance" entity ids for a brand-new user, used to seed
    // the section on first launch so it isn't empty. Demo mode returns its sample
    // sensors; a real connection picks a handful of numeric sensors (temperature,
    // humidity, air quality, …). May be empty if nothing suitable is available.
    suspend fun suggestedGlanceEntityIds(): List<String>

    // ── Media player ───────────────────────────────────────────────────────────
    // Resolves a media_player entity into the shape the Hero card consumes.
    // Returns a static demo snapshot when not authenticated, so the card has
    // something to render in demo mode. Emits live updates over WebSocket when
    // connected. Note: progress is sampled at parse time — the UI ticks
    // forward locally while playing to avoid resubscribing every second.
    fun getMediaPlayer(entityId: String): Flow<HaMedia?>
    suspend fun mediaPlayPause(entityId: String)
    suspend fun mediaSkipNext(entityId: String)
    suspend fun mediaSkipPrev(entityId: String)
    suspend fun mediaSetVolume(entityId: String, volume: Float)
    suspend fun mediaSetShuffle(entityId: String, on: Boolean)
    suspend fun mediaSetRepeat(entityId: String, mode: MediaRepeatMode)
    suspend fun mediaSeek(entityId: String, progress: Float)
    suspend fun mediaTurnOff(entityId: String)

    // ── Music Assistant ──────────────────────────────────────────────────────
    // Available only for players whose registry platform is "music_assistant"
    // (HaMedia.isMusicAssistant). Search runs through the MA integration's
    // `music_assistant.search` service (response-returning); the config entry id is
    // derived from the player's own registry entry, so no extra setup is needed.
    // [mediaType] null = search all kinds. Demo mode returns a sample library.
    suspend fun searchMusicAssistant(
        entityId: String,
        query: String,
        mediaType: MaMediaType? = null,
        libraryOnly: Boolean = false,
    ): MaSearchResults
    // Play / enqueue a search hit on the player via `music_assistant.play_media`.
    suspend fun playMusicAssistantMedia(entityId: String, item: MaSearchItem, mode: MaEnqueueMode)
    // All Music Assistant media players, shaped for the Music page's player rail
    // (live now-playing state per player). Demo mode emits sample players.
    fun getMusicAssistantPlayers(): Flow<List<HaMedia>>
    // One-shot queue snapshot for an MA player (`music_assistant.get_queue`).
    // Null when unavailable (older integration, not an MA player, offline).
    suspend fun getMaQueue(entityId: String): MaQueue?
    // Page through the MA library (`music_assistant.get_library`) — one media type
    // per call, optional favorites filter. Returns [] on failure.
    suspend fun getMaLibrary(
        entityId: String,
        mediaType: MaMediaType,
        favoritesOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<MaSearchItem>
    // Move the active queue from one MA player to another (`music_assistant.transfer_queue`).
    suspend fun transferMaQueue(fromEntityId: String, toEntityId: String)

    // ── Text-to-speech ───────────────────────────────────────────────────────
    // Resolve how a media_player accepts spoken text. Reactive so the composer
    // updates if the entity registry loads after the sheet opens. Emits a non-Echo
    // STANDARD target in demo mode. [TtsTarget.isEcho] tells the UI to show the
    // Speak/Announce toggle (Amazon's own voice) instead of the TTS-engine path.
    fun getTtsTarget(entityId: String): Flow<TtsTarget>
    // Installed TTS engines (`tts.*`), for the Settings voice picker + inline override.
    // Empty when not authenticated (real) — demo returns a small sample set.
    suspend fun getTtsEngines(): List<HaTtsEngine>
    // Voices an engine offers for a language. Empty for engines without per-voice choice.
    suspend fun getTtsVoices(engineId: String, language: String): List<HaTtsVoice>
    // Speak [message] on [mediaPlayerEntityId]. For Echo devices, [announce] picks the
    // chime+announce flow over plain speak (engine/voice are ignored — Amazon's voice).
    // For standard players, [engineId]/[voiceId]/[language] override the saved TTS
    // defaults when non-null; otherwise the saved defaults are used. No-op in demo.
    suspend fun sendTts(
        mediaPlayerEntityId: String,
        message: String,
        announce: Boolean = false,
        engineId: String? = null,
        voiceId: String? = null,
        language: String? = null,
    )

    // ── Camera ─────────────────────────────────────────────────────────────────
    // Resolve a snapshot URL for a `camera.*` entity. Uses the entity's rotating
    // `access_token` attribute (query param), so the URL is publicly fetchable —
    // no auth header needed. Returns null when offline or when the entity has no
    // access_token (e.g. integration not yet ready).
    fun getCameraSnapshotUrl(entityId: String): Flow<String?>
    // One-shot HLS stream URL for the camera detail sheet. Path includes a signed
    // token, so the playlist/segments can be fetched without auth headers. Returns
    // null on failure or when no auth. Used as the fallback when WebRTC is unavailable.
    suspend fun getCameraStreamUrl(entityId: String): String?

    // ── Camera WebRTC (real mode only) ──────────────────────────────────────────
    // Low-latency live streaming for the detail sheet. `startCameraWebRtc` sends our
    // SDP offer and streams answer/candidate/session/error back via [onSignal];
    // it returns the subscription id (feed it to `stopCameraWebRtc` to tear down) or
    // null when unsupported / in demo / not authed — the player then falls back to
    // the HLS path above. `sendCameraWebRtcCandidate` posts our local ICE candidates.
    suspend fun startCameraWebRtc(entityId: String, offerSdp: String, onSignal: (WebRtcSignal) -> Unit): Int?
    fun sendCameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate)
    fun stopCameraWebRtc(subscriptionId: Int)
    // HA-provided STUN/TURN servers for the local PeerConnection. Lets the player use the
    // server's TURN relay (Nabu Casa / configured) so WebRTC can traverse a remote link,
    // instead of a bare public STUN that only works on the LAN. Null in demo / on failure.
    suspend fun getCameraWebRtcConfig(entityId: String): List<WebRtcIceServer>?

    // ── Updates ──────────────────────────────────────────────────────────────────
    // All `update.*` entities that currently have an update available (state `on`),
    // resolved + classified (System / HACS / Add-ons / Firmware). Emits live as HA
    // reports install progress and completion, so installed cards drop off the list.
    // Demo mode returns a sample set with a simulated install flow.
    fun getUpdates(): Flow<List<HaUpdate>>
    // Install the update for [entityId] (calls `update.install`). When [backup] is true
    // and the entity supports it, HA snapshots before updating (`backup: true`).
    suspend fun installUpdate(entityId: String, backup: Boolean)
    // Mark the current latest version as skipped (`update.skip`) so it stops nagging.
    suspend fun skipUpdate(entityId: String)
    // Clear a previously skipped version (`update.clear_skipped`).
    suspend fun clearSkippedUpdate(entityId: String)

    // ── Energy (Helios-inspired solar card) ──────────────────────────────────────
    // Live values for the energy chips. Each resolves a configured sensor entity_id
    // to its current value; null when the id is blank or no state is available.
    // Power sensors are signed where HA reports them so (e.g.) grid import vs export
    // and battery charge vs discharge can be distinguished by the sign of `numeric`.
    fun getSolarProduction(entityId: String): Flow<HaEntityValue?>
    fun getBatterySoc(entityId: String): Flow<HaEntityValue?>
    fun getBatteryPower(entityId: String): Flow<HaEntityValue?>
    fun getGridPower(entityId: String): Flow<HaEntityValue?>
    // Numeric history (last 24h) for any energy sensor — feeds the production curve.
    // Returns empty for non-numeric entities or when not authenticated.
    fun getEnergyHistory(entityId: String): Flow<List<Float>>
    // The home's coordinates for the 3D map + solar math, resolved from (in order)
    // the user override, HA core config, then zone.home. Null until resolvable.
    fun getHomeCoords(): Flow<HomeCoords?>
    // Live solar/battery/grid values resolved automatically from the HA Energy dashboard's
    // own wiring (`energy/get_prefs`), like the Helios card: live power sensors are read
    // directly; cumulative kWh meters are differentiated into watts. Emits null when not
    // authenticated or the Energy dashboard isn't configured.
    fun getAutoEnergy(): Flow<AutoEnergy?>

    // ── Pulse (home health report) ───────────────────────────────────────────────
    // Aggregated health of the smart home itself: low batteries, unreachable devices,
    // silent sensors, pending updates, connection drops, and server vitals, rolled up
    // into one 0–100 score (see PulseAnalyzer). Demo returns a staged sample report.
    fun getPulse(): Flow<PulseReport>
}
