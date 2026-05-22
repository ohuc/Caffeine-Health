package com.uc.homehealth.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.data.HaRoom
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors

@Composable
fun RoomTile(
    room: HaRoom,
    height: Dp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val accentColor = try {
        Color(AndroidColor.parseColor(room.colorHex))
    } catch (e: IllegalArgumentException) {
        cs.primary
    }
    val inkColor = try {
        Color(AndroidColor.parseColor(room.inkHex))
    } catch (e: IllegalArgumentException) {
        cs.onPrimary
    }
    val haptic = rememberAppHaptics()

    Tap(onClick = { haptic.navigation(); onTap() }, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.large)
                .clip(MaterialTheme.shapes.large)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // ── Top: name + subtitle + icon ──────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.name,
                            fontFamily = InterFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp,
                            lineHeight = 18.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = if (room.activeCount > 0)
                                "${room.activeCount} on · ${room.deviceCount} devices"
                            else
                                "All off · ${room.deviceCount} devices",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Icon circle with optional alert dot
                    Box {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(accentColor, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = haIconFor(room.icon),
                                contentDescription = room.name,
                                tint = inkColor,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        if (room.hasAlert) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(custom.coral, CircleShape)
                                    .border(2.dp, cs.surface, CircleShape)
                                    .align(Alignment.TopEnd),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "!",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = InterFamily,
                                )
                            }
                        }
                    }
                }

                // ── Bottom: temperature + humidity ───────────────────────────
                Row(
                    verticalAlignment = Alignment.Bottom,
                ) {
                    RollingNumberText(
                        text = "${room.temp.toInt()}°",
                        style = TextStyle(
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 54.sp,
                            lineHeight = 50.sp,
                            color = cs.onSurface,
                        ),
                        labelPrefix = "room_temp_${room.id}",
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(
                        modifier = Modifier.padding(bottom = 5.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = "${room.humidity}%",
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 22.sp,
                            lineHeight = 20.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "HUM.",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 9.sp,
                            letterSpacing = 0.5.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }

            // Active indicator dot — absolute bottom-right corner
            if (room.activeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(custom.warn, CircleShape)
                        .align(Alignment.BottomEnd),
                )
            }
        }
    }
}
