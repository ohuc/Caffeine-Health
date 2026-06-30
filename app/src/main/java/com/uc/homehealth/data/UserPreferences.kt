package com.uc.homehealth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// User-facing personalization for the dashboard greeting + "at a glance" subtitle.
// Shares the same DataStore as AuthPreferences (different key namespace, no collisions).

data class GlanceConfig(
    val userName: String = "",
    val entityOutsideTemp: String = "",
    val entityInsideTemp: String = "",
    val entityDoorbell: String = "",
    val entityLightsOn: String = "",
    val entityAqi: String = "",
    val template: String = DEFAULT_TEMPLATE,
) {
    companion object {
        const val DEFAULT_TEMPLATE =
            "It is {outside_temp} outside & {inside_temp} inside. " +
                "Doorbell rang {doorbell} times. " +
                "Currently {lights_on} on, AQI {aqi}."

        // The fixed list of placeholder keys the template may reference.
        val SUPPORTED_KEYS = listOf("outside_temp", "inside_temp", "doorbell", "lights_on", "aqi")
    }

    fun entityIdFor(key: String): String = when (key) {
        "outside_temp" -> entityOutsideTemp
        "inside_temp" -> entityInsideTemp
        "doorbell" -> entityDoorbell
        "lights_on" -> entityLightsOn
        "aqi" -> entityAqi
        else -> ""
    }
}

// Per-room sensor overrides. Blank entityId means "fall back to auto-detection".
data class RoomSensorOverride(
    val tempEntityId: String = "",
    val humidityEntityId: String = "",
)

// Default text-to-speech engine + voice used by the announce composer. Blank engineId
// means "not configured yet"; the composer then prompts the user to pick one. [language]
// is needed to enumerate an engine's voices and is sent as `tts.speak`'s `language` field.
data class TtsDefaults(
    val engineId: String = "",
    val voiceId: String = "",
    val language: String = "",
)

// Wiring for the Energy tab (Helios-inspired solar card). Entity ids are the HA sensors
// that supply live values; a blank id means "not configured" and the card omits that chip.
// [pvPeakKwp] is the installation's peak power (kWp) used by the production-forecast model.
// [homeLatOverride]/[homeLonOverride] are non-null only when the user pins a location that
// differs from the HA home; otherwise coordinates resolve from HA (get_config / zone.home).
data class EnergyConfig(
    val solarProductionId: String = "",
    val batterySocId: String = "",
    val batteryPowerId: String = "",
    val gridPowerId: String = "",
    val pvPeakKwp: Float = 0f,
    val homeLatOverride: Double? = null,
    val homeLonOverride: Double? = null,
)

@Singleton
class UserPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val USER_NAME = stringPreferencesKey("user_name")
        private val ENTITY_OUTSIDE_TEMP = stringPreferencesKey("entity_outside_temp")
        private val ENTITY_INSIDE_TEMP  = stringPreferencesKey("entity_inside_temp")
        private val ENTITY_DOORBELL    = stringPreferencesKey("entity_doorbell")
        private val ENTITY_LIGHTS_ON   = stringPreferencesKey("entity_lights_on")
        private val ENTITY_AQI         = stringPreferencesKey("entity_aqi")
        private val GLANCE_TEMPLATE    = stringPreferencesKey("glance_template")
        private val QUICK_SCENE_IDS    = stringPreferencesKey("quick_scene_ids")
        private val FAVORITE_ENTITY_IDS = stringPreferencesKey("favorite_entity_ids")
        private val GLANCE_TILE_IDS    = stringPreferencesKey("glance_tile_ids")
        private val GLANCE_TILES_INIT  = booleanPreferencesKey("glance_tiles_initialized")
        private val VISIBLE_ROOM_IDS      = stringPreferencesKey("visible_room_ids")
        private val THEME_MODE            = stringPreferencesKey("theme_mode")
        private val FLIGHT_AUTOMATION_IDS = stringPreferencesKey("flight_automation_ids")
        private val ONBOARDING_COMPLETE   = booleanPreferencesKey("onboarding_complete")
        private val DEMO_FROM_ONBOARDING  = booleanPreferencesKey("demo_from_onboarding")
        private val ROOM_SENSOR_OVERRIDES = stringPreferencesKey("room_sensor_overrides")
        private val ROOM_WARNINGS_ENABLED = booleanPreferencesKey("room_warnings_enabled")
        private val SMART_GLANCE_ENABLED = booleanPreferencesKey("smart_glance_enabled")
        private val CLIMATE_MODE_ORDERS = stringPreferencesKey("climate_mode_orders")
        private val CLIMATE_FAN_ORDERS = stringPreferencesKey("climate_fan_orders")
        private val TTS_ENGINE_ID = stringPreferencesKey("tts_engine_id")
        private val TTS_VOICE_ID = stringPreferencesKey("tts_voice_id")
        private val TTS_LANGUAGE = stringPreferencesKey("tts_language")
        private val ENERGY_TAB_ENABLED = booleanPreferencesKey("energy_tab_enabled") // legacy: pre nav_tab_keys
        private val NAV_TAB_KEYS = stringPreferencesKey("nav_tab_keys")
        // True once the user has saved a tab arrangement AFTER Pulse shipped. Until then
        // Pulse is injected into pre-existing arrangements (default-on); after, the
        // user's explicit choice (including removing Pulse) is final.
        private val PULSE_TAB_SEEDED = booleanPreferencesKey("pulse_tab_seeded")

        // Canonical bottom-nav tab keys. Settings is mandatory (the nav editor lives
        // there); the rest are user-removable and freely reorderable.
        const val NAV_TAB_DASHBOARD = "dashboard"
        const val NAV_TAB_ACTIVITY = "activity"
        const val NAV_TAB_ENERGY = "energy"
        const val NAV_TAB_PULSE = "pulse"
        // Music Assistant page. Opt-in (added via the nav editor, never seeded) —
        // only useful for homes running the MA integration.
        const val NAV_TAB_MUSIC = "music"
        const val NAV_TAB_SETTINGS = "settings"
        val NAV_TAB_ALL = listOf(NAV_TAB_DASHBOARD, NAV_TAB_ACTIVITY, NAV_TAB_ENERGY, NAV_TAB_PULSE, NAV_TAB_MUSIC, NAV_TAB_SETTINGS)
        private val ENERGY_SOLAR_ID = stringPreferencesKey("energy_solar_id")
        private val ENERGY_BATTERY_SOC_ID = stringPreferencesKey("energy_battery_soc_id")
        private val ENERGY_BATTERY_POWER_ID = stringPreferencesKey("energy_battery_power_id")
        private val ENERGY_GRID_ID = stringPreferencesKey("energy_grid_id")
        private val ENERGY_PV_PEAK_KWP = stringPreferencesKey("energy_pv_peak_kwp")
        private val ENERGY_HOME_LAT = stringPreferencesKey("energy_home_lat")
        private val ENERGY_HOME_LON = stringPreferencesKey("energy_home_lon")
        private const val LIST_SEP = "\n"
        private const val OVERRIDE_FIELD_SEP = "|"
        private const val MODE_SEP = ","
    }

    val themeMode: Flow<ThemeMode> = dataStore.data
        .map { ThemeMode.fromStorage(it[THEME_MODE]) }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = value.name }
    }

    val onboardingComplete: Flow<Boolean> = dataStore.data
        .map { it[ONBOARDING_COMPLETE] ?: false }
        .distinctUntilChanged()

    val demoFromOnboarding: Flow<Boolean> = dataStore.data
        .map { it[DEMO_FROM_ONBOARDING] ?: false }
        .distinctUntilChanged()

    suspend fun setOnboardingComplete(value: Boolean) {
        dataStore.edit { prefs -> prefs[ONBOARDING_COMPLETE] = value }
    }

    suspend fun setDemoFromOnboarding(value: Boolean) {
        dataStore.edit { prefs -> prefs[DEMO_FROM_ONBOARDING] = value }
    }

    // Whether room cards show the alert badge + warning tooltips and the room sheet
    // shows its "needs attention" banner. Defaults to true (warnings visible); the
    // user can turn it off in Settings → Additional Card Settings.
    val roomWarningsEnabled: Flow<Boolean> = dataStore.data
        .map { it[ROOM_WARNINGS_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setRoomWarningsEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[ROOM_WARNINGS_ENABLED] = value }
    }

    // Whether the "at a glance" surface auto-surfaces dynamic "smart" cards (room alerts,
    // live activity, whole-home insights) alongside the user's pinned tiles. Defaults to
    // true; toggleable in Settings → Card Settings and in the At-a-glance editor.
    val smartGlanceEnabled: Flow<Boolean> = dataStore.data
        .map { it[SMART_GLANCE_ENABLED] ?: true }
        .distinctUntilChanged()

    suspend fun setSmartGlanceEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[SMART_GLANCE_ENABLED] = value }
    }

    // ── Bottom navigation tabs ────────────────────────────────────────────────
    // Ordered tab keys for the bottom bar (see NAV_TAB_ALL). Normalization enforces the
    // one hard rule of the feature: SETTINGS CAN NEVER BE REMOVED — it's where the nav
    // editor lives, so dropping it would lock the user out of reconfiguring the bar.
    // Unset → default order, honoring the legacy "Energy tab" toggle so older installs
    // keep their choice.
    val navTabKeys: Flow<List<String>> = dataStore.data
        .map { prefs ->
            normalizeNavTabs(
                saved = decodeList(prefs[NAV_TAB_KEYS]),
                legacyEnergyEnabled = prefs[ENERGY_TAB_ENABLED] ?: true,
                pulseSeeded = prefs[PULSE_TAB_SEEDED] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun setNavTabKeys(keys: List<String>) {
        dataStore.edit { prefs ->
            // The editor's list is the user's explicit choice — never inject Pulse into
            // it, and mark it seeded so removal sticks from here on.
            prefs[NAV_TAB_KEYS] = encodeList(
                normalizeNavTabs(keys, prefs[ENERGY_TAB_ENABLED] ?: true, pulseSeeded = true),
            )
            prefs[PULSE_TAB_SEEDED] = true
        }
    }

    private fun normalizeNavTabs(
        saved: List<String>,
        legacyEnergyEnabled: Boolean,
        pulseSeeded: Boolean,
    ): List<String> {
        val known = saved.filter { it in NAV_TAB_ALL }.distinct()
        if (known.isEmpty()) {
            return buildList {
                add(NAV_TAB_DASHBOARD)
                add(NAV_TAB_ACTIVITY)
                if (legacyEnergyEnabled) add(NAV_TAB_ENERGY)
                add(NAV_TAB_PULSE)
                add(NAV_TAB_SETTINGS)
            }
        }
        val withSettings = if (NAV_TAB_SETTINGS in known) known else known + NAV_TAB_SETTINGS
        // Default-on for arrangements saved before Pulse existed: surface it (just before
        // Settings) until the user edits the bar once — their next save is then final.
        if (!pulseSeeded && NAV_TAB_PULSE !in withSettings) {
            return withSettings.toMutableList().apply {
                add(indexOf(NAV_TAB_SETTINGS), NAV_TAB_PULSE)
            }
        }
        return withSettings
    }

    // Entity wiring + PV sizing + optional pinned home location for the Energy card.
    val energyConfig: Flow<EnergyConfig> = dataStore.data.map { prefs ->
        EnergyConfig(
            solarProductionId = prefs[ENERGY_SOLAR_ID].orEmpty(),
            batterySocId = prefs[ENERGY_BATTERY_SOC_ID].orEmpty(),
            batteryPowerId = prefs[ENERGY_BATTERY_POWER_ID].orEmpty(),
            gridPowerId = prefs[ENERGY_GRID_ID].orEmpty(),
            pvPeakKwp = prefs[ENERGY_PV_PEAK_KWP]?.toFloatOrNull() ?: 0f,
            homeLatOverride = prefs[ENERGY_HOME_LAT]?.toDoubleOrNull(),
            homeLonOverride = prefs[ENERGY_HOME_LON]?.toDoubleOrNull(),
        )
    }.distinctUntilChanged()

    suspend fun setEnergySolarId(value: String) = update(ENERGY_SOLAR_ID, value)
    suspend fun setEnergyBatterySocId(value: String) = update(ENERGY_BATTERY_SOC_ID, value)
    suspend fun setEnergyBatteryPowerId(value: String) = update(ENERGY_BATTERY_POWER_ID, value)
    suspend fun setEnergyGridId(value: String) = update(ENERGY_GRID_ID, value)
    suspend fun setEnergyPvPeakKwp(value: Float) = update(ENERGY_PV_PEAK_KWP, value.toString())
    suspend fun setEnergyHomeOverride(lat: Double?, lon: Double?) {
        dataStore.edit { prefs ->
            if (lat != null && lon != null) {
                prefs[ENERGY_HOME_LAT] = lat.toString()
                prefs[ENERGY_HOME_LON] = lon.toString()
            } else {
                prefs.remove(ENERGY_HOME_LAT)
                prefs.remove(ENERGY_HOME_LON)
            }
        }
    }

    val config: Flow<GlanceConfig> = dataStore.data.map { prefs ->
        GlanceConfig(
            userName = prefs[USER_NAME].orEmpty(),
            entityOutsideTemp = prefs[ENTITY_OUTSIDE_TEMP].orEmpty(),
            entityInsideTemp  = prefs[ENTITY_INSIDE_TEMP].orEmpty(),
            entityDoorbell    = prefs[ENTITY_DOORBELL].orEmpty(),
            entityLightsOn    = prefs[ENTITY_LIGHTS_ON].orEmpty(),
            entityAqi         = prefs[ENTITY_AQI].orEmpty(),
            template          = prefs[GLANCE_TEMPLATE].orEmpty().ifEmpty { GlanceConfig.DEFAULT_TEMPLATE },
        )
    }.distinctUntilChanged()

    val quickSceneIds: Flow<List<String>> = dataStore.data.map { decodeList(it[QUICK_SCENE_IDS]) }.distinctUntilChanged()
    val flightAutomationIds: Flow<List<String>> = dataStore.data.map { decodeList(it[FLIGHT_AUTOMATION_IDS]) }.distinctUntilChanged()
    val favoriteEntityIds: Flow<List<String>> = dataStore.data.map { decodeList(it[FAVORITE_ENTITY_IDS]) }.distinctUntilChanged()
    // User-curated "at a glance" tiles (ordered entity ids). First tile renders large.
    val glanceTileIds: Flow<List<String>> = dataStore.data.map { decodeList(it[GLANCE_TILE_IDS]) }.distinctUntilChanged()
    // Empty list = "not yet initialized" — all rooms are visible. Initialized on first
    // ViewModel launch with the full area list from HA so subsequent hides work correctly.
    val visibleRoomIds: Flow<List<String>> = dataStore.data.map { decodeList(it[VISIBLE_ROOM_IDS]) }.distinctUntilChanged()

    // Per-room user-picked temperature/humidity sensor entities. Keyed by area_id.
    // A missing key (or blank entity inside) means "use auto-detection".
    val roomSensorOverrides: Flow<Map<String, RoomSensorOverride>> = dataStore.data
        .map { decodeOverrides(it[ROOM_SENSOR_OVERRIDES]) }
        .distinctUntilChanged()

    // Per-climate-entity custom order of the hvac-mode buttons. Keyed by entity_id;
    // a missing key means "use the default (entity-reported) order".
    val climateModeOrders: Flow<Map<String, List<String>>> = dataStore.data
        .map { decodeModeOrders(it[CLIMATE_MODE_ORDERS]) }
        .distinctUntilChanged()

    suspend fun setClimateModeOrder(entityId: String, modes: List<String>) {
        dataStore.edit { prefs ->
            val current = decodeModeOrders(prefs[CLIMATE_MODE_ORDERS]).toMutableMap()
            if (modes.isEmpty()) current.remove(entityId) else current[entityId] = modes
            prefs[CLIMATE_MODE_ORDERS] = encodeModeOrders(current)
        }
    }

    // Per-climate-entity custom order + kept set of the fan-speed buttons (same
    // encoding/semantics as the mode order above).
    val climateFanOrders: Flow<Map<String, List<String>>> = dataStore.data
        .map { decodeModeOrders(it[CLIMATE_FAN_ORDERS]) }
        .distinctUntilChanged()

    suspend fun setClimateFanOrder(entityId: String, fans: List<String>) {
        dataStore.edit { prefs ->
            val current = decodeModeOrders(prefs[CLIMATE_FAN_ORDERS]).toMutableMap()
            if (fans.isEmpty()) current.remove(entityId) else current[entityId] = fans
            prefs[CLIMATE_FAN_ORDERS] = encodeModeOrders(current)
        }
    }

    // Default TTS engine/voice for the announce composer. Changing the engine (or
    // language) clears the saved voice, since a voice id is only valid for one engine.
    val ttsDefaults: Flow<TtsDefaults> = dataStore.data
        .map {
            TtsDefaults(
                engineId = it[TTS_ENGINE_ID].orEmpty(),
                voiceId = it[TTS_VOICE_ID].orEmpty(),
                language = it[TTS_LANGUAGE].orEmpty(),
            )
        }
        .distinctUntilChanged()

    suspend fun setTtsEngine(engineId: String, language: String) {
        dataStore.edit { prefs ->
            prefs[TTS_ENGINE_ID] = engineId
            prefs[TTS_LANGUAGE] = language
            prefs[TTS_VOICE_ID] = ""
        }
    }
    suspend fun setTtsLanguage(language: String) {
        dataStore.edit { prefs ->
            prefs[TTS_LANGUAGE] = language
            prefs[TTS_VOICE_ID] = ""
        }
    }
    suspend fun setTtsVoice(voiceId: String) = update(TTS_VOICE_ID, voiceId)

    suspend fun setUserName(value: String)        = update(USER_NAME, value)
    suspend fun setOutsideTempEntity(value: String) = update(ENTITY_OUTSIDE_TEMP, value)
    suspend fun setInsideTempEntity(value: String)  = update(ENTITY_INSIDE_TEMP, value)
    suspend fun setDoorbellEntity(value: String)    = update(ENTITY_DOORBELL, value)
    suspend fun setLightsOnEntity(value: String)    = update(ENTITY_LIGHTS_ON, value)
    suspend fun setAqiEntity(value: String)         = update(ENTITY_AQI, value)
    suspend fun setTemplate(value: String)          = update(GLANCE_TEMPLATE, value)

    suspend fun addQuickScene(sceneId: String) = mutateList(QUICK_SCENE_IDS) { current ->
        if (sceneId in current) current else current + sceneId
    }
    suspend fun removeQuickScene(sceneId: String) = mutateList(QUICK_SCENE_IDS) { it - sceneId }

    suspend fun addFavorite(entityId: String) = mutateList(FAVORITE_ENTITY_IDS) { current ->
        if (entityId in current) current else current + entityId
    }
    suspend fun removeFavorite(entityId: String) = mutateList(FAVORITE_ENTITY_IDS) { it - entityId }

    suspend fun setQuickSceneOrder(ids: List<String>) = mutateList(QUICK_SCENE_IDS) { ids }
    suspend fun setFavoriteOrder(ids: List<String>) = mutateList(FAVORITE_ENTITY_IDS) { ids }

    suspend fun addGlanceTile(entityId: String) = mutateList(GLANCE_TILE_IDS) { current ->
        if (entityId in current) current else current + entityId
    }
    suspend fun removeGlanceTile(entityId: String) = mutateList(GLANCE_TILE_IDS) { it - entityId }
    suspend fun setGlanceTileOrder(ids: List<String>) = mutateList(GLANCE_TILE_IDS) { ids }

    // One-time seed of the glance tiles from the legacy 5-slot config so existing
    // users keep their at-a-glance setup. The flag distinguishes "never seeded"
    // from "user removed every tile" (a valid empty state we must not re-seed over).
    suspend fun initGlanceTilesIfNeeded(defaults: List<String>) {
        dataStore.edit { prefs ->
            if (prefs[GLANCE_TILES_INIT] != true) {
                prefs[GLANCE_TILE_IDS] = encodeList(defaults.distinct().filter { it.isNotBlank() })
                prefs[GLANCE_TILES_INIT] = true
            }
        }
    }

    // Call once on startup with the full HA area list so the pref is seeded correctly.
    suspend fun initRoomsIfNeeded(allRoomIds: List<String>) = mutateList(VISIBLE_ROOM_IDS) { current ->
        if (current.isEmpty()) allRoomIds else current
    }
    suspend fun hideRoom(roomId: String) = mutateList(VISIBLE_ROOM_IDS) { it - roomId }
    suspend fun showRoom(roomId: String) = mutateList(VISIBLE_ROOM_IDS) { current ->
        if (roomId in current) current else current + roomId
    }
    suspend fun setRoomOrder(ids: List<String>) = mutateList(VISIBLE_ROOM_IDS) { ids }

    suspend fun setRoomTempSensor(roomId: String, entityId: String) = mutateOverrides { current ->
        val existing = current[roomId] ?: RoomSensorOverride()
        current + (roomId to existing.copy(tempEntityId = entityId))
    }
    suspend fun setRoomHumiditySensor(roomId: String, entityId: String) = mutateOverrides { current ->
        val existing = current[roomId] ?: RoomSensorOverride()
        current + (roomId to existing.copy(humidityEntityId = entityId))
    }

    suspend fun addFlightAutomation(entityId: String) = mutateList(FLIGHT_AUTOMATION_IDS) { current ->
        if (entityId in current) current else current + entityId
    }
    suspend fun removeFlightAutomation(entityId: String) = mutateList(FLIGHT_AUTOMATION_IDS) { it - entityId }
    suspend fun setFlightAutomationOrder(ids: List<String>) = mutateList(FLIGHT_AUTOMATION_IDS) { ids }

    private suspend fun update(key: Preferences.Key<String>, value: String) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private suspend fun mutateList(key: Preferences.Key<String>, transform: (List<String>) -> List<String>) {
        dataStore.edit { prefs ->
            val next = transform(decodeList(prefs[key]))
            prefs[key] = encodeList(next)
        }
    }

    private fun encodeList(items: List<String>): String = items.joinToString(LIST_SEP)
    private fun decodeList(raw: String?): List<String> =
        raw?.split(LIST_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    private suspend fun mutateOverrides(
        transform: (Map<String, RoomSensorOverride>) -> Map<String, RoomSensorOverride>,
    ) {
        dataStore.edit { prefs ->
            val current = decodeOverrides(prefs[ROOM_SENSOR_OVERRIDES])
            val next = transform(current).filterValues {
                it.tempEntityId.isNotBlank() || it.humidityEntityId.isNotBlank()
            }
            prefs[ROOM_SENSOR_OVERRIDES] = encodeOverrides(next)
        }
    }

    private fun encodeOverrides(map: Map<String, RoomSensorOverride>): String =
        map.entries.joinToString(LIST_SEP) { (id, o) ->
            "$id$OVERRIDE_FIELD_SEP${o.tempEntityId}$OVERRIDE_FIELD_SEP${o.humidityEntityId}"
        }

    private fun encodeModeOrders(map: Map<String, List<String>>): String =
        map.entries.joinToString(LIST_SEP) { (id, modes) ->
            "$id$OVERRIDE_FIELD_SEP${modes.joinToString(MODE_SEP)}"
        }

    private fun decodeModeOrders(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(LIST_SEP).mapNotNull { line ->
            val parts = line.split(OVERRIDE_FIELD_SEP)
            val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val modes = parts.getOrNull(1)?.split(MODE_SEP)?.filter { it.isNotBlank() }.orEmpty()
            if (modes.isEmpty()) null else id to modes
        }.toMap()
    }

    private fun decodeOverrides(raw: String?): Map<String, RoomSensorOverride> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split(LIST_SEP).mapNotNull { line ->
            val parts = line.split(OVERRIDE_FIELD_SEP)
            val id = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to RoomSensorOverride(
                tempEntityId = parts.getOrNull(1).orEmpty(),
                humidityEntityId = parts.getOrNull(2).orEmpty(),
            )
        }.toMap()
    }
}
