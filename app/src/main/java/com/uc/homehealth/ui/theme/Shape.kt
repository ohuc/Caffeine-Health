package com.uc.homehealth.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

internal val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(999.dp),
)

// Pill for toggle knobs, chips, and BottomNavBar
val PillShape = RoundedCornerShape(999.dp)
