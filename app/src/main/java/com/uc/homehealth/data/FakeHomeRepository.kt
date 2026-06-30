package com.uc.homehealth.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

private const val DEMO_PERSON_ID = "person.demo"

@Singleton
class FakeHomeRepository @Inject constructor() : HomeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rooms = listOf(
        HaRoom("living",   "Living Room",  "sofa",    "#E8B4D6", "#3a1a2c", 6, 3, 21.4f, 48),
        HaRoom("bedroom",  "Bedroom",      "bed",     "#9DD8A8", "#0f3a1a", 4, 1, 19.8f, 52),
        HaRoom("kitchen",  "Kitchen",      "cooking", "#F2A65E", "#3a1d0a", 5, 2, 22.6f, 41),
        HaRoom("office",   "Office",       "desk",    "#9CB6E8", "#0f1f3a", 4, 0, 20.1f, 45),
        HaRoom("bathroom", "Bathroom",     "water",   "#7DD3D8", "#0a2f30", 3, 0, 22.0f, 68),
        HaRoom("entry",    "Entry & Hall", "door",    "#C77DBA", "#2c0f25", 3, 1, 20.4f, 46,
            alerts = listOf("Front Door Lock — unreachable")),
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

    // In-memory mutable feed (seeded once) so demo swipe-to-delete / clear-all visibly
    // mutate the list, mirroring the real Room-backed feed. Resets on process restart.
    private val _notifications = MutableStateFlow(seedNotifications())

    private fun seedNotifications(): List<HaNotification> {
        val now = System.currentTimeMillis()
        return listOf(
            HaNotification(1L, "motion",  "Motion at Backyard",        "Camera detected movement",         now - 30_000L),
            HaNotification(2L, "door",    "Front Door unlocked",       "by Alex via app",                  now - 12 * 60_000L),
            HaNotification(3L, "energy",  "Solar peak",                "Producing 4.2 kW · highest today", now - 60 * 60_000L),
            HaNotification(4L, "climate", "Bedroom AC scheduled",      "Will cool to 22° at 9:30 PM",      now - 2 * 60 * 60_000L),
            HaNotification(5L, "auto",    "\"Movie night\" ran",       "Lights dimmed, blinds closed",     now - 26 * 60 * 60_000L),
            HaNotification(6L, "update",  "Firmware update",           "Bedroom switch updated to v2.4",   now - 30 * 60 * 60_000L),
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
        } + HaEntitySummary(
            entityId = DEMO_PERSON_ID,
            friendlyName = "NKC",
            domain = "person",
            areaName = "Home",
            state = "not_home",
            hasLocation = true,
        )
    )
    override fun getNotifications(): Flow<List<HaNotification>> = _notifications.asStateFlow()
    override suspend fun deleteNotification(id: Long) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }
    override suspend fun clearNotifications() {
        _notifications.value = emptyList()
    }
    override fun getAutomations(): Flow<List<HaAutomation>> = flowOf(automations)

    // ── Updates — sample set so the Updates screen is explorable offline ──────────
    // Logos are null → the screen falls back to a per-category icon. installUpdate
    // simulates HA's progress→complete flow so the install→progress→exit animation
    // is testable end-to-end without a live backend.
    private val _updates = MutableStateFlow(seedUpdates())

    private fun seedUpdates(): List<HaUpdate> = listOf(
        HaUpdate(
            entityId = "update.home_assistant_core_update",
            title = "Home Assistant Core",
            installedVersion = "2026.5.3", latestVersion = "2026.6.0",
            inProgress = false, updatePercentage = null,
            releaseSummary = "New expressive dashboard engine, faster startup, and a redesigned " +
                "automation editor. Includes several bug fixes for the recorder and templates.",
            releaseUrl = "https://www.home-assistant.io/latest-release-notes/",
            entityPictureUrl = null, skippedVersion = null,
            supportsBackup = true, category = UpdateCategory.SYSTEM,
        ),
        HaUpdate(
            entityId = "update.home_assistant_operating_system_update",
            title = "Home Assistant OS",
            installedVersion = "13.1", latestVersion = "13.2",
            inProgress = false, updatePercentage = null,
            releaseSummary = "Kernel and Docker updates, improved hardware support.",
            releaseUrl = "https://github.com/home-assistant/operating-system/releases",
            entityPictureUrl = null, skippedVersion = null,
            supportsBackup = true, category = UpdateCategory.SYSTEM,
        ),
        HaUpdate(
            entityId = "update.mushroom_update",
            title = "Mushroom",
            installedVersion = "3.1.0", latestVersion = "3.2.0",
            inProgress = false, updatePercentage = null,
            releaseSummary = "Adds a new chips card and fixes theming on Material expressive.",
            releaseUrl = "https://github.com/piitaya/lovelace-mushroom/releases",
            entityPictureUrl = null, skippedVersion = null,
            supportsBackup = false, category = UpdateCategory.HACS,
        ),
        HaUpdate(
            entityId = "update.mosquitto_broker_update",
            title = "Mosquitto broker",
            installedVersion = "6.4.0", latestVersion = "6.5.0",
            inProgress = false, updatePercentage = null,
            releaseSummary = "Security patch and TLS 1.3 support.",
            releaseUrl = null,
            entityPictureUrl = null, skippedVersion = null,
            supportsBackup = true, category = UpdateCategory.ADDON,
        ),
        HaUpdate(
            entityId = "update.bedroom_sensor_firmware",
            title = "Bedroom Sensor",
            installedVersion = "2026.4.0", latestVersion = "2026.5.1",
            inProgress = false, updatePercentage = null,
            releaseSummary = null, releaseUrl = null,
            entityPictureUrl = null, skippedVersion = null,
            supportsBackup = false, category = UpdateCategory.FIRMWARE,
        ),
    )

    override fun getUpdates(): Flow<List<HaUpdate>> = _updates.asStateFlow()

    // Staged Pulse report: a believable "mostly fine, a few things to care for" home so
    // every row state (critical, warn, info, healthy) is visible in demo mode. Score is
    // consistent with PulseAnalyzer's weights (8 + 6 + 3 + 3×2 = 23 → 77, FAIR).
    override fun getPulse(): Flow<PulseReport> = flowOf(demoPulseReport())

    private fun demoPulseReport(): PulseReport {
        val categories = listOf(
            PulseCategory(
                kind = PulseCategoryKind.DEVICES,
                summary = "1 device unreachable",
                issues = listOf(
                    PulseIssue("light.hallway_spot", "Hallway Spot", "Unreachable", PulseSeverity.CRITICAL),
                ),
            ),
            PulseCategory(
                kind = PulseCategoryKind.BATTERIES,
                summary = "2 batteries low",
                issues = listOf(
                    PulseIssue("sensor.bedroom_remote_battery", "Bedroom Remote", "8% battery", PulseSeverity.CRITICAL),
                    PulseIssue("sensor.front_door_sensor_battery", "Front Door Sensor", "15% battery", PulseSeverity.WARN),
                ),
            ),
            PulseCategory(kind = PulseCategoryKind.SENSORS, summary = "All sensors reporting"),
            PulseCategory(
                kind = PulseCategoryKind.UPDATES,
                summary = "3 updates pending",
                issues = listOf(
                    PulseIssue("update:ha_core", "Home Assistant Core", "Update available", PulseSeverity.INFO),
                    PulseIssue("update:mushroom", "HACS: Mushroom", "Update available", PulseSeverity.INFO),
                    PulseIssue("update:zigbee", "Zigbee coordinator firmware", "Update available", PulseSeverity.INFO),
                ),
            ),
            PulseCategory(kind = PulseCategoryKind.CONNECTIVITY, summary = "No drops this week"),
            PulseCategory(kind = PulseCategoryKind.SERVER, summary = "CPU 34% · RAM 41% · Disk 62%"),
        )
        val score = 77
        return PulseReport(
            score = score,
            grade = PulseAnalyzer.gradeFor(score),
            categories = categories,
            sampleSize = 142,
        )
    }

    override suspend fun installUpdate(entityId: String, backup: Boolean) {
        // Already installing? ignore re-taps.
        if (_updates.value.firstOrNull { it.entityId == entityId }?.inProgress == true) return
        scope.launch {
            // Ramp the percentage so the determinate indicator + rolling number animate,
            // then drop the entity so the card animates out of the list. Atomic update {}
            // is required — "Update all" fires several of these concurrently and a plain
            // value=read-modify-write would let them clobber each other's progress.
            for (pct in 0..100 step 10) {
                _updates.update { list ->
                    list.map { if (it.entityId == entityId) it.copy(inProgress = true, updatePercentage = pct) else it }
                }
                delay(280L)
            }
            _updates.update { list -> list.filterNot { it.entityId == entityId } }
        }
    }

    override suspend fun skipUpdate(entityId: String) {
        _updates.update { list ->
            list.map { if (it.entityId == entityId) it.copy(skippedVersion = it.latestVersion) else it }
        }
    }

    override suspend fun clearSkippedUpdate(entityId: String) {
        _updates.update { list ->
            list.map { if (it.entityId == entityId) it.copy(skippedVersion = null) else it }
        }
    }

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
    override suspend fun pressEntity(entityId: String) = Unit

    override fun getLightsForRoom(areaId: String): Flow<List<HaLight>> {
        val room = rooms.find { it.id == areaId } ?: return flowOf(emptyList())
        return flowOf(fakeLightsForRoom(room))
    }

    override fun getLight(entityId: String): Flow<HaLight?> = flowOf(
        HaLight(
            id = entityId,
            name = entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() },
            brightness = 70,
            colorHex = "#FFD9A8",
            isOn = true,
            isAvailable = true,
            supportsColor = true,
            supportsColorTemp = true,
            colorTempKelvin = 2700,
            minColorTempKelvin = 2200,
            maxColorTempKelvin = 6500,
        )
    )

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
                supportedModes = listOf("off", "heat", "cool", "auto", "dry", "fan_only"),
                tempStep = 0.5f,
                minTemp = 16f,
                maxTemp = 30f,
                fanMode = "auto",
                fanModes = listOf(
                    "auto", "night", "low", "lowMedium", "medium", "mediumHigh", "high", "powerful",
                ),
            )
        )
    }

    override fun getClimate(entityId: String): Flow<HaClimate?> = flowOf(
        HaClimate(
            id = entityId,
            name = "Thermostat",
            currentTemp = 21f,
            targetTemp = 22f,
            mode = "heat",
            action = "heating",
            supportedModes = listOf("off", "heat", "cool", "auto", "dry", "fan_only"),
            tempStep = 0.5f,
            minTemp = 16f,
            maxTemp = 30f,
            fanMode = "auto",
            fanModes = listOf("auto", "low", "medium", "high"),
        )
    )

    override suspend fun setClimateTemperature(entityId: String, temperature: Float) = Unit

    override suspend fun setClimateHvacMode(entityId: String, mode: String) = Unit

    override suspend fun setClimateFanMode(entityId: String, fanMode: String) = Unit

    override fun connectionStatus(): Flow<WsConnectionStatus> = flowOf(WsConnectionStatus.READY)

    override fun authError(): Flow<String?> = flowOf(null)

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

    // Demo person near Circus Avenue, Kolkata (matches the design mock) so the location
    // widget renders a real map in demo mode. Unknown ids resolve to null.
    override fun getPersonLocation(entityId: String): Flow<HaPersonLocation?> {
        if (entityId != DEMO_PERSON_ID) return flowOf(null)
        return flowOf(
            HaPersonLocation(
                entityId = DEMO_PERSON_ID,
                friendlyName = "NKC",
                latitude = 22.5413,
                longitude = 88.3643,
                gpsAccuracyMeters = 35,
                zone = "not_home",
                lastUpdatedEpochMs = System.currentTimeMillis() - 2 * 60 * 60 * 1000L,
            )
        )
    }

    // Seed new demo users with the sample sensors that resolve in mockEntityStates,
    // temperature first so it becomes the large featured tile. Five tiles spill onto
    // a second carousel page, showing the paging off in demo.
    override suspend fun suggestedGlanceEntityIds(): List<String> = listOf(
        "sensor.outside_temperature",
        "sensor.indoor_temperature",
        "sensor.aqi",
        "sensor.lights_on_count",
        "sensor.doorbell_count",
    )

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

    // ── Energy — demo solar card values (matches the Helios-style mock) ───────────
    // Demo mode is a fixed showcase, so each role returns its sample value regardless of
    // the (likely blank, unconfigured) entity id passed in.
    override fun getSolarProduction(entityId: String): Flow<HaEntityValue?> =
        flowOf(HaEntityValue("sensor.solar_production", "1890", "W", "Solar Production"))
    override fun getBatterySoc(entityId: String): Flow<HaEntityValue?> =
        flowOf(HaEntityValue("sensor.battery_soc", "56", "%", "Battery"))
    override fun getBatteryPower(entityId: String): Flow<HaEntityValue?> =
        flowOf(HaEntityValue("sensor.battery_power", "-420", "W", "Battery Power"))
    override fun getGridPower(entityId: String): Flow<HaEntityValue?> =
        flowOf(HaEntityValue("sensor.grid_power", "0", "W", "Grid"))

    // A clear-day solar production bell curve (W) over the last 24h, dark→peak→dark.
    override fun getEnergyHistory(entityId: String): Flow<List<Float>> {
        val points = (0 until 48).map { i ->
            val dayFraction = i / 48f                       // 0..1 across the day
            val bell = sin((dayFraction * PI)).toFloat()    // 0 at midnight, 1 at noon
            (bell * bell * 3600f).coerceAtLeast(0f)         // peak ~3.6 kW
        }
        return flowOf(points)
    }

    // Demo home — central Kolkata, matching the demo person's neighbourhood, so the
    // 3D map (Phase C) renders a real, building-dense location offline.
    override fun getHomeCoords(): Flow<HomeCoords?> = flowOf(HomeCoords(22.5413, 88.3643))

    // Demo mode already supplies role values directly; no Energy-dashboard auto-wiring.
    override fun getAutoEnergy(): Flow<AutoEnergy?> = flowOf(null)

    override fun getCameraSnapshotUrl(entityId: String): Flow<String?> = flowOf(null)
    override suspend fun getCameraStreamUrl(entityId: String): String? = null
    // Demo mode has no live server, so WebRTC is unavailable — the player just shows
    // the snapshot / error state instead of attempting a peer connection.
    override suspend fun startCameraWebRtc(entityId: String, offerSdp: String, onSignal: (WebRtcSignal) -> Unit): Int? = null
    override fun sendCameraWebRtcCandidate(entityId: String, sessionId: String, candidate: WebRtcIceCandidate) {}
    override fun stopCameraWebRtc(subscriptionId: Int) {}
    override suspend fun getCameraWebRtcConfig(entityId: String): List<WebRtcIceServer>? = null

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
            // Demo player presents as Music Assistant so the search sheet is explorable.
            isMusicAssistant = true,
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

    // ── Music Assistant — sample library so search & the Music page are explorable
    // in demo mode.
    private val demoMaLibrary = listOf(
        MaSearchItem("library://artist/1", "Nina Simone", MaMediaType.ARTIST, "", null),
        MaSearchItem("library://artist/2", "Khruangbin", MaMediaType.ARTIST, "", null),
        MaSearchItem("library://artist/3", "Bonobo", MaMediaType.ARTIST, "", null),
        MaSearchItem("library://artist/4", "Hania Rani", MaMediaType.ARTIST, "", null),
        MaSearchItem("library://album/1", "Little Girl Blue", MaMediaType.ALBUM, "Nina Simone", null),
        MaSearchItem("library://album/2", "Mordechai", MaMediaType.ALBUM, "Khruangbin", null),
        MaSearchItem("library://album/3", "Migration", MaMediaType.ALBUM, "Bonobo", null),
        MaSearchItem("library://album/4", "Esja", MaMediaType.ALBUM, "Hania Rani", null),
        MaSearchItem("library://track/1", "Feeling Good", MaMediaType.TRACK, "Nina Simone", null),
        MaSearchItem("library://track/2", "Time (You and I)", MaMediaType.TRACK, "Khruangbin", null),
        MaSearchItem("library://track/3", "Sinnerman", MaMediaType.TRACK, "Nina Simone", null),
        MaSearchItem("library://track/4", "Kerala", MaMediaType.TRACK, "Bonobo", null),
        MaSearchItem("library://track/5", "Glass", MaMediaType.TRACK, "Hania Rani", null),
        MaSearchItem("library://track/6", "Cirrus", MaMediaType.TRACK, "Bonobo", null),
        MaSearchItem("library://playlist/1", "Sunday morning", MaMediaType.PLAYLIST, "", null),
        MaSearchItem("library://playlist/2", "Focus deep work", MaMediaType.PLAYLIST, "", null),
        MaSearchItem("library://playlist/3", "Dinner jazz", MaMediaType.PLAYLIST, "", null),
        MaSearchItem("library://radio/1", "Lofi Beats FM", MaMediaType.RADIO, "", null),
        MaSearchItem("library://radio/2", "Jazz24", MaMediaType.RADIO, "", null),
    )

    override suspend fun searchMusicAssistant(
        entityId: String,
        query: String,
        mediaType: MaMediaType?,
        libraryOnly: Boolean,
    ): MaSearchResults {
        kotlinx.coroutines.delay(600) // let the sheet's loading state show
        val hits = demoMaLibrary.filter { item ->
            (mediaType == null || item.mediaType == mediaType) &&
                (item.name.contains(query, ignoreCase = true) ||
                    item.subtitle.contains(query, ignoreCase = true))
        }
        return MaSearchResults(
            artists = hits.filter { it.mediaType == MaMediaType.ARTIST },
            albums = hits.filter { it.mediaType == MaMediaType.ALBUM },
            tracks = hits.filter { it.mediaType == MaMediaType.TRACK },
            playlists = hits.filter { it.mediaType == MaMediaType.PLAYLIST },
            radio = hits.filter { it.mediaType == MaMediaType.RADIO },
        )
    }

    override suspend fun playMusicAssistantMedia(entityId: String, item: MaSearchItem, mode: MaEnqueueMode) = Unit

    // Three sample players so the Music page's rail, hero and transfer flow are
    // all explorable in demo mode.
    private fun demoMaPlayer(entityId: String, name: String, title: String, playing: Boolean) = HaMedia(
        entityId = entityId,
        friendlyName = name,
        title = title,
        source = "Music Assistant",
        isPlaying = playing,
        isOff = false,
        progress = if (playing) 0.42f else 0f,
        elapsedLabel = if (playing) "1:58" else "0:00",
        remainingLabel = if (playing) "-2:44" else "-0:00",
        volume = 0.6f,
        shuffleOn = false,
        repeatMode = MediaRepeatMode.OFF,
        entityPictureUrl = null,
        isMusicAssistant = true,
    )

    override fun getMusicAssistantPlayers(): Flow<List<HaMedia>> = flowOf(
        listOf(
            demoMaPlayer("media_player.living_room_speaker", "Living room speaker", "Time (You and I)", playing = true),
            demoMaPlayer("media_player.kitchen_display", "Kitchen display", "Nothing playing", playing = false),
            demoMaPlayer("media_player.bedroom_speaker", "Bedroom speaker", "Nothing playing", playing = false),
        )
    )

    override suspend fun getMaQueue(entityId: String): MaQueue? = MaQueue(
        currentTitle = "Time (You and I)",
        currentArtist = "Khruangbin",
        nextTitle = "Feeling Good",
        nextArtist = "Nina Simone",
        itemCount = 14,
        currentIndex = 3,
    )

    override suspend fun getMaLibrary(
        entityId: String,
        mediaType: MaMediaType,
        favoritesOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<MaSearchItem> {
        kotlinx.coroutines.delay(400) // let the page's loading state show
        val ofType = demoMaLibrary.filter { it.mediaType == mediaType }
        // Demo "favorites" = the first half of each type's list.
        val filtered = if (favoritesOnly) ofType.take((ofType.size + 1) / 2) else ofType
        return filtered.drop(offset).take(limit)
    }

    override suspend fun transferMaQueue(fromEntityId: String, toEntityId: String) = Unit

    // ── Text-to-speech — sample engines/voices so both screens are explorable in demo.
    override fun getTtsTarget(entityId: String): Flow<TtsTarget> = flowOf(
        TtsTarget(
            entityId = entityId.ifBlank { "media_player.bathroom_speaker" },
            friendlyName = "Bathroom speaker",
            isEcho = false,
        )
    )

    override suspend fun getTtsEngines(): List<HaTtsEngine> = listOf(
        HaTtsEngine("tts.google_translate_en_com", "Google Translate", listOf("en-US", "en-GB", "es-ES", "fr-FR")),
        HaTtsEngine("tts.piper", "Piper", listOf("en-US", "en-GB")),
        HaTtsEngine("tts.elevenlabs", "ElevenLabs", listOf("en-US")),
    )

    override suspend fun getTtsVoices(engineId: String, language: String): List<HaTtsVoice> = when {
        "elevenlabs" in engineId -> listOf(
            HaTtsVoice("rachel", "Rachel"), HaTtsVoice("adam", "Adam"),
            HaTtsVoice("bella", "Bella"), HaTtsVoice("antoni", "Antoni"),
        )
        "piper" in engineId -> listOf(
            HaTtsVoice("en_US-amy-low", "Amy"), HaTtsVoice("en_US-danny-low", "Danny"),
        )
        else -> emptyList()
    }

    override suspend fun sendTts(
        mediaPlayerEntityId: String,
        message: String,
        announce: Boolean,
        engineId: String?,
        voiceId: String?,
        language: String?,
    ) = Unit
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
