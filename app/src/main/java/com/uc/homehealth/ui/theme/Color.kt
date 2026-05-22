package com.uc.homehealth.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ─── Raw color tokens — translated from shared.jsx ────────────────────────────
// Dark palette (warm onyx base)
val Bg = Color(0xFF0E0E10)
val Surface = Color(0xFF1A1A1D)
val SurfaceHi = Color(0xFF222226)
val SurfaceCard = Color(0xFF1B1B1F)
val OnSurface = Color(0xFFF1EFE9)
val OnSurfaceVar = Color(0xFF8A8A90)
val Outline = Color(0xFF2A2A2E)
val AccentMauveDark = Color(0xFFE8B4D6)
val AccentInkDark = Color(0xFF2A0F22)
val AccentDeepDark = Color(0xFFC77DBA)
val CoralDark = Color(0xFFF2725C)
val SandDark = Color(0xFFE8C99B)
val MintDark = Color(0xFF9DD8A8)
val SkyDark = Color(0xFF9CB6E8)
val LavenderDark = Color(0xFFB8A8E8)
val CyanDark = Color(0xFF7FD8E2)
val WarnDark = Color(0xFFF2A65E)

// Light palette
val BgLight = Color(0xFFF4F1EC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceHiLight = Color(0xFFEDE9E3)
val OnSurfaceLight = Color(0xFF15141A)
val OnSurfaceVarLight = Color(0xFF5C5B62)
val OutlineLight = Color(0xFFDCD8D2)
val AccentMauveLight = Color(0xFFC77DBA)
val AccentInkLight = Color(0xFFFFFFFF)
val AccentDeepLight = Color(0xFF8B3F7A)
val CoralLight = Color(0xFFD9533C)
val SandLight = Color(0xFFB98F4B)
val MintLight = Color(0xFF3F8554)
val SkyLight = Color(0xFF3F5FA0)
val LavenderLight = Color(0xFF7958B8)
val CyanLight = Color(0xFF0A8A8A)
val WarnLight = Color(0xFFB98F4B)

// ─── M3 Color Schemes ─────────────────────────────────────────────────────────
internal val DarkColorScheme = darkColorScheme(
    primary = AccentMauveDark,
    onPrimary = AccentInkDark,
    primaryContainer = Color(0xFF3A1A2C),
    onPrimaryContainer = OnSurface,
    secondary = SkyDark,
    onSecondary = Bg,
    secondaryContainer = Color(0xFF1A2440),
    onSecondaryContainer = OnSurface,
    tertiary = MintDark,
    onTertiary = Bg,
    tertiaryContainer = Color(0xFF12301A),
    onTertiaryContainer = OnSurface,
    error = CoralDark,
    onError = Bg,
    errorContainer = Color(0xFF3A100A),
    onErrorContainer = OnSurface,
    background = Bg,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceHi,
    onSurfaceVariant = OnSurfaceVar,
    outline = Outline,
    outlineVariant = Color(0xFF1C1C20),
    surfaceContainer = SurfaceCard,
    surfaceContainerLow = Bg,
    surfaceContainerHigh = SurfaceHi,
)

internal val LightColorScheme = lightColorScheme(
    primary = AccentMauveLight,
    onPrimary = AccentInkLight,
    primaryContainer = Color(0xFFF5DCF0),
    onPrimaryContainer = AccentDeepLight,
    secondary = SkyLight,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4DEFF),
    onSecondaryContainer = Color(0xFF0D2560),
    tertiary = MintLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB8F0C8),
    onTertiaryContainer = Color(0xFF0A2E18),
    error = CoralLight,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = BgLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceHiLight,
    onSurfaceVariant = OnSurfaceVarLight,
    outline = OutlineLight,
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainer = Color(0xFFF5F0EA),
    surfaceContainerLow = BgLight,
    surfaceContainerHigh = SurfaceHiLight,
)

// ─── Custom colors (extra tokens beyond M3 roles) ─────────────────────────────
data class CustomColors(
    val sand: Color,
    val mint: Color,
    val sky: Color,
    val lavender: Color,
    val cyan: Color,
    val coral: Color,
    val warn: Color,
    val surfaceCard: Color,
    val accentDeep: Color,
)

internal val DarkCustomColors = CustomColors(
    sand = SandDark,
    mint = MintDark,
    sky = SkyDark,
    lavender = LavenderDark,
    cyan = CyanDark,
    coral = CoralDark,
    warn = WarnDark,
    surfaceCard = SurfaceCard,
    accentDeep = AccentDeepDark,
)

internal val LightCustomColors = CustomColors(
    sand = SandLight,
    mint = MintLight,
    sky = SkyLight,
    lavender = LavenderLight,
    cyan = CyanLight,
    coral = CoralLight,
    warn = WarnLight,
    surfaceCard = SurfaceLight,
    accentDeep = AccentDeepLight,
)

val LocalCustomColors = staticCompositionLocalOf { DarkCustomColors }
