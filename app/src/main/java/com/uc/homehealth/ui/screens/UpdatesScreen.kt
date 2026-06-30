package com.uc.homehealth.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.uc.homehealth.data.HaUpdate
import com.uc.homehealth.data.UpdateCategory
import com.uc.homehealth.ui.components.BackButton
import com.uc.homehealth.ui.components.UpdateAllConfirmSheet
import com.uc.homehealth.ui.components.UpdateCard
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.components.rememberTileBlobShape
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.UpdatesUiState
import com.uc.homehealth.ui.viewmodel.UpdatesViewModel

@Composable
fun UpdatesScreen(
    onBack: () -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
    onSheetVisibleChange: (Boolean) -> Unit = {},
    viewModel: UpdatesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Ask for notification permission so the background-install progress notification is
    // visible on Android 13+. The service runs either way; this just makes it show.
    val context = LocalContext.current
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    UpdatesScreenContent(
        uiState = uiState,
        onInstall = viewModel::install,
        onSkip = viewModel::skip,
        onClearSkipped = viewModel::clearSkipped,
        onUpdateAll = viewModel::updateAll,
        onOpenReleaseUrl = onOpenReleaseUrl,
        onSheetVisibleChange = onSheetVisibleChange,
        onBack = onBack,
    )
}

@Composable
internal fun UpdatesScreenContent(
    uiState: UpdatesUiState,
    onInstall: (entityId: String, backup: Boolean) -> Unit,
    onSkip: (String) -> Unit,
    onClearSkipped: (String) -> Unit,
    onUpdateAll: (backup: Boolean) -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
    onSheetVisibleChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    val available = remember(uiState.updates) { uiState.updates.filterNot { it.isSkipped } }
    val skipped = remember(uiState.updates) { uiState.updates.filter { it.isSkipped } }
    var skippedExpanded by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }

    // Tell the host to hide the top-level bottom nav while the confirm sheet is up. Restore
    // it only after the sheet's slide-out finishes (~380ms) so the nav doesn't flicker back
    // over the closing panel. Always restore when this screen leaves the composition.
    LaunchedEffect(showConfirm) {
        if (showConfirm) onSheetVisibleChange(true)
        else { delay(420); onSheetVisibleChange(false) }
    }
    DisposableEffect(Unit) { onDispose { onSheetVisibleChange(false) } }

    val subtitle = when {
        !uiState.loaded -> "Checking for updates…"
        available.isEmpty() -> "Everything is up to date"
        available.size == 1 -> "1 update available"
        else -> "${available.size} updates available"
    }

    Box(modifier = Modifier.fillMaxSize().background(cs.background)) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            // Back row + title — fixed above the scrolling list.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.ml, end = Spacing.xl, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BackButton(onClick = onBack)
                Text(
                    text = "Settings",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 6.dp, bottom = 14.dp)) {
                Text(
                    text = "Updates",
                    fontFamily = InstrumentSerifFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = 40.sp,
                    color = cs.onBackground,
                )
                Text(
                    text = subtitle,
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (available.isEmpty() && skipped.isEmpty()) {
                if (uiState.loaded) EmptyUpdatesState()
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(start = Spacing.ml, end = Spacing.ml, top = 4.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (available.isNotEmpty()) {
                    item(key = "update-all") {
                        UpdateAllButton(
                            count = available.size,
                            modifier = Modifier.animateItem(),
                            onClick = { showConfirm = true },
                        )
                    }
                }

                UpdateCategory.entries.forEach { category ->
                    val group = available.filter { it.category == category }
                    if (group.isNotEmpty()) {
                        item(key = "hdr-${category.name}") {
                            SectionHeader(category.label, Modifier.animateItem())
                        }
                        items(group, key = { it.entityId }) { update ->
                            UpdateCard(
                                update = update,
                                onInstall = { backup -> onInstall(update.entityId, backup) },
                                onSkip = { onSkip(update.entityId) },
                                onClearSkipped = { onClearSkipped(update.entityId) },
                                onOpenReleaseUrl = onOpenReleaseUrl,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }

                if (skipped.isNotEmpty()) {
                    item(key = "hdr-skipped") {
                        SkippedHeader(
                            count = skipped.size,
                            expanded = skippedExpanded,
                            modifier = Modifier.animateItem(),
                            onToggle = { skippedExpanded = !skippedExpanded },
                        )
                    }
                    if (skippedExpanded) {
                        items(skipped, key = { it.entityId }) { update ->
                            UpdateCard(
                                update = update,
                                onInstall = { backup -> onInstall(update.entityId, backup) },
                                onSkip = { onSkip(update.entityId) },
                                onClearSkipped = { onClearSkipped(update.entityId) },
                                onOpenReleaseUrl = onOpenReleaseUrl,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }

        UpdateAllConfirmSheet(
            visible = showConfirm,
            pending = available,
            onConfirm = onUpdateAll,
            onDismiss = { showConfirm = false },
        )
    }
}

@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label,
        fontFamily = InstrumentSerifFamily,
        fontStyle = FontStyle.Italic,
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun UpdateAllButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    com.uc.homehealth.ui.components.Tap(
        onClick = { haptic.confirm(); onClick() },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cs.primary)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(haIconFor("download"), contentDescription = null, tint = cs.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Update all ($count)",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = cs.onPrimary,
            )
        }
    }
}

@Composable
private fun SkippedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, tween(220), label = "skipped_chevron")
    com.uc.homehealth.ui.components.Tap(onClick = onToggle, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Skipped",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 22.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$count",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                haIconFor("chevron-right"),
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(16.dp).rotate(rotation),
            )
        }
    }
}

@Composable
private fun EmptyUpdatesState() {
    val cs = MaterialTheme.colorScheme
    val mint = MaterialTheme.customColors.mint
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(rememberTileBlobShape()).background(mint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(haIconFor("check"), contentDescription = null, tint = mint, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = "Everything's up to date",
            fontFamily = InstrumentSerifFamily,
            fontStyle = FontStyle.Italic,
            fontSize = 26.sp,
            color = cs.onBackground,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = "No updates available right now.",
            fontFamily = MontserratFamily,
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
        )
    }
}
