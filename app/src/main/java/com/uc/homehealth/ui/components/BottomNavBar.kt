package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import com.uc.homehealth.ui.components.rememberAppHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class NavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

// Catalog of every tab the bar can host, in default order. The user's actual bar is an
// ordered subset of these keys (UserPreferences.navTabKeys); Settings is mandatory there.
val navDestinationCatalog = listOf(
    NavDestination("dashboard", "Home",     Icons.Outlined.Home,         Icons.Filled.Home),
    NavDestination("activity",  "Activity", Icons.Outlined.Timeline,     Icons.Filled.Timeline),
    NavDestination("energy",    "Energy",   Icons.Outlined.ElectricBolt, Icons.Filled.ElectricBolt),
    NavDestination("pulse",     "Pulse",    Icons.Outlined.MonitorHeart, Icons.Filled.MonitorHeart),
    NavDestination("music",     "Music",    Icons.Outlined.MusicNote,    Icons.Filled.MusicNote),
    NavDestination("settings",  "Settings", Icons.Outlined.Settings,     Icons.Filled.Settings),
)

/** Resolve the user's ordered tab keys to destinations, dropping anything unknown. */
fun navDestinationsFor(keys: List<String>): List<NavDestination> =
    keys.mapNotNull { key -> navDestinationCatalog.find { it.route == key } }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    tabs: List<NavDestination> = navDestinationCatalog,
    // False renders the bar inline (no window insets / bottom margin) — used by the
    // nav-bar editor's preview card.
    floating: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    val destinations = tabs
    // Keyed on the tab set: stale bounds from a removed/reordered set would misplace the pill.
    val buttonBounds = remember(tabs) { mutableStateMapOf<Int, Rect>() }
    val currentIndex = destinations.indexOfFirst { it.route == currentRoute }
    val targetRect = buttonBounds[currentIndex]
    val button0Rect = buttonBounds[0]
    val pillRelativeX = (targetRect?.left ?: 0f) - (button0Rect?.left ?: 0f)

    val pillAnimatedX by animateFloatAsState(
        targetValue = pillRelativeX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pillX",
    )
    val pillAnimatedWidth by animateFloatAsState(
        targetValue = targetRect?.width ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pillWidth",
    )
    val pillColor = cs.primary

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.then(
            if (floating) Modifier.navigationBarsPadding().padding(bottom = 16.dp) else Modifier
        ),
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(
            toolbarContainerColor = cs.primaryContainer,
            toolbarContentColor = cs.onPrimaryContainer,
        ),
    ) {
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            destinations.forEachIndexed { index, dest ->
                val isSelected = currentRoute == dest.route

                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { haptic.navigation(); onNavigate(dest.route) },
                    modifier = Modifier
                        .height(56.dp)
                        .onGloballyPositioned { coords ->
                            buttonBounds[index] = coords.boundsInParent()
                        }
                        .then(
                            if (index == 0) {
                                Modifier.drawWithContent {
                                    if (pillAnimatedWidth > 0f) {
                                        drawRoundRect(
                                            color = pillColor,
                                            topLeft = Offset(pillAnimatedX, 0f),
                                            size = Size(pillAnimatedWidth, size.height),
                                            cornerRadius = CornerRadius(size.height / 2f),
                                        )
                                    }
                                    drawContent()
                                }
                            } else Modifier
                        ),
                    colors = ToggleButtonDefaults.toggleButtonColors(
                        checkedContainerColor = Color.Transparent,
                        checkedContentColor = cs.onPrimary,
                        containerColor = Color.Transparent,
                        contentColor = cs.onPrimaryContainer,
                    ),
                    shapes = ToggleButtonDefaults.shapes(
                        shape = CircleShape,
                        pressedShape = CircleShape,
                        checkedShape = CircleShape,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isSelected) dest.selectedIcon else dest.icon,
                            contentDescription = dest.label,
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = expandHorizontally(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                            exit = shrinkHorizontally(
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            ),
                        ) {
                            Text(
                                text = dest.label,
                                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
