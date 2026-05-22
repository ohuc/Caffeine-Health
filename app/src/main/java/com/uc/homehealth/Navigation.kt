package com.uc.homehealth

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.uc.homehealth.ui.components.BottomNavBar
import com.uc.homehealth.ui.components.ExitDemoBanner
import com.uc.homehealth.ui.components.FavoritePickerSheet
import com.uc.homehealth.ui.components.GlanceConfigSheet
import com.uc.homehealth.ui.components.RoomPickerSheet
import com.uc.homehealth.ui.components.RoomSensorEditorSheet
import com.uc.homehealth.ui.components.FlightSheetOverlay
import com.uc.homehealth.ui.components.SceneSelectorSheet
import com.uc.homehealth.ui.components.CameraDetailSheet
import com.uc.homehealth.ui.components.CameraEntityPickerSheet
import com.uc.homehealth.ui.components.MediaCardCallbacks
import com.uc.homehealth.ui.components.MediaPlayerEntityPickerSheet
import com.uc.homehealth.ui.components.SensorDetailSheet
import com.uc.homehealth.ui.components.SensorEntityPickerSheet
import com.uc.homehealth.ui.components.SwitchDetailSheet
import com.uc.homehealth.ui.components.SwitchEntityPickerSheet
import com.uc.homehealth.ui.screens.ActivityScreen
import com.uc.homehealth.ui.screens.DashboardScreen
import com.uc.homehealth.ui.screens.HaAuthWebViewScreen
import com.uc.homehealth.ui.screens.OnboardingScreen
import com.uc.homehealth.ui.screens.RoomSheetOverlay
import com.uc.homehealth.ui.screens.SettingsScreen
import com.uc.homehealth.ui.viewmodel.DashboardViewModel
import com.uc.homehealth.ui.viewmodel.OnboardingViewModel
import com.uc.homehealth.ui.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey

@Serializable object DashboardRoute : AppRoute
@Serializable object ActivityRoute : AppRoute
@Serializable object SettingsRoute : AppRoute
@Serializable data class HaAuthRoute(val haUrl: String) : AppRoute

@Composable
fun HomeHealthNavHost() {
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val onboardingState by onboardingViewModel.uiState.collectAsStateWithLifecycle()

    // Wait for the first DataStore emission before deciding which root screen
    // to show. Without this gate the synchronous initialValue (onboardingComplete=false)
    // briefly renders OnboardingScreen on every cold launch.
    if (!onboardingState.loaded) {
        return
    }

    // First-launch gate. While the flag is false the user is on the OnboardingScreen
    // and the rest of the app (NavHost, bottom nav, overlays) stays uncomposed —
    // no need to bring up the dashboard's WS connection just to hide it again.
    if (!onboardingState.onboardingComplete) {
        OnboardingScreen(
            onFinished = { /* uiState flip handled inside the screen */ },
            viewModel = onboardingViewModel,
        )
        return
    }

    val backStack = rememberNavBackStack(DashboardRoute)
    val currentRoute = backStack.lastOrNull() as? AppRoute ?: DashboardRoute

    // Activity-scoped: shared between the dashboard tab and the room sheet overlay
    // (which sits outside the NavHost and needs the same selectedRoom + lights state)
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    // Hoisted so the GlanceConfigSheet can render at the top-level Box (above
    // BottomNavBar in z-order). If the sheet lived inside SettingsScreen it'd
    // be clipped under the NavHost layer and the nav bar would have to be
    // hidden while it's open.
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // Clear the "demo from onboarding" flag automatically once the user is authenticated
    // anywhere in the app — keeps the banner gone after they connect via Settings.
    androidx.compose.runtime.LaunchedEffect(onboardingState.authenticated) {
        if (onboardingState.authenticated && onboardingState.demoFromOnboarding) {
            onboardingViewModel.clearDemoFromOnboarding()
        }
    }

    val showExitDemoBanner = onboardingState.demoFromOnboarding &&
        !onboardingState.authenticated &&
        currentRoute !is SettingsRoute &&
        currentRoute !is HaAuthRoute

    val showBottomNav = currentRoute !is HaAuthRoute
    val selectedTab = resolveSelectedTab(currentRoute)

    // Shared haze source for both bottom-sheet overlays. NavHost + BottomNav are tagged
    // as haze sources so a scrim using hazeEffect blurs the dashboard behind it.
    val hazeState = remember { HazeState() }

    // Tab switch: normalize the stack to either [Dashboard] or [Dashboard, otherTab]
    // so predictive back from any tab always reveals the Dashboard.
    val switchTab: (AppRoute) -> Unit = { destination ->
        if (currentRoute != destination) {
            if (currentRoute != DashboardRoute) {
                backStack.removeLastOrNull()
            }
            if (destination != DashboardRoute) {
                backStack.add(destination)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
            ),
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            popTransitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            predictivePopTransitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            entryProvider = entryProvider {
                entry<DashboardRoute> {
                    DashboardScreen(
                        viewModel = dashboardViewModel,
                        onRoomClick = { dashboardViewModel.selectRoom(it) },
                        onShowAllRooms = { dashboardViewModel.toggleShowAllRooms() },
                        onSceneTap = { dashboardViewModel.runScene(it) },
                        onAddScene = { dashboardViewModel.showScenePicker() },
                        onFavClick = { dashboardViewModel.toggleFavorite(it.id) },
                        onAddFavorite = { dashboardViewModel.showFavoritePicker() },
                        onRemoveScene = { dashboardViewModel.removeQuickScene(it) },
                        onReorderScenes = { dashboardViewModel.reorderScenes(it) },
                        onRemoveFavorite = { dashboardViewModel.removeFavorite(it) },
                        onReorderFavorites = { dashboardViewModel.reorderFavorites(it) },
                        onHideRoom = { dashboardViewModel.hideRoom(it) },
                        onReorderRooms = { dashboardViewModel.reorderRooms(it) },
                        onShowRoomPicker = { dashboardViewModel.showRoomPicker() },
                        onEditRoomSensors = { room -> dashboardViewModel.showRoomSensorEditor(room.id) },
                        onFlightsTap = { dashboardViewModel.openFlightSheet() },
                        onReconnect = { dashboardViewModel.reconnect() },
                        onGoToSettings = { switchTab(SettingsRoute) },
                    )
                }
                entry<ActivityRoute> {
                    ActivityScreen()
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        onConnect = { url -> backStack.add(HaAuthRoute(url)) },
                        onOpenGlanceSheet = settingsViewModel::showGlanceSheet,
                        viewModel = settingsViewModel,
                    )
                }
                entry<HaAuthRoute> { route ->
                    HaAuthWebViewScreen(
                        haUrl = route.haUrl,
                        onSuccess = { backStack.removeLastOrNull() },
                    )
                }
            },
        )

        if (showBottomNav) {
            BottomNavBar(
                currentRoute = selectedTab,
                onNavigate = { route ->
                    val destination = when (route) {
                        "dashboard" -> DashboardRoute
                        "activity" -> ActivityRoute
                        "settings" -> SettingsRoute
                        else -> return@BottomNavBar
                    }
                    switchTab(destination)
                },
                modifier = Modifier.align(Alignment.BottomCenter).hazeSource(hazeState),
            )
        }

        // Floating "Demo mode · Exit" pill — anchored just above BottomNavBar.
        // Rendered after BottomNavBar so it sits on top in z-order. Routes to
        // Settings (URL + token form lives there). Flag auto-clears on auth.
        if (showBottomNav) {
            ExitDemoBanner(
                visible = showExitDemoBanner,
                onClick = { switchTab(SettingsRoute) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Settings "At a glance" config sheet — hoisted here so it renders
        // above BottomNavBar in z-order. The nav bar stays in composition the
        // whole time; the sheet's scrim just covers it visually.
        GlanceConfigSheet(
            visible = settingsUiState.showGlanceSheet,
            glance = settingsUiState.glance,
            hazeState = hazeState,
            onDismiss = settingsViewModel::hideGlanceSheet,
            onOutsideTempChange = settingsViewModel::setOutsideTempEntity,
            onInsideTempChange = settingsViewModel::setInsideTempEntity,
            onDoorbellChange = settingsViewModel::setDoorbellEntity,
            onLightsOnChange = settingsViewModel::setLightsOnEntity,
            onAqiChange = settingsViewModel::setAqiEntity,
            onTemplateChange = settingsViewModel::setGlanceTemplate,
        )

        // Rendered last so it appears above BottomNavBar in z-order
        RoomSheetOverlay(
            room = dashboardUiState.selectedRoom,
            lights = dashboardUiState.selectedRoomLights,
            climate = dashboardUiState.selectedRoomClimate,
            tempHistory = dashboardUiState.selectedRoomTempHistory,
            widgets = dashboardUiState.selectedRoomWidgets,
            widgetStates = dashboardUiState.widgetStates,
            widgetHistories = dashboardUiState.widgetHistories,
            widgetCameraSnapshots = dashboardUiState.widgetCameraSnapshots,
            widgetMediaStates = dashboardUiState.widgetMediaStates,
            showWidgetCatalog = dashboardUiState.showWidgetCatalog,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.selectRoom(null) },
            onToggle = { entityId, isOn -> dashboardViewModel.toggleLight(entityId, isOn) },
            onBrightnessChange = { entityId, bri -> dashboardViewModel.setLightBrightness(entityId, bri) },
            onColorChange = { entityId, r, g, b -> dashboardViewModel.setLightColor(entityId, r, g, b) },
            onColorTempChange = { entityId, kelvin -> dashboardViewModel.setLightColorTemp(entityId, kelvin) },
            onClimateTargetChange = { id, t -> dashboardViewModel.setClimateTarget(id, t) },
            onClimateModeChange = { id, m -> dashboardViewModel.setClimateMode(id, m) },
            onShowWidgetCatalog = { dashboardViewModel.showWidgetCatalog() },
            onHideWidgetCatalog = { dashboardViewModel.hideWidgetCatalog() },
            onPickSwitchType = { dashboardViewModel.showSwitchEntityPicker() },
            onPickSensorType = { dashboardViewModel.showSensorEntityPicker() },
            onPickCameraType = { dashboardViewModel.showCameraEntityPicker() },
            onPickMediaType = { dashboardViewModel.showMediaEntityPicker() },
            onWidgetClick = { widget ->
                when (widget) {
                    is com.uc.homehealth.data.RoomWidget.Switch -> dashboardViewModel.toggleEntity(widget.entityId)
                    is com.uc.homehealth.data.RoomWidget.Sensor -> dashboardViewModel.openSensorDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Camera -> dashboardViewModel.openCameraDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Media -> Unit
                }
            },
            onWidgetLongPress = { widget ->
                when (widget) {
                    is com.uc.homehealth.data.RoomWidget.Switch -> dashboardViewModel.openSwitchDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Sensor -> dashboardViewModel.openSensorDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Camera -> dashboardViewModel.openCameraDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Media -> Unit
                }
            },
            onRemoveWidget = { widget ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@RoomSheetOverlay
                dashboardViewModel.removeWidget(areaId, widget.id)
            },
            onReorderWidgets = { reordered ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@RoomSheetOverlay
                dashboardViewModel.reorderWidgets(areaId, reordered)
            },
            mediaCallbacks = MediaCardCallbacks(
                onPlayPause = { id -> dashboardViewModel.mediaPlayPause(id) },
                onSkipPrev = { id -> dashboardViewModel.mediaSkipPrev(id) },
                onSkipNext = { id -> dashboardViewModel.mediaSkipNext(id) },
                onToggleShuffle = { id, current ->
                    dashboardViewModel.mediaSetShuffle(id, !current)
                },
                onCycleRepeat = { id, current ->
                    dashboardViewModel.mediaSetRepeat(id, com.uc.homehealth.data.MediaRepeatMode.next(current))
                },
                onOpenQueue = { /* TODO: queue sheet */ },
                onCast = { /* TODO: cast picker */ },
                onSeek = { id, progress -> dashboardViewModel.mediaSeek(id, progress) },
                onVolumeChange = { id, volume -> dashboardViewModel.mediaSetVolume(id, volume) },
            ),
        )

        SceneSelectorSheet(
            visible = dashboardUiState.showScenePicker,
            allScenes = dashboardUiState.allScenes,
            existingIds = dashboardUiState.scenes.map { it.id }.toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideScenePicker() },
            onPick = { scene -> dashboardViewModel.addQuickScene(scene.id) },
        )

        FavoritePickerSheet(
            visible = dashboardUiState.showFavoritePicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.favorites.map { it.id }.toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideFavoritePicker() },
            onPick = { entity -> dashboardViewModel.addFavorite(entity.entityId) },
        )

        RoomPickerSheet(
            visible = dashboardUiState.showRoomPicker,
            hiddenRooms = dashboardUiState.hiddenRooms,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideRoomPicker() },
            onPick = { room -> dashboardViewModel.showRoom(room.id) },
        )

        RoomSensorEditorSheet(
            room = dashboardUiState.editingSensorsForRoom,
            override = dashboardUiState.editingSensorsForRoom?.let {
                dashboardUiState.roomSensorOverrides[it.id]
            },
            allEntities = dashboardUiState.allEntities,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideRoomSensorEditor() },
            onSetTempSensor = { entityId ->
                dashboardUiState.editingSensorsForRoom?.id?.let {
                    dashboardViewModel.setRoomTempSensor(it, entityId)
                }
            },
            onSetHumiditySensor = { entityId ->
                dashboardUiState.editingSensorsForRoom?.id?.let {
                    dashboardViewModel.setRoomHumiditySensor(it, entityId)
                }
            },
        )

        // Switch entity picker — stacked above the room sheet's widget catalog.
        SwitchEntityPickerSheet(
            visible = dashboardUiState.showSwitchEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Switch>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideSwitchEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@SwitchEntityPickerSheet
                dashboardViewModel.addSwitchWidget(areaId, entity.entityId)
            },
        )

        FlightSheetOverlay(
            visible = dashboardUiState.showFlightSheet,
            flights = dashboardUiState.flights,
            isAddingFlight = dashboardUiState.isAddingFlight,
            flightAddError = dashboardUiState.flightAddError,
            flightAutomationIds = dashboardUiState.flightAutomationIds,
            allAutomations = dashboardUiState.allAutomations,
            isFlightRadar24Available = dashboardUiState.isFlightRadar24Available,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeFlightSheet() },
            onAddFlight = { query -> dashboardViewModel.addTrackedFlight(query) },
            onRemoveFlight = { flight -> dashboardViewModel.removeTrackedFlight(flight) },
            onDismissFlightAddError = { dashboardViewModel.dismissFlightAddError() },
            onAddFlightAutomation = { entityId -> dashboardViewModel.addFlightAutomation(entityId) },
            onRemoveFlightAutomation = { entityId -> dashboardViewModel.removeFlightAutomation(entityId) },
            onTriggerFlightAutomation = { entityId -> dashboardViewModel.triggerFlightAutomation(entityId) },
        )

        // Switch widget long-press detail sheet.
        val openedSwitch = dashboardUiState.openedSwitchWidget
        SwitchDetailSheet(
            visible = openedSwitch != null,
            entityId = openedSwitch?.entityId.orEmpty(),
            name = openedSwitch?.entityId
                ?.substringAfterLast('.')
                ?.replace('_', ' ')
                ?.replaceFirstChar { it.uppercase() }
                .orEmpty(),
            state = openedSwitch?.let { dashboardUiState.widgetStates[it.entityId] },
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeSwitchDetail() },
            onToggle = { openedSwitch?.entityId?.let { dashboardViewModel.toggleEntity(it) } },
        )

        // Sensor entity picker — stacked above the room sheet's widget catalog.
        SensorEntityPickerSheet(
            visible = dashboardUiState.showSensorEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Sensor>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideSensorEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@SensorEntityPickerSheet
                dashboardViewModel.addSensorWidget(areaId, entity.entityId)
            },
        )

        // Sensor widget detail sheet.
        val openedSensor = dashboardUiState.openedSensorWidget
        SensorDetailSheet(
            visible = openedSensor != null,
            entityId = openedSensor?.entityId.orEmpty(),
            name = openedSensor?.let {
                dashboardUiState.widgetStates[it.entityId]?.friendlyName
                    ?: it.entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { c -> c.uppercase() }
            }.orEmpty(),
            state = openedSensor?.let { dashboardUiState.widgetStates[it.entityId] },
            history = openedSensor?.let { dashboardUiState.widgetHistories[it.entityId] }.orEmpty(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeSensorDetail() },
        )

        // Camera entity picker.
        CameraEntityPickerSheet(
            visible = dashboardUiState.showCameraEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Camera>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideCameraEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@CameraEntityPickerSheet
                dashboardViewModel.addCameraWidget(areaId, entity.entityId)
            },
        )

        // Media player entity picker.
        MediaPlayerEntityPickerSheet(
            visible = dashboardUiState.showMediaEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Media>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideMediaEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@MediaPlayerEntityPickerSheet
                dashboardViewModel.addMediaWidget(areaId, entity.entityId)
            },
        )

        // Camera widget detail sheet — HLS stream URL resolved lazily on open.
        val openedCamera = dashboardUiState.openedCameraWidget
        CameraDetailSheet(
            visible = openedCamera != null,
            entityId = openedCamera?.entityId.orEmpty(),
            name = openedCamera?.let {
                dashboardUiState.widgetStates[it.entityId]?.friendlyName
                    ?: it.entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { c -> c.uppercase() }
            }.orEmpty(),
            snapshotUrl = openedCamera?.let { dashboardUiState.widgetCameraSnapshots[it.entityId] },
            hazeState = hazeState,
            getStreamUrl = {
                openedCamera?.entityId?.let { dashboardViewModel.getCameraStreamUrl(it) }
            },
            onDismiss = { dashboardViewModel.closeCameraDetail() },
        )
    }
}

// Map any route back to the top-level toolbar tab that should appear selected.
// Mirrors Caffeine's AppRoute.resolveToolbarDestination() — non-tab routes
// (HaAuthRoute) fall back to the Dashboard chip rather than collapsing the pill.
private fun resolveSelectedTab(route: AppRoute): String = when (route) {
    is DashboardRoute -> "dashboard"
    is ActivityRoute -> "activity"
    is SettingsRoute -> "settings"
    is HaAuthRoute -> "settings"
}
