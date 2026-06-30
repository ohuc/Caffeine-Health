package com.uc.homehealth.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SpaceDashboard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.uc.homehealth.data.WsConnectionStatus
import com.uc.homehealth.ui.components.OpenInHomeAssistantButton
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.launchHomeAssistant
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.SettingsUiState
import com.uc.homehealth.ui.viewmodel.SettingsViewModel

private fun settingsSlideSpring() = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

private enum class SettingsDestination : NavKey {
    Main,
    HomeAssistant,
    Updates,
    Profile,
    Appearance,
    AdditionalCards,
    Voice,
    ManageData,
    About,
}

// Caffeine-style segment shapes. HomeHealth's shape scale is 2x M3 defaults,
// so single-element segments use `small` (14dp) instead of `large` to avoid
// pill-like artifacts on SegmentedListItem's fixed-height row.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun segmentedListItemShapes(index: Int, count: Int): ListItemShapes =
    ListItemDefaults.segmentedShapes(
        index,
        count,
        ListItemDefaults.shapes(
            shape = if (count == 1) MaterialTheme.shapes.small else MaterialTheme.shapes.extraSmall,
            selectedShape = MaterialTheme.shapes.extraLargeIncreased,
            pressedShape = MaterialTheme.shapes.extraLargeIncreased,
            focusedShape = MaterialTheme.shapes.small,
            hoveredShape = MaterialTheme.shapes.extraLarge,
            draggedShape = MaterialTheme.shapes.extraLargeIncreased,
        ),
    )

@Composable
fun SettingsScreen(
    onConnect: (String) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    onSheetVisibleChange: (Boolean) -> Unit = {},
    // Opens the top-level nav-bar editor route (lives outside Settings' own NavDisplay
    // so the real bottom bar stays visible as a live preview while editing).
    onEditNavBar: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(SettingsDestination.Main)

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) backStack.removeLastOrNull()
        },
        modifier = Modifier.fillMaxSize(),
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        // M3 Expressive motion physics: spatial slides ride springs, only the simple
        // fades stay on tween (per docs/material3-expressive.md).
        transitionSpec = {
            slideInHorizontally(animationSpec = settingsSlideSpring()) { it } togetherWith
                (slideOutHorizontally(animationSpec = settingsSlideSpring()) { -it / 4 } +
                    fadeOut(animationSpec = tween(220)))
        },
        popTransitionSpec = {
            (slideInHorizontally(animationSpec = settingsSlideSpring()) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = settingsSlideSpring()) { it }
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(animationSpec = settingsSlideSpring()) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = settingsSlideSpring()) { it }
        },
        entryProvider = entryProvider {
            entry<SettingsDestination> { destination ->
                when (destination) {
                    SettingsDestination.Main -> SettingsListScreen(
                        uiState = uiState,
                        onOpenHomeAssistant = { backStack.add(SettingsDestination.HomeAssistant) },
                        onOpenUpdates = { backStack.add(SettingsDestination.Updates) },
                        onOpenProfile = { backStack.add(SettingsDestination.Profile) },
                        onOpenAppearance = { backStack.add(SettingsDestination.Appearance) },
                        onOpenNavBar = onEditNavBar,
                        onOpenAdditionalCards = { backStack.add(SettingsDestination.AdditionalCards) },
                        onOpenVoice = { backStack.add(SettingsDestination.Voice) },
                        onOpenManageData = { backStack.add(SettingsDestination.ManageData) },
                        onOpenAbout = { backStack.add(SettingsDestination.About) },
                        onRedoOnboarding = viewModel::redoOnboarding,
                    )
                    SettingsDestination.Updates -> {
                        val ctx = LocalContext.current
                        UpdatesScreen(
                            onBack = { backStack.removeAt(backStack.lastIndex) },
                            onOpenReleaseUrl = { url -> openUrl(ctx, url) },
                            onSheetVisibleChange = onSheetVisibleChange,
                        )
                    }
                    SettingsDestination.HomeAssistant -> HomeAssistantSettingsScreen(
                        uiState = uiState,
                        onUrlChange = viewModel::onUrlChange,
                        onConnect = onConnect,
                        onDisconnect = viewModel::disconnect,
                        onTokenChange = viewModel::onTokenChange,
                        onToggleTokenInput = viewModel::toggleTokenInput,
                        onConnectWithToken = viewModel::connectWithToken,
                        onSaveRouting = viewModel::saveNetworkRouting,
                        hasLocationPermission = viewModel::hasLocationPermission,
                        onRefreshSsid = viewModel::refreshSsid,
                        onDetectCurrentSsid = viewModel::currentSsid,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.Profile -> ProfileSettingsScreen(
                        name = uiState.userName,
                        onNameChange = viewModel::setUserName,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.Appearance -> AppearanceSettingsScreen(
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.AdditionalCards -> AdditionalCardSettingsScreen(
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.Voice -> VoiceSettingsScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.ManageData -> ManageDataSettingsScreen(
                        onClearActivity = viewModel::clearActivityData,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                    SettingsDestination.About -> AboutSettingsScreen(
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Main list screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsListScreen(
    uiState: SettingsUiState,
    onOpenHomeAssistant: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNavBar: () -> Unit,
    onOpenAdditionalCards: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenManageData: () -> Unit,
    onOpenAbout: () -> Unit,
    onRedoOnboarding: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val haptic = rememberAppHaptics()

    val subtitle = when {
        uiState.isLoggedIn ->
            "${uiState.serverName} · ${uiState.haVersion} · Up to date"
        uiState.demoFromOnboarding ->
            "Connect with Home Assistant to exit demo mode"
        else -> "Demo mode · Sample home data"
    }

    val haStatusSummary = when {
        uiState.isLoggedIn -> when (uiState.connectionStatus) {
            WsConnectionStatus.READY -> "Connected · ${uiState.haUrl}"
            WsConnectionStatus.CONNECTING -> "Connecting…"
            WsConnectionStatus.ERROR -> "Cannot reach server"
            WsConnectionStatus.DISCONNECTED -> "Disconnected"
            WsConnectionStatus.AUTH_INVALID -> "Session expired · sign in again"
            WsConnectionStatus.IP_BANNED -> "IP blocked by Home Assistant"
        }
        else -> "Not connected · demo mode"
    }

    val categories = listOf(
        SettingsRow(
            title = "Home Assistant",
            summary = haStatusSummary,
            icon = haIconFor("wifi"),
            onClick = onOpenHomeAssistant,
        ),
        SettingsRow(
            title = "Updates",
            summary = "HACS, add-ons, firmware & system updates",
            icon = haIconFor("update"),
            onClick = onOpenUpdates,
        ),
        SettingsRow(
            title = "Profile",
            summary = "Greeting name · ${uiState.userName.ifBlank { "Not set" }}",
            icon = Icons.Outlined.Person,
            onClick = onOpenProfile,
        ),
        SettingsRow(
            title = "Appearance",
            summary = "Light, dark, or system theme",
            icon = haIconFor("palette"),
            onClick = onOpenAppearance,
        ),
        SettingsRow(
            title = "Navigation Bar",
            summary = "Choose & reorder the bottom tabs",
            icon = Icons.Outlined.SpaceDashboard,
            onClick = onOpenNavBar,
        ),
        SettingsRow(
            title = "Additional Card Settings",
            summary = "Room warnings & other card options",
            icon = haIconFor("bell"),
            onClick = onOpenAdditionalCards,
        ),
        SettingsRow(
            title = "Voice & TTS",
            summary = "Announcement engine & voice for media players",
            icon = Icons.Outlined.Campaign,
            onClick = onOpenVoice,
        ),
        SettingsRow(
            title = "Manage Data",
            summary = "Activity history stored on this device",
            icon = haIconFor("data"),
            onClick = onOpenManageData,
        ),
        SettingsRow(
            title = "About",
            summary = "Version, credits & build info",
            icon = Icons.Outlined.Info,
            onClick = onOpenAbout,
        ),
    )

    SettingsPageScaffold(title = "Settings", subtitle = subtitle) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            categories.forEachIndexed { index, row ->
                SegmentedListItem(
                    onClick = { haptic.navigation(); row.onClick() },
                    modifier = Modifier.fillMaxWidth(),
                    leadingContent = {
                        Icon(
                            imageVector = row.icon,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                        )
                    },
                    content = {
                        Text(
                            text = row.title,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = cs.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = row.summary,
                            fontFamily = MontserratFamily,
                            fontSize = 12.sp,
                            color = cs.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = haIconFor("chevron-right"),
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shapes = segmentedListItemShapes(index, categories.size),
                    colors = ListItemDefaults.colors(containerColor = cs.surfaceContainerHigh),
                )
            }
        }

        Spacer(Modifier.size(20.dp))

        OpenInHomeAssistantButton(
            title = "Open in Home Assistant",
            subtitle = if (uiState.haUrl.isBlank())
                "Devices, automations, energy & security"
            else
                "Devices, automations, energy & security · ${uiState.haUrl}",
            modifier = Modifier.padding(horizontal = Spacing.ml),
            onClick = { haptic.navigation(); launchHomeAssistant(context, uiState.haUrl) },
        )

        Spacer(Modifier.size(12.dp))

        TextButton(
            onClick = { haptic.tick(); onRedoOnboarding() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ml),
        ) {
            Text(
                text = "Redo onboarding",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

private data class SettingsRow(
    val title: String,
    val summary: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

// ---------------------------------------------------------------------------
// Home Assistant subpage
// ---------------------------------------------------------------------------

@Composable
private fun HomeAssistantSettingsScreen(
    uiState: SettingsUiState,
    onUrlChange: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenInput: () -> Unit,
    onConnectWithToken: (String, String) -> Unit,
    onSaveRouting: (String, String, List<String>, String) -> Unit,
    hasLocationPermission: () -> Boolean,
    onRefreshSsid: () -> Unit,
    onDetectCurrentSsid: () -> String?,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = "Home Assistant",
        subtitle = if (uiState.isLoggedIn) "Manage your connection" else "Connect to your server",
        onBack = onBack,
    ) {
        HaConnectionCard(
            uiState = uiState,
            onUrlChange = onUrlChange,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onTokenChange = onTokenChange,
            onToggleTokenInput = onToggleTokenInput,
            onConnectWithToken = onConnectWithToken,
            modifier = Modifier.padding(horizontal = Spacing.ml),
        )
        Spacer(Modifier.size(12.dp))
        NetworkRoutingCard(
            uiState = uiState,
            hasLocationPermission = hasLocationPermission,
            onRefreshSsid = onRefreshSsid,
            onDetectCurrentSsid = onDetectCurrentSsid,
            onSave = onSaveRouting,
            modifier = Modifier.padding(horizontal = Spacing.ml),
        )
    }
}

@Composable
private fun NetworkRoutingCard(
    uiState: SettingsUiState,
    hasLocationPermission: () -> Boolean,
    onRefreshSsid: () -> Unit,
    onDetectCurrentSsid: () -> String?,
    onSave: (String, String, List<String>, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val localState = androidx.compose.runtime.remember(uiState.localUrl) {
        androidx.compose.runtime.mutableStateOf(uiState.localUrl)
    }
    val remoteState = androidx.compose.runtime.remember(uiState.remoteUrl) {
        androidx.compose.runtime.mutableStateOf(uiState.remoteUrl)
    }
    val go2rtcState = androidx.compose.runtime.remember(uiState.go2rtcUrl) {
        androidx.compose.runtime.mutableStateOf(uiState.go2rtcUrl)
    }
    val ssidListState = androidx.compose.runtime.remember(uiState.homeSsids) {
        androidx.compose.runtime.mutableStateOf(uiState.homeSsids)
    }
    val ssidInputState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val localUrl = localState.value
    val remoteUrl = remoteState.value
    val go2rtcUrl = go2rtcState.value
    val ssids = ssidListState.value
    val ssidInput = ssidInputState.value

    fun addTyped() {
        val v = ssidInput.trim()
        if (v.isEmpty() || v in ssids) {
            ssidInputState.value = ""
            return
        }
        ssidListState.value = ssids + v
        ssidInputState.value = ""
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val detected = onDetectCurrentSsid()
            if (!detected.isNullOrBlank() && detected !in ssidListState.value) {
                ssidListState.value = ssidListState.value + detected
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(haIconFor("wifi"), contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text("Network routing", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
        }
        Text(
            text = "On any listed home Wi-Fi the app uses the local URL; everywhere else it uses the remote URL.",
            fontFamily = MontserratFamily,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 15.sp,
        )

        Text("Remote URL", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurface)
        UrlField(value = remoteUrl, onValueChange = { remoteState.value = it }, placeholder = "https://ha.example.com", onGo = {})

        Text("Local URL", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurface)
        UrlField(value = localUrl, onValueChange = { localState.value = it }, placeholder = "http://homeassistant.local:8123", onGo = {})

        Text("go2rtc URL (optional)", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurface)
        UrlField(value = go2rtcUrl, onValueChange = { go2rtcState.value = it }, placeholder = "https://go2rtc.example.com", onGo = {})
        Text(
            text = "Set this to a reachable, protected go2rtc instance for low-latency MSE live view. Leave blank to use WebRTC/HLS.",
            fontFamily = MontserratFamily,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 15.sp,
        )

        Text("Home Wi-Fi SSIDs", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurface)
        com.uc.homehealth.ui.screens.SsidChipList(ssids = ssids, onRemove = { ssid ->
            ssidListState.value = ssids.filterNot { it == ssid }
        })
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                UrlField(value = ssidInput, onValueChange = { ssidInputState.value = it }, placeholder = "MyHomeWiFi", onGo = { addTyped() })
            }
            Button(
                onClick = { haptic.tick(); addTyped() },
                enabled = ssidInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Add", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = {
                haptic.tick()
                if (hasLocationPermission()) {
                    val detected = onDetectCurrentSsid()
                    if (!detected.isNullOrBlank() && detected !in ssidListState.value) {
                        ssidListState.value = ssidListState.value + detected
                    }
                } else {
                    permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) {
                Text("Add current Wi-Fi", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.primary)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { haptic.confirm(); onSave(localUrl, remoteUrl, ssids, go2rtcUrl) },
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("Save", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        val detectedLine = when {
            !hasLocationPermission() -> "Detected SSID: — (location permission denied)"
            !uiState.locationServicesEnabled -> "Detected SSID: — (Location Services off)"
            uiState.currentSsid == null -> "Detected SSID: — (Wi-Fi not the active network)"
            else -> "Detected SSID: \"${uiState.currentSsid}\""
        }
        val statusText = when {
            uiState.homeSsids.isEmpty() || uiState.localUrl.isBlank() ->
                "Add a local URL and at least one home SSID to enable automatic switching."
            uiState.onHomeWifi -> "Routing: local URL (matched home Wi-Fi)"
            else -> "Routing: remote URL (no match against saved SSIDs)"
        }
        Text(detectedLine, fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
        Text(statusText, fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
    }
}

// ---------------------------------------------------------------------------
// Profile subpage
// ---------------------------------------------------------------------------

@Composable
private fun ProfileSettingsScreen(
    name: String,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = "Profile",
        subtitle = "Used in the dashboard greeting",
        onBack = onBack,
    ) {
        ProfileCard(
            name = name,
            onNameChange = onNameChange,
            modifier = Modifier.padding(horizontal = Spacing.ml),
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers (HA card, profile card, open-in-HA, URL field) — unchanged behavior
// ---------------------------------------------------------------------------

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

@Composable
private fun HaConnectionCard(
    uiState: SettingsUiState,
    onUrlChange: (String) -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTokenChange: (String) -> Unit,
    onToggleTokenInput: () -> Unit,
    onConnectWithToken: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val haptic = rememberAppHaptics()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isLoggedIn) {
            val statusColor = when (uiState.connectionStatus) {
                WsConnectionStatus.READY -> custom.mint
                WsConnectionStatus.CONNECTING -> custom.warn
                WsConnectionStatus.ERROR, WsConnectionStatus.AUTH_INVALID, WsConnectionStatus.IP_BANNED -> cs.error
                WsConnectionStatus.DISCONNECTED -> cs.onSurfaceVariant
            }
            val statusLabel = when (uiState.connectionStatus) {
                WsConnectionStatus.READY -> "Connected"
                WsConnectionStatus.CONNECTING -> "Connecting…"
                WsConnectionStatus.ERROR -> "Cannot reach server"
                WsConnectionStatus.DISCONNECTED -> "Disconnected"
                WsConnectionStatus.AUTH_INVALID -> "Session expired"
                WsConnectionStatus.IP_BANNED -> "IP blocked by Home Assistant"
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(modifier = Modifier.size(8.dp).background(statusColor, CircleShape))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(statusLabel, fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
                        Text(uiState.haUrl, fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    TextButton(onClick = { haptic.confirm(); onDisconnect() }) {
                        Text("Disconnect", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = cs.error)
                    }
                }
                when (uiState.connectionStatus) {
                    WsConnectionStatus.ERROR -> Text(
                        text = "Check that your phone is on the same WiFi network as Home Assistant. Retrying automatically…",
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    WsConnectionStatus.AUTH_INVALID -> Text(
                        // Show HA's own reason when we have it: "Invalid access token or
                        // password" means the token itself; a local-only / inactive-user
                        // message means the token is fine but the *user* is restricted.
                        text = uiState.authErrorDetail
                            ?.let { "Home Assistant rejected the connection: “$it”. If this mentions the user or network, check the user's “local network only” setting in HA (Settings → People). Otherwise disconnect and sign in with a fresh token." }
                            ?: "Home Assistant rejected the saved access token. Disconnect and sign in again to issue a fresh token.",
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    WsConnectionStatus.IP_BANNED -> Text(
                        text = "Home Assistant has banned this device's IP after repeated failed logins. To restore access, " +
                            "open your HA config directory and remove this device's entry from ip_bans.yaml (or restart Home Assistant), " +
                            "then reconnect. The app will not retry on its own — retries renew the ban timer.",
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    else -> Unit
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(haIconFor("wifi"), contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Text("Home Assistant", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
            }

            UrlField(
                value = uiState.enteredUrl,
                onValueChange = onUrlChange,
                placeholder = "http://homeassistant.local:8123",
                onGo = { if (uiState.enteredUrl.isNotBlank() && !uiState.showTokenInput) onConnect(uiState.enteredUrl) },
            )

            if (!uiState.showTokenInput) {
                Button(
                    onClick = { haptic.confirm(); if (uiState.enteredUrl.isNotBlank()) onConnect(uiState.enteredUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                ) {
                    Icon(haIconFor("home"), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Login with Home Assistant", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { haptic.tick(); onToggleTokenInput() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Use long-lived access token instead",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "Paste your long-lived access token (HA → Profile → Security → Long-lived access tokens)",
                    fontFamily = MontserratFamily,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    lineHeight = 16.sp,
                )
                UrlField(
                    value = uiState.enteredToken,
                    onValueChange = onTokenChange,
                    placeholder = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                    onGo = {
                        if (uiState.enteredUrl.isNotBlank() && uiState.enteredToken.isNotBlank())
                            onConnectWithToken(uiState.enteredUrl, uiState.enteredToken)
                    },
                )
                Button(
                    onClick = {
                        haptic.confirm()
                        if (uiState.enteredUrl.isNotBlank() && uiState.enteredToken.isNotBlank())
                            onConnectWithToken(uiState.enteredUrl, uiState.enteredToken)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                ) {
                    Text("Connect with Token", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = { haptic.tick(); onToggleTokenInput() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use OAuth login instead", fontFamily = MontserratFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

// Stock M3 text field (same component as the Energy setup sheet) — theme-aware
// placeholder/container colors come from the component, not hand-rolled alphas.
@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onGo: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 13.sp),
        placeholder = { Text(placeholder, fontFamily = MontserratFamily, fontSize = 13.sp) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ProfileCard(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(cs.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = name.firstOrNull()?.toString()?.uppercase() ?: "A",
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 28.sp,
                    color = cs.onPrimary,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "GREETING NAME",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                )
                NameField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = "Your name",
                )
            }
        }
    }
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = MontserratFamily, fontSize = 13.sp),
        placeholder = { Text(placeholder, fontFamily = MontserratFamily, fontSize = 13.sp) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

