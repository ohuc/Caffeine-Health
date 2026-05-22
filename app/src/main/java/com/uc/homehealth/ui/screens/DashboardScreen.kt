package com.uc.homehealth.ui.screens

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.uc.homehealth.data.WsConnectionStatus
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.uc.homehealth.ui.components.ClimateSheetOverlay
import com.uc.homehealth.ui.components.ColorPickerSheetOverlay
import com.uc.homehealth.ui.components.FavCard
import com.uc.homehealth.ui.components.LightTile
import com.uc.homehealth.ui.components.PillToggle
import com.uc.homehealth.ui.components.PillToggleSize
import com.uc.homehealth.ui.components.RoomTile
import com.uc.homehealth.ui.components.SceneTile
import com.uc.homehealth.ui.components.SquigglySlider
import com.uc.homehealth.ui.components.CameraWidgetTile
import com.uc.homehealth.ui.components.SensorWidgetTile
import com.uc.homehealth.ui.components.SwitchWidgetTile
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.RollingNumberText
import com.uc.homehealth.ui.components.haIconFor
import androidx.compose.ui.text.TextStyle
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.DashboardUiState
import com.uc.homehealth.ui.viewmodel.DashboardViewModel
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
    onHideRoom: (String) -> Unit = {},
    onReorderRooms: (List<HaRoom>) -> Unit = {},
    onShowRoomPicker: () -> Unit = {},
    onEditRoomSensors: (HaRoom) -> Unit = {},
    onFlightsTap: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    DashboardScreenContent(
        uiState = uiState,
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
        onHideRoom = onHideRoom,
        onReorderRooms = onReorderRooms,
        onShowRoomPicker = onShowRoomPicker,
        onEditRoomSensors = onEditRoomSensors,
        onFlightsTap = onFlightsTap,
        onReconnect = onReconnect,
        onGoToSettings = onGoToSettings,
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DashboardScreenContent(
    uiState: DashboardUiState,
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
    onHideRoom: (String) -> Unit = {},
    onReorderRooms: (List<HaRoom>) -> Unit = {},
    onShowRoomPicker: () -> Unit = {},
    onEditRoomSensors: (HaRoom) -> Unit = {},
    onFlightsTap: () -> Unit = {},
    onReconnect: () -> Unit = {},
    onGoToSettings: () -> Unit = {},
) {
    if (!uiState.isOnline) {
        // During cold launch the combined flow may briefly hold the synchronous
        // initial value (loaded=false, DISCONNECTED, !isLoggedIn). Show nothing
        // until we actually know the user's auth/connection state — otherwise
        // OfflineState ("Not connected" + top-anchored spinner) flashes for a
        // few frames before the dashboard appears.
        //
        // Once loaded, only surface OfflineState for terminal/known-bad states:
        //   - user truly isn't logged in, or
        //   - WS has hit ERROR (auto-retry failed, sustained failure).
        // Transient DISCONNECTED/CONNECTING while authed renders nothing too —
        // the dashboard will appear as soon as the WS reaches READY and real
        // data arrives.
        val showOffline = uiState.loaded && (
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
        } else {
            // Bootstrap phase: WS handshake in progress. Show a centered spinner
            // so the screen isn't silently blank while rooms/scenes load.
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.ContainedLoadingIndicator()
            }
        }
        return
    }
    val cs = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    var roomsEditMode by remember { mutableStateOf(false) }
    var scenesEditMode by remember { mutableStateOf(false) }
    var favsEditMode by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.rooms.isEmpty()) { if (uiState.rooms.isEmpty()) roomsEditMode = false }
    LaunchedEffect(uiState.scenes.isEmpty()) { if (uiState.scenes.isEmpty()) scenesEditMode = false }
    LaunchedEffect(uiState.favorites.isEmpty()) { if (uiState.favorites.isEmpty()) favsEditMode = false }

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxWidth(),
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
            GlanceSubtitle(
                template = uiState.glanceTemplate,
                values = uiState.glanceValues,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

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
            AnimatedContent(
                targetState = roomsEditMode,
                transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) },
                label = "rooms_header",
            ) { inEditMode ->
                if (inEditMode) {
                    EditPill(editMode = true, onToggle = { roomsEditMode = false })
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AnimatedVisibility(
                            visible = uiState.hiddenRooms.isNotEmpty(),
                            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f),
                        ) {
                            SectionIconButton(icon = Icons.Outlined.Add, contentDescription = "Add room", onClick = onShowRoomPicker)
                        }
                        AnimatedVisibility(
                            visible = uiState.rooms.isNotEmpty(),
                            enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f),
                            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.7f),
                        ) {
                            SectionIconButton(icon = Icons.Outlined.Edit, contentDescription = "Edit rooms", onClick = { roomsEditMode = true })
                        }
                    }
                }
            }
        }

        // ─── Rooms content ────────────────────────────────────────────────────
        if (roomsEditMode) {
            ReorderableRoomColumn(
                rooms = uiState.rooms,
                modifier = Modifier.padding(horizontal = Spacing.ml),
                onReorderCommit = onReorderRooms,
                onRemove = { onHideRoom(it.id) },
                onEditSensors = onEditRoomSensors,
            )
        } else {
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
                            RoomTile(room = room, height = rowHeights.getOrElse(startRowIndex + i) { 160.dp }, onTap = { onRoomClick(room) })
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        right.forEachIndexed { i, room ->
                            RoomTile(room = room, height = rowHeights.getOrElse(startRowIndex + i) { 160.dp }, onTap = { onRoomClick(room) })
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
                            .background(cs.surfaceContainerHigh, RoundedCornerShape(16.dp))
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (uiState.showAllRooms) "Show less" else "Show all (${overflowRooms.size} more)",
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
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
}

@Composable
private fun FlightsPill(count: Int, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    Tap(onClick = { haptic.navigation(); onTap() }) {
        Row(
            modifier = Modifier
                .background(cs.surfaceContainerHigh, RoundedCornerShape(999.dp))
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
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.SemiBold,
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
                fontWeight = FontWeight.SemiBold,
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
                        .background(cs.primary, RoundedCornerShape(999.dp))
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
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
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
                animationSpec = tween(220),
                initialOffsetY = { -it * 2 },
            ) + fadeIn(animationSpec = tween(220)),
            exit = slideOutVertically(
                animationSpec = tween(220),
                targetOffsetY = { -it * 2 },
            ) + fadeOut(animationSpec = tween(220)),
        ) {
            androidx.compose.material3.ContainedLoadingIndicator()
        }
    }
}

// ─── Inline-chip subtitle — matches shared.jsx InlineChip + dashboard.jsx greeting ──

// Per-placeholder chip palette (kept consistent with the original prototype).
private data class ChipStyle(val bg: Color, val fg: Color)

@Composable
private fun chipStyleFor(key: String): ChipStyle {
    val cs = MaterialTheme.colorScheme
    return when (key) {
        "outside_temp", "inside_temp" -> ChipStyle(cs.onSurface, cs.background)
        "doorbell"                    -> ChipStyle(cs.tertiaryContainer, cs.onTertiaryContainer)
        "lights_on"                   -> ChipStyle(cs.secondaryContainer, cs.onSecondaryContainer)
        "aqi"                         -> ChipStyle(cs.primaryContainer, cs.onPrimaryContainer)
        else                          -> ChipStyle(cs.surfaceVariant, cs.onSurfaceVariant)
    }
}

// Parses a template like "It is {outside_temp} outside" into ordered segments.
private sealed class GlanceSegment {
    data class TextSeg(val text: String) : GlanceSegment()
    data class ChipSeg(val key: String) : GlanceSegment()
}

private fun parseGlanceTemplate(template: String): List<GlanceSegment> {
    val regex = Regex("""\{([a-z_][a-z0-9_]*)\}""")
    val result = mutableListOf<GlanceSegment>()
    var cursor = 0
    regex.findAll(template).forEach { match ->
        if (match.range.first > cursor) {
            result.add(GlanceSegment.TextSeg(template.substring(cursor, match.range.first)))
        }
        result.add(GlanceSegment.ChipSeg(match.groupValues[1]))
        cursor = match.range.last + 1
    }
    if (cursor < template.length) result.add(GlanceSegment.TextSeg(template.substring(cursor)))
    return result
}

// Default chip text when the entity ID is set but no state has arrived yet (or no ID configured).
private fun displayValueFor(key: String, value: HaEntityValue?): String {
    if (value == null) return when (key) {
        "outside_temp", "inside_temp" -> "—°"
        else -> "—"
    }
    val raw = value.state
    return when (key) {
        "outside_temp", "inside_temp" -> {
            val n = raw.toFloatOrNull()
            if (n != null) "${n.toInt()}°" else raw
        }
        else -> if (!value.unit.isNullOrBlank() && value.unit !in raw) "$raw ${value.unit}" else raw
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlanceSubtitle(
    template: String,
    values: Map<String, HaEntityValue?>,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val segments = remember(template) { parseGlanceTemplate(template) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        segments.forEach { seg ->
            when (seg) {
                is GlanceSegment.TextSeg -> Text(
                    text = seg.text,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 29.sp,
                    color = cs.onSurfaceVariant,
                )
                is GlanceSegment.ChipSeg -> {
                    val style = chipStyleFor(seg.key)
                    Box(
                        modifier = Modifier
                            .background(style.bg, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = displayValueFor(seg.key, values[seg.key]),
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = style.fg,
                        )
                    }
                }
            }
        }
    }
}

// ─── Custom animated room sheet overlay ──────────────────────────────────────
// Uses AnimatedVisibility with slow springs instead of ModalBottomSheet so we
// can control animation feel directly (stiffness → 120fps smooth settle).

// internal so Navigation.kt can render it above BottomNavBar
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
internal fun RoomSheetOverlay(
    room: HaRoom?,
    lights: List<HaLight>,
    climate: com.uc.homehealth.data.HaClimate? = null,
    tempHistory: List<Float> = emptyList(),
    widgets: List<RoomWidget> = emptyList(),
    widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    widgetHistories: Map<String, List<Float>> = emptyMap(),
    widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?> = emptyMap(),
    showWidgetCatalog: Boolean = false,
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onToggle: (entityId: String, isOn: Boolean) -> Unit = { _, _ -> },
    onBrightnessChange: (entityId: String, brightness: Int) -> Unit = { _, _ -> },
    onColorChange: (entityId: String, r: Int, g: Int, b: Int) -> Unit = { _, _, _, _ -> },
    onColorTempChange: (entityId: String, kelvin: Int) -> Unit = { _, _ -> },
    onClimateTargetChange: (entityId: String, temperature: Float) -> Unit = { _, _ -> },
    onClimateModeChange: (entityId: String, mode: String) -> Unit = { _, _ -> },
    onShowWidgetCatalog: () -> Unit = {},
    onHideWidgetCatalog: () -> Unit = {},
    onPickSwitchType: () -> Unit = {},
    onPickSensorType: () -> Unit = {},
    onPickCameraType: () -> Unit = {},
    onPickMediaType: () -> Unit = {},
    onWidgetClick: (RoomWidget) -> Unit = {},
    onWidgetLongPress: (RoomWidget) -> Unit = {},
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

    val overlayHaptic = com.uc.homehealth.ui.components.rememberAppHaptics()
    val onDismissWithHaptic: () -> Unit = { overlayHaptic.confirm(); onDismiss() }

    // Back gesture: dismiss any overlay sub-sheets first, otherwise dismiss the room sheet.
    // Without this, the system back falls through and exits the app.
    BackHandler(enabled = visible) {
        when {
            colorPickerLight != null -> colorPickerLight = null
            climateSheetClimate != null -> climateSheetClimate = null
            else -> onDismissWithHaptic()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(280, easing = EaseOut)),
            exit = fadeOut(tween(380, easing = EaseIn)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (hazeState != null) Modifier.hazeEffect(state = hazeState, style = HazeMaterials.regular())
                        else Modifier
                    )
                    .background(Color.Black.copy(alpha = 0.35f))
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
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
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
                            .background(Color.White.copy(alpha = 0.30f), RoundedCornerShape(2.dp))
                    )
                }

                capturedRoom?.let { r ->
                    RoomSheetContent(
                        room = r,
                        externalLights = lights,
                        climate = climate,
                        tempHistory = tempHistory,
                        widgets = widgets,
                        widgetStates = widgetStates,
                        widgetHistories = widgetHistories,
                        widgetCameraSnapshots = widgetCameraSnapshots,
                        widgetMediaStates = widgetMediaStates,
                        showWidgetCatalog = showWidgetCatalog,
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
                        onWidgetClick = onWidgetClick,
                        onWidgetLongPress = onWidgetLongPress,
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
    climate: com.uc.homehealth.data.HaClimate?,
    tempHistory: List<Float>,
    widgets: List<RoomWidget>,
    widgetStates: Map<String, HaEntityValue?>,
    widgetHistories: Map<String, List<Float>>,
    widgetCameraSnapshots: Map<String, String?>,
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?>,
    showWidgetCatalog: Boolean,
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
    onWidgetClick: (RoomWidget) -> Unit,
    onWidgetLongPress: (RoomWidget) -> Unit,
    onRemoveWidget: (RoomWidget) -> Unit,
    onReorderWidgets: (List<RoomWidget>) -> Unit,
    onOpenColorPicker: (HaLight) -> Unit,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
) {
    val cs = MaterialTheme.colorScheme
    val accentColor = try { Color(AndroidColor.parseColor(room.colorHex)) } catch (_: Exception) { cs.primary }
    val inkColor = try { Color(AndroidColor.parseColor(room.inkHex)) } catch (_: Exception) { cs.onPrimary }

    var selectedTab by remember(room.id) { mutableStateOf("Lights") }
    var selectedLightId by remember(room.id) { mutableStateOf<String?>(null) }

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
                        onBack = { selectedLightId = null },
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
            "widget_catalog" -> {
                WidgetCatalogPanel(
                    accentColor = accentColor,
                    inkColor = inkColor,
                    onBack = onHideWidgetCatalog,
                    onPickSwitchType = onPickSwitchType,
                    onPickSensorType = onPickSensorType,
                    onPickCameraType = onPickCameraType,
                    onPickMediaType = onPickMediaType,
                )
            }
            else -> {
                RoomOverviewContent(
                    room = room,
                    lights = lights,
                    climate = climate,
                    accentColor = accentColor,
                    inkColor = inkColor,
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    onDismiss = onDismiss,
                    tempHistory = tempHistory,
                    widgets = widgets,
                    widgetStates = widgetStates,
                    widgetHistories = widgetHistories,
                    widgetCameraSnapshots = widgetCameraSnapshots,
                    widgetMediaStates = widgetMediaStates,
                    onLightSelect = { light -> selectedLightId = light.id },
                    onClimateTargetChange = onClimateTargetChange,
                    onClimateModeChange = onClimateModeChange,
                    onClimateCardTap = onClimateCardTap,
                    onShowWidgetCatalog = onShowWidgetCatalog,
                    onWidgetClick = onWidgetClick,
                    onWidgetLongPress = onWidgetLongPress,
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
                modifier = Modifier.weight(1f),
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
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
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
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    tempHistory: List<Float> = emptyList(),
    widgets: List<RoomWidget> = emptyList(),
    widgetStates: Map<String, HaEntityValue?> = emptyMap(),
    widgetHistories: Map<String, List<Float>> = emptyMap(),
    widgetCameraSnapshots: Map<String, String?> = emptyMap(),
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?> = emptyMap(),
    onShowWidgetCatalog: () -> Unit = {},
    onWidgetClick: (RoomWidget) -> Unit = {},
    onWidgetLongPress: (RoomWidget) -> Unit = {},
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
                        if (room.hasAlert) append(" · ⚠ needs attention")
                    },
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
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

        // ── Env hero — sine wave fill (Phase 6: replace with live HA sensor data) ──
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp, bottom = 14.dp)
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(24.dp))
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
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumberText(
                            text = "%.1f".format(room.temp),
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
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            RollingNumberText(
                                text = room.humidity.toString(),
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
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "Comfortable · holding steady",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
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
        )

        // ── Tab content: directional slide + animated height ─────────────────
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                val fromIndex = tabs.indexOf(initialState)
                val toIndex = tabs.indexOf(targetState)
                val goingRight = toIndex > fromIndex
                (slideInHorizontally(tween(260)) { if (goingRight) it else -it } +
                    fadeIn(tween(200))) togetherWith
                (slideOutHorizontally(tween(260)) { if (goingRight) -it else it } +
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
                    onTargetChange = onClimateTargetChange,
                    onModeChange = onClimateModeChange,
                    onCardTap = onClimateCardTap,
                )
                "Media" -> MediaTabContent(
                    widgets = widgets,
                    widgetMediaStates = widgetMediaStates,
                    mediaCallbacks = mediaCallbacks,
                    onAddWidget = onShowWidgetCatalog,
                )
                "More" -> MoreTabContent(
                    widgets = widgets,
                    widgetStates = widgetStates,
                    widgetHistories = widgetHistories,
                    widgetCameraSnapshots = widgetCameraSnapshots,
                    widgetMediaStates = widgetMediaStates,
                    onAddWidget = onShowWidgetCatalog,
                    onWidgetClick = onWidgetClick,
                    onWidgetLongPress = onWidgetLongPress,
                    onRemoveWidget = onRemoveWidget,
                    onReorderWidgets = onReorderWidgets,
                    mediaCallbacks = mediaCallbacks,
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
            Tap(onClick = onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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

@OptIn(ExperimentalHazeMaterialsApi::class)
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
    val hazeState = remember { HazeState() }
    val glowAlpha by animateFloatAsState(
        targetValue = if (masterOn) 0.22f + (masterBri / 100f) * 0.58f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "master_glow_alpha",
    )

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(cs.surfaceContainerHigh)
                .haze(state = hazeState),
        ) {
            Canvas(modifier = Modifier.align(Alignment.TopEnd).size(140.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(size.width, 0f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hazeChild(state = hazeState, style = HazeMaterials.ultraThin())
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (masterOn) Color.White.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.03f)),
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
                        fontWeight = FontWeight.SemiBold,
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
                            fontWeight = FontWeight.SemiBold,
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

                Box(contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.size(96.dp)) {
                        val haloAlpha = if (masterOn) 0.08f + (masterBri / 100f) * 0.52f else 0f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(accentColor.copy(alpha = haloAlpha), Color.Transparent),
                                radius = size.width * 0.5f,
                            ),
                            radius = size.width * 0.5f,
                        )
                    }
                    PillToggle(
                        isOn = masterOn,
                        onToggle = { onMasterToggle(!masterOn) },
                        color = accentColor,
                        ink = inkColor,
                        size = PillToggleSize.Lg,
                    )
                }
            }

            SquigglySlider(
                value = masterBri,
                onValueChange = onMasterBrightness,
                onValueChangeFinished = onMasterBrightnessFinished,
                color = if (masterOn) accentColor else Color.White.copy(alpha = 0.18f),
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
            val availRowCount = (availableLights.size + 1) / 2
            val availTargetHeight = 154.dp * availRowCount + 10.dp * (availRowCount - 1).coerceAtLeast(0)
            val availGridHeight by animateDpAsState(
                targetValue = availTargetHeight,
                animationSpec = tween(durationMillis = 280),
                label = "availGridHeight",
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(availGridHeight),
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false,
            ) {
                items(items = availableLights, key = { it.id }) { light ->
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        if (unavailableLights.isNotEmpty()) {
            val cs = MaterialTheme.colorScheme
            // Toggle chip
            Tap(
                onClick = { showUnavailable = !showUnavailable },
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (showUnavailable) "Hide unavailable" else "${unavailableLights.size} unavailable",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.SemiBold,
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
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(220)) + fadeOut(tween(220)),
            ) {
                val unavailRowCount = ((unavailableLights.size + 1) / 2).coerceAtLeast(1)
                val unavailTargetHeight = 154.dp * unavailRowCount + 10.dp * (unavailRowCount - 1).coerceAtLeast(0)
                val unavailGridHeight by animateDpAsState(
                    targetValue = unavailTargetHeight,
                    animationSpec = tween(durationMillis = 280),
                    label = "unavailGridHeight",
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                        .height(unavailGridHeight),
                    contentPadding = PaddingValues(0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(items = unavailableLights, key = { it.id }) { light ->
                        LightTile(
                            light = light,
                            expanded = false,
                            onExpand = {},
                            onToggle = {},
                            onBrightnessChange = {},
                            onBrightnessChangeFinished = {},
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClimateTabContent(
    climate: com.uc.homehealth.data.HaClimate?,
    onTargetChange: (entityId: String, temperature: Float) -> Unit,
    onModeChange: (entityId: String, mode: String) -> Unit,
    onCardTap: (com.uc.homehealth.data.HaClimate) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    if (climate == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No climate device in this room",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
            )
        }
        return
    }

    // Optimistic local target — snaps back when HA confirms the new state.
    var localTarget by remember(climate.id, climate.targetTemp) {
        mutableStateOf(climate.targetTemp ?: 22f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
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
                .clip(RoundedCornerShape(22.dp))
                .background(cs.surfaceContainerHigh)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ADJUST TARGET",
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                    color = cs.onSurfaceVariant,
                )
                Text(
                    text = "Step ${"%.1f".format(climate.tempStep)}°",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
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
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
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
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$tab — coming in Phase 6",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Media tab ────────────────────────────────────────────────────────────────
// Renders every RoomWidget.Media added to this room as a Hero MediaCard,
// stacked vertically. If the room has no media widgets, shows a hint that
// directs the user to the More tab's widget catalog.

@Composable
private fun MediaTabContent(
    widgets: List<RoomWidget>,
    widgetMediaStates: Map<String, com.uc.homehealth.data.HaMedia?>,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
    onAddWidget: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val mediaWidgets = widgets.filterIsInstance<RoomWidget.Media>()

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (mediaWidgets.isEmpty()) {
            Tap(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
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
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = "Add a media player",
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "Pick a media_player entity to control it from here",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            return
        }

        mediaWidgets.forEach { widget ->
            val media = widgetMediaStates[widget.entityId] ?: return@forEach
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
                onOpenQueue = { mediaCallbacks.onOpenQueue(widget.entityId) },
                onCast = { mediaCallbacks.onCast(widget.entityId) },
                onSeek = { progress -> mediaCallbacks.onSeek(widget.entityId, progress) },
                onVolumeChange = { volume -> mediaCallbacks.onVolumeChange(widget.entityId, volume) },
            )
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
    onAddWidget: () -> Unit,
    onWidgetClick: (RoomWidget) -> Unit,
    onWidgetLongPress: (RoomWidget) -> Unit,
    onRemoveWidget: (RoomWidget) -> Unit,
    onReorderWidgets: (List<RoomWidget>) -> Unit,
    mediaCallbacks: com.uc.homehealth.ui.components.MediaCardCallbacks,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    var editMode by remember { mutableStateOf(false) }

    // Drop out of edit mode automatically once the user removes the last widget.
    LaunchedEffect(widgets.isEmpty()) {
        if (widgets.isEmpty() && editMode) editMode = false
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                        editMode = !editMode
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
                            onOpenQueue = { mediaCallbacks.onOpenQueue(widget.entityId) },
                            onCast = { mediaCallbacks.onCast(widget.entityId) },
                            onSeek = { progress -> mediaCallbacks.onSeek(widget.entityId, progress) },
                            onVolumeChange = { volume -> mediaCallbacks.onVolumeChange(widget.entityId, volume) },
                        )
                    }
                }
            }
        }

        // ── Add Your Widget card — hidden in edit mode to avoid mode mixup ───
        if (!editMode) {
            Tap(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
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
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = if (widgets.isEmpty()) "Switches, sensor graphs, more coming soon" else "Add another widget to this room",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
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
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(cs.surfaceContainerHigh, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = cs.onSurface,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun EditPill(editMode: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onToggle) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(if (editMode) cs.primary else cs.surfaceContainerHigh)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (editMode) Icons.Outlined.Check else Icons.Outlined.Edit,
                contentDescription = null,
                tint = if (editMode) cs.onPrimary else cs.onSurface,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (editMode) "Done" else "Edit",
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (editMode) cs.onPrimary else cs.onSurface,
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
                enter = fadeIn(tween(WIDGET_ENTER_MS)) + expandVertically(tween(WIDGET_ENTER_MS, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(tween(WIDGET_EXIT_MS, easing = FastOutSlowInEasing)) +
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
                                    .background(Color.Black.copy(alpha = 0.72f), CircleShape),
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
            Tap(onClick = onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
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
                    fontWeight = FontWeight.SemiBold,
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
                .clip(RoundedCornerShape(22.dp))
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
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = description,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
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
                enter = fadeIn(tween(WIDGET_ENTER_MS)) + expandVertically(tween(WIDGET_ENTER_MS, easing = FastOutSlowInEasing)),
                exit = shrinkVertically(tween(WIDGET_EXIT_MS, easing = FastOutSlowInEasing)) + fadeOut(tween(WIDGET_EXIT_MS - 60)),
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
                            .background(cs.surfaceContainerHigh, RoundedCornerShape(16.dp))
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
                            Text(room.name, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
                            Text(
                                "${room.deviceCount} device${if (room.deviceCount == 1) "" else "s"}",
                                fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = cs.onSurfaceVariant,
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
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.72f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit room sensors", tint = Color.White, modifier = Modifier.size(17.dp))
                            }
                        }
                        Tap(
                            onClick = { if (exitingIds[room.id] != true) { haptic.confirm(); exitingIds[room.id] = true } },
                        ) {
                            Box(
                                modifier = Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.72f), CircleShape),
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

// ─── Reorderable horizontal row ───────────────────────────────────────────────
// Long-press-then-drag in edit mode picks up a tile and slides it horizontally,
// swapping with an adjacent item when more than half its width is crossed.
// Mirrors ReorderableWidgetColumn but operates on the X axis.

private const val HORIZ_EXIT_MS = 260
private const val HORIZ_ENTER_MS = 220

@Composable
private fun <T : Any> ReorderableHorizontalRow(
    items: List<T>,
    key: (T) -> String,
    spacing: Dp,
    contentPadding: PaddingValues,
    onReorderCommit: (List<T>) -> Unit,
    onRemove: (T) -> Unit,
    content: @Composable (T) -> Unit,
) {
    val haptic = rememberAppHaptics()
    val jiggleTransition = rememberInfiniteTransition(label = "horiz_jiggle")
    val horizScroll = rememberScrollState()

    val widths = remember { mutableStateMapOf<String, Int>() }
    var order by remember(items) { mutableStateOf(items) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val exitingIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(items) {
        if (draggedId == null && exitingIds.isEmpty()) order = items
    }

    Row(
        modifier = Modifier
            .horizontalScroll(horizScroll)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        order.forEach { item ->
            val id = key(item)
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
                    onRemove(item)
                    exitingIds.remove(id)
                    order = order.filterNot { key(it) == id }
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                enter = fadeIn(tween(HORIZ_ENTER_MS)),
                exit = shrinkHorizontally(tween(HORIZ_EXIT_MS, easing = FastOutSlowInEasing)) +
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

                    Tap(
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
                                .background(Color.Black.copy(alpha = 0.72f), CircleShape),
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

