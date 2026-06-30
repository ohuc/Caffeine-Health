package com.uc.homehealth.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uc.homehealth.data.HaUpdate
import com.uc.homehealth.data.UpdateCategory
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily

// Confirmation sheet for "Update all". Lists every update that will be installed and
// offers a single "create backup first" toggle (default on when any item supports + is
// a heavy system/add-on update). Hosted locally inside the Updates screen.
@Composable
fun UpdateAllConfirmSheet(
    visible: Boolean,
    pending: List<HaUpdate>,
    onConfirm: (backup: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()

    val anyBackupable = pending.any { it.supportsBackup && it.category in BackupCategories }
    var backup by remember(pending) { mutableStateOf(anyBackupable) }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Update all",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 30.sp,
                color = cs.onSurface,
            )
            Text(
                text = if (pending.size == 1) "1 update will be installed."
                else "${pending.size} updates will be installed.",
                fontFamily = MontserratFamily,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pending.forEach { u -> PendingRow(u) }
            }

            if (anyBackupable) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(haIconFor("data"), contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Create a backup first", fontFamily = MontserratFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
                        Text("Snapshots system & add-on updates before installing", fontFamily = MontserratFamily, fontSize = 11.sp, color = cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(10.dp))
                    PillToggle(isOn = backup, onToggle = { backup = it }, color = cs.primary, ink = cs.onPrimary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tap(onClick = { haptic.tick(); onDismiss() }, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.surfaceContainerHigh)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Cancel", fontFamily = MontserratFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = cs.onSurface)
                    }
                }
                Tap(
                    onClick = { haptic.confirm(); onConfirm(backup); onDismiss() },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cs.primary)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Update all (${pending.size})",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = cs.onPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingRow(u: HaUpdate) {
    val cs = MaterialTheme.colorScheme
    val accent = categoryAccent(u.category)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (u.entityPictureUrl != null) {
                AsyncImage(model = u.entityPictureUrl, contentDescription = null, modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Icon(haIconFor(categoryIconKey(u.category)), contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = u.title,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = u.latestVersion ?: "—",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = accent,
        )
    }
}

private val BackupCategories = setOf(UpdateCategory.SYSTEM, UpdateCategory.ADDON)
