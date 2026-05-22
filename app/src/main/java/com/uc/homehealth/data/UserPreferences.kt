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
        private val VISIBLE_ROOM_IDS      = stringPreferencesKey("visible_room_ids")
        private val THEME_MODE            = stringPreferencesKey("theme_mode")
        private val FLIGHT_AUTOMATION_IDS = stringPreferencesKey("flight_automation_ids")
        private val ONBOARDING_COMPLETE   = booleanPreferencesKey("onboarding_complete")
        private val DEMO_FROM_ONBOARDING  = booleanPreferencesKey("demo_from_onboarding")
        private val ROOM_SENSOR_OVERRIDES = stringPreferencesKey("room_sensor_overrides")
        private const val LIST_SEP = "\n"
        private const val OVERRIDE_FIELD_SEP = "|"
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
    // Empty list = "not yet initialized" — all rooms are visible. Initialized on first
    // ViewModel launch with the full area list from HA so subsequent hides work correctly.
    val visibleRoomIds: Flow<List<String>> = dataStore.data.map { decodeList(it[VISIBLE_ROOM_IDS]) }.distinctUntilChanged()

    // Per-room user-picked temperature/humidity sensor entities. Keyed by area_id.
    // A missing key (or blank entity inside) means "use auto-detection".
    val roomSensorOverrides: Flow<Map<String, RoomSensorOverride>> = dataStore.data
        .map { decodeOverrides(it[ROOM_SENSOR_OVERRIDES]) }
        .distinctUntilChanged()

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
