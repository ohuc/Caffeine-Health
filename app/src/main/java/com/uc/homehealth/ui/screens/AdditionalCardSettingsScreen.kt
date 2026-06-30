package com.uc.homehealth.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.ui.components.SettingToggleCard
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.CardSettingsViewModel

@Composable
fun AdditionalCardSettingsScreen(
    onBack: () -> Unit,
    viewModel: CardSettingsViewModel = hiltViewModel(),
) {
    val roomWarningsEnabled by viewModel.roomWarningsEnabled.collectAsStateWithLifecycle()
    val smartGlanceEnabled by viewModel.smartGlanceEnabled.collectAsStateWithLifecycle()
    AdditionalCardSettingsScreenContent(
        roomWarningsEnabled = roomWarningsEnabled,
        onRoomWarningsChange = viewModel::setRoomWarningsEnabled,
        smartGlanceEnabled = smartGlanceEnabled,
        onSmartGlanceChange = viewModel::setSmartGlanceEnabled,
        onBack = onBack,
    )
}

@Composable
internal fun AdditionalCardSettingsScreenContent(
    roomWarningsEnabled: Boolean,
    onRoomWarningsChange: (Boolean) -> Unit,
    smartGlanceEnabled: Boolean,
    onSmartGlanceChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(
        title = "Card Settings",
        subtitle = "Extra options for dashboard cards",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingToggleCard(
                iconKey = "sparkle",
                title = "Smart at-a-glance cards",
                description = "Automatically surface alerts, live activity, and home insights in “At a glance” alongside your pinned tiles.",
                checked = smartGlanceEnabled,
                onCheckedChange = onSmartGlanceChange,
            )
            SettingToggleCard(
                iconKey = "bell",
                title = "Room warnings",
                description = "Show the alert badge on room cards (and the “needs attention” banner inside a room) when a device is unreachable.",
                checked = roomWarningsEnabled,
                onCheckedChange = onRoomWarningsChange,
            )
            // The old "Energy tab" toggle moved to Settings → Navigation Bar, where the
            // whole bottom bar is editable (hide/show/reorder any tab).
        }
    }
}
