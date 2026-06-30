package com.uc.homehealth.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Refresh
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.data.HaFavorite
import com.uc.homehealth.data.HaLight
import com.uc.homehealth.data.HaScene
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.data.RoomWidget
import com.uc.homehealth.data.WidgetSection
import com.uc.homehealth.data.WsConnectionStatus
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.PillShape
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.uc.homehealth.ui.components.BackButton
import com.uc.homehealth.ui.components.ClimateSheetOverlay
import com.uc.homehealth.ui.components.ColorPickerSheetOverlay
import com.uc.homehealth.ui.components.predictiveSheetTransform
import com.uc.homehealth.ui.components.FavCard
import com.uc.homehealth.ui.components.GlanceCard
import com.uc.homehealth.ui.components.glanceInkOn
import com.uc.homehealth.ui.components.SmartGlance
import com.uc.homehealth.ui.components.rememberGlancePalette
import com.uc.homehealth.ui.components.toCard
import com.uc.homehealth.ui.components.LightTile
import com.uc.homehealth.ui.components.PillToggle
import com.uc.homehealth.ui.components.PillToggleSize
import com.uc.homehealth.ui.components.RoomTile
import com.uc.homehealth.ui.components.SceneTile
import com.uc.homehealth.ui.components.BrightnessSlider
import com.uc.homehealth.ui.components.CameraWidgetTile
import com.uc.homehealth.ui.components.SensorWidgetTile
import com.uc.homehealth.ui.components.SwitchWidgetTile
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.RollingNumberText
import com.uc.homehealth.ui.components.haIconFor
import androidx.compose.ui.text.TextStyle
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.CustomColors
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.DashboardUiState
import com.uc.homehealth.ui.viewmodel.DashboardViewModel
import com.uc.homehealth.ui.viewmodel.GlanceActivity
import com.uc.homehealth.ui.viewmodel.GlanceSuggestion
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun DashboardScreen(
    onRoomClick: (HaRoom) -> Unit = {},
    onFavClick: (HaFavorite) -> Unit = {},
    onShowAllRooms: () -> Unit = {},
    onSceneTap: (String) -> Unit = {},
    onAddScene: () -> Unit = {},
    onAddFavorite: () -> Unit = {},
    onRemoveScene: (String) -> Unit = {},
    onReorderScenes: (List<HaScene>) -> Unit = {},
    onRemoveFavorite: (String) -> Unit = {},
    onReorderFavorites: (List<HaFavorite>) -> Unit = {},
    onEditGlance: () -> Unit = {},
    onEditRooms: () -> Unit = {},
    onFlightsTap: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenPulse: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val glanceSuggestion by viewModel.glanceSuggestion.collectAsStateWithLifecycle()
    val glanceActivity by viewModel.glanceActivity.collectAsStateWithLifecycle()
    // Pulse report for the glance alert tier — its own ViewModel/flow, deliberately
    // outside DashboardViewModel's maxed-out combine().
    val pulseViewModel: com.uc.homehealth.ui.viewmodel.PulseViewModel = hiltViewModel()
    val pulseState by pulseViewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreenContent(
        uiState = uiState,
        glanceSuggestion = glanceSuggestion,
        glanceActivity = glanceActivity,
        pulseReport = if (pulseState.isLoading) null else pulseState.report,
        onPulseTap = onOpenPulse,
        onActivityTap = onOpenActivity,
        onGlanceTileTap = viewModel::onGlanceTileTap,
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        onRoomClick = onRoomClick,
        onFavClick = onFavClick,
        onShowAllRooms = onShowAllRooms,
        onSceneTap = onSceneTap,
        onAddScene = onAddScene,
        onAddFavorite = onAddFavorite,
        onRemoveScene = onRemoveScene,
        onReorderScenes = onReorderScenes,
        onRemoveFavorite = onRemoveFavorite,
        onReorderFavorites = onReorderFavorites,
        onEditGlance = onEditGlance,
        onEditRooms = onEditRooms,
        onFlightsTap = onFlightsTap,
        onReconnect = onReconnect,
        onGoToSettings = onGoToSettings,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
    glanceSuggestion: GlanceSuggestion? = null,
    glanceActivity: GlanceActivity? = null,
    pulseReport: com.uc.homehealth.data.PulseReport? = null,
    onPulseTap: () -> Unit = {},
    onActivityTap: () -> Unit = {},
    onGlanceTileTap: (String) -> Unit = {},
    onRoomClick: (HaRoom) -> Unit,
    onFavClick: (HaFavorite) -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onShowAllRooms: () -> Unit = {},
    onSceneTap: (String) -> Unit = {},
    onAddScene: () -> Unit = {},
    onAddFavorite: () -> Unit = {},
    onRemoveScene: (String) -> Unit = {},
    onReorderScenes: (List<HaScene>) -> Unit = {},
    onRemoveFavorite: (String) -> Unit = {},
    onReorderFavorites: (List<HaFavorite>) -> Unit = {},
    onEditGlance: () -> Unit = {},
    onEditRooms: () -> Unit = {},
    onFlightsTap: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
) {
    // Terminal/offline states get the dedicated OfflineState screen. Only surface it
    // once we actually know the auth/connection state (uiState.loaded) and the WS is
    // not online — for a logged-out user with no demo data, or a sustained WS ERROR
    // (auto-retry exhausted). The loaded-guard avoids OfflineState flashing during the
    // initial synchronous frame; transient DISCONNECTED/CONNECTING while authed falls
    // through to the dashboard, which the skeleton overlay covers until data arrives.
    val showOffline = !uiState.isOnline && uiState.loaded && (
        !uiState.isLoggedIn ||
        uiState.connectionStatus == WsConnectionStatus.ERROR
    )
    if (showOffline) {
        OfflineState(
            status = uiState.connectionStatus,
            isLoggedIn = uiState.isLoggedIn,
            onReconnect = onReconnect,
            onGoToSettings = onGoToSettings,
        )
        return
    }

    // The real dashboard renders inside this Box; a skeleton overlay (added at the end
    // of the Box) sits on top during the first WS handshake and crossfades out once
    // online. Rendering the content underneath the opaque skeleton means there's no
    // jank/pop on load — the skeleton simply dissolves to reveal the populated
    // dashboard already laid out in place.
    Box(modifier = Modifier.fillMaxSize()) {
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    var scenesEditMode by remember { mutableStateOf(false) }
    var favsEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.scenes.isEmpty()) { if (uiState.scenes.isEmpty()) scenesEditMode = false }
    LaunchedEffect(uiState.favorites.isEmpty()) { if (uiState.favorites.isEmpty()) favsEditMode = false }

    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullState,
        modifier = Modifier.fillMaxWidth(),
        indicator = {
            // Push the spinner below the status bar / camera cutout — without this
            // top inset it anchors at y=0 and spins under the notch (the scrolling
            // content below already insets the same way).
            androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
            )
        },
    )  {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(scroll)
            .padding(bottom = 130.dp),
    ) {
        // ─── Greeting ────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Hello ",
                    fontFamily = InstrumentSerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    color = cs.onBackground,
                )
                Text(text = "👋", fontSize = 30.sp)
                Text(
                    text = " ${uiState.userName.ifBlank { "there" }}",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 42.sp,
                    lineHeight = 44.sp,
                    color = cs.onBackground,
                )
            }
        }

        // ─── At a glance (user-curated tiles) ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, top = 2.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "◐  At a glance")
            SectionIconButton(icon = Icons.Outlined.Edit, contentDescription = "Edit tiles", onClick = onEditGlance)
        }
        // One self-curating surface: the hero is whatever matters most right now (an alert
        // preempts it), pinned stats follow, then a live insight / delight. Tiles fade in
        // and out as they become relevant or stop being. See rememberGlanceFeed below.
        val glanceFeed = rememberGlanceFeed(
            uiState = uiState,
            suggestion = glanceSuggestion,
            activity = glanceActivity,
            pulse = pulseReport,
            onRoomClick = onRoomClick,
            onSceneTap = onSceneTap,
            onActivityTap = onActivityTap,
            onGlanceTileTap = onGlanceTileTap,
            onPulseTap = onPulseTap,
        )
        SmartGlance(
            cards = glanceFeed,
            onAdd = onEditGlance,
            modifier = Modifier.padding(horizontal = Spacing.ml),
        )

        // ─── Rooms mosaic ────────────────────────────────────────────────────
        val cs = MaterialTheme.colorScheme
        // ─── Rooms header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "🏡  Rooms")
            SectionIconButton(icon = Icons.Outlined.Edit, contentDescription = "Edit rooms", onClick = onEditRooms)
        }

        // ─── Rooms content ────────────────────────────────────────────────────
        run {
            // Base rooms (always visible — first 4) + animated overflow
            val rowHeights = listOf(188.dp, 160.dp, 188.dp, 160.dp, 188.dp, 160.dp)
            val baseRooms = uiState.rooms.take(minOf(4, uiState.rooms.size))
            val overflowRooms = if (uiState.rooms.size > 4) uiState.rooms.drop(4) else emptyList<HaRoom>()

            @Composable
            fun RoomColumns(rooms: List<HaRoom>, startRowIndex: Int = 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ml),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val left = rooms.filterIndexed { i, _ -> i % 2 == 0 }
                    val right = rooms.filterIndexed { i, _ -> i % 2 == 1 }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        left.forEachIndexed { i, room ->
                            RoomTile(room = room, height = rowHeights.getOrElse(startRowIndex + i) { 160.dp }, onTap = { onRoomClick(room) }, showWarnings = uiState.showRoomWarnings)
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        right.forEachIndexed { i, room ->
                            RoomTile(room = room, height = rowHeights.getOrElse(startRowIndex + i) { 160.dp }, onTap = { onRoomClick(room) }, showWarnings = uiState.showRoomWarnings)
                        }
                    }
                }
            }

            RoomColumns(rooms = baseRooms)

            if (overflowRooms.isNotEmpty()) {
                AnimatedVisibility(
                    visible = uiState.showAllRooms,
                    enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(220)),
                    exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(180)),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(10.dp))
                        RoomColumns(rooms = overflowRooms, startRowIndex = baseRooms.size / 2)
                    }
                }
                Tap(
                    onClick = onShowAllRooms,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ml).padding(top = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (uiState.showAllRooms) "Show less" else "Show all (${overflowRooms.size} more)",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ─── Quick access ────────────────────────────────────────────────────
        SectionLabel(
            text = "◎  Quick access",
            modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 24.dp, bottom = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.ml),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "__quick_flights__") {
                FlightsPill(count = uiState.flights.size, onTap = onFlightsTap)
            }
        }

        // ─── Quick scenes ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(top = 24.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "✦  Quick scenes")
            AnimatedContent(
                targetState = scenesEditMode,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "scenes_header",
            ) { inEditMode ->
                if (inEditMode) {
                    EditPill(editMode = true, onToggle = { scenesEditMode = false })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionIconButton(icon = Icons.Outlined.Add, contentDescription = "Add scene", onClick = onAddScene)
                        AnimatedVisibility(
                            visible = uiState.scenes.isNotEmpty(),
                            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f),
                        ) {
                            SectionIconButton(icon = Icons.Outlined.Edit, contentDescription = "Edit scenes", onClick = { scenesEditMode = true })
                        }
                    }
                }
            }
        }
        if (scenesEditMode) {
            ReorderableHorizontalRow(
                items = uiState.scenes,
                key = { it.id },
                spacing = 6.dp,
                contentPadding = PaddingValues(horizontal = Spacing.ml),
                onReorderCommit = onReorderScenes,
                onRemove = { onRemoveScene(it.id) },
            ) { scene ->
                SceneTile(scene = scene, onTap = {})
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.ml),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(uiState.scenes, key = { it.id }) { scene ->
                    SceneTile(scene = scene, onTap = { onSceneTap(scene.id) })
                }
            }
        }

        // ─── Favorites ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.xl, end = Spacing.xl, top = 28.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionLabel(text = "⭐  Favorites")
            AnimatedContent(
                targetState = favsEditMode,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "favs_header",
            ) { inEditMode ->
                if (inEditMode) {
                    EditPill(editMode = true, onToggle = { favsEditMode = false })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionIconButton(icon = Icons.Outlined.Add, contentDescription = "Add favorite", onClick = onAddFavorite)
                        AnimatedVisibility(
                            visible = uiState.favorites.isNotEmpty(),
                            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f),
                        ) {
                            SectionIconButton(icon = Icons.Outlined.Edit, contentDescription = "Edit favorites", onClick = { favsEditMode = true })
                        }
                    }
                }
            }
        }
        if (favsEditMode) {
            ReorderableHorizontalRow(
                items = uiState.favorites,
                key = { it.id },
                spacing = 10.dp,
                contentPadding = PaddingValues(horizontal = Spacing.ml),
                onReorderCommit = onReorderFavorites,
                onRemove = { onRemoveFavorite(it.id) },
            ) { fav ->
                FavCard(fav = fav, onTap = {})
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.ml),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.favorites, key = { it.id }) { fav ->
                    FavCard(fav = fav, onTap = { onFavClick(fav) })
                }
            }
        }
    }
    }

        // ─── Skeleton overlay ────────────────────────────────────────────────────
        // Covers the dashboard during the first WS handshake, then crossfades out once
        // online — revealing the real content that already rendered underneath, so the
        // load reads as a smooth dissolve instead of a pop. Initialised from the
        // current online state so it never flashes when we're already connected (demo
        // mode, or returning to this tab); a transient WS drop won't bring it back.
        var hasLoadedOnce by remember { mutableStateOf(uiState.isOnline) }
        LaunchedEffect(uiState.isOnline) { if (uiState.isOnline) hasLoadedOnce = true }
        // Re-cover the dashboard when a reconnect has actually WIPED the content (app
        // resumed from background: WS states clear, rooms empty out) — otherwise the page
        // flashes empty sections mid-reconnect. A transient drop that keeps data on screen
        // still doesn't bring the skeleton back.
        val reconnectWiped = !uiState.isOnline && uiState.rooms.isEmpty()
        val skeletonAlpha by animateFloatAsState(
            targetValue = if (!hasLoadedOnce || reconnectWiped) 1f else 0f,
            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
            label = "skeleton-crossfade",
        )
        if (skeletonAlpha > 0.004f) {
            com.uc.homehealth.ui.components.DashboardSkeleton(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(skeletonAlpha)
                    .background(cs.background),
            )
        }
    }
}

@Composable
private fun FlightsPill(count: Int, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    Tap(onClick = { haptic.navigation(); onTap() }) {
        Row(
            modifier = Modifier
                .background(cs.surfaceContainerHigh, PillShape)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Flight,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Flights",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = (-0.1).sp,
                    color = cs.onSurface,
                )
                Text(
                    text = when (count) {
                        0 -> "Tap to track"
                        1 -> "1 tracked"
                        else -> "$count tracked"
                    },
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    color = cs.onSurface.copy(alpha = 0.65f),
                )
            }
        }
    }
}

// ─── Offline state ──────────────────────────────────────────────────────────
// Dashboard collapses to a single sad-face + reconnect when not connected.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OfflineState(
    status: WsConnectionStatus,
    isLoggedIn: Boolean,
    onReconnect: () -> Unit,
    onGoToSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (title, subtitle, ctaLabel) = when {
        !isLoggedIn -> Triple(
            "Not connected",
            "Sign in to Home Assistant to see your home.",
            "Connect",
        )
        status == WsConnectionStatus.CONNECTING -> Triple(
            "Connecting…",
            "Reaching your Home Assistant server.",
            "Try again",
        )
        status == WsConnectionStatus.ERROR -> Triple(
            "Can't reach Home Assistant",
            "Check that your phone is on the same network as your server.",
            "Reconnect",
        )
        else -> Triple(
            "Not connected",
            "Open settings to reconnect to Home Assistant.",
            "Reconnect",
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(horizontal = Spacing.xl)
                .padding(bottom = 130.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "ʕ•́ᴥ•̀ʔ",
                fontFamily = InstrumentSerifFamily,
                fontSize = 64.sp,
                lineHeight = 72.sp,
                color = cs.onBackground,
            )
            Text(
                text = title,
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Normal,
                fontSize = 36.sp,
                lineHeight = 40.sp,
                color = cs.onBackground,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = subtitle,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Tap(
                onClick = { if (!isLoggedIn) onGoToSettings() else onReconnect() },
                modifier = Modifier.padding(top = 28.dp),
            ) {
                Row(
                    modifier = Modifier
                        .background(cs.primary, PillShape)
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = ctaLabel,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = cs.onPrimary,
                    )
                }
            }
        }

        // Pull-to-refresh-style loading indicator pinned near the top while the WS
        // is mid-handshake. Slides in/out so a manual Reconnect tap (or auto retry)
        // gets visible feedback before status flips to READY or back to ERROR.
        AnimatedVisibility(
            visible = status == WsConnectionStatus.CONNECTING,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = 24.dp),
            enter = slideInVertically(
                animationSpec = offsetSpring(),
                initialOffsetY = { -it * 2 },
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                animationSpec = offsetSpring(),
                targetOffsetY = { -it * 2 },
            ) + fadeOut(animationSpec = tween(220)),
        ) {
            androidx.compose.material3.ContainedLoadingIndicator()
        }
    }
}

// ─── Custom animated room sheet overlay ──────────────────────────────────────
// Uses AnimatedVisibility with slow springs instead of ModalBottomSheet so we
// can control animation feel directly (stiffness → 120fps smooth settle).

// internal so Navigation.kt can render it above BottomNavBar
@OptIn(ExperimentalHazeMaterialsApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RoomSheetOverlay(
    room: HaRoom?,
    lights: List<HaLight>,
    // Live HA connection status, so the sheet can explain a mid-session drop (lights
    // vanishing) instead of leaving the user staring at an empty list. READY in demo.
    connectionStatus: WsConnectionStatus = WsConnectionStatus.READY,
    onReconnect: () -> Unit = {},
    climate: com.uc.homehealth.data.HaClimate? = null,
    tempHistory: List<Float> = emptyList(),
    widgets: List<RoomWidget> = emptyList(),
    widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    widgetHistories: Map<String, List<Float>> = emptyMap(),
    widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?> = emptyMap(),
    widgetLocations: Map<String, com.uc.homehealth.data.HaPersonLocation?> = emptyMap(),
    widgetClimateStates: Map<String, com.uc.homehealth.data.HaClimate?> = emptyMap(),
    showWidgetCatalog: Boolean = false,
    showWarnings: Boolean = true,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onToggle: (entityId: String, isOn: Boolean) -> Unit = { _, _ -> },
    onBrightnessChange: (entityId: String, brightness: Int) -> Unit = { _, _ -> },
    onColorChange: (entityId: String, r: Int, g: Int, b: Int) -> Unit = { _, _, _, _ -> },
    onColorTempChange: (entityId: String, kelvin: Int) -> Unit = { _, _ -> },
    onClimateTargetChange: (entityId: String, temperature: Float) -> Unit = { _, _ -> },
    onClimateModeChange: (entityId: String, mode: String) -> Unit = { _, _ -> },
    onClimateFanModeChange: (entityId: String, fanMode: String) -> Unit = { _, _ -> },
    climateModeOrders: Map<String, List<String>> = emptyMap(),
    onClimateModeOrderChange: (entityId: String, modes: List<String>) -> Unit = { _, _ -> },
    climateFanOrders: Map<String, List<String>> = emptyMap(),
    onClimateFanOrderChange: (entityId: String, fans: List<String>) -> Unit = { _, _ -> },
    onShowWidgetCatalog: () -> Unit = {},
    onHideWidgetCatalog: () -> Unit = {},
    onPickSwitchType: () -> Unit = {},
    onPickSensorType: () -> Unit = {},
    onPickCameraType: () -> Unit = {},
    onPickMediaType: () -> Unit = {},
    onPickClimateType: () -> Unit = {},
    onPickLocationType: () -> Unit = {},
    onPickAirQualityType: () -> Unit = {},
    // Section-scoped add affordances: the Media/Climate tab "Add" buttons open the
    // matching entity picker directly (vs. the generic More catalog).
    onAddMedia: () -> Unit = {},
    onAddClimate: () -> Unit = {},
    onWidgetClick: (RoomWidget) -> Unit = {},
    onWidgetLongPress: (RoomWidget) -> Unit = {},
    onPtzPress: (String) -> Unit = {},
    onRemoveWidget: (RoomWidget) -> Unit = {},
    onReorderWidgets: (List<RoomWidget>) -> Unit = {},
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks = com.uc.homehealth.ui.components.MediaCardCallbacks(),
) {
    val visible = room != null
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Local state for the climate detail sheet that overlays this room sheet.
    var climateSheetClimate by remember(room?.id) { mutableStateOf<com.uc.homehealth.data.HaClimate?>(null) }
    // Local state for the color/temperature picker sheet that overlays this room sheet.
    var colorPickerLight by remember(room?.id) { mutableStateOf<HaLight?>(null) }
    // Light-detail selection, hoisted out of RoomSheetContent so the single back
    // handler below can pop it (return to overview) instead of dismissing the sheet.
    // Reset only when a *different* room opens — keep it through the dismiss
    // slide-out (when `room` is briefly null) so the panel doesn't flip to overview
    // mid-animation.
    var selectedLightId by remember { mutableStateOf<String?>(null) }
    // Attention sub-panel (devices needing attention), pushed like the light detail.
    var alertsShown by remember { mutableStateOf(false) }
    LaunchedEffect(room?.id) { if (room != null) { selectedLightId = null; alertsShown = false } }

    val overlayHaptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    val onDismissWithHaptic: () -> Unit = { overlayHaptic.confirm(); onDismiss() }

    // Back gesture: pop the deepest open sub-view first (color picker → climate →
    // widget catalog → light detail), and only dismiss the whole room sheet once
    // we're back at the overview. Without this, back falls through and exits the app.
    // Registered only while open so it joins the back dispatcher in OPENING order —
    // sheets opened on top register later and take the gesture first (see the
    // predictive-back notes in AppBottomSheet.kt). The panel only tracks the gesture
    // visually when back will dismiss the sheet; inner pops shouldn't drag the panel.
    val roomBackProgress = remember { Animatable(0f) }
    LaunchedEffect(visible) { if (visible) roomBackProgress.snapTo(0f) }
    if (visible) {
        PredictiveBackHandler { events ->
            val dismissesSheet = colorPickerLight == null && climateSheetClimate == null &&
                !showWidgetCatalog && selectedLightId == null && !alertsShown
            try {
                events.collect { event ->
                    if (dismissesSheet) roomBackProgress.snapTo(event.progress)
                }
                when {
                    colorPickerLight != null -> colorPickerLight = null
                    climateSheetClimate != null -> climateSheetClimate = null
                    showWidgetCatalog -> onHideWidgetCatalog()
                    selectedLightId != null -> selectedLightId = null
                    alertsShown -> alertsShown = false
                    else -> onDismissWithHaptic()
                }
            } catch (_: CancellationException) {
                roomBackProgress.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        else Modifier
                    )
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismissWithHaptic,
                    )
            )
        }

        // Sheet panel
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            // Latch onto the most-recent non-null room so the title/color stay
            // valid through the exit slide-out animation (when `room` is null),
            // while still updating when a different room is opened before the
            // previous content slot has been disposed. A plain `remember { room }`
            // snapshotted once and never refreshed — so opening room Y after X
            // kept X's title and accent color on screen.
            var capturedRoom by remember { mutableStateOf(room) }
            if (room != null && room.id != capturedRoom?.id) capturedRoom = room
            var cumulativeDrag by remember { mutableStateOf(0f) }
            val sheetHaptic = com.uc.homehealth.ui.components.rememberAppHaptics()

            Column(
                modifier = Modifier
                    .predictiveSheetTransform { roomBackProgress.value }
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.97f)
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                    .navigationBarsPadding(),
            ) {
                // Drag handle — brighter, draggable to dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp)
                        .pointerInput(Unit) {
                            val threshold = 120.dp.toPx()
                            detectVerticalDragGestures(
                                onDragStart = { cumulativeDrag = 0f },
                                onDragEnd = { cumulativeDrag = 0f },
                                onVerticalDrag = { _: PointerInputChange, dragAmount: Float ->
                                    if (dragAmount > 0f) cumulativeDrag += dragAmount
                                    if (cumulativeDrag > threshold) {
                                        cumulativeDrag = 0f
                                        sheetHaptic.confirm()
                                        onDismiss()
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 44.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), PillShape)
                    )
                }

                capturedRoom?.let { r ->
                    RoomSheetContent(
                        room = r,
                        externalLights = lights,
                        connectionStatus = connectionStatus,
                        onReconnect = onReconnect,
                        climate = climate,
                        tempHistory = tempHistory,
                        widgets = widgets,
                        widgetStates = widgetStates,
                        widgetHistories = widgetHistories,
                        widgetCameraSnapshots = widgetCameraSnapshots,
                        widgetMediaStates = widgetMediaStates,
                        widgetLocations = widgetLocations,
                        widgetClimateStates = widgetClimateStates,
                        showWidgetCatalog = showWidgetCatalog,
                        showWarnings = showWarnings,
                        selectedLightId = selectedLightId,
                        onSelectLight = { selectedLightId = it },
                        alertsShown = alertsShown,
                        onShowAlerts = { alertsShown = true },
                        onHideAlerts = { alertsShown = false },
                        onDismiss = onDismissWithHaptic,
                        onToggle = onToggle,
                        onBrightnessChange = onBrightnessChange,
                        onColorChange = onColorChange,
                        onColorTempChange = onColorTempChange,
                        onClimateTargetChange = onClimateTargetChange,
                        onClimateModeChange = onClimateModeChange,
                        onClimateCardTap = { c -> climateSheetClimate = c },
                        onShowWidgetCatalog = onShowWidgetCatalog,
                        onHideWidgetCatalog = onHideWidgetCatalog,
                        onPickSwitchType = onPickSwitchType,
                        onPickSensorType = onPickSensorType,
                        onPickCameraType = onPickCameraType,
                        onPickMediaType = onPickMediaType,
                        onPickClimateType = onPickClimateType,
                        onPickLocationType = onPickLocationType,
                        onPickAirQualityType = onPickAirQualityType,
                        onAddMedia = onAddMedia,
                        onAddClimate = onAddClimate,
                        onWidgetClick = onWidgetClick,
                        onWidgetLongPress = onWidgetLongPress,
                        onPtzPress = onPtzPress,
                        onRemoveWidget = onRemoveWidget,
                        onReorderWidgets = onReorderWidgets,
                        onOpenColorPicker = { l -> colorPickerLight = l },
                        mediaCallbacks = mediaCallbacks,
                    )
                }
            }
        }

        // Climate detail sheet — overlays the room sheet when a ClimateCard is tapped.
        val accent = room?.let { try { Color(AndroidColor.parseColor(it.colorHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary } }
            ?: MaterialTheme.colorScheme.primary
        val ink = room?.let { try { Color(AndroidColor.parseColor(it.inkHex)) } catch (_: Exception) { MaterialTheme.colorScheme.onPrimary } }
            ?: MaterialTheme.colorScheme.onPrimary
        ClimateSheetOverlay(
            climate = climateSheetClimate,
            roomLabel = room?.name ?: "",
            accentColor = accent,
            inkColor = ink,
            hazeState = hazeState,
            onDismiss = { climateSheetClimate = null },
            onTargetChange = onClimateTargetChange,
            onModeChange = onClimateModeChange,
            onFanModeChange = onClimateFanModeChange,
            modeOrders = climateModeOrders,
            onModeOrderChange = onClimateModeOrderChange,
            fanOrders = climateFanOrders,
            onFanOrderChange = onClimateFanOrderChange,
        )

        // Color / temperature picker sheet — overlays the room sheet when the
        // palette button is tapped in the light detail view.
        ColorPickerSheetOverlay(
            light = colorPickerLight,
            hazeState = hazeState,
            onDismiss = { colorPickerLight = null },
            onColorCommit = { r, g, b -> colorPickerLight?.let { onColorChange(it.id, r, g, b) } },
            onTemperatureCommit = { k -> colorPickerLight?.let { onColorTempChange(it.id, k) } },
        )
    }
}

// ─── Room sheet content ───────────────────────────────────────────────────────

@Composable
private fun RoomSheetContent(
    room: HaRoom,
    externalLights: List<HaLight>,
    connectionStatus: WsConnectionStatus,
    onReconnect: () -> Unit,
    climate: com.uc.homehealth.data.HaClimate?,
    tempHistory: List<Float>,
    widgets: List<RoomWidget>,
    widgetStates: Map<String, HaEntityValue?>,
    widgetHistories: Map<String, List<Float>>,
    widgetCameraSnapshots: Map<String, String?>,
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?>,
    widgetLocations: Map<String, com.uc.homehealth.data.HaPersonLocation?>,
    widgetClimateStates: Map<String, com.uc.homehealth.data.HaClimate?>,
    showWidgetCatalog: Boolean,
    showWarnings: Boolean,
    selectedLightId: String?,
    onSelectLight: (String?) -> Unit,
    alertsShown: Boolean,
    onShowAlerts: () -> Unit,
    onHideAlerts: () -> Unit,
    onDismiss: () -> Unit,
    onToggle: (entityId: String, isOn: Boolean) -> Unit,
    onBrightnessChange: (entityId: String, brightness: Int) -> Unit,
    onColorChange: (entityId: String, r: Int, g: Int, b: Int) -> Unit,
    onColorTempChange: (entityId: String, kelvin: Int) -> Unit,
    onClimateTargetChange: (entityId: String, temperature: Float) -> Unit,
    onClimateModeChange: (entityId: String, mode: String) -> Unit,
    onClimateCardTap: (com.uc.homehealth.data.HaClimate) -> Unit,
    onShowWidgetCatalog: () -> Unit,
    onHideWidgetCatalog: () -> Unit,
    onPickSwitchType: () -> Unit,
    onPickSensorType: () -> Unit,
    onPickCameraType: () -> Unit,
    onPickMediaType: () -> Unit,
    onPickClimateType: () -> Unit,
    onPickLocationType: () -> Unit,
    onPickAirQualityType: () -> Unit,
    onAddMedia: () -> Unit,
    onAddClimate: () -> Unit,
    onWidgetClick: (RoomWidget) -> Unit,
    onWidgetLongPress: (RoomWidget) -> Unit,
    onPtzPress: (String) -> Unit = {},
    onRemoveWidget: (RoomWidget) -> Unit,
    onReorderWidgets: (List<RoomWidget>) -> Unit,
    onOpenColorPicker: (HaLight) -> Unit,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
) {
    val cs = MaterialTheme.colorScheme
    val accentColor = try { Color(AndroidColor.parseColor(room.colorHex)) } catch (_: Exception) { cs.primary }
    val inkColor = try { Color(AndroidColor.parseColor(room.inkHex)) } catch (_: Exception) { cs.onPrimary }

    var selectedTab by remember(room.id) { mutableStateOf("Lights") }
    // Which tab's section is currently in edit mode (null = none). Hoisted here so a
    // long-press on a room tab can drop that section straight into edit mode, and so a
    // normal tab tap exits it. Only ever equals the selected tab (or null).
    var editTab by remember(room.id) { mutableStateOf<String?>(null) }

    // Per-entity intent overlay. Set whenever the user touches a light; overrides
    // the matching field of externalLights so HA's brightness-ramp state_changed
    // echoes don't yank the slider mid-drag or jiggle it just after release.
    // Each entry has a TTL — long while the user is dragging, short (a few
    // seconds) after release — and is dropped early once HA's reported state
    // converges to the intended value.
    val intent = remember(room.id) { mutableStateMapOf<String, LightIntent>() }

    fun setIntent(id: String, ttlMs: Long, block: LightIntent.() -> LightIntent) {
        val base = intent[id] ?: LightIntent()
        intent[id] = base.block().copy(expiresAt = System.currentTimeMillis() + ttlMs)
    }

    // Convergence cleanup: when HA's state catches up to the user's intent
    // (within tolerance), drop the override so future external updates flow
    // through. Skipped while the user is still dragging (long TTL = active).
    LaunchedEffect(externalLights) {
        val nowMs = System.currentTimeMillis()
        val converged = intent.entries.mapNotNull { (id, i) ->
            if (i.expiresAt - nowMs > INTENT_RELEASE_TTL_MS) return@mapNotNull null
            val ext = externalLights.find { it.id == id } ?: return@mapNotNull id
            val briOk = i.brightness?.let { kotlin.math.abs(it - ext.brightness) <= 2 } ?: true
            val onOk = i.isOn?.let { it == ext.isOn } ?: true
            val colorOk = i.colorHex?.let { it.equals(ext.colorHex, ignoreCase = true) } ?: true
            val tempOk = i.colorTempKelvin?.let { tk ->
                ext.colorTempKelvin?.let { ek -> kotlin.math.abs(tk - ek) <= 50 } == true
            } ?: true
            if (briOk && onOk && colorOk && tempOk) id else null
        }
        converged.forEach { intent.remove(it) }
    }

    // TTL fallback: if HA never echoes back (offline bulb, dropped command),
    // expire the overlay so the slider eventually reflects reality.
    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            if (intent.isEmpty()) continue
            val nowMs = System.currentTimeMillis()
            val expired = intent.entries.filter { nowMs > it.value.expiresAt }.map { it.key }
            expired.forEach { intent.remove(it) }
        }
    }

    // Effective lights = externalLights with per-entity intent overlay applied.
    val lights = externalLights.map { ext ->
        intent[ext.id]?.let { i ->
            ext.copy(
                brightness = i.brightness ?: ext.brightness,
                isOn = i.isOn ?: ext.isOn,
                colorHex = i.colorHex ?: ext.colorHex,
                colorTempKelvin = i.colorTempKelvin ?: ext.colorTempKelvin,
            )
        } ?: ext
    }

    // Derived view state: three mutually-exclusive panels share the sheet body and
    // animate between each other (slide-forward when leaving Overview, slide-back when
    // returning to Overview).
    val sheetView: String = when {
        showWidgetCatalog -> "widget_catalog"
        selectedLightId != null -> "light_detail"
        alertsShown && room.hasAlert -> "alerts"
        else -> "overview"
    }

    AnimatedContent(
        targetState = sheetView,
        transitionSpec = {
            val goingForward = targetState != "overview"
            if (goingForward) {
                (slideInHorizontally { it } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally { -it } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally { -it } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally { it } + fadeOut(tween(150)))
            }
        },
        label = "room_sheet_nav",
    ) { view ->
        when (view) {
            "light_detail" -> {
                val currentLightId = selectedLightId
                val currentLight = currentLightId?.let { id -> lights.find { it.id == id } }
                if (currentLight != null && currentLightId != null) {
                    LightDetailPanel(
                        light = currentLight,
                        onBack = { onSelectLight(null) },
                        onToggle = {
                            val newIsOn = !currentLight.isOn
                            setIntent(currentLightId, INTENT_RELEASE_TTL_MS) {
                                copy(isOn = newIsOn, brightness = if (newIsOn) 70 else 0)
                            }
                            onToggle(currentLightId, newIsOn)
                        },
                        onBrightnessChange = { bri ->
                            setIntent(currentLightId, INTENT_DRAG_TTL_MS) {
                                copy(brightness = bri, isOn = bri > 0)
                            }
                        },
                        onBrightnessChangeFinished = { bri ->
                            setIntent(currentLightId, INTENT_RELEASE_TTL_MS) {
                                copy(brightness = bri, isOn = bri > 0)
                            }
                            onBrightnessChange(currentLightId, bri)
                        },
                        onColorChange = { r, g, b ->
                            val hex = "#%02x%02x%02x".format(r, g, b)
                            setIntent(currentLightId, INTENT_DRAG_TTL_MS) { copy(colorHex = hex) }
                        },
                        onColorChangeFinished = { r, g, b ->
                            val hex = "#%02x%02x%02x".format(r, g, b)
                            setIntent(currentLightId, INTENT_RELEASE_TTL_MS) { copy(colorHex = hex) }
                            onColorChange(currentLightId, r, g, b)
                        },
                        onColorTempChange = { kelvin ->
                            setIntent(currentLightId, INTENT_DRAG_TTL_MS) { copy(colorTempKelvin = kelvin) }
                        },
                        onColorTempChangeFinished = { kelvin ->
                            setIntent(currentLightId, INTENT_RELEASE_TTL_MS) { copy(colorTempKelvin = kelvin) }
                            onColorTempChange(currentLightId, kelvin)
                        },
                        onOpenColorPicker = { onOpenColorPicker(currentLight) },
                    )
                }
            }
            "alerts" -> {
                AlertsPanel(room = room, onBack = onHideAlerts)
            }
            "widget_catalog" -> {
                WidgetCatalogPanel(
                    accentColor = accentColor,
                    inkColor = inkColor,
                    onBack = onHideWidgetCatalog,
                    onPickSwitchType = onPickSwitchType,
                    onPickSensorType = onPickSensorType,
                    onPickCameraType = onPickCameraType,
                    onPickMediaType = onPickMediaType,
                    onPickClimateType = onPickClimateType,
                    onPickLocationType = onPickLocationType,
                    onPickAirQualityType = onPickAirQualityType,
                )
            }
            else -> {
                RoomOverviewContent(
                    room = room,
                    lights = lights,
                    climate = climate,
                    accentColor = accentColor,
                    inkColor = inkColor,
                    showWarnings = showWarnings,
                    connectionStatus = connectionStatus,
                    onReconnect = onReconnect,
                    selectedTab = selectedTab,
                    // A plain tab tap exits edit mode — but guard against the long-press
                    // race (the ToggleButton's click also fires on release): only clear
                    // edit when switching to a DIFFERENT tab than the one just put in edit.
                    onTabSelected = { if (it != editTab) editTab = null; selectedTab = it },
                    onTabLongPress = { selectedTab = it; editTab = it },
                    editTab = editTab,
                    onEditTabChange = { editTab = it },
                    onDismiss = onDismiss,
                    onShowAlerts = onShowAlerts,
                    tempHistory = tempHistory,
                    widgets = widgets,
                    widgetStates = widgetStates,
                    widgetHistories = widgetHistories,
                    widgetCameraSnapshots = widgetCameraSnapshots,
                    widgetMediaStates = widgetMediaStates,
                    widgetLocations = widgetLocations,
                    widgetClimateStates = widgetClimateStates,
                    onLightSelect = { light -> onSelectLight(light.id) },
                    onClimateTargetChange = onClimateTargetChange,
                    onClimateModeChange = onClimateModeChange,
                    onClimateCardTap = onClimateCardTap,
                    onShowWidgetCatalog = onShowWidgetCatalog,
                    onAddMedia = onAddMedia,
                    onAddClimate = onAddClimate,
                    onWidgetClick = onWidgetClick,
                    onWidgetLongPress = onWidgetLongPress,
                    onPtzPress = onPtzPress,
                    onRemoveWidget = onRemoveWidget,
                    onReorderWidgets = onReorderWidgets,
                    mediaCallbacks = mediaCallbacks,
                    onToggle = { id ->
                        val newIsOn = !(lights.find { it.id == id }?.isOn ?: false)
                        setIntent(id, INTENT_RELEASE_TTL_MS) {
                            copy(isOn = newIsOn, brightness = if (newIsOn) 70 else 0)
                        }
                        onToggle(id, newIsOn)
                    },
                    onBrightnessChange = { id, bri ->
                        setIntent(id, INTENT_DRAG_TTL_MS) { copy(brightness = bri, isOn = bri > 0) }
                    },
                    onBrightnessChangeFinished = { id, bri ->
                        setIntent(id, INTENT_RELEASE_TTL_MS) { copy(brightness = bri, isOn = bri > 0) }
                        onBrightnessChange(id, bri)
                    },
                    onColorChange = { id, r, g, b ->
                        val hex = "#%02x%02x%02x".format(r, g, b)
                        setIntent(id, INTENT_DRAG_TTL_MS) { copy(colorHex = hex) }
                    },
                    onColorChangeFinished = { id, r, g, b ->
                        val hex = "#%02x%02x%02x".format(r, g, b)
                        setIntent(id, INTENT_RELEASE_TTL_MS) { copy(colorHex = hex) }
                        onColorChange(id, r, g, b)
                    },
                    onColorTempChange = { id, kelvin ->
                        setIntent(id, INTENT_DRAG_TTL_MS) { copy(colorTempKelvin = kelvin) }
                    },
                    onColorTempChangeFinished = { id, kelvin ->
                        setIntent(id, INTENT_RELEASE_TTL_MS) { copy(colorTempKelvin = kelvin) }
                        onColorTempChange(id, kelvin)
                    },
                    onMasterToggle = { isOn ->
                        // Only target available lights. Sending turn_on/off to an
                        // unavailable bulb never echoes back, leaving its stale isOn
                        // to drag the master average.
                        val targets = lights.filter { it.isAvailable && it.isOn != isOn }
                        targets.forEach { l ->
                            setIntent(l.id, INTENT_RELEASE_TTL_MS) {
                                copy(isOn = isOn, brightness = if (isOn) maxOf(l.brightness, 30) else 0)
                            }
                            onToggle(l.id, isOn)
                        }
                    },
                    onMasterBrightness = { bri ->
                        lights.filter { it.isOn && it.isAvailable }.forEach { l ->
                            setIntent(l.id, INTENT_DRAG_TTL_MS) { copy(brightness = bri, isOn = bri > 0) }
                        }
                    },
                    onMasterBrightnessFinished = { bri ->
                        lights.filter { it.isOn && it.isAvailable }.forEach { l ->
                            setIntent(l.id, INTENT_RELEASE_TTL_MS) { copy(brightness = bri, isOn = bri > 0) }
                            onBrightnessChange(l.id, bri)
                        }
                    },
                )
            }
        }
    }
}

private data class LightIntent(
    val brightness: Int? = null,
    val isOn: Boolean? = null,
    val colorHex: String? = null,
    val colorTempKelvin: Int? = null,
    val expiresAt: Long = 0L,
)

// Long enough that a typical drag never expires mid-gesture; the convergence
// check uses this threshold to detect "still dragging" vs "waiting for ack".
private const val INTENT_DRAG_TTL_MS = 60_000L

// After release, hold the overlay long enough to ride out HA's brightness ramp
// echoes but short enough that a failed command doesn't leave the slider lying.
private const val INTENT_RELEASE_TTL_MS = 5_000L

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RoomTabButtonGroup(
    tabs: List<String>,
    selectedTab: String,
    accentColor: Color,
    inkColor: Color,
    onTabSelected: (String) -> Unit,
    onTabLongPress: (String) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    val tabIcons = listOf(
        Icons.Outlined.Lightbulb,
        Icons.Outlined.Thermostat,
        Icons.Outlined.Speaker,
        Icons.Outlined.AutoAwesome,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isActive = tab == selectedTab
            ToggleButton(
                checked = isActive,
                onCheckedChange = { if (!isActive) { haptic.navigation(); onTabSelected(tab) } },
                // Hold a tab to jump into that section's edit mode. A parallel long-press
                // detector that NEVER consumes the down, so the ToggleButton's own tap-to-
                // select still fires normally (awaitLongPressOrCancellation is detection-only
                // — unlike detectTapGestures, which consumes the press and breaks the toggle).
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(tab) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (awaitLongPressOrCancellation(down.id) != null) {
                                haptic.editLongPress()
                                onTabLongPress(tab)
                            }
                        }
                    },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = accentColor,
                    checkedContentColor = inkColor,
                    containerColor = cs.surfaceContainerHigh,
                    contentColor = cs.onSurface,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = tabIcons.getOrElse(index) { Icons.Outlined.AutoAwesome },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = tab,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

// ── Connection-lost banner ─────────────────────────────────────────────────────
// Shown inside the room sheet whenever the live HA connection isn't READY. Mirrors
// the attention-card styling (coral tint + badge). Tap (or the refresh affordance)
// requests an immediate reconnect; while CONNECTING it shows a spinner instead.
@Composable
private fun RoomConnectionBanner(
    status: WsConnectionStatus,
    onReconnect: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val haptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    val connecting = status == WsConnectionStatus.CONNECTING
    val (title, subtitle) = when (status) {
        WsConnectionStatus.CONNECTING -> "Reconnecting…" to "Restoring the live connection."
        WsConnectionStatus.AUTH_INVALID -> "Session expired" to "Sign in again to control devices."
        WsConnectionStatus.IP_BANNED -> "Blocked by Home Assistant" to "This device's IP is temporarily banned."
        else -> "Connection lost" to "Can't reach Home Assistant — controls may be stale."
    }
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(custom.coral.copy(alpha = 0.12f))
            .then(
                if (!connecting) Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { haptic.navigation(); onReconnect() } else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(custom.coral, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = glanceInkOn(custom.coral),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurface,
            )
            Text(
                text = subtitle,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        if (connecting) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = custom.coral,
            )
        } else {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = "Reconnect",
                tint = cs.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RoomOverviewContent(
    room: HaRoom,
    lights: List<HaLight>,
    climate: com.uc.homehealth.data.HaClimate?,
    accentColor: Color,
    inkColor: Color,
    showWarnings: Boolean = true,
    connectionStatus: WsConnectionStatus = WsConnectionStatus.READY,
    onReconnect: () -> Unit = {},
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onTabLongPress: (String) -> Unit = {},
    editTab: String? = null,
    onEditTabChange: (String?) -> Unit = {},
    onDismiss: () -> Unit,
    onShowAlerts: () -> Unit = {},
    tempHistory: List<Float> = emptyList(),
    widgets: List<RoomWidget> = emptyList(),
    widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    widgetHistories: Map<String, List<Float>> = emptyMap(),
    widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?> = emptyMap(),
    widgetLocations: Map<String, com.uc.homehealth.data.HaPersonLocation?> = emptyMap(),
    widgetClimateStates: Map<String, com.uc.homehealth.data.HaClimate?> = emptyMap(),
    onShowWidgetCatalog: () -> Unit = {},
    onAddMedia: () -> Unit = {},
    onAddClimate: () -> Unit = {},
    onWidgetClick: (RoomWidget) -> Unit = {},
    onWidgetLongPress: (RoomWidget) -> Unit = {},
    onPtzPress: (String) -> Unit = {},
    onRemoveWidget: (RoomWidget) -> Unit = {},
    onReorderWidgets: (List<RoomWidget>) -> Unit = {},
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks = com.uc.homehealth.ui.components.MediaCardCallbacks(),
    onLightSelect: (HaLight) -> Unit,
    onToggle: (String) -> Unit,
    onBrightnessChange: (String, Int) -> Unit,
    onBrightnessChangeFinished: (String, Int) -> Unit,
    onColorChange: (String, Int, Int, Int) -> Unit,
    onColorChangeFinished: (String, Int, Int, Int) -> Unit,
    onColorTempChange: (String, Int) -> Unit,
    onColorTempChangeFinished: (String, Int) -> Unit,
    onMasterToggle: (Boolean) -> Unit,
    onMasterBrightness: (Int) -> Unit,
    onMasterBrightnessFinished: (Int) -> Unit,
    onClimateTargetChange: (entityId: String, temperature: Float) -> Unit = { _, _ -> },
    onClimateModeChange: (entityId: String, mode: String) -> Unit = { _, _ -> },
    onClimateCardTap: (com.uc.homehealth.data.HaClimate) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    val tabs = listOf("Lights", "Climate", "Media", "More")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // ── Title row ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = haIconFor(room.icon),
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 26.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = buildString {
                        if (room.activeCount > 0) append("${room.activeCount} active · ")
                        append("${room.deviceCount} devices")
                    },
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            Tap(onClick = onDismiss) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Connection banner — only when the live HA link isn't READY. Without it,
        // a mid-session drop just makes the light tiles vanish with no explanation.
        if (connectionStatus != WsConnectionStatus.READY) {
            RoomConnectionBanner(
                status = connectionStatus,
                onReconnect = onReconnect,
            )
        }

        // ── Attention card — compact summary that pushes a dedicated detail panel
        // (mirrors how a light card opens its detail view), instead of expanding inline.
        if (room.hasAlert && showWarnings) {
            val custom = MaterialTheme.customColors
            val count = room.alerts.size
            Tap(
                onClick = { haptic.navigation(); onShowAlerts() },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(custom.coral.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(custom.coral, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = glanceInkOn(custom.coral),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = if (count == 1) "1 device needs attention"
                            else "$count devices need attention",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "View devices",
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // ── Env hero — sine wave fill (Phase 6: replace with live HA sensor data) ──
        // Only shown when the room actually has a temperature/humidity sensor (or a
        // user-set override). With neither resolved the repository leaves both null, so
        // don't render a meaningless "0.0° · 0%" graph by default.
        val roomTemp = room.temp
        val roomHumidity = room.humidity
        val hasEnvSensor = roomTemp != null || roomHumidity != null
        if (hasEnvSensor) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 14.dp)
                .fillMaxWidth()
                .height(140.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainerHigh),
        ) {
            TempHistoryGraph(history = tempHistory, color = accentColor, modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    if (roomTemp != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingNumberText(
                                text = "%.1f".format(roomTemp),
                                style = TextStyle(
                                    fontFamily = InstrumentSerifFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 68.sp,
                                    lineHeight = 70.sp,
                                    color = cs.onSurface,
                                ),
                                labelPrefix = "hero_temp",
                            )
                            Text(
                                text = "°",
                                fontFamily = InstrumentSerifFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 32.sp,
                                color = cs.onSurface,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (roomHumidity != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                RollingNumberText(
                                    text = roomHumidity.toString(),
                                    style = TextStyle(
                                        fontFamily = InstrumentSerifFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 22.sp,
                                        color = cs.onSurfaceVariant,
                                    ),
                                    labelPrefix = "hero_humidity",
                                )
                                Text(
                                    text = "%",
                                    fontFamily = InstrumentSerifFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                    color = cs.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                            Text(
                                text = "HUMIDITY",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = "Comfortable · holding steady",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        }

        // ── Master control card ───────────────────────────────────────────────
        LightMasterCard(
            room = room,
            lights = lights,
            accentColor = accentColor,
            inkColor = inkColor,
            onMasterToggle = onMasterToggle,
            onMasterBrightness = onMasterBrightness,
            onMasterBrightnessFinished = onMasterBrightnessFinished,
        )

        // ── Tab chips (connected ButtonGroup) ─────────────────────────────────
        RoomTabButtonGroup(
            tabs = tabs,
            selectedTab = selectedTab,
            accentColor = accentColor,
            inkColor = inkColor,
            onTabSelected = onTabSelected,
            onTabLongPress = onTabLongPress,
        )

        // ── Tab content: directional slide + animated height ─────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val fromIndex = tabs.indexOf(initialState)
                val toIndex = tabs.indexOf(targetState)
                val goingRight = toIndex > fromIndex
                (slideInHorizontally(offsetSpring()) { if (goingRight) it else -it } +
                    fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(offsetSpring()) { if (goingRight) -it else it } +
                    fadeOut(tween(160))) using
                SizeTransform { _, _ ->
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = "tab_content",
        ) { tab ->
            when (tab) {
                "Lights" -> LightsTabContent(
                    lights = lights,
                    onLightSelect = onLightSelect,
                    onToggle = onToggle,
                    onBrightnessChange = onBrightnessChange,
                    onBrightnessChangeFinished = onBrightnessChangeFinished,
                    onColorChange = onColorChange,
                    onColorChangeFinished = onColorChangeFinished,
                    onColorTempChange = onColorTempChange,
                    onColorTempChangeFinished = onColorTempChangeFinished,
                )
                "Climate" -> ClimateTabContent(
                    climate = climate,
                    climateWidgets = widgets.filterIsInstance<RoomWidget.Climate>()
                        .filter { it.section == WidgetSection.CLIMATE },
                    climateStates = widgetClimateStates,
                    onTargetChange = onClimateTargetChange,
                    onModeChange = onClimateModeChange,
                    onCardTap = onClimateCardTap,
                    onAddClimate = onAddClimate,
                    onRemoveWidget = onRemoveWidget,
                    editMode = editTab == tab,
                    onEditModeChange = { on -> onEditTabChange(if (on) tab else null) },
                )
                "Media" -> MediaTabContent(
                    widgets = widgets,
                    widgetMediaStates = widgetMediaStates,
                    mediaCallbacks = mediaCallbacks,
                    onAddMedia = onAddMedia,
                    onRemoveWidget = onRemoveWidget,
                    editMode = editTab == tab,
                    onEditModeChange = { on -> onEditTabChange(if (on) tab else null) },
                )
                "More" -> MoreTabContent(
                    widgets = widgets.filter { it.section == WidgetSection.MORE },
                    widgetStates = widgetStates,
                    widgetHistories = widgetHistories,
                    widgetCameraSnapshots = widgetCameraSnapshots,
                    widgetMediaStates = widgetMediaStates,
                    widgetLocations = widgetLocations,
                    widgetClimateStates = widgetClimateStates,
                    onAddWidget = onShowWidgetCatalog,
                    onWidgetClick = onWidgetClick,
                    onWidgetLongPress = onWidgetLongPress,
                    onClimateTap = onClimateCardTap,
                    onPtzPress = onPtzPress,
                    onRemoveWidget = onRemoveWidget,
                    onReorderWidgets = onReorderWidgets,
                    mediaCallbacks = mediaCallbacks,
                    editMode = editTab == tab,
                    onEditModeChange = { on -> onEditTabChange(if (on) tab else null) },
                )
                else -> PlaceholderTabContent(tab = tab)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LightDetailPanel(
    light: HaLight,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onBrightnessChangeFinished: (Int) -> Unit,
    onColorChange: (Int, Int, Int) -> Unit,
    onColorChangeFinished: (Int, Int, Int) -> Unit,
    onColorTempChange: (Int) -> Unit,
    onColorTempChangeFinished: (Int) -> Unit,
    onOpenColorPicker: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Back button + light name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text(
                text = light.name,
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                color = cs.onSurface,
            )
        }

        // Full LightTile always-expanded for the detail view
        LightTile(
            light = light,
            expanded = true,
            onExpand = {},
            onToggle = onToggle,
            onBrightnessChange = onBrightnessChange,
            onBrightnessChangeFinished = onBrightnessChangeFinished,
            onColorChange = onColorChange,
            onColorChangeFinished = onColorChangeFinished,
            onColorTempChange = onColorTempChange,
            onColorTempChangeFinished = onColorTempChangeFinished,
            onOpenColorPicker = onOpenColorPicker,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Alerts panel ────────────────────────────────────────────────────────────
// Pushed sub-view (mirrors the light-detail view) listing every device that needs
// attention, one M3 card each, parsed into device name + reason. Flat surfaces,
// coral accent — no inline expanding banner.

@Composable
private fun AlertsPanel(room: HaRoom, onBack: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val count = room.alerts.size
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        // Back button + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = "Needs attention",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 24.sp,
                    lineHeight = 26.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = if (count == 1) "1 device in ${room.name}"
                        else "$count devices in ${room.name}",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // One card per device — "Name — reason" split into title + subtitle.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            room.alerts.forEach { alert ->
                val parts = alert.split(" — ", limit = 2)
                val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: alert
                val reason = (parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Needs attention")
                    .replaceFirstChar { it.uppercase() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(cs.surfaceContainerHigh)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(custom.coral.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = custom.coral,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = reason,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LightMasterCard(
    room: HaRoom,
    lights: List<HaLight>,
    accentColor: Color,
    inkColor: Color,
    onMasterToggle: (Boolean) -> Unit,
    onMasterBrightness: (Int) -> Unit,
    onMasterBrightnessFinished: (Int) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Excluding unavailable bulbs keeps offline devices from dragging the
    // average and prevents commands from being sent to entities that won't ack.
    val activeLights = lights.filter { it.isOn && it.isAvailable }
    val masterOn = activeLights.isNotEmpty()
    val masterBri = if (masterOn) activeLights.map { it.brightness }.average().toInt() else 0

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Bulb inset well
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(cs.onSurface.copy(alpha = if (masterOn) 0.06f else 0.03f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = haIconFor("bulb"),
                        contentDescription = null,
                        tint = if (masterOn) accentColor else cs.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = "MASTER · ${room.name.uppercase()}",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        RollingNumberText(
                            text = "$masterBri",
                            style = TextStyle(
                                fontFamily = InstrumentSerifFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 32.sp,
                                lineHeight = 30.sp,
                                color = cs.onSurface,
                            ),
                            labelPrefix = "master_bri",
                        )
                        Text(
                            text = "%",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        )
                        Text(
                            text = "  ${activeLights.size} of ${lights.size} on",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }

                PillToggle(
                    isOn = masterOn,
                    onToggle = { onMasterToggle(!masterOn) },
                    color = accentColor,
                    ink = inkColor,
                    size = PillToggleSize.Lg,
                )
            }

            BrightnessSlider(
                brightness = masterBri,
                enabled = masterOn,
                accent = accentColor,
                onBrightnessChange = onMasterBrightness,
                onBrightnessChangeFinished = onMasterBrightnessFinished,
            )
        }
    }
}

// ─── Temperature history graph (replaces static sine wave) ───────────────────
// Shows real 24h temp data when available; graceful sine-wave fallback otherwise.

@Composable
private fun TempHistoryGraph(
    history: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    fillOpacity: Float = 0.22f,
) {
    if (history.size < 3) {
        // Fall back to decorative sine wave when no real data yet
        SineWaveFill(color = color, modifier = modifier, fillOpacity = fillOpacity)
        return
    }
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height
        val minV = history.min()
        val maxV = history.max()
        val range = (maxV - minV).coerceAtLeast(0.5f)
        val points = history.mapIndexed { i, v ->
            val x = (i.toFloat() / (history.size - 1)) * W
            // Map temp range to occupy 30-80% of card height (top-anchored so higher = higher on card)
            val y = H * 0.75f - ((v - minV) / range) * (H * 0.55f)
            Offset(x, y)
        }
        val path = Path()
        path.moveTo(0f, H)
        path.lineTo(points.first().x, points.first().y)
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val cx = (p0.x + p1.x) / 2f
            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
        path.lineTo(W, H)
        path.close()
        drawPath(path = path, color = color.copy(alpha = fillOpacity))
    }
}

@Composable
private fun SineWaveFill(
    color: Color,
    modifier: Modifier = Modifier,
    fillOpacity: Float = 0.22f,
    frequency: Float = 1.8f,
) {
    Canvas(modifier = modifier) {
        val W = size.width
        val H = size.height
        val baseY = H * 0.55f
        val amp = H * 0.22f
        val path = Path()
        path.moveTo(0f, H)
        path.lineTo(0f, baseY)
        val steps = 200
        for (i in 0..steps) {
            val x = (i.toFloat() / steps) * W
            val y = baseY + sin((i.toFloat() / steps) * PI.toFloat() * frequency).toFloat() * amp
            path.lineTo(x, y)
        }
        path.lineTo(W, H)
        path.close()
        drawPath(path = path, color = color.copy(alpha = fillOpacity))
    }
}

// ─── Light tiles ──────────────────────────────────────────────────────────────

@Composable
private fun LightsTabContent(
    lights: List<HaLight>,
    onLightSelect: (HaLight) -> Unit,
    onToggle: (String) -> Unit,
    onBrightnessChange: (String, Int) -> Unit,
    onBrightnessChangeFinished: (String, Int) -> Unit,
    onColorChange: (String, Int, Int, Int) -> Unit,
    onColorChangeFinished: (String, Int, Int, Int) -> Unit,
    onColorTempChange: (String, Int) -> Unit,
    onColorTempChangeFinished: (String, Int) -> Unit,
) {
    val availableLights = lights.filter { it.isAvailable }
    val unavailableLights = lights.filter { !it.isAvailable }
    var showUnavailable by remember { mutableStateOf(false) }

    Column {
        if (availableLights.isNotEmpty()) {
            // A content-wrapping two-column grid (not a fixed-height LazyVerticalGrid)
            // so tiles are never clipped — their intrinsic height drives the layout,
            // and the parent AnimatedContent measures it exactly.
            LightTileGrid(
                lights = availableLights,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateContentSize(),
            ) { light ->
                LightTile(
                    light = light,
                    expanded = false,
                    onExpand = { onLightSelect(light) },
                    onToggle = { onToggle(light.id) },
                    onBrightnessChange = { bri -> onBrightnessChange(light.id, bri) },
                    onBrightnessChangeFinished = { bri -> onBrightnessChangeFinished(light.id, bri) },
                    onColorChange = { r, g, b -> onColorChange(light.id, r, g, b) },
                    onColorChangeFinished = { r, g, b -> onColorChangeFinished(light.id, r, g, b) },
                    onColorTempChange = { k -> onColorTempChange(light.id, k) },
                    onColorTempChangeFinished = { k -> onColorTempChangeFinished(light.id, k) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (unavailableLights.isNotEmpty()) {
            val cs = MaterialTheme.colorScheme
            // Toggle chip
            Tap(
                onClick = { showUnavailable = !showUnavailable },
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (showUnavailable) "Hide unavailable" else "${unavailableLights.size} unavailable",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (showUnavailable)
                            Icons.Outlined.KeyboardArrowUp
                        else
                            Icons.Outlined.KeyboardArrowDown,
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showUnavailable,
                enter = expandVertically(animationSpec = sizeSpring()) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = sizeSpring()) + fadeOut(tween(220)),
            ) {
                LightTileGrid(
                    lights = unavailableLights,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                ) { light ->
                    LightTile(
                        light = light,
                        expanded = false,
                        onExpand = {},
                        onToggle = {},
                        onBrightnessChange = {},
                        onBrightnessChangeFinished = {},
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// Two-column grid that wraps its content height. Used instead of a fixed-height
// LazyVerticalGrid (which clipped tiles taller than its per-row estimate). Rows
// are laid out top-to-bottom; an odd final tile keeps its half-width via a spacer.
@Composable
private fun LightTileGrid(
    lights: List<HaLight>,
    modifier: Modifier = Modifier,
    tile: @Composable RowScope.(HaLight) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        lights.chunked(2).forEach { rowLights ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowLights.forEach { light -> tile(light) }
                if (rowLights.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ClimateTabContent(
    climate: com.uc.homehealth.data.HaClimate?,
    climateWidgets: List<RoomWidget.Climate> = emptyList(),
    climateStates: Map<String, com.uc.homehealth.data.HaClimate?> = emptyMap(),
    onTargetChange: (entityId: String, temperature: Float) -> Unit,
    onModeChange: (entityId: String, mode: String) -> Unit,
    onCardTap: (com.uc.homehealth.data.HaClimate) -> Unit = {},
    onAddClimate: () -> Unit = {},
    onRemoveWidget: (RoomWidget) -> Unit = {},
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
) {
    LaunchedEffect(climateWidgets.isEmpty()) {
        if (climateWidgets.isEmpty() && editMode) onEditModeChange(false)
    }

    // Empty = no area-detected climate AND no user-added card. Crossfade between the
    // controls and the empty "add" card so the empty state eases in instead of popping.
    val showEmpty = climate == null && climateWidgets.isEmpty()
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AnimatedContent(
            targetState = showEmpty,
            transitionSpec = {
                fadeIn(tween(260)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false)
            },
            label = "climate_section",
        ) { isEmpty ->
            if (isEmpty) {
                AddSectionCard(
                    icon = Icons.Outlined.Thermostat,
                    title = "Add a climate device",
                    subtitle = "Pick a climate entity to control it from here",
                    onClick = onAddClimate,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Edit pill — only when there are removable (user-added) climate cards.
                    if (climateWidgets.isNotEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            EditPill(editMode = editMode, onToggle = { onEditModeChange(!editMode) })
                        }
                    }
                    // Area-detected climate (not user-added → never removable).
                    climate?.let { ClimateControl(climate = it, onTargetChange = onTargetChange, onCardTap = onCardTap) }
                    // User-added climate cards — removable in edit mode.
                    RemovableWidgetColumn(
                        widgets = climateWidgets,
                        editMode = editMode,
                        onRemove = onRemoveWidget,
                    ) { widget ->
                        val c = climateStates[(widget as RoomWidget.Climate).entityId]
                        if (c != null) {
                            ClimateControl(climate = c, onTargetChange = onTargetChange, onCardTap = onCardTap)
                        } else {
                            UnavailableWidgetCard(label = widget.entityId)
                        }
                    }
                }
            }
        }
    }
}

// One climate card + its target +/- stepper. Shared by the Climate tab (area-detected
// and user-added cards) so they look and behave identically.
@Composable
private fun ClimateControl(
    climate: com.uc.homehealth.data.HaClimate,
    onTargetChange: (entityId: String, temperature: Float) -> Unit,
    onCardTap: (com.uc.homehealth.data.HaClimate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Optimistic local target — snaps back when HA confirms the new state.
    var localTarget by remember(climate.id, climate.targetTemp) {
        mutableStateOf(climate.targetTemp ?: 22f)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        com.uc.homehealth.ui.components.ClimateCard(
            climate = climate.copy(targetTemp = localTarget),
            onTap = { onCardTap(climate.copy(targetTemp = localTarget)) },
        )

        // Target +/- stepper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainerHigh)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ADJUST TARGET",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = "Step ${"%.1f".format(climate.tempStep)}°",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            StepperButton(label = "−", enabled = climate.isAvailable) {
                val next = (localTarget - climate.tempStep).coerceAtLeast(5f)
                localTarget = next
                onTargetChange(climate.id, next)
            }
            Spacer(Modifier.width(8.dp))
            StepperButton(label = "+", enabled = climate.isAvailable) {
                val next = (localTarget + climate.tempStep).coerceAtMost(35f)
                localTarget = next
                onTargetChange(climate.id, next)
            }
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(
        onClick = { if (enabled) onClick() },
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (enabled) cs.surfaceVariant else cs.surfaceVariant.copy(alpha = 0.4f)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                color = cs.onSurface,
            )
        }
    }
}

@Composable
private fun PlaceholderTabContent(tab: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(120.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$tab — coming in Phase 6",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Media tab ────────────────────────────────────────────────────────────────
// Renders the MEDIA-section media widgets for this room as Hero MediaCards. Media
// added here lives ONLY in this tab (not in More); the "Add a media player" button
// opens the media picker scoped to the Media section. Empty state offers the Add card.

@Composable
private fun MediaTabContent(
    widgets: List<RoomWidget>,
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?>,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
    onAddMedia: () -> Unit,
    onRemoveWidget: (RoomWidget) -> Unit,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
) {
    val mediaWidgets = widgets.filterIsInstance<RoomWidget.Media>()
        .filter { it.section == WidgetSection.MEDIA }
    LaunchedEffect(mediaWidgets.isEmpty()) {
        if (mediaWidgets.isEmpty() && editMode) onEditModeChange(false)
    }

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        // Crossfade + grow between the player list and the empty "add" card, so the empty
        // state eases in after the last player is removed instead of popping.
        AnimatedContent(
            targetState = mediaWidgets.isEmpty(),
            transitionSpec = {
                fadeIn(tween(260)) togetherWith fadeOut(tween(160)) using SizeTransform(clip = false)
            },
            label = "media_section",
        ) { isEmpty ->
            if (isEmpty) {
                AddSectionCard(
                    icon = Icons.Outlined.MusicNote,
                    title = "Add a media player",
                    subtitle = "Pick a media_player entity to control it from here",
                    onClick = onAddMedia,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Edit pill — removal lives here since MEDIA-section media is hidden from More.
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        EditPill(editMode = editMode, onToggle = { onEditModeChange(!editMode) })
                    }
                    RemovableWidgetColumn(
                        widgets = mediaWidgets,
                        editMode = editMode,
                        onRemove = onRemoveWidget,
                    ) { widget ->
                        val entityId = (widget as RoomWidget.Media).entityId
                        val media = widgetMediaStates[entityId]
                        if (media != null) {
                            com.uc.homehealth.ui.components.MediaCardHero(
                                media = media,
                                onPlayPause = { mediaCallbacks.onPlayPause(entityId) },
                                onSkipPrev = { mediaCallbacks.onSkipPrev(entityId) },
                                onSkipNext = { mediaCallbacks.onSkipNext(entityId) },
                                onToggleShuffle = { mediaCallbacks.onToggleShuffle(entityId, media.shuffleOn) },
                                onCycleRepeat = { mediaCallbacks.onCycleRepeat(entityId, media.repeatMode) },
                                onSeek = { progress -> mediaCallbacks.onSeek(entityId, progress) },
                                onVolumeChange = { volume -> mediaCallbacks.onVolumeChange(entityId, volume) },
                                onAnnounce = { mediaCallbacks.onAnnounce(entityId) },
                                onSearchMusic = { mediaCallbacks.onSearchMusic(entityId) },
                            )
                        } else {
                            UnavailableWidgetCard(label = entityId)
                        }
                    }
                }
            }
        }
    }
}

// Empty-state "add" card shared by the Media and Climate tabs.
@Composable
private fun AddSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.onSurface,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 14.dp)) {
                Text(
                    text = title,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = subtitle,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// Placeholder shown for a section widget whose live state hasn't resolved (or whose
// entity is gone) — keeps the card present so it can still be removed in edit mode.
@Composable
private fun UnavailableWidgetCard(label: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = label,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Unavailable",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// Edit-mode removable list (no drag-reorder), used by the Media and Climate tabs where
// reordering across sections isn't supported. Tapping the ✕ plays the exit transition
// then commits the removal — same visual language as ReorderableWidgetColumn.
@Composable
private fun RemovableWidgetColumn(
    widgets: List<RoomWidget>,
    editMode: Boolean,
    onRemove: (RoomWidget) -> Unit,
    content: @Composable (RoomWidget) -> Unit,
) {
    val haptic = rememberAppHaptics()
    val exitingIds = remember { mutableStateMapOf<String, Boolean>() }
    // Render from a local [order] rather than straight from `widgets`. The exit handler
    // drops the tile from `order` the instant its shrink finishes — instead of waiting for
    // the DataStore removal to round-trip. Otherwise clearing the exit flag flips
    // AnimatedVisibility back to visible (the tile is still in upstream `widgets`) and
    // replays the enter animation for a frame: the "removed, reappears, then vanishes"
    // flicker. [pendingRemoval] keeps a just-removed tile dropped until upstream catches up.
    val pendingRemoval = remember { mutableStateListOf<String>() }
    var order by remember { mutableStateOf(widgets) }

    LaunchedEffect(widgets) {
        val upstream = widgets.associateBy { it.id }
        pendingRemoval.retainAll { it in upstream }
        val merged = buildList {
            order.forEach { existing ->
                when {
                    existing.id in pendingRemoval -> Unit                  // awaiting upstream: stay dropped
                    existing.id in upstream -> add(upstream.getValue(existing.id))
                    exitingIds[existing.id] == true -> add(existing)       // mid-exit: keep until done
                }
            }
            val have = map { it.id }.toSet()
            widgets.forEach { if (it.id !in have && it.id !in pendingRemoval) add(it) }
        }
        if (merged != order) order = merged
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEach { widget ->
            // Keyed by id so AnimatedVisibility/exit state follows the item, not its slot —
            // otherwise removing a tile makes its neighbour replay the enter animation.
            key(widget.id) {
            val isExiting = exitingIds[widget.id] == true

            LaunchedEffect(isExiting) {
                if (isExiting) {
                    kotlinx.coroutines.delay(WIDGET_EXIT_MS.toLong())
                    pendingRemoval.add(widget.id)
                    exitingIds.remove(widget.id)
                    order = order.filterNot { it.id == widget.id }
                    onRemove(widget)
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(WIDGET_ENTER_MS)) + expandVertically(sizeSpring()),
                exit = shrinkVertically(sizeSpring(Spring.StiffnessMedium)) +
                    fadeOut(tween(WIDGET_EXIT_MS - 60)),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    content(widget)

                    if (editMode) {
                        Tap(
                            onClick = {
                                if (exitingIds[widget.id] != true) {
                                    haptic.confirm()
                                    exitingIds[widget.id] = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove widget",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ─── More tab ────────────────────────────────────────────────────────────────
// Renders the user's per-room custom widgets in a 3-column flow row and shows a
// persistent "Add Your Widget" button below.

@Composable
private fun MoreTabContent(
    widgets: List<RoomWidget>,
    widgetStates: Map<String, HaEntityValue?>,
    widgetHistories: Map<String, List<Float>>,
    widgetCameraSnapshots: Map<String, String?>,
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?>,
    widgetLocations: Map<String, com.uc.homehealth.data.HaPersonLocation?>,
    widgetClimateStates: Map<String, com.uc.homehealth.data.HaClimate?> = emptyMap(),
    onAddWidget: () -> Unit,
    onWidgetClick: (RoomWidget) -> Unit,
    onWidgetLongPress: (RoomWidget) -> Unit,
    onClimateTap: (com.uc.homehealth.data.HaClimate) -> Unit = {},
    onPtzPress: (String) -> Unit = {},
    onRemoveWidget: (RoomWidget) -> Unit,
    onReorderWidgets: (List<RoomWidget>) -> Unit,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
    editMode: Boolean = false,
    onEditModeChange: (Boolean) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    // Drop out of edit mode automatically once the user removes the last widget.
    LaunchedEffect(widgets.isEmpty()) {
        if (widgets.isEmpty() && editMode) onEditModeChange(false)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Toggling edit mode hides/shows the "Add Your Widget" card, changing this
            // column's height. The tab AnimatedContent's SizeTransform only fires on a
            // tab switch, not on this in-tab change, so without this the sheet snaps to
            // the new height. animateContentSize eases the shrink/grow instead.
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Edit / Done pill (only when there's something to edit) ────────────
        if (widgets.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                EditPill(
                    editMode = editMode,
                    onToggle = {
                        if (editMode) haptic.tick() else haptic.confirm()
                        onEditModeChange(!editMode)
                    },
                )
            }
        }

        // ── Widget list — reorderable + removable when in edit mode ───────────
        ReorderableWidgetColumn(
            widgets = widgets,
            editMode = editMode,
            onReorderCommit = onReorderWidgets,
            onRemove = onRemoveWidget,
        ) { widget ->
            when (widget) {
                is RoomWidget.Switch -> {
                    val live = widgetStates[widget.entityId]
                    val name = live?.friendlyName ?: widget.entityId.substringAfterLast('.')
                        .replace('_', ' ').replaceFirstChar { it.uppercase() }
                    SwitchWidgetTile(
                        name = name,
                        subtitle = widget.entityId,
                        isOn = live?.state == "on",
                        onClick = { onWidgetClick(widget) },
                        onLongPress = { onWidgetLongPress(widget) },
                        enabled = !editMode,
                    )
                }
                is RoomWidget.Sensor -> {
                    val live = widgetStates[widget.entityId]
                    val name = live?.friendlyName ?: widget.entityId.substringAfterLast('.')
                        .replace('_', ' ').replaceFirstChar { it.uppercase() }
                    SensorWidgetTile(
                        name = name,
                        subtitle = widget.entityId,
                        state = live,
                        history = widgetHistories[widget.entityId].orEmpty(),
                        onClick = { onWidgetClick(widget) },
                        onLongPress = { onWidgetLongPress(widget) },
                        enabled = !editMode,
                    )
                }
                is RoomWidget.Camera -> {
                    val live = widgetStates[widget.entityId]
                    val name = live?.friendlyName ?: widget.entityId.substringAfterLast('.')
                        .replace('_', ' ').replaceFirstChar { it.uppercase() }
                    CameraWidgetTile(
                        name = name,
                        subtitle = widget.entityId,
                        snapshotUrl = widgetCameraSnapshots[widget.entityId],
                        onClick = { onWidgetClick(widget) },
                        onLongPress = { onWidgetLongPress(widget) },
                        enabled = !editMode,
                        ptz = widget.ptz,
                        onPtzPress = onPtzPress,
                    )
                }
                is RoomWidget.Location -> {
                    val loc = widgetLocations[widget.entityId]
                    val name = loc?.friendlyName ?: widget.entityId.substringAfterLast('.')
                        .replace('_', ' ').replaceFirstChar { it.uppercase() }
                    com.uc.homehealth.ui.components.LocationWidgetTile(
                        name = name,
                        subtitle = widget.entityId,
                        location = loc,
                        onClick = { onWidgetClick(widget) },
                        onLongPress = { onWidgetLongPress(widget) },
                        enabled = !editMode,
                    )
                }
                is RoomWidget.Media -> {
                    val media = widgetMediaStates[widget.entityId]
                    if (media != null) {
                        com.uc.homehealth.ui.components.MediaCardHero(
                            media = media,
                            onPlayPause = { mediaCallbacks.onPlayPause(widget.entityId) },
                            onSkipPrev = { mediaCallbacks.onSkipPrev(widget.entityId) },
                            onSkipNext = { mediaCallbacks.onSkipNext(widget.entityId) },
                            onToggleShuffle = {
                                mediaCallbacks.onToggleShuffle(widget.entityId, media.shuffleOn)
                            },
                            onCycleRepeat = {
                                mediaCallbacks.onCycleRepeat(widget.entityId, media.repeatMode)
                            },
                            onSeek = { progress -> mediaCallbacks.onSeek(widget.entityId, progress) },
                            onVolumeChange = { volume -> mediaCallbacks.onVolumeChange(widget.entityId, volume) },
                            onAnnounce = { mediaCallbacks.onAnnounce(widget.entityId) },
                            onSearchMusic = { mediaCallbacks.onSearchMusic(widget.entityId) },
                        )
                    }
                }
                is RoomWidget.Climate -> {
                    val climate = widgetClimateStates[widget.entityId]
                    if (climate != null) {
                        com.uc.homehealth.ui.components.ClimateCard(
                            climate = climate,
                            onTap = { onClimateTap(climate) },
                        )
                    } else {
                        UnavailableWidgetCard(label = widget.entityId)
                    }
                }
                is RoomWidget.AirQuality -> {
                    com.uc.homehealth.ui.components.AirQualityWidget(
                        baseState = widgetStates[widget.entityId],
                        cleanState = widgetStates[widget.cleanDurationId],
                        moderateState = widgetStates[widget.moderateDurationId],
                        poorState = widgetStates[widget.poorDurationId],
                    )
                }
            }
        }

        // ── Add Your Widget card — hidden in edit mode to avoid mode mixup ───
        if (!editMode) {
            Tap(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = "Add Your Widget",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = if (widgets.isEmpty()) "Switches, sensor graphs, more coming soon" else "Add another widget to this room",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    // Material 3 Expressive filled-tonal icon button. The signature expressive move is
    // the shape morph on press — a rounded square at rest springs to a full circle while
    // held — paired with Tap's spring press-scale. We share Tap's interaction source so
    // the corner radius can track the pressed state.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 18.dp else 12.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "section_btn_corner",
    )
    Tap(
        onClick = { haptic.navigation(); onClick() },
        interactionSource = interaction,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(cs.secondaryContainer, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = cs.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EditPill(editMode: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    // Filled-tonal at rest, filled primary while editing — an expressive container-color
    // shift that mirrors the section icon buttons. The toggle fires a clear on/off haptic.
    Tap(onClick = { haptic.toggle(!editMode); onToggle() }) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(if (editMode) cs.primary else cs.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (editMode) Icons.Outlined.Check else Icons.Outlined.Edit,
                contentDescription = null,
                tint = if (editMode) cs.onPrimary else cs.onSecondaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (editMode) "Done" else "Edit",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = if (editMode) cs.onPrimary else cs.onSecondaryContainer,
            )
        }
    }
}

// ─── Reorderable column ───────────────────────────────────────────────────────
// Long-press-then-drag in edit mode picks up a tile and slides it through the
// list, swapping when half of an adjacent item is crossed. Non-dragged tiles
// jiggle subtly; the dragged tile renders above siblings via zIndex. Heights
// are captured per item so sensor (tall) and switch (short) tiles compose
// happily in the same list. Order is owned locally during the drag and committed
// to the repo on release so DataStore writes don't fight the active gesture.

private const val WIDGET_EXIT_MS = 260
private const val WIDGET_ENTER_MS = 220

// M3 Expressive motion physics: spatial enter/exit rides springs; fades stay on tween.
// Exits that are duration-coupled to a deferred removal delay (WIDGET_EXIT_MS /
// HORIZ_EXIT_MS) use StiffnessMedium so the spring settles inside that window.
private fun sizeSpring(stiffness: Float = Spring.StiffnessMediumLow) =
    spring(stiffness = stiffness, visibilityThreshold = IntSize.VisibilityThreshold)

private fun offsetSpring() =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)

@Composable
private fun ReorderableWidgetColumn(
    widgets: List<RoomWidget>,
    editMode: Boolean,
    onReorderCommit: (List<RoomWidget>) -> Unit,
    onRemove: (RoomWidget) -> Unit,
    content: @Composable (RoomWidget) -> Unit,
) {
    val haptic = rememberAppHaptics()
    // One shared transition; per-tile phase offset (initialStartOffset, seeded by
    // widget.id) breaks the synchronized-jerk feel. FastOutSlowInEasing eases at
    // each turnaround so there's no constant-velocity-then-snap motion.
    val jiggleTransition = rememberInfiniteTransition(label = "edit_jiggle")

    val heights = remember { mutableStateMapOf<String, Int>() }
    var order by remember(widgets) { mutableStateOf(widgets) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    // IDs that the user has tapped X for; tile stays in `order` so AnimatedVisibility
    // can play the exit transition, then we commit the actual repo removal.
    val exitingIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(widgets) {
        // Re-sync local order with upstream (add/remove/external update), but only
        // when no drag is in flight and no exit animation is mid-flight — either
        // would yank a tile out from under the user.
        if (draggedId == null && exitingIds.isEmpty()) order = widgets
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEach { widget ->
            // Keyed by id so per-tile animation/drag state follows the item, not its slot —
            // otherwise removing a tile makes its neighbour replay the enter animation.
            key(widget.id) {
            val isDragged = draggedId == widget.id
            val isExiting = exitingIds[widget.id] == true

            val translationY by animateFloatAsState(
                targetValue = if (isDragged) dragOffsetPx else 0f,
                label = "drag_translation",
            )

            // Per-widget phase offset so the row doesn't jiggle in lock-step.
            val phaseOffsetMs = remember(widget.id) {
                ((widget.id.hashCode() and Int.MAX_VALUE) % 440)
            }
            val jiggleRaw by jiggleTransition.animateFloat(
                initialValue = -0.55f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(phaseOffsetMs),
                ),
                label = "jiggle_${widget.id}",
            )
            // Smoothly ramp the rotation in/out of edit mode (and to 0 while dragging)
            // so toggling Edit doesn't snap from straight to jiggling.
            val rotation by animateFloatAsState(
                targetValue = if (editMode && !isDragged && !isExiting) jiggleRaw else 0f,
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                label = "jiggle_blend",
            )

            // AnimatedVisibility provides the enter/exit transitions. The actual
            // repo removal is deferred via a LaunchedEffect so the exit animation
            // gets to play before the tile is dropped from the upstream list.
            LaunchedEffect(isExiting) {
                if (isExiting) {
                    kotlinx.coroutines.delay(WIDGET_EXIT_MS.toLong())
                    onRemove(widget)
                    exitingIds.remove(widget.id)
                    // Optimistically drop from local order so it doesn't briefly
                    // reappear before the upstream list catches up.
                    order = order.filterNot { it.id == widget.id }
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(WIDGET_ENTER_MS)) + expandVertically(sizeSpring()),
                exit = shrinkVertically(sizeSpring(Spring.StiffnessMedium)) +
                    fadeOut(tween(WIDGET_EXIT_MS - 60)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords -> heights[widget.id] = coords.size.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer {
                            this.translationY = translationY
                            this.rotationZ = rotation
                        }
                        .pointerInput(editMode) {
                            if (!editMode) return@pointerInput
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggedId = widget.id
                                    dragOffsetPx = 0f
                                    haptic.confirm()
                                },
                                onDragEnd = {
                                    draggedId = null
                                    dragOffsetPx = 0f
                                    onReorderCommit(order)
                                },
                                onDragCancel = {
                                    draggedId = null
                                    dragOffsetPx = 0f
                                },
                                onDrag = { _, drag ->
                                    dragOffsetPx += drag.y
                                    val currentIndex = order.indexOfFirst { it.id == widget.id }
                                    if (currentIndex < 0) return@detectDragGesturesAfterLongPress

                                    // Move up
                                    if (dragOffsetPx < 0 && currentIndex > 0) {
                                        val prev = order[currentIndex - 1]
                                        val prevH = heights[prev.id] ?: return@detectDragGesturesAfterLongPress
                                        val spacingPx = 10.dp.toPx()
                                        if (-dragOffsetPx > (prevH + spacingPx) / 2f) {
                                            order = order.toMutableList().also {
                                                it[currentIndex] = prev
                                                it[currentIndex - 1] = widget
                                            }
                                            dragOffsetPx += prevH + spacingPx
                                            haptic.tick()
                                        }
                                    }
                                    // Move down
                                    if (dragOffsetPx > 0 && currentIndex < order.lastIndex) {
                                        val next = order[currentIndex + 1]
                                        val nextH = heights[next.id] ?: return@detectDragGesturesAfterLongPress
                                        val spacingPx = 10.dp.toPx()
                                        if (dragOffsetPx > (nextH + spacingPx) / 2f) {
                                            order = order.toMutableList().also {
                                                it[currentIndex] = next
                                                it[currentIndex + 1] = widget
                                            }
                                            dragOffsetPx -= nextH + spacingPx
                                            haptic.tick()
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    content(widget)

                    // Remove badge — top-right, only in edit mode.
                    if (editMode) {
                        Tap(
                            onClick = {
                                if (exitingIds[widget.id] != true) {
                                    haptic.confirm()
                                    exitingIds[widget.id] = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove widget",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ─── Widget catalog (3rd state of the room sheet) ────────────────────────────
// Slides in from the right when the user taps "Add Your Widget" in the More tab.
// Shows the limited set of widget types we support. Tapping a type triggers the
// entity picker (a stacked BottomSheet rendered at Navigation level).

@Composable
private fun WidgetCatalogPanel(
    accentColor: Color,
    inkColor: Color,
    onBack: () -> Unit,
    onPickSwitchType: () -> Unit,
    onPickSensorType: () -> Unit,
    onPickCameraType: () -> Unit,
    onPickMediaType: () -> Unit,
    onPickClimateType: () -> Unit,
    onPickLocationType: () -> Unit,
    onPickAirQualityType: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add a widget",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 26.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = "Pick a widget type",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WidgetCatalogRow(
                title = "Switch",
                description = "Toggle a switch entity. Tap to flip, hold to expand.",
                icon = Icons.Outlined.PowerSettingsNew,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickSwitchType,
            )
            WidgetCatalogRow(
                title = "Sensor graph",
                description = "Plot 24h of a numeric sensor — same look as the room temperature graph.",
                icon = Icons.AutoMirrored.Outlined.ShowChart,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickSensorType,
            )
            WidgetCatalogRow(
                title = "Camera",
                description = "Snapshot tile that taps to a live HLS stream (via go2rtc).",
                icon = Icons.Outlined.Videocam,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickCameraType,
            )
            WidgetCatalogRow(
                title = "Media player",
                description = "Now-playing surface with transport, squiggly progress, and volume.",
                icon = Icons.Outlined.MusicNote,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickMediaType,
            )
            WidgetCatalogRow(
                title = "Climate",
                description = "Thermostat / AC card with mode, fan and target-temperature controls.",
                icon = Icons.Outlined.Thermostat,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickClimateType,
            )
            WidgetCatalogRow(
                title = "Location",
                description = "Map a person's live location. Tap for a full interactive map.",
                icon = Icons.Outlined.LocationOn,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickLocationType,
            )
            WidgetCatalogRow(
                title = "Air quality",
                description = "PM2.5 with VINDRIKTNING colours and a clean / moderate / poor time breakdown.",
                icon = Icons.Outlined.Air,
                accentColor = accentColor,
                inkColor = inkColor,
                onClick = onPickAirQualityType,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WidgetCatalogRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    inkColor: Color,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accentColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = inkColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp)) {
                Text(
                    text = title,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = description,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ─── Reorderable room column ──────────────────────────────────────────────────
// Compact vertical list shown when the user enters rooms edit mode. Each row
// shows the room's accent circle + name; long-press to drag, × to hide.

@Composable
private fun ReorderableRoomColumn(
    rooms: List<HaRoom>,
    modifier: Modifier = Modifier,
    onReorderCommit: (List<HaRoom>) -> Unit,
    onRemove: (HaRoom) -> Unit,
    onEditSensors: (HaRoom) -> Unit,
) {
    val haptic = rememberAppHaptics()
    val jiggleTransition = rememberInfiniteTransition(label = "room_jiggle")
    val cs = MaterialTheme.colorScheme

    val heights = remember { mutableStateMapOf<String, Int>() }
    var order by remember(rooms) { mutableStateOf(rooms) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val exitingIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(rooms) {
        if (draggedId == null && exitingIds.isEmpty()) order = rooms
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        order.forEach { room ->
            // Keyed by id so per-row animation/drag state follows the room, not its slot.
            key(room.id) {
            val isDragged = draggedId == room.id
            val isExiting = exitingIds[room.id] == true

            val translationY by animateFloatAsState(
                targetValue = if (isDragged) dragOffsetPx else 0f,
                label = "room_drag_y",
            )
            val phaseOffsetMs = remember(room.id) { ((room.id.hashCode() and Int.MAX_VALUE) % 440) }
            val jiggleRaw by jiggleTransition.animateFloat(
                initialValue = -0.45f, targetValue = 0.45f,
                animationSpec = infiniteRepeatable(
                    animation = tween(220, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(phaseOffsetMs),
                ),
                label = "room_jiggle_${room.id}",
            )
            val rotation by animateFloatAsState(
                targetValue = if (!isDragged && !isExiting) jiggleRaw else 0f,
                animationSpec = tween(140, easing = FastOutSlowInEasing),
                label = "room_jiggle_blend",
            )

            LaunchedEffect(isExiting) {
                if (isExiting) {
                    kotlinx.coroutines.delay(WIDGET_EXIT_MS.toLong())
                    onRemove(room)
                    exitingIds.remove(room.id)
                    order = order.filterNot { it.id == room.id }
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(WIDGET_ENTER_MS)) + expandVertically(sizeSpring()),
                exit = shrinkVertically(sizeSpring(Spring.StiffnessMedium)) + fadeOut(tween(WIDGET_EXIT_MS - 60)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { heights[room.id] = it.size.height }
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { this.translationY = translationY; this.rotationZ = rotation }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggedId = room.id; dragOffsetPx = 0f; haptic.confirm() },
                                onDragEnd = { draggedId = null; dragOffsetPx = 0f; onReorderCommit(order) },
                                onDragCancel = { draggedId = null; dragOffsetPx = 0f },
                                onDrag = { _, drag ->
                                    dragOffsetPx += drag.y
                                    val idx = order.indexOfFirst { it.id == room.id }
                                    if (idx < 0) return@detectDragGesturesAfterLongPress
                                    val spacingPx = 8.dp.toPx()
                                    if (dragOffsetPx < 0 && idx > 0) {
                                        val prev = order[idx - 1]
                                        val prevH = heights[prev.id] ?: return@detectDragGesturesAfterLongPress
                                        if (-dragOffsetPx > (prevH + spacingPx) / 2f) {
                                            order = order.toMutableList().also { it[idx] = prev; it[idx - 1] = room }
                                            dragOffsetPx += prevH + spacingPx; haptic.tick()
                                        }
                                    }
                                    if (dragOffsetPx > 0 && idx < order.lastIndex) {
                                        val next = order[idx + 1]
                                        val nextH = heights[next.id] ?: return@detectDragGesturesAfterLongPress
                                        if (dragOffsetPx > (nextH + spacingPx) / 2f) {
                                            order = order.toMutableList().also { it[idx] = next; it[idx + 1] = room }
                                            dragOffsetPx -= nextH + spacingPx; haptic.tick()
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    val accent = runCatching { Color(android.graphics.Color.parseColor(room.colorHex)) }
                        .getOrDefault(cs.primaryContainer)
                    val ink = runCatching { Color(android.graphics.Color.parseColor(room.inkHex)) }
                        .getOrDefault(cs.onPrimaryContainer)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.small)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp).background(accent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = haIconFor(room.icon), contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(room.name, fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = cs.onSurface)
                            Text(
                                "${room.deviceCount} device${if (room.deviceCount == 1) "" else "s"}",
                                fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = cs.onSurfaceVariant,
                            )
                        }
                    }
                    // Action badges — edit (sensors) + remove, top-right corner
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tap(onClick = { haptic.confirm(); onEditSensors(room) }) {
                            Box(
                                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit room sensors", tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                        }
                        Tap(
                            onClick = { if (exitingIds[room.id] != true) { haptic.confirm(); exitingIds[room.id] = true } },
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = "Hide room", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

// ─── Reorderable horizontal row ───────────────────────────────────────────────
// Long-press-then-drag in edit mode picks up a tile and slides it horizontally,
// swapping with an adjacent item when more than half its width is crossed.
// Mirrors ReorderableWidgetColumn but operates on the X axis.

private const val HORIZ_EXIT_MS = 260
private const val HORIZ_ENTER_MS = 220

@Composable
internal fun <T : Any> ReorderableHorizontalRow(
    items: List<T>,
    key: (T) -> String,
    spacing: Dp,
    contentPadding: PaddingValues,
    onReorderCommit: (List<T>) -> Unit,
    onRemove: (T) -> Unit,
    // Items reporting false get no remove badge (still draggable) — e.g. the mandatory
    // Settings tab in the nav-bar editor.
    removable: (T) -> Boolean = { true },
    content: @Composable (T) -> Unit,
) {
    val haptic = rememberAppHaptics()
    val jiggleTransition = rememberInfiniteTransition(label = "horiz_jiggle")
    val horizScroll = rememberScrollState()

    val widths = remember { mutableStateMapOf<String, Int>() }
    var order by remember { mutableStateOf(items) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val exitingIds = remember { mutableStateMapOf<String, Boolean>() }
    // Ids whose exit animation has finished and that we've dropped locally, but
    // which upstream hasn't removed yet. Keeps a just-removed tile from flashing
    // back when an unrelated emission lands during the prefs round-trip.
    val pendingRemoval = remember { mutableStateListOf<String>() }

    // Reconcile upstream `items` into the rendered `order` without disturbing an
    // active drag or an in-flight exit animation: tiles mid-exit are kept until
    // their animation finishes, newly added tiles are appended, and tiles dropped
    // upstream disappear — but one we're still animating/awaiting stays put. Never
    // keyed on `items`, so an unrelated emission can't reset order mid-animation.
    LaunchedEffect(items) {
        if (draggedId != null) return@LaunchedEffect
        val upstream = items.associateBy(key)
        pendingRemoval.retainAll { it in upstream }
        val merged = buildList {
            order.forEach { existing ->
                val id = key(existing)
                when {
                    id in pendingRemoval -> Unit                  // awaiting upstream: drop
                    id in upstream -> add(upstream.getValue(id))  // present: latest content
                    exitingIds[id] == true -> add(existing)       // animating out: keep
                }
            }
            val have = map(key).toSet()
            items.forEach { if (key(it) !in have && key(it) !in pendingRemoval) add(it) }
        }
        if (merged != order) order = merged
    }

    Row(
        modifier = Modifier
            .horizontalScroll(horizScroll)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        order.forEach { item ->
            val id = key(item)
            // Key each tile's subtree by identity, not loop position. When a tile is
            // removed the list shrinks and the tiles after it slide down one slot;
            // without this key Compose would hand the removed tile's (now invisible)
            // AnimatedVisibility slot to the shifted tile, replaying its enter
            // animation — the tile appears to vanish, flash back in, then vanish again.
            androidx.compose.runtime.key(id) {
                val isDragged = draggedId == id
                val isExiting = exitingIds[id] == true

                val translationX by animateFloatAsState(
                    targetValue = if (isDragged) dragOffsetPx else 0f,
                    label = "drag_x_$id",
                )

                val phaseOffsetMs = remember(id) { ((id.hashCode() and Int.MAX_VALUE) % 440) }
                val jiggleRaw by jiggleTransition.animateFloat(
                    initialValue = -0.55f,
                    targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(phaseOffsetMs),
                    ),
                    label = "horiz_jiggle_$id",
                )
                val rotation by animateFloatAsState(
                    targetValue = if (!isDragged && !isExiting) jiggleRaw else 0f,
                    animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
                    label = "horiz_jiggle_blend_$id",
                )

                LaunchedEffect(isExiting) {
                    if (isExiting) {
                        kotlinx.coroutines.delay(HORIZ_EXIT_MS.toLong())
                        // Mark pending and collapse the slot locally (the shrink has
                        // already closed the gap) before telling upstream, so the
                        // tile can't reappear while the prefs write round-trips.
                        pendingRemoval.add(id)
                        exitingIds.remove(id)
                        order = order.filterNot { key(it) == id }
                        onRemove(item)
                    }
                }

                AnimatedVisibility(
                    visible = !isExiting,
                    enter = fadeIn(tween(HORIZ_ENTER_MS)),
                    exit = shrinkHorizontally(sizeSpring(Spring.StiffnessMedium)) +
                        fadeOut(tween(HORIZ_EXIT_MS - 60)),
                ) {
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { widths[id] = it.size.width }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                this.translationX = translationX
                                this.rotationZ = rotation
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedId = id
                                        dragOffsetPx = 0f
                                        haptic.confirm()
                                    },
                                    onDragEnd = {
                                        draggedId = null
                                        dragOffsetPx = 0f
                                        onReorderCommit(order)
                                    },
                                    onDragCancel = {
                                        draggedId = null
                                        dragOffsetPx = 0f
                                    },
                                    onDrag = { _, drag ->
                                        dragOffsetPx += drag.x
                                        val currentIndex = order.indexOfFirst { key(it) == id }
                                        if (currentIndex < 0) return@detectDragGesturesAfterLongPress
                                        val spacingPx = spacing.toPx()

                                        if (dragOffsetPx < 0 && currentIndex > 0) {
                                            val prev = order[currentIndex - 1]
                                            val prevW = widths[key(prev)] ?: return@detectDragGesturesAfterLongPress
                                            if (-dragOffsetPx > (prevW + spacingPx) / 2f) {
                                                order = order.toMutableList().also {
                                                    it[currentIndex] = prev
                                                    it[currentIndex - 1] = item
                                                }
                                                dragOffsetPx += prevW + spacingPx
                                                haptic.tick()
                                            }
                                        }
                                        if (dragOffsetPx > 0 && currentIndex < order.lastIndex) {
                                            val next = order[currentIndex + 1]
                                            val nextW = widths[key(next)] ?: return@detectDragGesturesAfterLongPress
                                            if (dragOffsetPx > (nextW + spacingPx) / 2f) {
                                                order = order.toMutableList().also {
                                                    it[currentIndex] = next
                                                    it[currentIndex + 1] = item
                                                }
                                                dragOffsetPx -= nextW + spacingPx
                                                haptic.tick()
                                            }
                                        }
                                    },
                                )
                            },
                    ) {
                        content(item)

                        if (removable(item)) Tap(
                            onClick = {
                                if (exitingIds[id] != true) {
                                    haptic.confirm()
                                    exitingIds[id] = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Remove",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Shared ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = InstrumentSerifFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        letterSpacing = (-0.4).sp,
        lineHeight = 30.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

// ─── Self-curating "At a glance" feed ─────────────────────────────────────────
// Builds the single prioritized card list for [SmartGlance] from the dashboard state
// already in hand (no extra flows → no pressure on the maxed-out combine()). Order:
//   1) ALERT   — a room HA flags as needing attention (filled accent; preempts the hero)
//   2) PINNED  — the user's chosen entity tiles, in their order (the "stats")
//   3) INSIGHT — how alive the home is right now (devices on), or a calm "all clear"
//   4) DELIGHT — whole-home temperature ("oh wow" aggregate)
// Items only appear when relevant, so the surface reshapes itself as the home changes:
// an alert clears and drops out, "all quiet" gives way to "active" once a light turns on.
@Composable
private fun rememberGlanceFeed(
    uiState: DashboardUiState,
    suggestion: GlanceSuggestion?,
    activity: GlanceActivity?,
    pulse: com.uc.homehealth.data.PulseReport?,
    onRoomClick: (HaRoom) -> Unit,
    onSceneTap: (String) -> Unit,
    onActivityTap: () -> Unit,
    onGlanceTileTap: (String) -> Unit,
    onPulseTap: () -> Unit,
): List<GlanceCard> {
    val c = MaterialTheme.customColors
    val palette = rememberGlancePalette()
    return remember(uiState.glanceTiles, uiState.rooms, uiState.showRoomWarnings, uiState.smartGlanceEnabled, suggestion, activity, pulse, c, palette) {
        buildGlanceFeed(uiState, suggestion, activity, pulse, palette, c, onRoomClick, onSceneTap, onActivityTap, onGlanceTileTap, onPulseTap)
    }
}

private fun buildGlanceFeed(
    uiState: DashboardUiState,
    suggestion: GlanceSuggestion?,
    activity: GlanceActivity?,
    pulse: com.uc.homehealth.data.PulseReport?,
    palette: List<Color>,
    c: CustomColors,
    onRoomClick: (HaRoom) -> Unit,
    onSceneTap: (String) -> Unit,
    onActivityTap: () -> Unit,
    onGlanceTileTap: (String) -> Unit,
    onPulseTap: () -> Unit,
): List<GlanceCard> {
    val rooms = uiState.rooms
    val smart = uiState.smartGlanceEnabled
    val cards = mutableListOf<GlanceCard>()

    // 1) ALERT — rooms HA flags as needing attention (only when warnings + smart cards on).
    val attention = if (smart && uiState.showRoomWarnings) rooms.filter { it.hasAlert } else emptyList()
    if (attention.isNotEmpty()) {
        val first = attention.first()
        cards += GlanceCard(
            key = "smart.alert",
            icon = "pulse",
            name = if (attention.size == 1) "Needs attention" else "${attention.size} rooms need attention",
            featuredValue = first.name,
            miniValue = if (attention.size == 1) first.name else "${attention.size} rooms",
            accent = c.coral,
            filled = true,
            onTap = { onRoomClick(first) },
        )
    }

    // 1.25) PULSE — the home's own health, only when degraded (below the Healthy band).
    // Filled like an alert so it preempts the hero; tap opens the full Pulse report.
    if (smart && pulse != null && pulse.grade != com.uc.homehealth.data.PulseGrade.HEALTHY) {
        cards += GlanceCard(
            key = "smart.pulse",
            icon = "pulse",
            name = "Home score",
            featuredValue = "${pulse.score}/100",
            miniValue = "${pulse.score}/100",
            accent = if (pulse.grade == com.uc.homehealth.data.PulseGrade.NEEDS_CARE) c.coral else c.warn,
            filled = true,
            onTap = onPulseTap,
        )
    }

    // 1.5) SUGGESTION — a scene the user usually runs about now, learned on-device. Sits
    // just below alerts so it becomes the hero when nothing needs attention; tap runs it.
    if (smart && suggestion != null) {
        cards += GlanceCard(
            key = "smart.suggestion",
            icon = "sparkle",
            name = "Usually now",
            featuredValue = suggestion.name,
            miniValue = suggestion.name,
            accent = c.lavender,
            onTap = { onSceneTap(suggestion.sceneId) },
        )
    }

    // 2) ACTIVITY — the latest thing that just happened, when fresh + not ignored (decided
    // upstream by the learning rule). Placed BEFORE pinned tiles so it's actually visible
    // on the first page (one hero + three) instead of being pushed onto a later carousel
    // page; tap opens the full activity feed.
    if (smart && activity != null) {
        cards += GlanceCard(
            key = "card:activity",
            icon = activity.iconKey,
            name = activity.relative,
            featuredValue = activity.title,
            miniValue = activity.title,
            accent = c.cyan,
            onTap = onActivityTap,
        )
    }

    // 3) PINNED stats — the user's chosen entities, in their order. Tapping a tile opens
    // that entity's controls (light/climate sheet) or toggles it, via onGlanceTileTap.
    uiState.glanceTiles.forEachIndexed { i, tile ->
        cards += tile.toCard(palette[i % palette.size], onTap = { onGlanceTileTap(tile.entityId) })
    }

    // 4) INSIGHT — devices on across the home, or a calm "all clear" when nothing's on.
    val onCount = rooms.sumOf { it.activeCount }
    if (smart && onCount > 0) {
        cards += GlanceCard(
            key = "smart.active",
            icon = "bulb",
            name = "Active now",
            featuredValue = "$onCount",
            miniValue = if (onCount == 1) "1 on" else "$onCount on",
            accent = c.sky,
        )
    } else if (smart && rooms.isNotEmpty() && attention.isEmpty()) {
        cards += GlanceCard(
            key = "smart.quiet",
            icon = "check",
            name = "All quiet",
            featuredValue = "All off",
            miniValue = "All off",
            accent = c.mint,
        )
    }

    // 4) DELIGHT — whole-home average temperature (an aggregate no single room card shows).
    val temps = rooms.mapNotNull { it.temp }
    if (smart && temps.isNotEmpty()) {
        val avg = Math.round(temps.average()).toInt()
        cards += GlanceCard(
            key = "smart.indoor",
            icon = "thermo",
            name = "Indoors",
            featuredValue = "$avg°",
            miniValue = "$avg°",
            accent = c.sand,
        )
    }

    return cards
}

