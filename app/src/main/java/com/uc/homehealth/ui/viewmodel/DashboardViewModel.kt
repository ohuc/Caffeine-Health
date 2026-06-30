@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.GLANCE_SUGGESTION_MIN_SCORE
import com.uc.homehealth.data.GlanceConfig
import com.uc.homehealth.data.GlanceInteractionStore
import com.uc.homehealth.data.glanceScoreFor
import com.uc.homehealth.data.glanceSceneKey
import com.uc.homehealth.data.glanceTimeBucket
import com.uc.homehealth.data.HaAutomation
import com.uc.homehealth.data.HaClimate
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.GlanceTile
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.data.HaFavorite
import com.uc.homehealth.data.HaFlight
import com.uc.homehealth.data.HaLight
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.HaPersonLocation
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.data.HaScene
import com.uc.homehealth.data.HaTtsEngine
import com.uc.homehealth.data.HaTtsVoice
import com.uc.homehealth.data.MaEnqueueMode
import com.uc.homehealth.data.MaMediaType
import com.uc.homehealth.data.MaSearchItem
import com.uc.homehealth.data.MaSearchResults
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.data.TtsTarget
import com.uc.homehealth.data.ActivityLog
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.FlightScheduleScheduler
import com.uc.homehealth.data.FlightScheduleStore
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.ScheduledFlightAdd
import com.uc.homehealth.data.ScheduledFlightStatus
import com.uc.homehealth.data.RoomSensorOverride
import com.uc.homehealth.data.RoomWidget
import com.uc.homehealth.data.WidgetSection
import com.uc.homehealth.data.UserPreferences
import com.uc.homehealth.data.WidgetPreferences
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// "sensor.living_room_temp" → "Living Room Temp" (fallback tile name when the
// entity has no friendly name yet).
private fun prettifyEntityId(entityId: String): String =
    entityId.substringAfterLast('.')
        .replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

data class DashboardUiState(
    val rooms: List<HaRoom> = emptyList(),
    val allRooms: List<HaRoom> = emptyList(),
    val hiddenRooms: List<HaRoom> = emptyList(),
    val showRoomPicker: Boolean = false,
    val scenes: List<HaScene> = emptyList(),
    val favorites: List<HaFavorite> = emptyList(),
    val allScenes: List<HaScene> = emptyList(),
    val allEntities: List<HaEntitySummary> = emptyList(),
    val userName: String = "Alex",
    val glanceTemplate: String = GlanceConfig.DEFAULT_TEMPLATE,
    val glanceValues: Map<String, HaEntityValue?> = emptyMap(),
    val glanceTiles: List<GlanceTile> = emptyList(),
    val glanceTilePickerDomain: String? = null,
    val selectedRoom: HaRoom? = null,
    val selectedRoomLights: List<HaLight> = emptyList(),
    val selectedRoomClimate: HaClimate? = null,
    val selectedRoomTempHistory: List<Float> = emptyList(),
    val selectedRoomWidgets: List<RoomWidget> = emptyList(),
    val widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    val widgetHistories: Map<String, List<Float>> = emptyMap(),
    val widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    val widgetMediaStates: Map<String, HaMedia?> = emptyMap(),
    val widgetLocations: Map<String, HaPersonLocation?> = emptyMap(),
    // Live state for user-added climate widgets, keyed by entity_id.
    val widgetClimateStates: Map<String, HaClimate?> = emptyMap(),
    val showWidgetCatalog: Boolean = false,
    val showSwitchEntityPicker: Boolean = false,
    val showSensorEntityPicker: Boolean = false,
    val showCameraEntityPicker: Boolean = false,
    val showMediaEntityPicker: Boolean = false,
    val showClimateEntityPicker: Boolean = false,
    val showLocationEntityPicker: Boolean = false,
    val showAirQualityConfig: Boolean = false,
    val openedSwitchWidget: RoomWidget.Switch? = null,
    val openedSensorWidget: RoomWidget.Sensor? = null,
    val openedCameraWidget: RoomWidget.Camera? = null,
    val openedLocationWidget: RoomWidget.Location? = null,
    // Non-null while the add-camera PTZ setup sheet is open (the camera being added).
    val ptzConfigCamera: RoomWidget.Camera? = null,
    val showAllRooms: Boolean = false,
    val showScenePicker: Boolean = false,
    val showFavoritePicker: Boolean = false,
    val connectionStatus: WsConnectionStatus = WsConnectionStatus.DISCONNECTED,
    val isLoggedIn: Boolean = false,
    val flights: List<HaFlight> = emptyList(),
    val isFlightRadar24Available: Boolean = true,
    val showFlightSheet: Boolean = false,
    val isAddingFlight: Boolean = false,
    val flightAutomationIds: List<String> = emptyList(),
    val allAutomations: List<HaAutomation> = emptyList(),
    val flightAddError: String? = null,
    // Date-scheduled flight adds waiting (or failed) in the on-device queue — the
    // actual FR24 track command is only sent on each entry's target day.
    val scheduledFlights: List<ScheduledFlightAdd> = emptyList(),
    val roomSensorOverrides: Map<String, RoomSensorOverride> = emptyMap(),
    val editingSensorsForRoom: HaRoom? = null,
    // When false, room cards hide the alert badge/tooltip and the room sheet hides
    // its "needs attention" banner (Settings → Additional Card Settings).
    val showRoomWarnings: Boolean = true,
    // When false, the "at a glance" surface shows only the user's pinned tiles — no
    // auto-surfaced alert/insight/delight cards (Settings → Card Settings, or the editor).
    val smartGlanceEnabled: Boolean = true,
    // Per-climate-entity user-customized order of the hvac-mode buttons.
    val climateModeOrders: Map<String, List<String>> = emptyMap(),
    // Per-climate-entity user-customized order of the fan-speed buttons.
    val climateFanOrders: Map<String, List<String>> = emptyMap(),
    // False until the first combined emission lands (auth + WS status both
    // resolved). DashboardScreen uses this to avoid flashing OfflineState
    // before bootstrap completes on cold launch.
    val loaded: Boolean = false,
) {
    val isOnline: Boolean get() = connectionStatus == WsConnectionStatus.READY
}

// State for the announce (text-to-speech) composer sheet, kept separate from the giant
// DashboardUiState so it can be collected on its own without touching the maxed-out
// combine(). [visible] is driven by which media player (if any) the composer is open for.
// A learned "you usually run this scene about now" suggestion for the at-a-glance feed.
// Null when the on-device interaction history isn't confident enough yet.
data class GlanceSuggestion(
    val sceneId: String,
    val name: String,
    val emoji: String,
)

// The most recent activity event, surfaced as a glance card when fresh + relevant.
data class GlanceActivity(
    val title: String,
    val iconKey: String,
    val relative: String,   // "3m ago"
)

// Only surface activity that just happened ("what's going on"), within the last hour.
private const val GLANCE_ACTIVITY_RECENT_MS = 60L * 60 * 1000

// Day label for scheduled flight adds ("Fri, 19 Jun") — error banners + activity log.
private val scheduledDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

// HA activity-event kind → glance icon key.
private fun activityIconKey(kind: String): String = when (kind) {
    "motion" -> "pulse"
    "door" -> "door"
    "energy" -> "energy"
    "climate" -> "thermo"
    "light" -> "bulb"
    "media" -> "speaker"
    "update" -> "update"
    "scene", "auto" -> "sparkle"
    else -> "bell"
}

// Compact "x ago" for the card kicker.
private fun relativeTimeShort(ageMs: Long): String {
    val mins = ageMs / 60_000
    return when {
        mins < 1 -> "Just now"
        mins < 60 -> "${mins}m ago"
        mins < 24 * 60 -> "${mins / 60}h ago"
        else -> "${mins / (24 * 60)}d ago"
    }
}

data class TtsComposerUiState(
    val visible: Boolean = false,
    val target: TtsTarget? = null,        // resolved player (Echo vs standard, friendly name)
    val engines: List<HaTtsEngine> = emptyList(),
    val defaultEngineId: String = "",
    val defaultVoiceId: String = "",
    val defaultLanguage: String = "",
    val sending: Boolean = false,
)

// Music Assistant search sheet — open for one MA player at a time.
data class MaSearchUiState(
    val visible: Boolean = false,
    val entityId: String = "",
    val playerName: String = "",
)

private data class RoomExtras(
    val room: HaRoom?,
    val lights: List<HaLight>,
    val climate: HaClimate?,
    val history: List<Float>,
    val widgets: List<RoomWidget>,
    val widgetStates: Map<String, HaEntityValue?>,
    val widgetHistories: Map<String, List<Float>>,
    val widgetCameraSnapshots: Map<String, String?>,
    val widgetMediaStates: Map<String, HaMedia?>,
    val widgetLocations: Map<String, HaPersonLocation?>,
    val widgetClimateStates: Map<String, HaClimate?>,
)

private data class RoomWidgetBundle(
    val widgets: List<RoomWidget>,
    val states: Map<String, HaEntityValue?>,
    val histories: Map<String, List<Float>>,
    val cameraSnapshots: Map<String, String?>,
    val mediaStates: Map<String, HaMedia?>,
    val locations: Map<String, HaPersonLocation?>,
    val climateStates: Map<String, HaClimate?>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val userPreferences: UserPreferences,
    private val authPreferences: AuthPreferences,
    private val widgetPreferences: WidgetPreferences,
    private val glanceInteractions: GlanceInteractionStore,
    private val flightScheduleStore: FlightScheduleStore,
    private val flightScheduleScheduler: FlightScheduleScheduler,
    private val activityLog: ActivityLog,
) : ViewModel() {

    private val _selectedRoom = MutableStateFlow<HaRoom?>(null)
    private val _showAllRooms = MutableStateFlow(false)
    private val _showScenePicker = MutableStateFlow(false)
    private val _showFavoritePicker = MutableStateFlow(false)
    // Non-null = the tile entity picker is open, scoped to this HA domain (e.g. "sensor").
    private val _glanceTilePickerDomain = MutableStateFlow<String?>(null)
    private val _showWidgetCatalog = MutableStateFlow(false)
    private val _showSwitchEntityPicker = MutableStateFlow(false)
    private val _showSensorEntityPicker = MutableStateFlow(false)
    private val _showCameraEntityPicker = MutableStateFlow(false)
    private val _showMediaEntityPicker = MutableStateFlow(false)
    private val _showClimateEntityPicker = MutableStateFlow(false)
    private val _showLocationEntityPicker = MutableStateFlow(false)
    private val _showAirQualityConfig = MutableStateFlow(false)
    // Which section requested the media / climate picker — determines the section the
    // added widget lands in (Media tab → MEDIA / Climate tab → CLIMATE / catalog → MORE).
    private var mediaPickerSection = WidgetSection.MORE
    private var climatePickerSection = WidgetSection.MORE
    private val _openedSwitchWidget = MutableStateFlow<RoomWidget.Switch?>(null)
    private val _openedSensorWidget = MutableStateFlow<RoomWidget.Sensor?>(null)
    private val _openedCameraWidget = MutableStateFlow<RoomWidget.Camera?>(null)
    private val _openedLocationWidget = MutableStateFlow<RoomWidget.Location?>(null)
    private val _ptzConfigCamera = MutableStateFlow<RoomWidget.Camera?>(null)
    private val _showFlightSheet = MutableStateFlow(false)
    private val _isAddingFlight = MutableStateFlow(false)
    // Flight ids the user just hit remove on. Filtered out of the UI list immediately
    // so the user gets feedback; cleared once HA confirms (or after a timeout, so a
    // failed removal silently lets the flight reappear instead of leaving the row
    // missing forever).
    private val _pendingFlightRemovals = MutableStateFlow<Set<String>>(emptySet())
    // Set when addTrackedFlight times out without FR24 actually starting to track.
    // Surfaced as a banner inside FlightSheet; dismissed by the user or by the next add.
    private val _flightAddError = MutableStateFlow<String?>(null)
    private val _showRoomPicker = MutableStateFlow(false)
    private val _editingSensorsRoomId = MutableStateFlow<String?>(null)
    // Announce/TTS composer: non-null entity_id = the sheet is open for that media player.
    private val _ttsComposerEntityId = MutableStateFlow<String?>(null)
    private val _ttsEngines = MutableStateFlow<List<HaTtsEngine>>(emptyList())
    private val _ttsSending = MutableStateFlow(false)
    // Music Assistant search: non-null entity_id = the sheet is open for that player.
    private val _maSearchEntityId = MutableStateFlow<String?>(null)
    private val brightnessChannel = Channel<Pair<String, Int>>(Channel.CONFLATED)
    private val colorChannel = Channel<Pair<String, Triple<Int, Int, Int>>>(Channel.CONFLATED)
    private val colorTempChannel = Channel<Pair<String, Int>>(Channel.CONFLATED)

    // Re-resolves the 5 glance entities whenever the user changes them in settings.
    private val glanceValues = userPreferences.config.flatMapLatest { config ->
        combine(
            repo.getEntityState(config.entityIdFor("outside_temp")),
            repo.getEntityState(config.entityIdFor("inside_temp")),
            repo.getEntityState(config.entityIdFor("doorbell")),
            repo.getEntityState(config.entityIdFor("lights_on")),
            repo.getEntityState(config.entityIdFor("aqi")),
        ) { o, i, d, l, a ->
            mapOf(
                "outside_temp" to o,
                "inside_temp" to i,
                "doorbell" to d,
                "lights_on" to l,
                "aqi" to a,
            )
        }
    }

    // Live state for each user-curated at-a-glance tile, in saved order.
    private val glanceTiles: kotlinx.coroutines.flow.Flow<List<GlanceTile>> =
        userPreferences.glanceTileIds.flatMapLatest { ids ->
            if (ids.isEmpty()) flowOf(emptyList())
            else combine(ids.map { id -> repo.getEntityState(id).map { id to it } }) { pairs ->
                pairs.map { (id, value) ->
                    GlanceTile(
                        entityId = id,
                        name = value?.friendlyName?.takeIf { it.isNotBlank() } ?: prettifyEntityId(id),
                        state = value?.state ?: "—",
                        unit = value?.unit,
                        domain = id.substringBefore('.', missingDelimiterValue = ""),
                    )
                }
            }
        }

    private data class GlanceState(
        val userName: String,
        val template: String,
        val values: Map<String, HaEntityValue?>,
        val tiles: List<GlanceTile>,
        val tilePickerDomain: String?,
    )

    private val glanceState = combine(
        userPreferences.config,
        glanceValues,
        glanceTiles,
        _glanceTilePickerDomain,
    ) { config, values, tiles, pickerDomain ->
        GlanceState(config.userName, config.template, values, tiles, pickerDomain)
    }

    private val selectedRoomLights = _selectedRoom.flatMapLatest { room ->
        if (room != null) repo.getLightsForRoom(room.id) else flowOf(emptyList())
    }

    private val selectedRoomClimate = _selectedRoom.flatMapLatest { room ->
        if (room != null) repo.getClimateForRoom(room.id) else flowOf(null)
    }

    private val selectedRoomTempHistory = _selectedRoom.flatMapLatest { room ->
        if (room != null) repo.getTempHistory(room.id) else flowOf(emptyList())
    }

    // Widgets list + live state map + 24h history map for sensor widgets. Re-subscribes
    // when the room or its widget list changes, so each tile reflects state_changed
    // events in real time. Switch widgets get live state only; sensor widgets get
    // live state AND a one-shot history fetch (graphed in the tile + detail sheet).
    private val selectedRoomWidgetsBundle: kotlinx.coroutines.flow.Flow<RoomWidgetBundle> =
        _selectedRoom.flatMapLatest { room ->
            if (room == null) flowOf(RoomWidgetBundle(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            else widgetPreferences.widgetsForRoom(room.id).flatMapLatest { widgets ->
                val stateIds = widgets.flatMap { w ->
                    when (w) {
                        is RoomWidget.Switch -> listOf(w.entityId)
                        is RoomWidget.Sensor -> listOf(w.entityId)
                        is RoomWidget.Camera -> listOf(w.entityId)
                        is RoomWidget.Media -> listOf(w.entityId)
                        is RoomWidget.Location -> listOf(w.entityId)
                        // Base PM2.5 + its three duration sensors all resolve via getEntityState.
                        is RoomWidget.AirQuality ->
                            listOf(w.entityId, w.cleanDurationId, w.moderateDurationId, w.poorDurationId)
                        // Climate widgets resolve their live state via getClimate(), not the
                        // generic entity-state map.
                        is RoomWidget.Climate -> emptyList()
                    }
                }.filter { it.isNotBlank() }.distinct()
                val historyIds = widgets.filterIsInstance<RoomWidget.Sensor>().map { it.entityId }
                val cameraIds = widgets.filterIsInstance<RoomWidget.Camera>().map { it.entityId }
                val mediaIds = widgets.filterIsInstance<RoomWidget.Media>().map { it.entityId }
                val climateIds = widgets.filterIsInstance<RoomWidget.Climate>().map { it.entityId }
                val locationIds = widgets.filterIsInstance<RoomWidget.Location>().map { it.entityId }
                val stateFlow = if (stateIds.isEmpty()) flowOf(emptyMap<String, HaEntityValue?>())
                    else combine(stateIds.map { repo.getEntityState(it) }) { values ->
                        stateIds.zip(values.toList()).toMap()
                    }
                val historyFlow = if (historyIds.isEmpty()) flowOf(emptyMap<String, List<Float>>())
                    else combine(historyIds.map { id -> repo.getEntityHistory(id).map { id to it } }) { pairs ->
                        pairs.toMap()
                    }
                val snapshotFlow = if (cameraIds.isEmpty()) flowOf(emptyMap<String, String?>())
                    else combine(cameraIds.map { id -> repo.getCameraSnapshotUrl(id).map { id to it } }) { pairs ->
                        pairs.toMap()
                    }
                val mediaFlow = if (mediaIds.isEmpty()) flowOf(emptyMap<String, HaMedia?>())
                    else combine(mediaIds.map { id -> repo.getMediaPlayer(id).map { id to it } }) { pairs ->
                        pairs.toMap()
                    }
                val climateFlow = if (climateIds.isEmpty()) flowOf(emptyMap<String, HaClimate?>())
                    else combine(climateIds.map { id -> repo.getClimate(id).map { id to it } }) { pairs ->
                        pairs.toMap()
                    }
                val locationFlow = if (locationIds.isEmpty()) flowOf(emptyMap<String, HaPersonLocation?>())
                    else combine(locationIds.map { id -> repo.getPersonLocation(id).map { id to it } }) { pairs ->
                        pairs.toMap()
                    }
                // combine() caps at 5 typed sources — pair media + climate so we stay within it.
                val mediaClimateFlow = combine(mediaFlow, climateFlow) { media, climate -> media to climate }
                combine(stateFlow, historyFlow, snapshotFlow, mediaClimateFlow, locationFlow) { states, histories, snapshots, mediaClimate, locations ->
                    RoomWidgetBundle(widgets, states, histories, snapshots, mediaClimate.first, locations, mediaClimate.second)
                }
            }
        }

    private data class CoreData(
        val rooms: List<HaRoom>,
        val allRooms: List<HaRoom>,
        val scenes: List<HaScene>,
        val favs: List<HaFavorite>,
        val allScenes: List<HaScene>,
        val allEntities: List<HaEntitySummary>,
        val allAutomations: List<HaAutomation>,
        val roomSensorOverrides: Map<String, RoomSensorOverride>,
        val editingSensorsRoomId: String?,
        val climateModeOrders: Map<String, List<String>>,
        val climateFanOrders: Map<String, List<String>>,
    )

    // Rooms visible to the user, ordered per their preference. Empty visibleRoomIds means
    // "all rooms" (first launch before the pref is seeded).
    private val orderedRooms = combine(repo.getAllRooms(), userPreferences.visibleRoomIds) { all, ids ->
        if (ids.isEmpty()) all
        else {
            val byId = all.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }
    }

    private val coreData = combine(
        orderedRooms,
        repo.getAllRooms(),
        repo.getScenes(),
        repo.getFavorites(),
        repo.getAllScenes(),
        repo.getAllEntities(),
        repo.getAutomations(),
        userPreferences.roomSensorOverrides,
        _editingSensorsRoomId,
        userPreferences.climateModeOrders,
        userPreferences.climateFanOrders,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val rooms = args[0] as List<HaRoom>
        val allRooms = args[1] as List<HaRoom>
        val scenes = args[2] as List<HaScene>
        val favs = args[3] as List<HaFavorite>
        val allScenes = args[4] as List<HaScene>
        val allEntities = args[5] as List<HaEntitySummary>
        val allAutomations = args[6] as List<HaAutomation>
        val overrides = args[7] as Map<String, RoomSensorOverride>
        val editingId = args[8] as String?
        val modeOrders = args[9] as Map<String, List<String>>
        val fanOrders = args[10] as Map<String, List<String>>
        CoreData(rooms, allRooms, scenes, favs, allScenes, allEntities, allAutomations, overrides, editingId, modeOrders, fanOrders)
    }

    private val roomExtras = combine(
        _selectedRoom,
        selectedRoomLights,
        selectedRoomClimate,
        selectedRoomTempHistory,
        selectedRoomWidgetsBundle,
    ) { room, lights, climate, history, widgetsBundle ->
        RoomExtras(
            room, lights, climate, history,
            widgetsBundle.widgets, widgetsBundle.states, widgetsBundle.histories,
            widgetsBundle.cameraSnapshots, widgetsBundle.mediaStates, widgetsBundle.locations,
            widgetsBundle.climateStates,
        )
    }

    // Holds the connection/auth + card-display flags group (the inner combine outgrew Triple).
    private data class StatusFlags(
        val ws: WsConnectionStatus,
        val authed: Boolean,
        val warnings: Boolean,
        val smartGlance: Boolean,
    )

    private data class StatusBundle(
        val showScenePicker: Boolean,
        val showFavoritePicker: Boolean,
        val showRoomPicker: Boolean,
        val showWidgetCatalog: Boolean,
        val showSwitchEntityPicker: Boolean,
        val showSensorEntityPicker: Boolean,
        val showCameraEntityPicker: Boolean,
        val showMediaEntityPicker: Boolean,
        val showClimateEntityPicker: Boolean,
        val showLocationEntityPicker: Boolean,
        val showAirQualityConfig: Boolean,
        val openedSwitchWidget: RoomWidget.Switch?,
        val openedSensorWidget: RoomWidget.Sensor?,
        val openedCameraWidget: RoomWidget.Camera?,
        val openedLocationWidget: RoomWidget.Location?,
        val ptzConfigCamera: RoomWidget.Camera?,
        val connectionStatus: WsConnectionStatus,
        val isLoggedIn: Boolean,
        val showRoomWarnings: Boolean,
        val smartGlanceEnabled: Boolean,
        val flights: List<HaFlight>,
        val isFlightRadar24Available: Boolean,
        val showFlightSheet: Boolean,
        val isAddingFlight: Boolean,
        val flightAutomationIds: List<String>,
        val flightAddError: String?,
        val scheduledFlights: List<ScheduledFlightAdd>,
    )

    private data class FlightData(
        val flights: List<HaFlight>,
        val automationIds: List<String>,
        val available: Boolean,
        val scheduled: List<ScheduledFlightAdd>,
    )

    private data class WidgetUiState(
        val showCatalog: Boolean,
        val showSwitchPicker: Boolean,
        val showSensorPicker: Boolean,
        val showCameraPicker: Boolean,
        val showMediaPicker: Boolean,
        val showClimatePicker: Boolean,
        val showLocationPicker: Boolean,
        val showAirQualityConfig: Boolean,
        val openedSwitch: RoomWidget.Switch?,
        val openedSensor: RoomWidget.Sensor?,
        val openedCamera: RoomWidget.Camera?,
        val openedLocation: RoomWidget.Location?,
        val ptzConfigCamera: RoomWidget.Camera?,
    )

    private data class OpenedWidgets(
        val sw: RoomWidget.Switch?,
        val se: RoomWidget.Sensor?,
        val ca: RoomWidget.Camera?,
        val ptz: RoomWidget.Camera?,
        val lo: RoomWidget.Location?,
    )

    // combine() caps at 5 typed sources — bundle the widget-related flows into two
    // groups then merge so we can hand a single value back into the parent combine().
    // The flags group uses the vararg combine (>5 booleans) returning a positional list.
    private val widgetUiState = combine(
        combine(
            _showWidgetCatalog, _showSwitchEntityPicker, _showSensorEntityPicker,
            _showCameraEntityPicker, _showMediaEntityPicker, _showClimateEntityPicker,
            _showLocationEntityPicker, _showAirQualityConfig,
        ) { flags -> flags.toList() },
        combine(_openedSwitchWidget, _openedSensorWidget, _openedCameraWidget, _ptzConfigCamera, _openedLocationWidget) { sw, se, ca, ptz, lo ->
            OpenedWidgets(sw, se, ca, ptz, lo)
        },
    ) { flags, opens ->
        WidgetUiState(
            showCatalog = flags[0],
            showSwitchPicker = flags[1],
            showSensorPicker = flags[2],
            showCameraPicker = flags[3],
            showMediaPicker = flags[4],
            showClimatePicker = flags[5],
            showLocationPicker = flags[6],
            showAirQualityConfig = flags[7],
            openedSwitch = opens.sw,
            openedSensor = opens.se,
            openedCamera = opens.ca,
            openedLocation = opens.lo,
            ptzConfigCamera = opens.ptz,
        )
    }

    private val statusBundle = combine(
        combine(_showScenePicker, _showFavoritePicker, _showRoomPicker) { scene, fav, room -> Triple(scene, fav, room) },
        widgetUiState,
        combine(
            repo.connectionStatus(),
            authPreferences.authState.map { it.accessToken.isNotEmpty() },
            userPreferences.roomWarningsEnabled,
            userPreferences.smartGlanceEnabled,
        ) { ws, authed, warnings, smart ->
            StatusFlags(ws, authed, warnings, smart)
        },
        combine(
            repo.getTrackedFlights(),
            userPreferences.flightAutomationIds,
            repo.isFlightRadar24Available(),
            _pendingFlightRemovals,
            flightScheduleStore.schedules,
        ) { flights, autoIds, available, pending, scheduled ->
            val visible = if (pending.isEmpty()) flights else flights.filter { it.id !in pending }
            FlightData(visible, autoIds, available, scheduled)
        },
        combine(_showFlightSheet, _isAddingFlight, _flightAddError) { show, adding, err -> Triple(show, adding, err) },
    ) { sceneFavRoom, widget, wsAuth, flightData, flightUi ->
        StatusBundle(
            showScenePicker = sceneFavRoom.first,
            showFavoritePicker = sceneFavRoom.second,
            showRoomPicker = sceneFavRoom.third,
            showWidgetCatalog = widget.showCatalog,
            showSwitchEntityPicker = widget.showSwitchPicker,
            showSensorEntityPicker = widget.showSensorPicker,
            showCameraEntityPicker = widget.showCameraPicker,
            showMediaEntityPicker = widget.showMediaPicker,
            showClimateEntityPicker = widget.showClimatePicker,
            showLocationEntityPicker = widget.showLocationPicker,
            showAirQualityConfig = widget.showAirQualityConfig,
            openedSwitchWidget = widget.openedSwitch,
            openedSensorWidget = widget.openedSensor,
            openedCameraWidget = widget.openedCamera,
            openedLocationWidget = widget.openedLocation,
            ptzConfigCamera = widget.ptzConfigCamera,
            connectionStatus = wsAuth.ws,
            isLoggedIn = wsAuth.authed,
            showRoomWarnings = wsAuth.warnings,
            smartGlanceEnabled = wsAuth.smartGlance,
            flights = flightData.flights,
            isFlightRadar24Available = flightData.available,
            showFlightSheet = flightUi.first,
            isAddingFlight = flightUi.second,
            flightAutomationIds = flightData.automationIds,
            flightAddError = flightUi.third,
            scheduledFlights = flightData.scheduled,
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        coreData,
        roomExtras,
        _showAllRooms,
        glanceState,
        statusBundle,
    ) { core, extras, showAll, glance, status ->
        DashboardUiState(
            rooms = core.rooms,
            allRooms = core.allRooms,
            hiddenRooms = core.allRooms.filter { room -> core.rooms.none { it.id == room.id } },
            showRoomPicker = status.showRoomPicker,
            scenes = core.scenes,
            favorites = core.favs,
            allScenes = core.allScenes,
            allEntities = core.allEntities,
            userName = glance.userName,
            glanceTemplate = glance.template,
            glanceValues = glance.values,
            glanceTiles = glance.tiles,
            glanceTilePickerDomain = glance.tilePickerDomain,
            selectedRoom = extras.room,
            selectedRoomLights = extras.lights,
            selectedRoomClimate = extras.climate,
            selectedRoomTempHistory = extras.history,
            selectedRoomWidgets = extras.widgets,
            widgetStates = extras.widgetStates,
            widgetHistories = extras.widgetHistories,
            widgetCameraSnapshots = extras.widgetCameraSnapshots,
            widgetMediaStates = extras.widgetMediaStates,
            widgetLocations = extras.widgetLocations,
            widgetClimateStates = extras.widgetClimateStates,
            showAllRooms = showAll,
            showScenePicker = status.showScenePicker,
            showFavoritePicker = status.showFavoritePicker,
            showWidgetCatalog = status.showWidgetCatalog,
            showSwitchEntityPicker = status.showSwitchEntityPicker,
            showSensorEntityPicker = status.showSensorEntityPicker,
            showCameraEntityPicker = status.showCameraEntityPicker,
            showMediaEntityPicker = status.showMediaEntityPicker,
            showClimateEntityPicker = status.showClimateEntityPicker,
            showLocationEntityPicker = status.showLocationEntityPicker,
            showAirQualityConfig = status.showAirQualityConfig,
            openedSwitchWidget = status.openedSwitchWidget,
            openedSensorWidget = status.openedSensorWidget,
            openedCameraWidget = status.openedCameraWidget,
            openedLocationWidget = status.openedLocationWidget,
            ptzConfigCamera = status.ptzConfigCamera,
            connectionStatus = status.connectionStatus,
            isLoggedIn = status.isLoggedIn,
            flights = status.flights,
            isFlightRadar24Available = status.isFlightRadar24Available,
            showFlightSheet = status.showFlightSheet,
            isAddingFlight = status.isAddingFlight,
            flightAutomationIds = status.flightAutomationIds,
            allAutomations = core.allAutomations,
            flightAddError = status.flightAddError,
            scheduledFlights = status.scheduledFlights,
            roomSensorOverrides = core.roomSensorOverrides,
            editingSensorsForRoom = core.editingSensorsRoomId
                ?.let { id -> core.allRooms.firstOrNull { it.id == id } },
            showRoomWarnings = status.showRoomWarnings,
            smartGlanceEnabled = status.smartGlanceEnabled,
            climateModeOrders = core.climateModeOrders,
            climateFanOrders = core.climateFanOrders,
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    // ── Learned "usually now" scene suggestion (on-device) ────────────────────────
    // Kept out of the maxed-out uiState combine() — its own small flow, like the TTS
    // composer state. The ticker re-reads the time-of-day bucket every 10 min so the
    // suggestion can switch as the day moves even without an interaction. Emits null until
    // a scene's decayed run-count for the current bucket clears the confidence threshold.
    private val timeBucketTicker: kotlinx.coroutines.flow.Flow<Int> = flow {
        while (true) {
            emit(glanceTimeBucket(System.currentTimeMillis()))
            delay(10 * 60_000L)
        }
    }.distinctUntilChanged()

    val glanceSuggestion: StateFlow<GlanceSuggestion?> = combine(
        glanceInteractions.stats,
        repo.getScenes(),
        timeBucketTicker,
    ) { stats, scenes, bucket ->
        val now = System.currentTimeMillis()
        val best = scenes
            .mapNotNull { scene ->
                val value = glanceScoreFor(stats.engagement, glanceSceneKey(scene.id), bucket, now)
                if (value >= GLANCE_SUGGESTION_MIN_SCORE) scene to value else null
            }
            .maxByOrNull { it.second }
        android.util.Log.d(
            "HomeHealth_Glance",
            "suggestion bucket=$bucket scenes=${scenes.size} → ${best?.first?.name ?: "none (need ≥$GLANCE_SUGGESTION_MIN_SCORE)"}",
        )
        best?.let { (scene, _) -> GlanceSuggestion(scene.id, scene.name, scene.emoji) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Recent-activity glance card (uses the activity feed) ──────────────────────────
    // Surfaces the most recent activity event whenever it's fresh ("what just happened").
    // No CTR/ignore demotion here on purpose: for an ambient info card, *viewing it is
    // using it* — penalising it for not being tapped would (and did) make it vanish during
    // normal use. The ticker keeps the "x ago" label current.
    val glanceActivity: StateFlow<GlanceActivity?> = combine(
        repo.getNotifications(),
        timeBucketTicker,
    ) { events, _ ->
        val latest = events.maxByOrNull { it.timestamp }
        val now = System.currentTimeMillis()
        val age = latest?.let { now - it.timestamp } ?: Long.MAX_VALUE
        val fresh = latest != null && age <= GLANCE_ACTIVITY_RECENT_MS
        android.util.Log.d(
            "HomeHealth_Glance",
            "activity events=${events.size} latest='${latest?.title}' age=${age / 1000}s fresh=$fresh",
        )
        if (!fresh) return@combine null
        GlanceActivity(
            title = latest!!.title,
            iconKey = activityIconKey(latest.kind),
            relative = relativeTimeShort(age),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            brightnessChannel.receiveAsFlow()
                .debounce(250L)
                .collect { (entityId, brightness) ->
                    repo.setLightBrightness(entityId, brightness)
                }
        }
        viewModelScope.launch {
            colorChannel.receiveAsFlow()
                .debounce(250L)
                .collect { (entityId, rgb) ->
                    repo.setLightColor(entityId, rgb.first, rgb.second, rgb.third)
                }
        }
        viewModelScope.launch {
            colorTempChannel.receiveAsFlow()
                .debounce(250L)
                .collect { (entityId, kelvin) ->
                    repo.setLightColorTemp(entityId, kelvin)
                }
        }
        // Seed room visibility pref with all HA room IDs on first launch.
        viewModelScope.launch {
            repo.getAllRooms().first().let { allRooms ->
                userPreferences.initRoomsIfNeeded(allRooms.map { it.id })
            }
        }
        // Seed at-a-glance tiles once on first launch, preserving existing setups.
        // Prefer the legacy 5-slot config (devices-on first so it becomes the large
        // featured tile); if the user never configured those, fall back to repo-
        // suggested defaults so the section isn't empty for a brand-new user.
        viewModelScope.launch {
            val cfg = userPreferences.config.first()
            val fromConfig = listOf(
                cfg.entityLightsOn,
                cfg.entityOutsideTemp,
                cfg.entityInsideTemp,
                cfg.entityAqi,
                cfg.entityDoorbell,
            ).filter { it.isNotBlank() }
            val defaults = fromConfig.ifEmpty { repo.suggestedGlanceEntityIds() }
            userPreferences.initGlanceTilesIfNeeded(defaults)
        }
    }

    fun selectRoom(room: HaRoom?) {
        _selectedRoom.value = room
        if (room == null) {
            // Reset all transient per-room overlays when the sheet is dismissed.
            _showWidgetCatalog.value = false
            _showSwitchEntityPicker.value = false
            _showSensorEntityPicker.value = false
            _showCameraEntityPicker.value = false
            _showMediaEntityPicker.value = false
            _showAirQualityConfig.value = false
            _openedSwitchWidget.value = null
            _openedSensorWidget.value = null
            _openedCameraWidget.value = null
            _ptzConfigCamera.value = null
        }
    }
    fun toggleShowAllRooms() { _showAllRooms.value = !_showAllRooms.value }

    fun reorderRooms(rooms: List<HaRoom>) {
        viewModelScope.launch { userPreferences.setRoomOrder(rooms.map { it.id }) }
    }

    fun hideRoom(roomId: String) {
        viewModelScope.launch { userPreferences.hideRoom(roomId) }
    }

    fun showRoom(roomId: String) {
        viewModelScope.launch {
            userPreferences.showRoom(roomId)
            _showRoomPicker.value = false
        }
    }

    fun showRoomPicker() { _showRoomPicker.value = true }
    fun hideRoomPicker() { _showRoomPicker.value = false }

    fun showRoomSensorEditor(roomId: String) { _editingSensorsRoomId.value = roomId }
    fun hideRoomSensorEditor() { _editingSensorsRoomId.value = null }

    fun setRoomTempSensor(roomId: String, entityId: String) {
        viewModelScope.launch { userPreferences.setRoomTempSensor(roomId, entityId) }
    }
    fun setRoomHumiditySensor(roomId: String, entityId: String) {
        viewModelScope.launch { userPreferences.setRoomHumiditySensor(roomId, entityId) }
    }

    // ── Glance-tile controls ──────────────────────────────────────────────────────
    // Tapping a glance tile opens the SAME control surface the room sheet uses: lights →
    // the light control sheet, climate → the climate sheet (both resolved by entity_id);
    // simple togglables just toggle in place. Sensors etc. have no control → no-op.
    private val _glanceControlEntityId = MutableStateFlow<String?>(null)

    fun onGlanceTileTap(entityId: String) {
        when (entityId.substringBefore('.')) {
            "light", "climate" -> _glanceControlEntityId.value = entityId
            "switch", "input_boolean", "fan", "lock" ->
                viewModelScope.launch { repo.toggleEntity(entityId) }
        }
    }

    fun closeGlanceControl() { _glanceControlEntityId.value = null }

    val glanceControlLight: StateFlow<HaLight?> = _glanceControlEntityId.flatMapLatest { id ->
        if (id != null && id.startsWith("light.")) repo.getLight(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val glanceControlClimate: StateFlow<HaClimate?> = _glanceControlEntityId.flatMapLatest { id ->
        if (id != null && id.startsWith("climate.")) repo.getClimate(id) else flowOf(null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleLight(entityId: String, isOn: Boolean) {
        viewModelScope.launch { repo.toggleLight(entityId, isOn) }
    }

    fun setLightBrightness(entityId: String, brightness: Int) {
        brightnessChannel.trySend(entityId to brightness)
    }

    fun setLightColor(entityId: String, r: Int, g: Int, b: Int) {
        colorChannel.trySend(entityId to Triple(r, g, b))
    }

    fun setLightColorTemp(entityId: String, kelvin: Int) {
        colorTempChannel.trySend(entityId to kelvin)
    }

    fun setClimateTarget(entityId: String, temperature: Float) {
        viewModelScope.launch { repo.setClimateTemperature(entityId, temperature) }
    }

    fun setClimateMode(entityId: String, mode: String) {
        viewModelScope.launch { repo.setClimateHvacMode(entityId, mode) }
    }

    fun setClimateFanMode(entityId: String, fanMode: String) {
        viewModelScope.launch { repo.setClimateFanMode(entityId, fanMode) }
    }

    fun setClimateModeOrder(entityId: String, modes: List<String>) {
        viewModelScope.launch { userPreferences.setClimateModeOrder(entityId, modes) }
    }

    fun setClimateFanOrder(entityId: String, fans: List<String>) {
        viewModelScope.launch { userPreferences.setClimateFanOrder(entityId, fans) }
    }

    fun showScenePicker() { _showScenePicker.value = true }
    fun hideScenePicker() { _showScenePicker.value = false }
    fun showFavoritePicker() { _showFavoritePicker.value = true }
    fun hideFavoritePicker() { _showFavoritePicker.value = false }
    fun showGlanceTilePicker(domain: String) { _glanceTilePickerDomain.value = domain }
    fun hideGlanceTilePicker() { _glanceTilePickerDomain.value = null }

    fun addGlanceTile(entityId: String) {
        viewModelScope.launch {
            userPreferences.addGlanceTile(entityId)
            _glanceTilePickerDomain.value = null
        }
    }

    fun removeGlanceTile(entityId: String) {
        viewModelScope.launch { userPreferences.removeGlanceTile(entityId) }
    }

    fun reorderGlanceTiles(tiles: List<GlanceTile>) {
        viewModelScope.launch { userPreferences.setGlanceTileOrder(tiles.map { it.entityId }) }
    }

    fun setSmartGlanceEnabled(value: Boolean) {
        viewModelScope.launch { userPreferences.setSmartGlanceEnabled(value) }
    }

    fun addQuickScene(sceneId: String) {
        viewModelScope.launch {
            userPreferences.addQuickScene(sceneId)
            _showScenePicker.value = false
        }
    }

    fun removeQuickScene(sceneId: String) {
        viewModelScope.launch { userPreferences.removeQuickScene(sceneId) }
    }

    fun reorderScenes(scenes: List<HaScene>) {
        viewModelScope.launch { userPreferences.setQuickSceneOrder(scenes.map { it.id }) }
    }

    fun runScene(sceneId: String) {
        viewModelScope.launch {
            repo.runScene(sceneId)
            // Learn from it: this run feeds the on-device "usually now" suggestion.
            glanceInteractions.recordSceneRun(sceneId)
        }
    }

    fun addFavorite(entityId: String) {
        viewModelScope.launch {
            userPreferences.addFavorite(entityId)
            _showFavoritePicker.value = false
        }
    }

    fun removeFavorite(entityId: String) {
        viewModelScope.launch { userPreferences.removeFavorite(entityId) }
    }

    fun reorderFavorites(favs: List<HaFavorite>) {
        viewModelScope.launch { userPreferences.setFavoriteOrder(favs.map { it.id }) }
    }

    fun toggleFavorite(entityId: String) {
        viewModelScope.launch { repo.toggleEntity(entityId) }
    }

    fun toggleEntity(entityId: String) {
        viewModelScope.launch { repo.toggleEntity(entityId) }
    }

    fun addSwitchWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Switch(entityId))
            // After adding, close both the picker and the catalog so the user lands
            // back on the More tab with the new widget visible.
            _showSwitchEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun addSensorWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Sensor(entityId))
            _showSensorEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    // Step 1 of the add-camera flow: the user picked a camera entity. Rather than
    // adding it immediately, open the PTZ setup sheet ("do you have PTZ controls?").
    // The widget is committed in confirmAddCamera() once they answer.
    fun beginAddCamera(entityId: String) {
        _showCameraEntityPicker.value = false
        _showWidgetCatalog.value = false
        _ptzConfigCamera.value = RoomWidget.Camera(entityId, null)
    }

    fun addMediaWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            // Section was set when the picker opened (Media tab → MEDIA, catalog → MORE),
            // scoping the widget so it appears only in the tab it was added from.
            widgetPreferences.addWidget(areaId, RoomWidget.Media(entityId, mediaPickerSection))
            _showMediaEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun addClimateWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Climate(entityId, climatePickerSection))
            _showClimateEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun addLocationWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Location(entityId))
            _showLocationEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun removeWidget(areaId: String, widgetId: String) {
        viewModelScope.launch { widgetPreferences.removeWidget(areaId, widgetId) }
    }

    // [reordered] is only the visible subset of one section's widgets (More is the only
    // tab that reorders). Merge it back into the full stored list by refilling each
    // reordered widget's original slot in sequence, leaving other-section widgets in
    // place — otherwise committing the subset would drop the hidden sections' widgets.
    fun reorderWidgets(areaId: String, reordered: List<RoomWidget>) {
        viewModelScope.launch {
            val current = widgetPreferences.widgetsForRoom(areaId).first()
            val subsetIds = reordered.map { it.id }.toSet()
            val iter = reordered.iterator()
            val merged = current.map { w -> if (w.id in subsetIds && iter.hasNext()) iter.next() else w }
            widgetPreferences.setOrder(areaId, merged)
        }
    }

    fun showWidgetCatalog() { _showWidgetCatalog.value = true }
    fun hideWidgetCatalog() { _showWidgetCatalog.value = false }

    fun showSwitchEntityPicker() { _showSwitchEntityPicker.value = true }
    fun hideSwitchEntityPicker() { _showSwitchEntityPicker.value = false }

    fun showSensorEntityPicker() { _showSensorEntityPicker.value = true }
    fun hideSensorEntityPicker() { _showSensorEntityPicker.value = false }

    fun showCameraEntityPicker() { _showCameraEntityPicker.value = true }
    fun hideCameraEntityPicker() { _showCameraEntityPicker.value = false }

    fun showMediaEntityPicker(section: WidgetSection = WidgetSection.MORE) {
        mediaPickerSection = section
        _showMediaEntityPicker.value = true
    }
    fun hideMediaEntityPicker() { _showMediaEntityPicker.value = false }

    fun showClimateEntityPicker(section: WidgetSection = WidgetSection.MORE) {
        climatePickerSection = section
        _showClimateEntityPicker.value = true
    }
    fun hideClimateEntityPicker() { _showClimateEntityPicker.value = false }

    fun showLocationEntityPicker() { _showLocationEntityPicker.value = true }
    fun hideLocationEntityPicker() { _showLocationEntityPicker.value = false }

    fun showAirQualityPicker() { _showAirQualityConfig.value = true }
    fun hideAirQualityConfig() { _showAirQualityConfig.value = false }

    fun addAirQualityWidget(
        areaId: String,
        baseEntityId: String,
        cleanDurationId: String,
        moderateDurationId: String,
        poorDurationId: String,
    ) {
        if (baseEntityId.isBlank()) return
        viewModelScope.launch {
            widgetPreferences.addWidget(
                areaId,
                RoomWidget.AirQuality(baseEntityId, cleanDurationId, moderateDurationId, poorDurationId),
            )
            _showAirQualityConfig.value = false
            _showWidgetCatalog.value = false
        }
    }

    // ── Media player actions — commit on release for sliders, fire-and-forget
    // for transport. Each goes through HomeRepository which checks auth and
    // no-ops in demo mode.
    fun mediaPlayPause(entityId: String) {
        viewModelScope.launch { repo.mediaPlayPause(entityId) }
    }
    fun mediaSkipNext(entityId: String) {
        viewModelScope.launch { repo.mediaSkipNext(entityId) }
    }
    fun mediaSkipPrev(entityId: String) {
        viewModelScope.launch { repo.mediaSkipPrev(entityId) }
    }
    fun mediaSetVolume(entityId: String, volume: Float) {
        viewModelScope.launch { repo.mediaSetVolume(entityId, volume) }
    }
    fun mediaSetShuffle(entityId: String, on: Boolean) {
        viewModelScope.launch { repo.mediaSetShuffle(entityId, on) }
    }
    fun mediaSetRepeat(entityId: String, mode: MediaRepeatMode) {
        viewModelScope.launch { repo.mediaSetRepeat(entityId, mode) }
    }
    fun mediaSeek(entityId: String, progress: Float) {
        viewModelScope.launch { repo.mediaSeek(entityId, progress) }
    }

    // ── Announce / text-to-speech composer ───────────────────────────────────
    // Resolves the open player (Echo vs standard) + the saved TTS defaults into one
    // state object. Engines are loaded once when the sheet opens (a one-shot WS call).
    val ttsComposerState: StateFlow<TtsComposerUiState> = combine(
        _ttsComposerEntityId.flatMapLatest { id ->
            if (id == null) flowOf<TtsTarget?>(null) else repo.getTtsTarget(id)
        },
        _ttsEngines,
        userPreferences.ttsDefaults,
        _ttsSending,
        _ttsComposerEntityId,
    ) { target, engines, defaults, sending, id ->
        TtsComposerUiState(
            visible = id != null,
            target = target,
            engines = engines,
            defaultEngineId = defaults.engineId,
            defaultVoiceId = defaults.voiceId,
            defaultLanguage = defaults.language,
            sending = sending,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsComposerUiState())

    fun openTtsComposer(entityId: String) {
        if (entityId.isBlank()) return
        _ttsComposerEntityId.value = entityId
        // Refresh the engine list each open so a newly-installed TTS integration shows up.
        viewModelScope.launch { _ttsEngines.value = repo.getTtsEngines() }
    }

    fun closeTtsComposer() { _ttsComposerEntityId.value = null }

    // Voices for the inline override picker. Suspends on a one-shot WS call; the sheet
    // caches the result in its own state.
    suspend fun loadTtsVoices(engineId: String, language: String): List<HaTtsVoice> =
        repo.getTtsVoices(engineId, language)

    // Send the composed message. engine/voice/language are non-null only when the user
    // picked an inline override; otherwise the repo falls back to the saved defaults.
    // Closes the sheet on completion.
    fun sendTts(message: String, announce: Boolean, engineId: String?, voiceId: String?, language: String?) {
        val entityId = _ttsComposerEntityId.value ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            _ttsSending.value = true
            try {
                repo.sendTts(entityId, message, announce, engineId, voiceId, language)
            } finally {
                _ttsSending.value = false
                _ttsComposerEntityId.value = null
            }
        }
    }

    // ── Music Assistant search ───────────────────────────────────────────────
    // Opened from the media card's search pill (MA players only). The sheet owns its
    // query/results; this just resolves which player it targets + its display name.
    val maSearchState: StateFlow<MaSearchUiState> = _maSearchEntityId.flatMapLatest { id ->
        if (id == null) flowOf(MaSearchUiState())
        else repo.getMediaPlayer(id).map { media ->
            MaSearchUiState(
                visible = true,
                entityId = id,
                playerName = media?.friendlyName ?: "this player",
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaSearchUiState())

    fun openMaSearch(entityId: String) {
        if (entityId.isNotBlank()) _maSearchEntityId.value = entityId
    }

    fun closeMaSearch() { _maSearchEntityId.value = null }

    // Suspend pass-through: the sheet debounces + caches results in its own state.
    suspend fun searchMusicAssistant(query: String, type: MaMediaType?, libraryOnly: Boolean): MaSearchResults {
        val entityId = _maSearchEntityId.value ?: return MaSearchResults()
        return repo.searchMusicAssistant(entityId, query, type, libraryOnly)
    }

    fun playMaItem(item: MaSearchItem, mode: MaEnqueueMode) {
        val entityId = _maSearchEntityId.value ?: return
        viewModelScope.launch { repo.playMusicAssistantMedia(entityId, item, mode) }
    }

    fun openSwitchDetail(widget: RoomWidget.Switch) { _openedSwitchWidget.value = widget }
    fun closeSwitchDetail() { _openedSwitchWidget.value = null }

    fun openSensorDetail(widget: RoomWidget.Sensor) { _openedSensorWidget.value = widget }
    fun closeSensorDetail() { _openedSensorWidget.value = null }

    fun openCameraDetail(widget: RoomWidget.Camera) { _openedCameraWidget.value = widget }
    fun closeCameraDetail() { _openedCameraWidget.value = null }

    fun openLocationDetail(widget: RoomWidget.Location) { _openedLocationWidget.value = widget }
    fun closeLocationDetail() { _openedLocationWidget.value = null }

    // ── Camera add + PTZ ─────────────────────────────────────────────────────
    // Cancel the in-progress add (the camera is not committed).
    fun closePtzConfig() { _ptzConfigCamera.value = null }

    /** Step 2: commit the picked camera to [areaId] with its chosen PTZ bindings (ptz=null = no PTZ). */
    fun confirmAddCamera(areaId: String, entityId: String, ptz: com.uc.homehealth.data.CameraPtz?) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Camera(entityId, ptz))
            _ptzConfigCamera.value = null
        }
    }

    /** Fire a PTZ direction press (button.press / switch.turn_on / etc.). No-op if blank. */
    fun pressPtz(entityId: String) {
        if (entityId.isBlank()) return
        viewModelScope.launch { repo.pressEntity(entityId) }
    }

    // Suspend pass-through used by the camera detail sheet's HLS fallback resolver.
    suspend fun getCameraStreamUrl(entityId: String): String? = repo.getCameraStreamUrl(entityId)

    // WebRTC signaling pass-throughs for the camera detail sheet's live player.
    suspend fun startCameraWebRtc(
        entityId: String,
        offerSdp: String,
        onSignal: (com.uc.homehealth.data.WebRtcSignal) -> Unit,
    ): Int? = repo.startCameraWebRtc(entityId, offerSdp, onSignal)

    fun sendCameraWebRtcCandidate(
        entityId: String,
        sessionId: String,
        candidate: com.uc.homehealth.data.WebRtcIceCandidate,
    ) = repo.sendCameraWebRtcCandidate(entityId, sessionId, candidate)

    fun stopCameraWebRtc(subscriptionId: Int) = repo.stopCameraWebRtc(subscriptionId)

    suspend fun getCameraWebRtcConfig(entityId: String): List<com.uc.homehealth.data.WebRtcIceServer>? =
        repo.getCameraWebRtcConfig(entityId)

    /** Configured go2rtc base URL (blank = not set → MSE disabled, use WebRTC/HLS). */
    val go2rtcUrl: StateFlow<String> = authPreferences.authState
        .map { it.go2rtcUrl }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** User-initiated reconnect from the offline state. Drops backoff, reopens WS now. */
    fun reconnect() { repo.reconnectNow() }

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /** Pull-to-refresh trigger. Reopens the WS (no-op in demo) and shows the indicator briefly. */
    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        repo.reconnectNow()
        viewModelScope.launch {
            kotlinx.coroutines.delay(700)
            _isRefreshing.value = false
        }
    }

    fun addFlightAutomation(entityId: String) {
        viewModelScope.launch { userPreferences.addFlightAutomation(entityId) }
    }
    fun removeFlightAutomation(entityId: String) {
        viewModelScope.launch { userPreferences.removeFlightAutomation(entityId) }
    }
    fun reorderFlightAutomations(ids: List<String>) {
        viewModelScope.launch { userPreferences.setFlightAutomationOrder(ids) }
    }
    fun triggerFlightAutomation(entityId: String) {
        viewModelScope.launch { repo.toggleEntity(entityId) }
    }

    fun openFlightSheet() { _showFlightSheet.value = true }
    fun closeFlightSheet() { _showFlightSheet.value = false }

    /**
     * Optimistic removal: hide the flight in the UI immediately, then ask HA to drop it.
     * If HA hasn't dropped it after 15s, clear the pending mark so the flight quietly
     * reappears instead of leaving a phantom hole in the list.
     */
    fun removeTrackedFlight(flight: HaFlight) {
        viewModelScope.launch {
            _pendingFlightRemovals.value = _pendingFlightRemovals.value + flight.id
            repo.removeTrackedFlight(flight.flightNumber)
            try {
                withTimeout(15_000) {
                    repo.getTrackedFlights().first { upstream -> upstream.none { it.id == flight.id } }
                }
            } catch (_: TimeoutCancellationException) {
                // HA didn't drop it — silently undo the optimistic hide so the user
                // can see it's still tracked and try again.
            }
            _pendingFlightRemovals.value = _pendingFlightRemovals.value - flight.id
        }
    }

    fun addTrackedFlight(query: String) {
        viewModelScope.launch {
            val baseline = repo.getTrackedFlights().first().size
            _isAddingFlight.value = true
            _flightAddError.value = null
            repo.addTrackedFlight(query)
            try {
                withTimeout(15_000) {
                    repo.getTrackedFlights().first { it.size > baseline }
                }
            } catch (_: TimeoutCancellationException) {
                // FR24 didn't add it within 15s. Surface a banner so the user knows
                // the request didn't go through and can decide whether to retry or
                // troubleshoot the integration.
                _flightAddError.value =
                    "Flight \"$query\" still not added — there may be an issue with " +
                        "the FlightRadar24 Integration. Update it in HACS or try again later."
            }
            _isAddingFlight.value = false
        }
    }

    fun dismissFlightAddError() { _flightAddError.value = null }

    /**
     * Queue a flight add for a future date. The FR24 integration has no date input —
     * it always latches onto the nearest instance of a number — so the only correct
     * way to track "Friday's DL113" is to hold the command on-device and send it on
     * Friday itself (FlightScheduleWorker). A target of today (or earlier) degenerates
     * to a normal immediate add.
     */
    fun scheduleTrackedFlight(query: String, targetEpochDay: Long) {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return
        if (targetEpochDay <= LocalDate.now().toEpochDay()) {
            addTrackedFlight(q)
            return
        }
        viewModelScope.launch {
            val dayLabel = LocalDate.ofEpochDay(targetEpochDay).format(scheduledDayFormatter)
            val duplicate = flightScheduleStore.all().any {
                it.query == q && it.targetEpochDay == targetEpochDay && it.status != ScheduledFlightStatus.FAILED
            }
            if (duplicate) {
                _flightAddError.value = "\"$q\" is already scheduled for $dayLabel."
                return@launch
            }
            flightScheduleStore.add(
                ScheduledFlightAdd(
                    id = "$q-$targetEpochDay-${System.currentTimeMillis()}",
                    query = q,
                    targetEpochDay = targetEpochDay,
                    createdAtMs = System.currentTimeMillis(),
                ),
            )
            activityLog.record("auto", "Scheduled flight $q", "Track command will be sent on $dayLabel")
            flightScheduleScheduler.reschedule()
        }
    }

    /**
     * Drop a queued entry. Deliberately leaves any already-tracked flight alone: once
     * the command was SENT the flight shows in the normal carousel with its own delete,
     * and removing by number could hit a same-number flight the user tracks manually.
     */
    fun cancelScheduledFlight(id: String) {
        viewModelScope.launch {
            flightScheduleStore.remove(id)
            flightScheduleScheduler.reschedule()
        }
    }
}
