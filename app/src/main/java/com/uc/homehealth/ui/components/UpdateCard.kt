package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uc.homehealth.data.HaUpdate
import com.uc.homehealth.data.UpdateCategory
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors

// One expressive update card: logo, title, version delta, install→progress morph,
// expandable release notes, optional backup toggle, and skip/undo. Stateless except
// for local expanded/backup UI flags keyed to the entity id. Removal/move animations
// are owned by the parent LazyColumn's animateItem().
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateCard(
    update: HaUpdate,
    onInstall: (backup: Boolean) -> Unit,
    onSkip: () -> Unit,
    onClearSkipped: () -> Unit,
    onOpenReleaseUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = categoryAccent(update.category)
    val haptic = rememberAppHaptics()

    var expanded by remember(update.entityId) { mutableStateOf(false) }
    var backup by remember(update.entityId) {
        mutableStateOf(update.supportsBackup && update.category in BackupOnByDefault)
    }
    val hasNotes = !update.releaseSummary.isNullOrBlank() || !update.releaseUrl.isNullOrBlank()
    val skipped = update.isSkipped

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpdateLogo(update.entityPictureUrl, update.category, accent)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = update.title,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = cs.onSurface,
                )
                VersionDelta(update.installedVersion, update.latestVersion, accent)
                if (update.errorMessage != null && !update.inProgress) {
                    Text(
                        text = "Couldn't update — ${update.errorMessage}",
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = cs.error,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            // Install button morphs in place into the progress indicator; once HA reports
            // completion the card is dropped upstream and animateItem() slides it out.
            AnimatedContent(
                targetState = update.inProgress,
                transitionSpec = {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.85f)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(targetScale = 0.85f)) using
                        SizeTransform(clip = false)
                },
                label = "install_${update.entityId}",
            ) { installing ->
                when {
                    installing -> InstallProgress(update.updatePercentage, accent)
                    skipped -> UndoButton(onClick = { haptic.confirm(); onClearSkipped() })
                    else -> UpdateButton(accent) { haptic.confirm(); onInstall(backup) }
                }
            }
        }

        // Backup toggle — only for entities that support it, and only while idle.
        AnimatedVisibility(
            visible = update.supportsBackup && !update.inProgress && !skipped,
            enter = fadeIn(tween(200)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(160)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(haIconFor("data"), contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Back up before updating",
                    fontFamily = MontserratFamily,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                PillToggle(isOn = backup, onToggle = { backup = it }, color = accent, ink = cs.surface)
            }
        }

        // Footer: release-notes disclosure + skip / skipped status.
        if (hasNotes || !skipped) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasNotes) {
                    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, tween(220), label = "notes_chevron")
                    FooterAction(
                        label = "Release notes",
                        leadingIconKey = null,
                        color = cs.onSurfaceVariant,
                        onClick = { haptic.tick(); expanded = !expanded },
                        trailing = {
                            Icon(
                                haIconFor("chevron-right"),
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(14.dp).rotate(chevronRotation),
                            )
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                if (skipped) {
                    Text(
                        text = "Skipped",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                    )
                } else if (!update.inProgress) {
                    FooterAction(
                        label = "Skip",
                        leadingIconKey = null,
                        color = cs.onSurfaceVariant,
                        onClick = { haptic.confirm(); onSkip() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(220)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
            exit = shrinkVertically(tween(260, easing = FastOutSlowInEasing)) + fadeOut(tween(200)),
        ) {
            ReleaseNotes(update.releaseSummary, update.releaseUrl, accent, onOpenReleaseUrl)
        }
    }
}

@Composable
private fun UpdateLogo(url: String?, category: UpdateCategory, accent: Color) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Icon(
                imageVector = haIconFor(categoryIconKey(category)),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun VersionDelta(installed: String?, latest: String?, accent: Color) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = installed ?: "—",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
        )
        Icon(haIconFor("chevron-right"), contentDescription = "to", tint = cs.onSurfaceVariant, modifier = Modifier.size(12.dp))
        Text(
            text = latest ?: "—",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = accent,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InstallProgress(percentage: Int?, accent: Color) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (percentage != null) {
            val animated by animateFloatAsState(
                targetValue = (percentage / 100f).coerceIn(0f, 1f),
                animationSpec = tween(300),
                label = "install_pct",
            )
            ContainedLoadingIndicator(progress = { animated }, modifier = Modifier.size(40.dp))
            RollingNumberText(
                text = "$percentage%",
                style = TextStyle(
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 20.sp,
                    color = accent,
                ),
                labelPrefix = "install_pct_num",
            )
        } else {
            ContainedLoadingIndicator(modifier = Modifier.size(40.dp))
            Text("Installing…", fontFamily = MontserratFamily, fontSize = 12.sp, color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun UpdateButton(accent: Color, onClick: () -> Unit) {
    val ink = if (accent.luminanceIsLight()) Color(0xFF15141A) else Color.White
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(accent)
                .padding(horizontal = 18.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Update", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ink)
        }
    }
}

@Composable
private fun UndoButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(cs.surfaceVariant)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Undo", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = cs.onSurface)
        }
    }
}

@Composable
private fun FooterAction(
    label: String,
    leadingIconKey: String?,
    color: Color,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Tap(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            if (leadingIconKey != null) {
                Icon(haIconFor(leadingIconKey), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            }
            Text(label, fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color)
            trailing?.invoke()
        }
    }
}

@Composable
private fun ReleaseNotes(summary: String?, url: String?, accent: Color, onOpenReleaseUrl: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceVariant.copy(alpha = 0.5f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                fontFamily = MontserratFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = cs.onSurface,
            )
        } else {
            Text(
                text = "No release summary provided.",
                fontFamily = MontserratFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
        }
        if (!url.isNullOrBlank()) {
            FooterAction(
                label = "Open full release notes",
                leadingIconKey = "open",
                color = accent,
                onClick = { haptic.navigation(); onOpenReleaseUrl(url) },
            )
        }
    }
}

// Categories whose updates default the "back up first" toggle on — heavier, harder to roll back.
private val BackupOnByDefault = setOf(UpdateCategory.SYSTEM, UpdateCategory.ADDON)

internal fun categoryIconKey(category: UpdateCategory): String = when (category) {
    UpdateCategory.SYSTEM -> "settings"
    UpdateCategory.HACS -> "sparkle"
    UpdateCategory.ADDON -> "extension"
    UpdateCategory.FIRMWARE -> "chip"
}

@Composable
internal fun categoryAccent(category: UpdateCategory): Color {
    val c = MaterialTheme.customColors
    return when (category) {
        UpdateCategory.SYSTEM -> c.sky
        UpdateCategory.HACS -> c.lavender
        UpdateCategory.ADDON -> c.mint
        UpdateCategory.FIRMWARE -> c.sand
    }
}

// Cheap luminance check to pick black/white ink on a filled accent without importing
// the graphics luminance extension everywhere.
private fun Color.luminanceIsLight(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f
