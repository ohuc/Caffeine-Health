package com.uc.homehealth.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

fun haIconFor(name: String): ImageVector = when (name) {
    "sofa"          -> Icons.Outlined.Weekend
    "bed"           -> Icons.Outlined.Bed
    "cooking"       -> Icons.Outlined.Kitchen
    "desk"          -> Icons.Outlined.Computer
    "water"         -> Icons.Outlined.Shower
    "door"          -> Icons.Outlined.DoorFront
    "thermo"        -> Icons.Outlined.Thermostat
    "bulb"          -> Icons.Outlined.Lightbulb
    "speaker"       -> Icons.Outlined.Speaker
    "lock"          -> Icons.Outlined.Lock
    "energy"        -> Icons.Outlined.ElectricBolt
    "pulse"         -> Icons.Outlined.MonitorHeart
    "sparkle"       -> Icons.Outlined.AutoAwesome
    "palette"       -> Icons.Outlined.Palette
    "shield"        -> Icons.Outlined.Security
    "wifi"          -> Icons.Outlined.Wifi
    "bell"          -> Icons.Outlined.Notifications
    "chevron-right" -> Icons.AutoMirrored.Outlined.ArrowForwardIos
    "settings"      -> Icons.Outlined.Settings
    "activity"      -> Icons.Outlined.Timeline
    "home"          -> Icons.Outlined.Home
    "battery"       -> Icons.Outlined.BatteryChargingFull
    "trash"         -> Icons.Outlined.DeleteOutline
    "data"          -> Icons.Outlined.Storage
    "update"        -> Icons.Outlined.Update
    "download"      -> Icons.Outlined.Download
    "extension"     -> Icons.Outlined.Extension
    "chip"          -> Icons.Outlined.Memory
    "check"         -> Icons.Outlined.CheckCircle
    "open"          -> Icons.AutoMirrored.Outlined.OpenInNew
    "more"          -> Icons.Outlined.MoreVert
    else            -> Icons.Outlined.Home
}
