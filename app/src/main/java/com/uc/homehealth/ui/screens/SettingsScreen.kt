package com.uc.homehealth.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
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
import com.uc.homehealth.R
import com.uc.homehealth.data.WsConnectionStatus
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.SettingsUiState
import com.uc.homehealth.ui.viewmodel.SettingsViewModel

private const val HA_COMPANION_PACKAGE = "io.homeassistant.companion.android"
private const val HA_COMPANION_MINIMAL_PACKAGE = "io.homeassistant.companion.android.minimal"

private val HaBlue = Color(0xFF18BCF2)

private enum class SettingsDestination : NavKey {
    Main,
    HomeAssistant,
    Profile,
    Appearance,
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
    onOpenGlanceSheet: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
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
        transitionSpec = {
            slideInHorizontally(animationSpec = tween(280)) { it } togetherWith
                (slideOutHorizontally(animationSpec = tween(280)) { -it / 4 } +
                    fadeOut(animationSpec = tween(220)))
        },
        popTransitionSpec = {
            (slideInHorizontally(animationSpec = tween(280)) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = tween(280)) { it }
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(animationSpec = tween(280)) { -it / 4 } +
                fadeIn(animationSpec = tween(220))) togetherWith
                slideOutHorizontally(animationSpec = tween(280)) { it }
        },
        entryProvider = entryProvider {
            entry<SettingsDestination> { destination ->
                when (destination) {
                    SettingsDestination.Main -> SettingsListScreen(
                        uiState = uiState,
                        onOpenHomeAssistant = { backStack.add(SettingsDestination.HomeAssistant) },
                        onOpenProfile = { backStack.add(SettingsDestination.Profile) },
                        onOpenGlanceSheet = onOpenGlanceSheet,
                        onOpenAppearance = { backStack.add(SettingsDestination.Appearance) },
                        onRedoOnboarding = viewModel::redoOnboarding,
                    )
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
    onOpenProfile: () -> Unit,
    onOpenGlanceSheet: () -> Unit,
    onOpenAppearance: () -> Unit,
    onRedoOnboarding: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

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
            title = "Profile",
            summary = "Greeting name · ${uiState.userName.ifBlank { "Not set" }}",
            icon = Icons.Outlined.Person,
            onClick = onOpenProfile,
        ),
        SettingsRow(
            title = "At a glance",
            summary = "Configure dashboard greeting & entity slots",
            icon = haIconFor("sparkle"),
            onClick = onOpenGlanceSheet,
        ),
        SettingsRow(
            title = "Appearance",
            summary = "Light, dark, or system theme",
            icon = haIconFor("palette"),
            onClick = onOpenAppearance,
        ),
    )

    SettingsPageScaffold(title = "Settings", subtitle = subtitle) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            categories.forEachIndexed { index, row ->
                SegmentedListItem(
                    onClick = row.onClick,
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
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = cs.onSurface,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = row.summary,
                            fontFamily = InterFamily,
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
            haUrl = uiState.haUrl,
            modifier = Modifier.padding(horizontal = Spacing.ml),
            onClick = { launchHomeAssistant(context, uiState.haUrl) },
        )

        Spacer(Modifier.size(12.dp))

        TextButton(
            onClick = onRedoOnboarding,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ml),
        ) {
            Text(
                text = "Redo onboarding",
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
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
    onSaveRouting: (String, String, List<String>) -> Unit,
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
    onSave: (String, String, List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val localState = androidx.compose.runtime.remember(uiState.localUrl) {
        androidx.compose.runtime.mutableStateOf(uiState.localUrl)
    }
    val remoteState = androidx.compose.runtime.remember(uiState.remoteUrl) {
        androidx.compose.runtime.mutableStateOf(uiState.remoteUrl)
    }
    val ssidListState = androidx.compose.runtime.remember(uiState.homeSsids) {
        androidx.compose.runtime.mutableStateOf(uiState.homeSsids)
    }
    val ssidInputState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val localUrl = localState.value
    val remoteUrl = remoteState.value
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
            Text("Network routing", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
        }
        Text(
            text = "On any listed home Wi-Fi the app uses the local URL; everywhere else it uses the remote URL.",
            fontFamily = InterFamily,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 15.sp,
        )

        Text("Remote URL", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = cs.onSurface)
        UrlField(value = remoteUrl, onValueChange = { remoteState.value = it }, placeholder = "https://ha.example.com", onGo = {})

        Text("Local URL", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = cs.onSurface)
        UrlField(value = localUrl, onValueChange = { localState.value = it }, placeholder = "http://homeassistant.local:8123", onGo = {})

        Text("Home Wi-Fi SSIDs", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = cs.onSurface)
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
                onClick = { addTyped() },
                enabled = ssidInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("Add", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = {
                if (hasLocationPermission()) {
                    val detected = onDetectCurrentSsid()
                    if (!detected.isNullOrBlank() && detected !in ssidListState.value) {
                        ssidListState.value = ssidListState.value + detected
                    }
                } else {
                    permissionLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }) {
                Text("Add current Wi-Fi", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = cs.primary)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onSave(localUrl, remoteUrl, ssids) },
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text("Save", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
        Text(detectedLine, fontFamily = InterFamily, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
        Text(statusText, fontFamily = InterFamily, fontSize = 11.sp, color = cs.onSurfaceVariant, lineHeight = 15.sp)
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

private fun launchHomeAssistant(context: Context, haUrl: String) {
    val pm = context.packageManager
    val launchIntent = pm.getLaunchIntentForPackage(HA_COMPANION_PACKAGE)
        ?: pm.getLaunchIntentForPackage(HA_COMPANION_MINIMAL_PACKAGE)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return
    }
    val webTarget = haUrl.takeIf { it.isNotBlank() } ?: "https://www.home-assistant.io/"
    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webTarget)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(viewIntent) }
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.isLoggedIn) {
            val statusColor = when (uiState.connectionStatus) {
                WsConnectionStatus.READY -> Color(0xFF4CAF50)
                WsConnectionStatus.CONNECTING -> Color(0xFFFFC107)
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
                        Text(statusLabel, fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
                        Text(uiState.haUrl, fontFamily = InterFamily, fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.error)
                    }
                }
                when (uiState.connectionStatus) {
                    WsConnectionStatus.ERROR -> Text(
                        text = "Check that your phone is on the same WiFi network as Home Assistant. Retrying automatically…",
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    WsConnectionStatus.AUTH_INVALID -> Text(
                        text = "Home Assistant rejected the saved access token. Disconnect and sign in again to issue a fresh token.",
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                    )
                    WsConnectionStatus.IP_BANNED -> Text(
                        text = "Home Assistant has banned this device's IP after repeated failed logins. To restore access, " +
                            "open your HA config directory and remove this device's entry from ip_bans.yaml (or restart Home Assistant), " +
                            "then reconnect. The app will not retry on its own — retries renew the ban timer.",
                        fontFamily = InterFamily,
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
                Text("Home Assistant", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
            }

            UrlField(
                value = uiState.enteredUrl,
                onValueChange = onUrlChange,
                placeholder = "http://homeassistant.local:8123",
                onGo = { if (uiState.enteredUrl.isNotBlank() && !uiState.showTokenInput) onConnect(uiState.enteredUrl) },
            )

            if (!uiState.showTokenInput) {
                Button(
                    onClick = { if (uiState.enteredUrl.isNotBlank()) onConnect(uiState.enteredUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(haIconFor("home"), contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Login with Home Assistant", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = onToggleTokenInput, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Use long-lived access token instead",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = "Paste your long-lived access token (HA → Profile → Security → Long-lived access tokens)",
                    fontFamily = InterFamily,
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
                        if (uiState.enteredUrl.isNotBlank() && uiState.enteredToken.isNotBlank())
                            onConnectWithToken(uiState.enteredUrl, uiState.enteredToken)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Connect with Token", fontFamily = InterFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                TextButton(onClick = onToggleTokenInput, modifier = Modifier.fillMaxWidth()) {
                    Text("Use OAuth login instead", fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun UrlField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onGo: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurfaceVariant.copy(alpha = 0.5f))
                }
                inner()
            }
        },
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
            .clip(MaterialTheme.shapes.large)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .background(cs.primary.copy(alpha = 0.20f), CircleShape)
                .blur(20.dp),
        )
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
                    fontFamily = InterFamily,
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
    val cs = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurface),
        cursorBrush = SolidColor(cs.primary),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cs.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, fontFamily = InterFamily, fontSize = 13.sp, color = cs.onSurfaceVariant.copy(alpha = 0.5f))
                }
                inner()
            }
        },
    )
}

@Composable
private fun OpenInHomeAssistantButton(
    haUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = HaBlue,
            contentColor = Color.White,
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_assistant),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Open in Home Assistant",
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
            )
            Text(
                text = if (haUrl.isBlank())
                    "Devices, automations, energy & security"
                else
                    "Devices, automations, energy & security · $haUrl",
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
