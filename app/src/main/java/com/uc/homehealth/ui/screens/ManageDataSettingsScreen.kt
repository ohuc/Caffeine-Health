package com.uc.homehealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.components.SettingsPageScaffold
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing

/**
 * Settings → Manage Data. For now this hosts a single destructive action: wiping the
 * on-device activity history. The action is irreversible, so it goes behind a
 * confirmation dialog (Material 3 AlertDialog) before [onClearActivity] fires.
 */
@Composable
fun ManageDataSettingsScreen(
    onClearActivity: () -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    var showConfirm by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }

    SettingsPageScaffold(
        title = "Manage Data",
        subtitle = "Data stored on this device",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.ml)
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        imageVector = haIconFor("data"),
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Activity history",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = cs.onSurface,
                    )
                    Text(
                        text = "Actions you take in this app are logged on-device to power the Activity tab.",
                        fontFamily = MontserratFamily,
                        fontSize = 11.sp,
                        color = cs.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Button(
                onClick = { haptic.reject(); showConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.errorContainer,
                    contentColor = cs.onErrorContainer,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = haIconFor("trash"),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delete all activity data",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Removes activity data stored on device — NOT from Home Assistant",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        color = cs.onErrorContainer.copy(alpha = 0.8f),
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            if (cleared) {
                Text(
                    text = "Activity history cleared.",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = cs.primary,
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            // The app's theme maps extraLarge (AlertDialog's default shape) to a 999.dp
            // pill, which turns the dialog into a blob — pin a sane dialog radius instead.
            shape = RoundedCornerShape(28.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.confirm()
                        onClearActivity()
                        cleared = true
                        showConfirm = false
                    },
                ) {
                    Text(
                        text = "Delete all",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        color = cs.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("Cancel", fontFamily = MontserratFamily)
                }
            },
            title = {
                Text(
                    text = "Delete all activity data?",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This permanently removes the activity history stored on this " +
                        "device. It does not affect Home Assistant.",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
        )
    }
}
