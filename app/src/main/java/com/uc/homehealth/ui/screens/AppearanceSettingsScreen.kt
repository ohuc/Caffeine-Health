package com.uc.homehealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.ThemeMode
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.viewmodel.ThemeViewModel

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: ThemeViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    AppearanceSettingsScreenContent(
        themeMode = themeMode,
        onThemeModeChange = viewModel::setThemeMode,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppearanceSettingsScreenContent(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    val modes = listOf(
        ThemeModeOption(ThemeMode.LIGHT, "Light", Icons.Outlined.LightMode),
        ThemeModeOption(ThemeMode.DARK, "Dark", Icons.Outlined.DarkMode),
        ThemeModeOption(ThemeMode.SYSTEM, "Auto", Icons.Outlined.BrightnessAuto),
    )

    SettingsPageScaffold(
        title = "Appearance",
        subtitle = "Theme & color palette",
        onBack = onBack,
    ) {
        // Theme card
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.ml)
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.primary.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Theme",
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = cs.onSurface,
                    )
                    Text(
                        text = "Choose how HomeHealth looks",
                        fontFamily = InterFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                modes.forEachIndexed { index, option ->
                    ToggleButton(
                        checked = themeMode == option.mode,
                        onCheckedChange = { checked ->
                            if (checked && themeMode != option.mode) {
                                haptic.toggle(true)
                                onThemeModeChange(option.mode)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = option.label,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

private data class ThemeModeOption(
    val mode: ThemeMode,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)
