@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)

package com.uc.homehealth.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uc.homehealth.data.GlanceConfig
import com.uc.homehealth.data.HaAutomation
import com.uc.homehealth.data.HaClimate
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.data.HaFavorite
import com.uc.homehealth.data.HaFlight
import com.uc.homehealth.data.HaLight
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.data.HaScene
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.data.AuthPreferences
import com.uc.homehealth.data.HomeRepository
import com.uc.homehealth.data.RoomSensorOverride
import com.uc.homehealth.data.RoomWidget
import com.uc.homehealth.data.UserPreferences
import com.uc.homehealth.data.WidgetPreferences
import com.uc.homehealth.data.WsConnectionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import javax.inject.Inject

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
    val selectedRoom: HaRoom? = null,
    val selectedRoomLights: List<HaLight> = emptyList(),
    val selectedRoomClimate: HaClimate? = null,
    val selectedRoomTempHistory: List<Float> = emptyList(),
    val selectedRoomWidgets: List<RoomWidget> = emptyList(),
    val widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    val widgetHistories: Map<String, List<Float>> = emptyMap(),
    val widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    val widgetMediaStates: Map<String, HaMedia?> = emptyMap(),
    val showWidgetCatalog: Boolean = false,
    val showSwitchEntityPicker: Boolean = false,
    val showSensorEntityPicker: Boolean = false,
    val showCameraEntityPicker: Boolean = false,
    val showMediaEntityPicker: Boolean = false,
    val openedSwitchWidget: RoomWidget.Switch? = null,
    val openedSensorWidget: RoomWidget.Sensor? = null,
    val openedCameraWidget: RoomWidget.Camera? = null,
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
    val roomSensorOverrides: Map<String, RoomSensorOverride> = emptyMap(),
    val editingSensorsForRoom: HaRoom? = null,
    // False until the first combined emission lands (auth + WS status both
    // resolved). DashboardScreen uses this to avoid flashing OfflineState
    // before bootstrap completes on cold launch.
    val loaded: Boolean = false,
) {
    val isOnline: Boolean get() = connectionStatus == WsConnectionStatus.READY
}

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
)

private data class RoomWidgetBundle(
    val widgets: List<RoomWidget>,
    val states: Map<String, HaEntityValue?>,
    val histories: Map<String, List<Float>>,
    val cameraSnapshots: Map<String, String?>,
    val mediaStates: Map<String, HaMedia?>,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val userPreferences: UserPreferences,
    private val authPreferences: AuthPreferences,
    private val widgetPreferences: WidgetPreferences,
) : ViewModel() {

    private val _selectedRoom = MutableStateFlow<HaRoom?>(null)
    private val _showAllRooms = MutableStateFlow(false)
    private val _showScenePicker = MutableStateFlow(false)
    private val _showFavoritePicker = MutableStateFlow(false)
    private val _showWidgetCatalog = MutableStateFlow(false)
    private val _showSwitchEntityPicker = MutableStateFlow(false)
    private val _showSensorEntityPicker = MutableStateFlow(false)
    private val _showCameraEntityPicker = MutableStateFlow(false)
    private val _showMediaEntityPicker = MutableStateFlow(false)
    private val _openedSwitchWidget = MutableStateFlow<RoomWidget.Switch?>(null)
    private val _openedSensorWidget = MutableStateFlow<RoomWidget.Sensor?>(null)
    private val _openedCameraWidget = MutableStateFlow<RoomWidget.Camera?>(null)
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

    private val glanceState = combine(userPreferences.config, glanceValues) { config, values ->
        Triple(config.userName, config.template, values)
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
            if (room == null) flowOf(RoomWidgetBundle(emptyList(), emptyMap(), emptyMap(), emptyMap(), emptyMap()))
            else widgetPreferences.widgetsForRoom(room.id).flatMapLatest { widgets ->
                val stateIds = widgets.mapNotNull {
                    when (it) {
                        is RoomWidget.Switch -> it.entityId
                        is RoomWidget.Sensor -> it.entityId
                        is RoomWidget.Camera -> it.entityId
                        is RoomWidget.Media -> it.entityId
                    }
                }
                val historyIds = widgets.filterIsInstance<RoomWidget.Sensor>().map { it.entityId }
                val cameraIds = widgets.filterIsInstance<RoomWidget.Camera>().map { it.entityId }
                val mediaIds = widgets.filterIsInstance<RoomWidget.Media>().map { it.entityId }
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
                combine(stateFlow, historyFlow, snapshotFlow, mediaFlow) { states, histories, snapshots, media ->
                    RoomWidgetBundle(widgets, states, histories, snapshots, media)
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
        CoreData(rooms, allRooms, scenes, favs, allScenes, allEntities, allAutomations, overrides, editingId)
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
            widgetsBundle.cameraSnapshots, widgetsBundle.mediaStates,
        )
    }

    private data class StatusBundle(
        val showScenePicker: Boolean,
        val showFavoritePicker: Boolean,
        val showRoomPicker: Boolean,
        val showWidgetCatalog: Boolean,
        val showSwitchEntityPicker: Boolean,
        val showSensorEntityPicker: Boolean,
        val showCameraEntityPicker: Boolean,
        val showMediaEntityPicker: Boolean,
        val openedSwitchWidget: RoomWidget.Switch?,
        val openedSensorWidget: RoomWidget.Sensor?,
        val openedCameraWidget: RoomWidget.Camera?,
        val connectionStatus: WsConnectionStatus,
        val isLoggedIn: Boolean,
        val flights: List<HaFlight>,
        val isFlightRadar24Available: Boolean,
        val showFlightSheet: Boolean,
        val isAddingFlight: Boolean,
        val flightAutomationIds: List<String>,
        val flightAddError: String?,
    )

    private data class WidgetUiState(
        val showCatalog: Boolean,
        val showSwitchPicker: Boolean,
        val showSensorPicker: Boolean,
        val showCameraPicker: Boolean,
        val showMediaPicker: Boolean,
        val openedSwitch: RoomWidget.Switch?,
        val openedSensor: RoomWidget.Sensor?,
        val openedCamera: RoomWidget.Camera?,
    )

    // combine() caps at 5 sources — bundle the widget-related flows into two groups
    // then merge so we can hand a single value back into the parent combine().
    private val widgetUiState = combine(
        combine(_showWidgetCatalog, _showSwitchEntityPicker, _showSensorEntityPicker, _showCameraEntityPicker, _showMediaEntityPicker) {
            cat, sw, se, ca, me -> listOf(cat, sw, se, ca, me)
        },
        combine(_openedSwitchWidget, _openedSensorWidget, _openedCameraWidget) { sw, se, ca ->
            Triple(sw, se, ca)
        },
    ) { flags, opens ->
        WidgetUiState(
            showCatalog = flags[0],
            showSwitchPicker = flags[1],
            showSensorPicker = flags[2],
            showCameraPicker = flags[3],
            showMediaPicker = flags[4],
            openedSwitch = opens.first,
            openedSensor = opens.second,
            openedCamera = opens.third,
        )
    }

    private val statusBundle = combine(
        combine(_showScenePicker, _showFavoritePicker, _showRoomPicker) { scene, fav, room -> Triple(scene, fav, room) },
        widgetUiState,
        combine(repo.connectionStatus(), authPreferences.authState.map { it.accessToken.isNotEmpty() }) { ws, authed ->
            ws to authed
        },
        combine(
            repo.getTrackedFlights(),
            userPreferences.flightAutomationIds,
            repo.isFlightRadar24Available(),
            _pendingFlightRemovals,
        ) { flights, autoIds, available, pending ->
            val visible = if (pending.isEmpty()) flights else flights.filter { it.id !in pending }
            Triple(visible, autoIds, available)
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
            openedSwitchWidget = widget.openedSwitch,
            openedSensorWidget = widget.openedSensor,
            openedCameraWidget = widget.openedCamera,
            connectionStatus = wsAuth.first,
            isLoggedIn = wsAuth.second,
            flights = flightData.first,
            isFlightRadar24Available = flightData.third,
            showFlightSheet = flightUi.first,
            isAddingFlight = flightUi.second,
            flightAutomationIds = flightData.second,
            flightAddError = flightUi.third,
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
            userName = glance.first,
            glanceTemplate = glance.second,
            glanceValues = glance.third,
            selectedRoom = extras.room,
            selectedRoomLights = extras.lights,
            selectedRoomClimate = extras.climate,
            selectedRoomTempHistory = extras.history,
            selectedRoomWidgets = extras.widgets,
            widgetStates = extras.widgetStates,
            widgetHistories = extras.widgetHistories,
            widgetCameraSnapshots = extras.widgetCameraSnapshots,
            widgetMediaStates = extras.widgetMediaStates,
            showAllRooms = showAll,
            showScenePicker = status.showScenePicker,
            showFavoritePicker = status.showFavoritePicker,
            showWidgetCatalog = status.showWidgetCatalog,
            showSwitchEntityPicker = status.showSwitchEntityPicker,
            showSensorEntityPicker = status.showSensorEntityPicker,
            showCameraEntityPicker = status.showCameraEntityPicker,
            showMediaEntityPicker = status.showMediaEntityPicker,
            openedSwitchWidget = status.openedSwitchWidget,
            openedSensorWidget = status.openedSensorWidget,
            openedCameraWidget = status.openedCameraWidget,
            connectionStatus = status.connectionStatus,
            isLoggedIn = status.isLoggedIn,
            flights = status.flights,
            isFlightRadar24Available = status.isFlightRadar24Available,
            showFlightSheet = status.showFlightSheet,
            isAddingFlight = status.isAddingFlight,
            flightAutomationIds = status.flightAutomationIds,
            allAutomations = core.allAutomations,
            flightAddError = status.flightAddError,
            roomSensorOverrides = core.roomSensorOverrides,
            editingSensorsForRoom = core.editingSensorsRoomId
                ?.let { id -> core.allRooms.firstOrNull { it.id == id } },
            loaded = true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

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
            _openedSwitchWidget.value = null
            _openedSensorWidget.value = null
            _openedCameraWidget.value = null
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

    fun showScenePicker() { _showScenePicker.value = true }
    fun hideScenePicker() { _showScenePicker.value = false }
    fun showFavoritePicker() { _showFavoritePicker.value = true }
    fun hideFavoritePicker() { _showFavoritePicker.value = false }

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
        viewModelScope.launch { repo.runScene(sceneId) }
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

    fun addCameraWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Camera(entityId))
            _showCameraEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun addMediaWidget(areaId: String, entityId: String) {
        viewModelScope.launch {
            widgetPreferences.addWidget(areaId, RoomWidget.Media(entityId))
            _showMediaEntityPicker.value = false
            _showWidgetCatalog.value = false
        }
    }

    fun removeWidget(areaId: String, widgetId: String) {
        viewModelScope.launch { widgetPreferences.removeWidget(areaId, widgetId) }
    }

    fun reorderWidgets(areaId: String, widgets: List<RoomWidget>) {
        viewModelScope.launch { widgetPreferences.setOrder(areaId, widgets) }
    }

    fun showWidgetCatalog() { _showWidgetCatalog.value = true }
    fun hideWidgetCatalog() { _showWidgetCatalog.value = false }

    fun showSwitchEntityPicker() { _showSwitchEntityPicker.value = true }
    fun hideSwitchEntityPicker() { _showSwitchEntityPicker.value = false }

    fun showSensorEntityPicker() { _showSensorEntityPicker.value = true }
    fun hideSensorEntityPicker() { _showSensorEntityPicker.value = false }

    fun showCameraEntityPicker() { _showCameraEntityPicker.value = true }
    fun hideCameraEntityPicker() { _showCameraEntityPicker.value = false }

    fun showMediaEntityPicker() { _showMediaEntityPicker.value = true }
    fun hideMediaEntityPicker() { _showMediaEntityPicker.value = false }

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

    fun openSwitchDetail(widget: RoomWidget.Switch) { _openedSwitchWidget.value = widget }
    fun closeSwitchDetail() { _openedSwitchWidget.value = null }

    fun openSensorDetail(widget: RoomWidget.Sensor) { _openedSensorWidget.value = widget }
    fun closeSensorDetail() { _openedSensorWidget.value = null }

    fun openCameraDetail(widget: RoomWidget.Camera) { _openedCameraWidget.value = widget }
    fun closeCameraDetail() { _openedCameraWidget.value = null }

    // Suspend pass-through used by the camera detail sheet's HLS resolver.
    suspend fun getCameraStreamUrl(entityId: String): String? = repo.getCameraStreamUrl(entityId)

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
}
