package com.uc.homehealth.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import com.uc.homehealth.ui.theme.customColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomTile(
    room: HaRoom,
    height: Dp,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    showWarnings: Boolean = true,
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

    Tap(onClick = { haptic.navigation(); onTap() }, modifier = modifier, enabled = interactive) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainerHigh),
        ) {
            // Per-room accent bar — slim rounded edge tinted with the room's color
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 16.dp, bottom = 16.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(PillShape)
                    .background(accentColor),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 26.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // The bottom of the card is always a single big-serif "hero" metric so the
                // fixed-height tile never has a void. Priority: temperature → humidity →
                // device activity (the always-available fallback). The "X on · Y devices"
                // status line only rides under the name when an env reading is the hero, so
                // it isn't duplicated by the activity hero. See docs/material3-expressive.md.
                val temp = room.temp
                val humidity = room.humidity
                val hasEnv = temp != null || humidity != null

                // ── Top: name + (status when env is the hero) + icon ─────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = room.name,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp,
                            lineHeight = 18.sp,
                            color = cs.onSurface,
                        )
                        if (hasEnv) {
                            Text(
                                text = if (room.activeCount > 0)
                                    "${room.activeCount} on · ${room.deviceCount} devices"
                                else
                                    "All off · ${room.deviceCount} devices",
                                fontFamily = MontserratFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp,
                                color = cs.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
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
                        if (room.hasAlert && showWarnings) {
                            val tooltipState = rememberTooltipState(isPersistent = false)
                            val scope = rememberCoroutineScope()
                            TooltipBox(
                                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above,
                                ),
                                tooltip = {
                                    PlainTooltip {
                                        Text(
                                            text = room.alerts.joinToString("\n") { "• $it" },
                                            fontFamily = MontserratFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp,
                                        )
                                    }
                                },
                                state = tooltipState,
                                // Sit on the icon-circle's upper-right edge. A slight inward
                                // nudge (down-left) keeps the badge clear of the card's rounded
                                // corner so it can't get clipped, and anchors it to the icon
                                // instead of floating in the empty box corner.
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-1).dp, y = 1.dp),
                            ) {
                                // Own tap target: tapping the badge reveals the reasons and,
                                // because the nested clickable consumes the gesture, does NOT
                                // open the room sheet. A card-colored ring cuts the coral disc
                                // out from the card; a crisp vector "!" replaces the old tiny
                                // text glyph that rendered as an artifact at this size. Coral +
                                // exclamation matches the app's attention language (the room
                                // sheet banner / "At a glance" alert card).
                                Tap(onClick = { haptic.tick(); scope.launch { tooltipState.show() } }) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(cs.surfaceContainerHigh, CircleShape)
                                            .padding(2.dp)
                                            .background(custom.coral, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PriorityHigh,
                                            contentDescription = null,
                                            tint = glanceInkOn(custom.coral),
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Bottom: hero metric — always present so the tile never voids ─
                val heroStyle = TextStyle(
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 54.sp,
                    lineHeight = 50.sp,
                    color = cs.onSurface,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    when {
                        // Temperature leads; humidity rides alongside as a labeled secondary.
                        temp != null -> {
                            RollingNumberText(
                                text = "${temp.toInt()}°",
                                style = heroStyle,
                                labelPrefix = "room_temp_${room.id}",
                            )
                            if (humidity != null) {
                                Spacer(Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.padding(bottom = 5.dp),
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    Text(
                                        text = "$humidity%",
                                        fontFamily = InstrumentSerifFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 22.sp,
                                        lineHeight = 20.sp,
                                        color = cs.onSurface,
                                    )
                                    Text(
                                        text = "HUM.",
                                        fontFamily = MontserratFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.5.sp,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        // Humidity-only room: promote humidity into the hero slot.
                        humidity != null -> {
                            Column(verticalArrangement = Arrangement.Bottom) {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    RollingNumberText(
                                        text = "$humidity",
                                        style = heroStyle,
                                        labelPrefix = "room_hum_${room.id}",
                                    )
                                    Text(
                                        text = "%",
                                        fontFamily = InstrumentSerifFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 24.sp,
                                        color = cs.onSurface,
                                        modifier = Modifier.padding(start = 1.dp, bottom = 7.dp),
                                    )
                                }
                                Text(
                                    text = "HUMIDITY",
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.5.sp,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                        // No env sensors: device activity is the always-available fallback,
                        // so the bottom half stays meaningful instead of collapsing to a void.
                        else -> {
                            Column(verticalArrangement = Arrangement.Bottom) {
                                if (room.activeCount > 0) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        RollingNumberText(
                                            text = "${room.activeCount}",
                                            style = heroStyle,
                                            labelPrefix = "room_active_${room.id}",
                                        )
                                        Text(
                                            text = "on",
                                            fontFamily = InstrumentSerifFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 24.sp,
                                            color = cs.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 5.dp, bottom = 7.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "Off",
                                        fontFamily = InstrumentSerifFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 54.sp,
                                        lineHeight = 50.sp,
                                        color = cs.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    text = "${room.deviceCount} devices",
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // Active indicator dot — absolute bottom-right corner
            if (room.activeCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(8.dp)
                        .background(custom.warn, CircleShape),
                )
            }
        }
    }
}
