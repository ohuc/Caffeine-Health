package com.uc.homehealth.data

import kotlinx.coroutines.flow.Flow

// Lightweight value object for arbitrary entity lookups (used by the glance subtitle).
data class HaEntityValue(
    val entityId: String,
    val state: String,
    val unit: String? = null,
    val friendlyName: String? = null,
)

// Lightweight entity descriptor for the favorites picker.
data class HaEntitySummary(
    val entityId: String,
    val friendlyName: String,
    val domain: String,
    val areaName: String,
    val state: String,
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
    fun getLightsForRoom(areaId: String): Flow<List<HaLight>>
    suspend fun toggleLight(entityId: String, isOn: Boolean)
    suspend fun setLightBrightness(entityId: String, brightness: Int)
    suspend fun setLightColor(entityId: String, r: Int, g: Int, b: Int)
    suspend fun setLightColorTemp(entityId: String, kelvin: Int)
    fun getClimateForRoom(areaId: String): Flow<HaClimate?>
    suspend fun setClimateTemperature(entityId: String, temperature: Float)
    suspend fun setClimateHvacMode(entityId: String, mode: String)
    fun getTempHistory(areaId: String): Flow<List<Float>>
    // Numeric history (last 24h) for any entity_id. Returns empty for non-numeric
    // entities or when not authenticated. Used by user-added sensor graph widgets.
    fun getEntityHistory(entityId: String): Flow<List<Float>>
    fun connectionStatus(): Flow<WsConnectionStatus>
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
    // Resolve any entity_id to its current value. Returns null if the id is blank
    // or no state is available. Emits updates when the underlying state changes.
    fun getEntityState(entityId: String): Flow<HaEntityValue?>

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

    // ── Camera ─────────────────────────────────────────────────────────────────
    // Resolve a snapshot URL for a `camera.*` entity. Uses the entity's rotating
    // `access_token` attribute (query param), so the URL is publicly fetchable —
    // no auth header needed. Returns null when offline or when the entity has no
    // access_token (e.g. integration not yet ready).
    fun getCameraSnapshotUrl(entityId: String): Flow<String?>
    // One-shot HLS stream URL for the camera detail sheet. Path includes a signed
    // token, so the playlist/segments can be fetched without auth headers. Returns
    // null on failure or when no auth.
    suspend fun getCameraStreamUrl(entityId: String): String?
}
