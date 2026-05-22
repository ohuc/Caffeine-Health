package com.uc.homehealth.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class EntityAccent { Mauve, Coral, Sand, Mint, Sky, Lavender, Cyan }

@Composable
fun EntityAccent.color(): Color {
    val custom = LocalCustomColors.current
    return when (this) {
        EntityAccent.Mauve -> if (isSystemInDarkThemeLocal()) AccentMauveDark else AccentMauveLight
        EntityAccent.Coral -> custom.coral
        EntityAccent.Sand -> custom.sand
        EntityAccent.Mint -> custom.mint
        EntityAccent.Sky -> custom.sky
        EntityAccent.Lavender -> custom.lavender
        EntityAccent.Cyan -> custom.cyan
    }
}

// Non-composable helpers for cases where compose context isn't available
fun EntityAccent.darkColor(): Color = when (this) {
    EntityAccent.Mauve -> AccentMauveDark
    EntityAccent.Coral -> CoralDark
    EntityAccent.Sand -> SandDark
    EntityAccent.Mint -> MintDark
    EntityAccent.Sky -> SkyDark
    EntityAccent.Lavender -> LavenderDark
    EntityAccent.Cyan -> CyanDark
}

fun EntityAccent.lightColor(): Color = when (this) {
    EntityAccent.Mauve -> AccentMauveLight
    EntityAccent.Coral -> CoralLight
    EntityAccent.Sand -> SandLight
    EntityAccent.Mint -> MintLight
    EntityAccent.Sky -> SkyLight
    EntityAccent.Lavender -> LavenderLight
    EntityAccent.Cyan -> CyanLight
}

// Internal helper — avoids pulling isSystemInDarkTheme into non-composable code
@Composable
private fun isSystemInDarkThemeLocal(): Boolean =
    LocalCustomColors.current.surfaceCard == SurfaceCard
