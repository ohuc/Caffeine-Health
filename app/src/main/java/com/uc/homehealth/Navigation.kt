package com.uc.homehealth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.material3.MaterialTheme
import com.uc.homehealth.ui.components.BottomNavBar
import com.uc.homehealth.ui.components.navDestinationsFor
import com.uc.homehealth.ui.components.ClimateSheetOverlay
import com.uc.homehealth.ui.components.ExitDemoBanner
import com.uc.homehealth.ui.components.LightControlSheetOverlay
import com.uc.homehealth.ui.components.FavoritePickerSheet
import com.uc.homehealth.ui.components.GlanceTilePickerSheet
import com.uc.homehealth.ui.components.RoomPickerSheet
import com.uc.homehealth.ui.components.RoomSensorEditorSheet
import com.uc.homehealth.ui.components.FlightSheetOverlay
import com.uc.homehealth.ui.components.SceneSelectorSheet
import com.uc.homehealth.ui.components.CameraDetailSheet
import com.uc.homehealth.ui.components.CameraWebRtcClient
import com.uc.homehealth.ui.components.CameraEntityPickerSheet
import com.uc.homehealth.ui.components.CameraPtzConfigSheet
import com.uc.homehealth.ui.components.ClimateEntityPickerSheet
import com.uc.homehealth.ui.components.LocationDetailSheet
import com.uc.homehealth.ui.components.LocationEntityPickerSheet
import com.uc.homehealth.ui.components.AirQualityConfigSheet
import com.uc.homehealth.ui.components.MaSearchSheet
import com.uc.homehealth.ui.components.MediaCardCallbacks
import com.uc.homehealth.ui.components.MediaPlayerEntityPickerSheet
import com.uc.homehealth.ui.components.SensorDetailSheet
import com.uc.homehealth.ui.components.SensorEntityPickerSheet
import com.uc.homehealth.ui.components.TtsComposerSheet
import com.uc.homehealth.ui.components.SwitchDetailSheet
import com.uc.homehealth.ui.components.SwitchEntityPickerSheet
import com.uc.homehealth.ui.screens.ActivityScreen
import com.uc.homehealth.ui.screens.DashboardScreen
import com.uc.homehealth.ui.screens.EnergyHomeDetailSheetOverlay
import com.uc.homehealth.ui.screens.EnergyScreen
import com.uc.homehealth.ui.screens.EnergySetupSheetOverlay
import com.uc.homehealth.ui.screens.GlanceEditScreen
import com.uc.homehealth.ui.screens.HaAuthWebViewScreen
import com.uc.homehealth.ui.screens.MusicScreen
import com.uc.homehealth.ui.screens.NavBarEditScreen
import com.uc.homehealth.ui.screens.OnboardingScreen
import com.uc.homehealth.ui.screens.PulseScreen
import com.uc.homehealth.ui.screens.RoomSheetOverlay
import com.uc.homehealth.ui.screens.RoomsEditScreen
import com.uc.homehealth.ui.screens.SettingsScreen
import com.uc.homehealth.ui.viewmodel.CardSettingsViewModel
import com.uc.homehealth.ui.viewmodel.DashboardViewModel
import com.uc.homehealth.ui.viewmodel.EnergyViewModel
import com.uc.homehealth.ui.viewmodel.OnboardingViewModel
import com.uc.homehealth.ui.viewmodel.PulseViewModel
import com.uc.homehealth.ui.viewmodel.SettingsViewModel
import kotlinx.serialization.Serializable

sealed interface AppRoute : NavKey

@Serializable object DashboardRoute : AppRoute
@Serializable object ActivityRoute : AppRoute
@Serializable object EnergyRoute : AppRoute
@Serializable object PulseRoute : AppRoute
@Serializable object MusicRoute : AppRoute
@Serializable object SettingsRoute : AppRoute
@Serializable object GlanceEditRoute : AppRoute
@Serializable object RoomsEditRoute : AppRoute
@Serializable object NavBarEditRoute : AppRoute
@Serializable data class HaAuthRoute(val haUrl: String) : AppRoute

private fun routeForTabKey(key: String): AppRoute = when (key) {
    "dashboard" -> DashboardRoute
    "activity" -> ActivityRoute
    "energy" -> EnergyRoute
    "pulse" -> PulseRoute
    "music" -> MusicRoute
    else -> SettingsRoute
}

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

    // The user's bottom-nav tab arrangement (Settings → Navigation Bar). Gate the whole
    // NavHost on the first DataStore emission so a cold launch never flashes the default
    // tab set — or starts on the wrong root tab — before the saved arrangement arrives.
    val cardSettingsViewModel: CardSettingsViewModel = hiltViewModel()
    val navTabKeysOrNull by cardSettingsViewModel.navTabKeys.collectAsStateWithLifecycle()
    val navTabKeys = navTabKeysOrNull ?: return
    // The first visible tab is the app's root (predictive back from any tab reveals it).
    // Normalization guarantees the list is never empty — Settings is always present.
    val rootRoute = routeForTabKey(navTabKeys.first())

    val backStack = rememberNavBackStack(rootRoute)
    val currentRoute = backStack.lastOrNull() as? AppRoute ?: rootRoute

    // Keep the back-stack root in sync when the editor changes the first tab (reorder,
    // or hiding Home itself). Inserts the new root underneath and removes the old one;
    // whatever the user is standing on (e.g. Settings → nav editor) stays on top. The
    // stack is never emptied mid-rewrite — NavDisplay requires at least one entry.
    androidx.compose.runtime.LaunchedEffect(rootRoute) {
        val oldRoot = backStack.firstOrNull()
        if (oldRoot == rootRoute) return@LaunchedEffect
        backStack.add(0, rootRoute)
        if (backStack.size > 1 && backStack[1] == oldRoot) backStack.removeAt(1)
        // Dedupe a deeper occurrence of the new root (e.g. it was the "other tab").
        var i = 1
        while (i < backStack.size) {
            if (backStack[i] == rootRoute) backStack.removeAt(i) else i++
        }
    }

    // Activity-scoped: shared between the dashboard tab and the room sheet overlay
    // (which sits outside the NavHost and needs the same selectedRoom + lights state)
    val dashboardViewModel: DashboardViewModel = hiltViewModel()
    val dashboardUiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    val settingsViewModel: SettingsViewModel = hiltViewModel()

    // Activity-scoped like DashboardViewModel: shared between the Energy screen and its
    // setup sheet, which renders at this top level so it covers the bottom nav.
    val energyViewModel: EnergyViewModel = hiltViewModel()

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

    // A settings subpage (Updates) hosts its own bottom sheet locally; while it's open the
    // nav bar — a top-level sibling rendered above the NavDisplay — would otherwise poke
    // through the sheet's scrim. Hoist that "sheet open" flag here so we can slide it away.
    var settingsSheetOpen by remember { mutableStateOf(false) }

    // The glance/rooms/nav-bar editors keep the bottom nav (tapping Dashboard pops back
    // to it — resolveSelectedTab maps the editors onto their parent chip; the nav-bar
    // editor relies on the live bar as its real preview). Only the HA auth webview
    // removes it, since that's a modal sign-in flow.
    val navOnScreen = currentRoute !is HaAuthRoute
    val selectedTab = resolveSelectedTab(currentRoute, navTabKeys)

    // While a settings sheet is open, slide the nav bar off-screen instead of unmounting it.
    // Unmounting wipes BottomNavBar's remembered button bounds, so on return its selection
    // pill resets to index 0 and "swings" across to the real tab. Sliding keeps it put.
    val navHideFraction by animateFloatAsState(
        targetValue = if (settingsSheetOpen) 1f else 0f,
        animationSpec = tween(260),
        label = "navHide",
    )

    // Shared haze source for both bottom-sheet overlays. NavHost + BottomNav are tagged
    // as haze sources so a scrim using hazeEffect blurs the dashboard behind it.
    val hazeState = remember { HazeState() }

    // Tab switch: normalize the stack to either [root] or [root, otherTab] so predictive
    // back from any tab always reveals the root (the user's first tab). Pops everything
    // above the root first — subpages like the nav-bar editor can sit more than one deep.
    val switchTab: (AppRoute) -> Unit = { destination ->
        if (currentRoute != destination) {
            while (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
            if (destination != rootRoute) {
                backStack.add(destination)
            }
        }
    }

    // Full-screen subpages (Rooms / Glance editors, HA auth) push in horizontally
    // like a drill-down, instead of the flat crossfade used for sibling tabs — the
    // same slide the Settings subpages use. Applied per-entry via NavDisplay metadata.
    val subpageTransition =
        NavDisplay.transitionSpec {
            slideInHorizontally(tween(280)) { it } togetherWith
                (slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(220)))
        } + NavDisplay.popTransitionSpec {
            (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                slideOutHorizontally(tween(280)) { it }
        } + NavDisplay.predictivePopTransitionSpec {
            (slideInHorizontally(tween(280)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                slideOutHorizontally(tween(280)) { it }
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
                        onEditGlance = { backStack.add(GlanceEditRoute) },
                        onEditRooms = { backStack.add(RoomsEditRoute) },
                        onFlightsTap = { dashboardViewModel.openFlightSheet() },
                        onReconnect = { dashboardViewModel.reconnect() },
                        onGoToSettings = { switchTab(SettingsRoute) },
                        onOpenActivity = { switchTab(ActivityRoute) },
                        onOpenPulse = { switchTab(PulseRoute) },
                    )
                }
                entry<ActivityRoute> {
                    ActivityScreen()
                }
                entry<EnergyRoute> {
                    EnergyScreen(viewModel = energyViewModel)
                }
                entry<PulseRoute> {
                    PulseScreen()
                }
                entry<MusicRoute> {
                    // Announce reuses the global TTS overlay wired through the
                    // activity-scoped DashboardViewModel; search/library are the
                    // tab's own subpages.
                    MusicScreen(
                        onAnnounce = { id -> dashboardViewModel.openTtsComposer(id) },
                    )
                }
                entry<RoomsEditRoute>(metadata = subpageTransition) {
                    RoomsEditScreen(
                        rooms = dashboardUiState.rooms,
                        hiddenRooms = dashboardUiState.hiddenRooms,
                        onBack = { backStack.removeLastOrNull() },
                        onReorder = { dashboardViewModel.reorderRooms(it) },
                        onHideRoom = { dashboardViewModel.hideRoom(it.id) },
                        onShowRoom = { dashboardViewModel.showRoom(it.id) },
                        onEditSensors = { dashboardViewModel.showRoomSensorEditor(it.id) },
                    )
                }
                entry<GlanceEditRoute>(metadata = subpageTransition) {
                    GlanceEditScreen(
                        tiles = dashboardUiState.glanceTiles,
                        allEntities = dashboardUiState.allEntities,
                        smartGlanceEnabled = dashboardUiState.smartGlanceEnabled,
                        onSmartGlanceChange = { dashboardViewModel.setSmartGlanceEnabled(it) },
                        onBack = { backStack.removeLastOrNull() },
                        onRemoveTile = { dashboardViewModel.removeGlanceTile(it) },
                        onReorderTiles = { dashboardViewModel.reorderGlanceTiles(it) },
                        onPickCategory = { dashboardViewModel.showGlanceTilePicker(it) },
                    )
                }
                entry<SettingsRoute> {
                    SettingsScreen(
                        onConnect = { url -> backStack.add(HaAuthRoute(url)) },
                        viewModel = settingsViewModel,
                        onSheetVisibleChange = { settingsSheetOpen = it },
                        onEditNavBar = { backStack.add(NavBarEditRoute) },
                    )
                }
                entry<NavBarEditRoute>(metadata = subpageTransition) {
                    NavBarEditScreen(
                        onBack = { backStack.removeLastOrNull() },
                        viewModel = cardSettingsViewModel,
                    )
                }
                entry<HaAuthRoute>(metadata = subpageTransition) { route ->
                    HaAuthWebViewScreen(
                        haUrl = route.haUrl,
                        onSuccess = { backStack.removeLastOrNull() },
                    )
                }
            },
        )

        if (navOnScreen) {
            BottomNavBar(
                currentRoute = selectedTab,
                onNavigate = { route -> switchTab(routeForTabKey(route)) },
                tabs = remember(navTabKeys) { navDestinationsFor(navTabKeys) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // Slide down + fade out while a settings sheet is open (kept composed).
                    .graphicsLayer {
                        translationY = navHideFraction * (size.height + 120.dp.toPx())
                        alpha = 1f - navHideFraction
                    }
                    .hazeSource(hazeState),
            )
        }

        // Floating "Demo mode · Exit" pill — anchored just above BottomNavBar.
        // Rendered after BottomNavBar so it sits on top in z-order. Routes to
        // Settings (URL + token form lives there). Flag auto-clears on auth.
        if (navOnScreen) {
            ExitDemoBanner(
                visible = showExitDemoBanner,
                onClick = { switchTab(SettingsRoute) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = navHideFraction * (size.height + 120.dp.toPx())
                        alpha = 1f - navHideFraction
                    },
            )
        }

        // Rendered last so it appears above BottomNavBar in z-order
        RoomSheetOverlay(
            room = dashboardUiState.selectedRoom,
            lights = dashboardUiState.selectedRoomLights,
            connectionStatus = dashboardUiState.connectionStatus,
            onReconnect = { dashboardViewModel.reconnect() },
            climate = dashboardUiState.selectedRoomClimate,
            tempHistory = dashboardUiState.selectedRoomTempHistory,
            widgets = dashboardUiState.selectedRoomWidgets,
            widgetStates = dashboardUiState.widgetStates,
            widgetHistories = dashboardUiState.widgetHistories,
            widgetCameraSnapshots = dashboardUiState.widgetCameraSnapshots,
            widgetMediaStates = dashboardUiState.widgetMediaStates,
            widgetLocations = dashboardUiState.widgetLocations,
            widgetClimateStates = dashboardUiState.widgetClimateStates,
            showWidgetCatalog = dashboardUiState.showWidgetCatalog,
            showWarnings = dashboardUiState.showRoomWarnings,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.selectRoom(null) },
            onToggle = { entityId, isOn -> dashboardViewModel.toggleLight(entityId, isOn) },
            onBrightnessChange = { entityId, bri -> dashboardViewModel.setLightBrightness(entityId, bri) },
            onColorChange = { entityId, r, g, b -> dashboardViewModel.setLightColor(entityId, r, g, b) },
            onColorTempChange = { entityId, kelvin -> dashboardViewModel.setLightColorTemp(entityId, kelvin) },
            onClimateTargetChange = { id, t -> dashboardViewModel.setClimateTarget(id, t) },
            onClimateModeChange = { id, m -> dashboardViewModel.setClimateMode(id, m) },
            onClimateFanModeChange = { id, fm -> dashboardViewModel.setClimateFanMode(id, fm) },
            climateModeOrders = dashboardUiState.climateModeOrders,
            onClimateModeOrderChange = { id, modes -> dashboardViewModel.setClimateModeOrder(id, modes) },
            climateFanOrders = dashboardUiState.climateFanOrders,
            onClimateFanOrderChange = { id, fans -> dashboardViewModel.setClimateFanOrder(id, fans) },
            onShowWidgetCatalog = { dashboardViewModel.showWidgetCatalog() },
            onHideWidgetCatalog = { dashboardViewModel.hideWidgetCatalog() },
            onPickSwitchType = { dashboardViewModel.showSwitchEntityPicker() },
            onPickSensorType = { dashboardViewModel.showSensorEntityPicker() },
            onPickCameraType = { dashboardViewModel.showCameraEntityPicker() },
            onPickMediaType = { dashboardViewModel.showMediaEntityPicker(com.uc.homehealth.data.WidgetSection.MORE) },
            onPickClimateType = { dashboardViewModel.showClimateEntityPicker(com.uc.homehealth.data.WidgetSection.MORE) },
            onPickLocationType = { dashboardViewModel.showLocationEntityPicker() },
            onPickAirQualityType = { dashboardViewModel.showAirQualityPicker() },
            // Section-scoped adds from the Media / Climate tabs.
            onAddMedia = { dashboardViewModel.showMediaEntityPicker(com.uc.homehealth.data.WidgetSection.MEDIA) },
            onAddClimate = { dashboardViewModel.showClimateEntityPicker(com.uc.homehealth.data.WidgetSection.CLIMATE) },
            onWidgetClick = { widget ->
                when (widget) {
                    is com.uc.homehealth.data.RoomWidget.Switch -> dashboardViewModel.toggleEntity(widget.entityId)
                    is com.uc.homehealth.data.RoomWidget.Sensor -> dashboardViewModel.openSensorDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Camera -> dashboardViewModel.openCameraDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Location -> dashboardViewModel.openLocationDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Media -> Unit
                    // Climate cards open the climate sheet via their own onTap (ClimateCard),
                    // not the generic widget-click path.
                    is com.uc.homehealth.data.RoomWidget.Climate -> Unit
                    // Air-quality cards are informational — no tap action.
                    is com.uc.homehealth.data.RoomWidget.AirQuality -> Unit
                }
            },
            onWidgetLongPress = { widget ->
                when (widget) {
                    is com.uc.homehealth.data.RoomWidget.Switch -> dashboardViewModel.openSwitchDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Sensor -> dashboardViewModel.openSensorDetail(widget)
                    // Long-press a camera → open the camera view (stream) sheet.
                    is com.uc.homehealth.data.RoomWidget.Camera -> dashboardViewModel.openCameraDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Location -> dashboardViewModel.openLocationDetail(widget)
                    is com.uc.homehealth.data.RoomWidget.Media -> Unit
                    is com.uc.homehealth.data.RoomWidget.Climate -> Unit
                    is com.uc.homehealth.data.RoomWidget.AirQuality -> Unit
                }
            },
            onPtzPress = { entityId -> dashboardViewModel.pressPtz(entityId) },
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
                onSeek = { id, progress -> dashboardViewModel.mediaSeek(id, progress) },
                onVolumeChange = { id, volume -> dashboardViewModel.mediaSetVolume(id, volume) },
                onAnnounce = { id -> dashboardViewModel.openTtsComposer(id) },
                onSearchMusic = { id -> dashboardViewModel.openMaSearch(id) },
            ),
        )

        // Energy sheets — top level for the same reason as the room sheet: their scrims
        // must cover the bottom nav.
        EnergySetupSheetOverlay(viewModel = energyViewModel, hazeState = hazeState)
        EnergyHomeDetailSheetOverlay(viewModel = energyViewModel, hazeState = hazeState)

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

        GlanceTilePickerSheet(
            visible = dashboardUiState.glanceTilePickerDomain != null,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.glanceTiles.map { it.entityId }.toSet(),
            domain = dashboardUiState.glanceTilePickerDomain,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideGlanceTilePicker() },
            onPick = { entity -> dashboardViewModel.addGlanceTile(entity.entityId) },
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
            scheduledFlights = dashboardUiState.scheduledFlights,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeFlightSheet() },
            onAddFlight = { query -> dashboardViewModel.addTrackedFlight(query) },
            onRemoveFlight = { flight -> dashboardViewModel.removeTrackedFlight(flight) },
            onDismissFlightAddError = { dashboardViewModel.dismissFlightAddError() },
            onScheduleFlight = { query, day -> dashboardViewModel.scheduleTrackedFlight(query, day) },
            onCancelScheduledFlight = { id -> dashboardViewModel.cancelScheduledFlight(id) },
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
                // Picking a camera starts the add flow: ask about PTZ before committing.
                dashboardViewModel.beginAddCamera(entity.entityId)
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

        // Air-quality widget config — pick the PM2.5 base + clean/moderate/poor durations.
        AirQualityConfigSheet(
            visible = dashboardUiState.showAirQualityConfig,
            allEntities = dashboardUiState.allEntities,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideAirQualityConfig() },
            onAdd = { base, clean, moderate, poor ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@AirQualityConfigSheet
                dashboardViewModel.addAirQualityWidget(areaId, base, clean, moderate, poor)
            },
        )

        // Climate entity picker — for both the Climate-tab add and the More catalog.
        ClimateEntityPickerSheet(
            visible = dashboardUiState.showClimateEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Climate>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideClimateEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@ClimateEntityPickerSheet
                dashboardViewModel.addClimateWidget(areaId, entity.entityId)
            },
        )

        // Location (person) entity picker.
        LocationEntityPickerSheet(
            visible = dashboardUiState.showLocationEntityPicker,
            allEntities = dashboardUiState.allEntities,
            existingIds = dashboardUiState.selectedRoomWidgets
                .filterIsInstance<com.uc.homehealth.data.RoomWidget.Location>()
                .map { it.entityId }
                .toSet(),
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.hideLocationEntityPicker() },
            onPick = { entity ->
                val areaId = dashboardUiState.selectedRoom?.id ?: return@LocationEntityPickerSheet
                dashboardViewModel.addLocationWidget(areaId, entity.entityId)
            },
        )

        // Camera widget detail sheet — low-latency live (MSE/WebRTC) with HLS fallback.
        val openedCamera = dashboardUiState.openedCameraWidget
        val go2rtcUrl by dashboardViewModel.go2rtcUrl.collectAsStateWithLifecycle()
        val cameraWebRtcClient = remember(dashboardViewModel) {
            object : CameraWebRtcClient {
                override suspend fun start(
                    entityId: String,
                    offerSdp: String,
                    onSignal: (com.uc.homehealth.data.WebRtcSignal) -> Unit,
                ): Int? = dashboardViewModel.startCameraWebRtc(entityId, offerSdp, onSignal)

                override fun sendCandidate(
                    entityId: String,
                    sessionId: String,
                    candidate: com.uc.homehealth.data.WebRtcIceCandidate,
                ) = dashboardViewModel.sendCameraWebRtcCandidate(entityId, sessionId, candidate)

                override fun stop(subscriptionId: Int) = dashboardViewModel.stopCameraWebRtc(subscriptionId)

                override suspend fun getClientConfig(
                    entityId: String,
                ): List<com.uc.homehealth.data.WebRtcIceServer>? =
                    dashboardViewModel.getCameraWebRtcConfig(entityId)
            }
        }
        CameraDetailSheet(
            visible = openedCamera != null,
            entityId = openedCamera?.entityId.orEmpty(),
            name = openedCamera?.let {
                dashboardUiState.widgetStates[it.entityId]?.friendlyName
                    ?: it.entityId.substringAfterLast('.').replace('_', ' ').replaceFirstChar { c -> c.uppercase() }
            }.orEmpty(),
            snapshotUrl = openedCamera?.let { dashboardUiState.widgetCameraSnapshots[it.entityId] },
            webRtcClient = cameraWebRtcClient,
            // Snapshot-only entities (no STREAM feature) skip the futile connect attempt.
            // Default to true when the entity isn't in the registry yet, so we still try.
            supportsStream = openedCamera?.let { cam ->
                dashboardUiState.allEntities.find { it.entityId == cam.entityId }?.supportsStream ?: true
            } ?: true,
            // When set, MSE (fMP4 over WebSocket) is the primary low-latency path.
            go2rtcUrl = go2rtcUrl.ifBlank { null },
            hazeState = hazeState,
            // Same PTZ bindings as the tile, rendered as a press-and-hold pad under the live view.
            ptz = openedCamera?.ptz,
            onPtzPress = { entityId -> dashboardViewModel.pressPtz(entityId) },
            getStreamUrl = {
                openedCamera?.entityId?.let { dashboardViewModel.getCameraStreamUrl(it) }
            },
            onDismiss = { dashboardViewModel.closeCameraDetail() },
        )

        // Add-camera PTZ setup — shown after the user picks a camera entity. "Add
        // camera" commits the widget (with the chosen PTZ bindings) to the open room;
        // dismissing cancels the add.
        val ptzCamera = dashboardUiState.ptzConfigCamera
        CameraPtzConfigSheet(
            visible = ptzCamera != null,
            camera = ptzCamera,
            allEntities = dashboardUiState.allEntities,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closePtzConfig() },
            onSave = { ptz ->
                val areaId = dashboardUiState.selectedRoom?.id
                if (areaId != null && ptzCamera != null) {
                    dashboardViewModel.confirmAddCamera(areaId, ptzCamera.entityId, ptz)
                }
            },
        )

        // Location widget detail sheet — full interactive map.
        val openedLocation = dashboardUiState.openedLocationWidget
        LocationDetailSheet(
            visible = openedLocation != null,
            entityId = openedLocation?.entityId.orEmpty(),
            location = openedLocation?.let { dashboardUiState.widgetLocations[it.entityId] },
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeLocationDetail() },
        )

        // Announce / text-to-speech composer — opened from a media player card. Collected
        // separately from the giant dashboard state so it stays out of that combine().
        val ttsComposer by dashboardViewModel.ttsComposerState.collectAsStateWithLifecycle()
        // Pulse report for the composer's "Home status" quick phrase.
        val pulseViewModel: PulseViewModel = hiltViewModel()
        val pulseUi by pulseViewModel.uiState.collectAsStateWithLifecycle()
        TtsComposerSheet(
            visible = ttsComposer.visible,
            target = ttsComposer.target,
            engines = ttsComposer.engines,
            defaultEngineId = ttsComposer.defaultEngineId,
            defaultVoiceId = ttsComposer.defaultVoiceId,
            defaultLanguage = ttsComposer.defaultLanguage,
            sending = ttsComposer.sending,
            hazeState = hazeState,
            loadVoices = { engineId, language -> dashboardViewModel.loadTtsVoices(engineId, language) },
            onSend = { message, announce, engineId, voiceId, language ->
                dashboardViewModel.sendTts(message, announce, engineId, voiceId, language)
            },
            onOpenSettings = {
                dashboardViewModel.closeTtsComposer()
                switchTab(SettingsRoute)
            },
            onDismiss = { dashboardViewModel.closeTtsComposer() },
            suggestedMessage = pulseUi.report?.takeIf { !pulseUi.isLoading }?.spokenSummary(),
        )

        // Music Assistant search — opened from a MA media player card's search pill.
        // Top level for the same reason as the other sheets: scrim must cover the nav bar.
        val maSearch by dashboardViewModel.maSearchState.collectAsStateWithLifecycle()
        MaSearchSheet(
            visible = maSearch.visible,
            playerName = maSearch.playerName,
            hazeState = hazeState,
            search = { query, type, libraryOnly ->
                dashboardViewModel.searchMusicAssistant(query, type, libraryOnly)
            },
            onPlay = { item, mode -> dashboardViewModel.playMaItem(item, mode) },
            onDismiss = { dashboardViewModel.closeMaSearch() },
        )

        // Glance-tile controls — tapping a light/climate glance tile opens the SAME control
        // sheet the room uses, resolved by entity_id. Collected outside the giant dashboard
        // combine (their own side-flows), rendered here so they sit above the bottom nav.
        val glanceLight by dashboardViewModel.glanceControlLight.collectAsStateWithLifecycle()
        LightControlSheetOverlay(
            light = glanceLight,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeGlanceControl() },
            onToggle = { id, isOn -> dashboardViewModel.toggleLight(id, isOn) },
            onBrightnessChange = { id, bri -> dashboardViewModel.setLightBrightness(id, bri) },
            onBrightnessChangeFinished = { id, bri -> dashboardViewModel.setLightBrightness(id, bri) },
            onColorChange = { id, r, g, b -> dashboardViewModel.setLightColor(id, r, g, b) },
            onColorChangeFinished = { id, r, g, b -> dashboardViewModel.setLightColor(id, r, g, b) },
            onColorTempChange = { id, k -> dashboardViewModel.setLightColorTemp(id, k) },
            onColorTempChangeFinished = { id, k -> dashboardViewModel.setLightColorTemp(id, k) },
        )

        val glanceClimate by dashboardViewModel.glanceControlClimate.collectAsStateWithLifecycle()
        ClimateSheetOverlay(
            climate = glanceClimate,
            roomLabel = glanceClimate?.name ?: "Climate",
            accentColor = MaterialTheme.colorScheme.primary,
            inkColor = MaterialTheme.colorScheme.onPrimary,
            hazeState = hazeState,
            onDismiss = { dashboardViewModel.closeGlanceControl() },
            onTargetChange = { id, t -> dashboardViewModel.setClimateTarget(id, t) },
            onModeChange = { id, m -> dashboardViewModel.setClimateMode(id, m) },
            onFanModeChange = { id, f -> dashboardViewModel.setClimateFanMode(id, f) },
            modeOrders = dashboardUiState.climateModeOrders,
            onModeOrderChange = { id, modes -> dashboardViewModel.setClimateModeOrder(id, modes) },
            fanOrders = dashboardUiState.climateFanOrders,
            onFanOrderChange = { id, fans -> dashboardViewModel.setClimateFanOrder(id, fans) },
        )
    }
}

// Map any route back to the top-level toolbar tab that should appear selected.
// Mirrors Caffeine's AppRoute.resolveToolbarDestination() — non-tab routes fall back to
// their parent chip rather than collapsing the pill. If that chip was hidden by the
// nav-bar editor, fall back to the first visible tab so the pill never collapses.
private fun resolveSelectedTab(route: AppRoute, tabKeys: List<String>): String {
    val key = when (route) {
        is DashboardRoute -> "dashboard"
        is GlanceEditRoute -> "dashboard"
        is RoomsEditRoute -> "dashboard"
        is ActivityRoute -> "activity"
        is EnergyRoute -> "energy"
        is PulseRoute -> "pulse"
        is MusicRoute -> "music"
        is SettingsRoute -> "settings"
        is NavBarEditRoute -> "settings"
        is HaAuthRoute -> "settings"
    }
    return if (key in tabKeys) key else tabKeys.first()
}
