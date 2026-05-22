package com.uc.homehealth.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class FakeHomeRepository @Inject constructor() : HomeRepository {

    private val rooms = listOf(
        HaRoom("living",   "Living Room",  "sofa",    "#E8B4D6", "#3a1a2c", 6, 3, 21.4f, 48, false),
        HaRoom("bedroom",  "Bedroom",      "bed",     "#9DD8A8", "#0f3a1a", 4, 1, 19.8f, 52, false),
        HaRoom("kitchen",  "Kitchen",      "cooking", "#F2A65E", "#3a1d0a", 5, 2, 22.6f, 41, false),
        HaRoom("office",   "Office",       "desk",    "#9CB6E8", "#0f1f3a", 4, 0, 20.1f, 45, false),
        HaRoom("bathroom", "Bathroom",     "water",   "#7DD3D8", "#0a2f30", 3, 0, 22.0f, 68, false),
        HaRoom("entry",    "Entry & Hall", "door",    "#C77DBA", "#2c0f25", 3, 1, 20.4f, 46, true),
    )

    private val scenes = listOf(
        HaScene("morning", "Morning", "☕", isActive = true),
        HaScene("focus",   "Focus",   "🎯", isActive = false),
        HaScene("movie",   "Movie",   "🎬", isActive = false),
        HaScene("sleep",   "Sleep",   "🌙", isActive = false),
        HaScene("away",    "Away",    "✈", isActive = false),
    )

    private val favorites = listOf(
        HaFavorite("thermostat",  "climate", "Thermostat",  21f,  "°",  "Heating",     true,  "Living Room"),
        HaFavorite("main-lights", "light",   "Main Lights", 72f,  "%",  "Warm white",  true,  "Living Room"),
        HaFavorite("speakers",    "media",   "Speakers",    null, null, "Now playing", true,  "Whole home"),
        HaFavorite("frontdoor",   "lock",    "Front Door",  null, null, "Locked",      true,  "Entry"),
        HaFavorite("energy",      "energy",  "Energy",      1.4f, "kW", "Solar +0.6",  true,  "Live"),
        HaFavorite("aircon",      "climate", "AC Bedroom",  23f,  "°",  "Cool",        false, "Bedroom"),
    )

    // In-memory mutable list so the demo "add flight" flow visually appends in dev mode.
    private val _flights = MutableStateFlow(
        listOf(
            HaFlight(
                id = "3fbff54e",
                flightNumber = "DL113",
                callsign = "DAL113",
                aircraftRegistration = "N807NW",
                aircraftPhotoSmall = "https://cdn.jetphotos.com/200/5/646441_1776912092_tb.jpg",
                aircraftPhotoMedium = "https://cdn.jetphotos.com/400/5/646441_1776912092.jpg",
                aircraftPhotoLarge = "https://cdn.jetphotos.com/640cb/5/646441_1776912092.jpg",
                aircraftModel = "Airbus A330-323",
                airline = "Delta Air Lines",
                airlineIata = "DL",
                originCity = "Rome",
                originIata = "FCO",
                originName = "Rome Leonardo da Vinci Fiumicino Airport",
                destinationCity = "Boston",
                destinationIata = "BOS",
                destinationName = "Boston Logan International Airport",
                scheduledDeparture = 1779102600L,
                scheduledArrival = 1779136440L,
                realDeparture = 1779104268L,
                realArrival = null,
                estimatedDeparture = null,
                estimatedArrival = 1779135232L,
                altitudeFt = 36025,
                groundSpeedKts = 416,
                distanceKm = 8216.6f,
                heading = 280,
                onGround = false,
                trackedType = "live",
            ),
        )
    )

    private val notifications: List<HaNotification> get() {
        val now = System.currentTimeMillis()
        return listOf(
            HaNotification(1, "motion",  "Motion at Backyard",        "Camera detected movement",         now - 30_000L),
            HaNotification(2, "door",    "Front Door unlocked",       "by Alex via app",                  now - 12 * 60_000L),
            HaNotification(3, "energy",  "Solar peak",                "Producing 4.2 kW · highest today", now - 60 * 60_000L),
            HaNotification(4, "climate", "Bedroom AC scheduled",      "Will cool to 22° at 9:30 PM",      now - 2 * 60 * 60_000L),
            HaNotification(5, "auto",    "\"Movie night\" ran",       "Lights dimmed, blinds closed",     now - 26 * 60 * 60_000L),
            HaNotification(6, "update",  "Firmware update",           "Bedroom switch updated to v2.4",   now - 30 * 60 * 60_000L),
        )
    }

    private val automations = listOf(
        HaAutomation("automation.notify_flight_landed",   "Notify when flight lands",          true),
        HaAutomation("automation.notify_flight_delayed",  "Notify on flight delay",            true),
        HaAutomation("automation.dim_lights_for_landing", "Dim lights when flight is near",    true),
        HaAutomation("automation.turn_on_arrival_mode",   "Activate Arrival mode on landing",  false),
        HaAutomation("automation.notify_gate_change",     "Alert on gate or terminal change",  true),
        HaAutomation("automation.unlock_door_on_arrival", "Unlock front door on arrival",      false),
        HaAutomation("automation.send_eta_to_family",     "Send ETA to family group",          true),
        HaAutomation("automation.log_flight_history",     "Log flight to history tracker",     true),
    )

    override fun getRooms(): Flow<List<HaRoom>> = flowOf(rooms)
    override fun getAllRooms(): Flow<List<HaRoom>> = flowOf(rooms)
    override fun getScenes(): Flow<List<HaScene>> = flowOf(scenes)
    override fun getAllScenes(): Flow<List<HaScene>> = flowOf(scenes)
    override fun getFavorites(): Flow<List<HaFavorite>> = flowOf(favorites)
    override fun getAllEntities(): Flow<List<HaEntitySummary>> = flowOf(
        favorites.map { f ->
            HaEntitySummary(
                entityId = "${f.kind}.${f.id}",
                friendlyName = f.name,
                domain = f.kind,
                areaName = f.room,
                state = f.state,
            )
        }
    )
    override fun getNotifications(): Flow<List<HaNotification>> = flowOf(notifications)
    override fun getAutomations(): Flow<List<HaAutomation>> = flowOf(automations)

    override fun getTrackedFlights(): Flow<List<HaFlight>> = _flights.asStateFlow()
    override fun isFlightRadar24Available(): Flow<Boolean> = flowOf(true)
    override suspend fun addTrackedFlight(query: String) {
        // Dev mode: pretend the FR24 integration responded by appending a placeholder
        // flight so the UI flow is testable end-to-end without a live HA backend.
        val q = query.trim().uppercase()
        if (q.isEmpty()) return
        if (_flights.value.any { it.flightNumber.equals(q, ignoreCase = true) }) return
        _flights.value = _flights.value + buildFakeFlight(q)
    }

    override suspend fun removeTrackedFlight(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        _flights.value = _flights.value.filterNot { it.flightNumber.equals(q, ignoreCase = true) }
    }

    private fun buildFakeFlight(q: String): HaFlight {
        return HaFlight(
            id = "fake-${System.currentTimeMillis()}",
            flightNumber = q,
            callsign = null,
            aircraftRegistration = null,
            aircraftPhotoSmall = null,
            aircraftPhotoMedium = null,
            aircraftPhotoLarge = null,
            aircraftModel = null,
            airline = null,
            airlineIata = q.takeWhile { it.isLetter() }.take(2),
            originCity = null,
            originIata = null,
            originName = null,
            destinationCity = null,
            destinationIata = null,
            destinationName = null,
            scheduledDeparture = null,
            scheduledArrival = null,
            realDeparture = null,
            realArrival = null,
            estimatedDeparture = null,
            estimatedArrival = null,
            altitudeFt = 0,
            groundSpeedKts = 0,
            distanceKm = 0f,
            heading = 0,
            onGround = false,
            trackedType = "schedule",
        )
    }

    override suspend fun runScene(sceneId: String) = Unit
    override suspend fun toggleEntity(entityId: String) = Unit

    override fun getLightsForRoom(areaId: String): Flow<List<HaLight>> {
        val room = rooms.find { it.id == areaId } ?: return flowOf(emptyList())
        return flowOf(fakeLightsForRoom(room))
    }

    override suspend fun toggleLight(entityId: String, isOn: Boolean) = Unit

    override suspend fun setLightBrightness(entityId: String, brightness: Int) = Unit

    override suspend fun setLightColor(entityId: String, r: Int, g: Int, b: Int) = Unit

    override suspend fun setLightColorTemp(entityId: String, kelvin: Int) = Unit

    override fun getClimateForRoom(areaId: String): Flow<HaClimate?> {
        val room = rooms.find { it.id == areaId } ?: return flowOf(null)
        return flowOf(
            HaClimate(
                id = "${areaId}_climate",
                name = "Thermostat",
                currentTemp = room.temp,
                targetTemp = 22f,
                mode = "heat",
                action = "heating",
                supportedModes = listOf("off", "heat", "cool", "auto"),
                tempStep = 0.5f,
                minTemp = 16f,
                maxTemp = 30f,
            )
        )
    }

    override suspend fun setClimateTemperature(entityId: String, temperature: Float) = Unit

    override suspend fun setClimateHvacMode(entityId: String, mode: String) = Unit

    override fun connectionStatus(): Flow<WsConnectionStatus> = flowOf(WsConnectionStatus.READY)

    override fun reconnectNow() = Unit

    // Small mock map so the glance subtitle has something plausible to show in dev / offline.
    private val mockEntityStates = mapOf(
        "sensor.outside_temperature" to HaEntityValue("sensor.outside_temperature", "19", "°", "Outside"),
        "sensor.indoor_temperature"  to HaEntityValue("sensor.indoor_temperature",  "21", "°", "Indoor"),
        "sensor.doorbell_count"      to HaEntityValue("sensor.doorbell_count",      "3",  "🔔", "Doorbell"),
        "sensor.lights_on_count"     to HaEntityValue("sensor.lights_on_count",     "6 💡", "on", "Lights"),
        "sensor.aqi"                 to HaEntityValue("sensor.aqi",                 "32 🟢", "μg/m³", "AQI"),
    )

    override fun getEntityState(entityId: String): Flow<HaEntityValue?> {
        if (entityId.isBlank()) return flowOf(null)
        return flowOf(mockEntityStates[entityId])
    }

    override fun getTempHistory(areaId: String): Flow<List<Float>> {
        val base = rooms.find { it.id == areaId }?.temp ?: 21f
        // Generate a realistic-looking temperature curve for the last 24h
        val points = (0 until 48).map { i ->
            (base + sin(i * PI / 12).toFloat() * 1.5f + sin(i * PI / 4).toFloat() * 0.4f).toFloat()
        }
        return flowOf(points)
    }

    override fun getEntityHistory(entityId: String): Flow<List<Float>> {
        val base = entityId.hashCode().toFloat().mod(20f) + 10f
        val points = (0 until 48).map { i ->
            (base + sin(i * PI / 14).toFloat() * 2.2f + sin(i * PI / 5).toFloat() * 0.6f).toFloat()
        }
        return flowOf(points)
    }

    override fun getCameraSnapshotUrl(entityId: String): Flow<String?> = flowOf(null)
    override suspend fun getCameraStreamUrl(entityId: String): String? = null

    // ── Media player — demo snapshot (matches the handoff mock) ──────────────
    override fun getMediaPlayer(entityId: String): Flow<HaMedia?> = flowOf(
        HaMedia(
            entityId = entityId.ifBlank { "media_player.bathroom_speaker" },
            friendlyName = "Bathroom speaker",
            title = "silent.mp3",
            source = "local · 192.168.1.10",
            isPlaying = true,
            isOff = false,
            progress = 0.42f,
            elapsedLabel = "0:00",
            remainingLabel = "-0:01",
            volume = 0.75f,
            shuffleOn = true,
            repeatMode = MediaRepeatMode.OFF,
            entityPictureUrl = null,
        )
    )

    override suspend fun mediaPlayPause(entityId: String) = Unit
    override suspend fun mediaSkipNext(entityId: String) = Unit
    override suspend fun mediaSkipPrev(entityId: String) = Unit
    override suspend fun mediaSetVolume(entityId: String, volume: Float) = Unit
    override suspend fun mediaSetShuffle(entityId: String, on: Boolean) = Unit
    override suspend fun mediaSetRepeat(entityId: String, mode: MediaRepeatMode) = Unit
    override suspend fun mediaSeek(entityId: String, progress: Float) = Unit
    override suspend fun mediaTurnOff(entityId: String) = Unit
}

private data class FakeLightDef(val name: String, val hex: String, val supportsColor: Boolean, val supportsColorTemp: Boolean, val kelvin: Int?)

fun fakeLightsForRoom(room: HaRoom): List<HaLight> {
    val defs = listOf(
        FakeLightDef("Main Light",   "#FFD9A8", supportsColor = false, supportsColorTemp = true,  kelvin = 2700),
        FakeLightDef("Accent Strip", "#FF9B6A", supportsColor = true,  supportsColorTemp = true,  kelvin = 3000),
        FakeLightDef("Floor Lamp",   "#FFE8C0", supportsColor = true,  supportsColorTemp = true,  kelvin = 3500),
        FakeLightDef("Ceiling",      "#C8E8FF", supportsColor = false, supportsColorTemp = false, kelvin = null),
    )
    return defs.take(room.deviceCount.coerceAtMost(4)).mapIndexed { i, d ->
        HaLight(
            id = "${room.id}_light_$i",
            name = d.name,
            brightness = if (i < room.activeCount) 70 else 0,
            colorHex = d.hex,
            isOn = i < room.activeCount,
            supportsColor = d.supportsColor,
            supportsColorTemp = d.supportsColorTemp,
            colorTempKelvin = d.kelvin,
        )
    }
}
