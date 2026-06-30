package com.uc.homehealth.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ButtonGroup
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape

// Bundle of media-control callbacks plumbed through the room sheet. Each
// closure takes the target entityId so a single callbacks object can serve
// every media widget in the room.
data class MediaCardCallbacks(
    val onPlayPause: (entityId: String) -> Unit = {},
    val onSkipPrev: (entityId: String) -> Unit = {},
    val onSkipNext: (entityId: String) -> Unit = {},
    val onToggleShuffle: (entityId: String, current: Boolean) -> Unit = { _, _ -> },
    val onCycleRepeat: (entityId: String, current: MediaRepeatMode) -> Unit = { _, _ -> },
    val onSeek: (entityId: String, progress: Float) -> Unit = { _, _ -> },
    val onVolumeChange: (entityId: String, volume: Float) -> Unit = { _, _ -> },
    // Opens the announce/text-to-speech composer targeting this player.
    val onAnnounce: (entityId: String) -> Unit = {},
    // Opens the Music Assistant search sheet (MA players only).
    val onSearchMusic: (entityId: String) -> Unit = {},
)

// Hero media card: art + serif title (the hero), squiggly progress, an expressive
// ButtonGroup transport with bouncy neighbor compression, and ONE settle row —
// volume with shuffle/repeat toggles beside it. Accent is the theme's primary
// (mauve in dark, deep mauve in light), so the card themes correctly; containers
// are onSurface washes so the wells survive light theme too.
@Composable
fun MediaCardHero(
    media: HaMedia,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onAnnounce: () -> Unit = {},
    onSearchMusic: () -> Unit = {},
    // Overridable so callers can pair the card with an attached sibling surface
    // (the Music page groups it with the queue card via grouped corners).
    shape: Shape = MaterialTheme.shapes.large,
) {
    val cs = MaterialTheme.colorScheme
    val accent = cs.primary
    val ink = cs.onPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cs.surfaceContainerHigh)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroRow(media = media, accent = accent, onAnnounce = onAnnounce, onSearchMusic = onSearchMusic)
            ProgressBlock(
                media = media,
                accent = accent,
                onSeek = onSeek,
            )
            TransportRow(
                isPlaying = media.isPlaying,
                accent = accent,
                ink = ink,
                onPlayPause = onPlayPause,
                onSkipPrev = onSkipPrev,
                onSkipNext = onSkipNext,
            )
            // Volume + playback toggles share one row: they're all "how it plays"
            // settings, and one group reads calmer than two stacked chip strips.
            OptionsRow(
                volume = media.volume,
                shuffleOn = media.shuffleOn,
                repeatMode = media.repeatMode,
                accent = accent,
                ink = ink,
                onVolumeChange = onVolumeChange,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )
        }
    }
}

@Composable
private fun HeroRow(media: HaMedia, accent: Color, onAnnounce: () -> Unit, onSearchMusic: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Album art if HA provided one, otherwise the rotating Material shape.
        val picture = media.entityPictureUrl
        if (picture != null) {
            AsyncImage(
                model = picture,
                contentDescription = null,
                modifier = Modifier
                    .size(84.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(cs.surfaceContainerHigh),
            )
        } else {
            RotatingMediaShape(fillColor = accent, size = 84.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Now playing · ${media.friendlyName}".uppercase(),
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = media.title,
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                lineHeight = 28.sp,
                letterSpacing = (-0.6).sp,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.source.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = media.source,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Quick actions stack in the hero's top-right: announce always; Music
        // Assistant players also get search (the two pills together match the
        // 84dp art height, so the row stays balanced either way).
        Column(
            modifier = Modifier.align(Alignment.Top),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroPillButton(
                icon = Icons.Outlined.Campaign,
                contentDescription = "Announce",
                accent = accent,
                onClick = onAnnounce,
            )
            if (media.isMusicAssistant) {
                HeroPillButton(
                    icon = Icons.Outlined.Search,
                    contentDescription = "Search music",
                    accent = accent,
                    onClick = onSearchMusic,
                )
            }
        }
    }
}

// Small pill action in the hero row's top-right (announce / MA search) — kept out of
// the transport row so it doesn't crowd the playback controls.
@Composable
private fun HeroPillButton(
    icon: ImageVector,
    contentDescription: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Tap(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(PillShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ProgressBlock(
    media: HaMedia,
    accent: Color,
    onSeek: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val progressPct = (media.progress * 100f).toInt().coerceIn(0, 100)

    // Local drag state so the bar visibly tracks the finger even if the
    // backend doesn't echo back immediately. Same pattern as VolumePill.
    var dragPct by remember { mutableStateOf<Int?>(null) }
    val displayed = dragPct ?: progressPct

    Column {
        SquigglySlider(
            value = displayed,
            onValueChange = { dragPct = it },
            onValueChangeFinished = {
                dragPct = null
                onSeek(it / 100f)
            },
            color = accent,
            dimColor = cs.onSurface.copy(alpha = 0.12f),
            trackHeight = 22.dp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = media.elapsedLabel,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = media.remainingLabel,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportRow(
    isPlaying: Boolean,
    accent: Color,
    ink: Color,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
) {
    // ButtonGroup gives the pressed child a width-expand animation while its
    // siblings compress — the bouncy M3 Expressive feel. Each child needs its
    // own MutableInteractionSource so animateWidth can observe presses.
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val playSource = remember { MutableInteractionSource() }
        val prevSource = remember { MutableInteractionSource() }
        val nextSource = remember { MutableInteractionSource() }

        // Big labeled play/pause button — keeps the original shape-morph
        // (22dp↔30dp) and accent fill, but the bouncy width comes from
        // ButtonGroup.
        PlayPauseSlot(
            isPlaying = isPlaying,
            accent = accent,
            ink = ink,
            onClick = onPlayPause,
            modifier = Modifier
                .weight(2.4f)
                .animateWidth(playSource),
            interactionSource = playSource,
        )
        SkipSlot(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Skip previous",
            onClick = onSkipPrev,
            modifier = Modifier
                .weight(1f)
                .animateWidth(prevSource),
            interactionSource = prevSource,
        )
        SkipSlot(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Skip next",
            onClick = onSkipNext,
            modifier = Modifier
                .weight(1f)
                .animateWidth(nextSource),
            interactionSource = nextSource,
        )
    }
}

@Composable
private fun PlayPauseSlot(
    isPlaying: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
) {
    // M3E shape-morph between the app's medium and large tokens: rounder at rest
    // ("press me"), squarer while playing (the active/selected vocabulary).
    val radius by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 32.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "play_fab_radius",
    )
    Tap(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(radius))
                .background(accent),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Crossfade(targetState = isPlaying, animationSpec = tween(220), label = "play_icon") { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (isPlaying) "Pause" else "Play",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = ink,
            )
        }
    }
}

@Composable
private fun SkipSlot(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
) {
    val cs = MaterialTheme.colorScheme
    Tap(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier = Modifier
                .height(60.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(cs.onSurface.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = cs.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

// One settle row: the volume pill (the frequently-used control gets the width)
// with the shuffle/repeat toggles beside it. Active toggles fill with the accent
// + ink — the same vocabulary as the Play button, so "active" reads consistently.
@Composable
private fun OptionsRow(
    volume: Float,
    shuffleOn: Boolean,
    repeatMode: MediaRepeatMode,
    accent: Color,
    ink: Color,
    onVolumeChange: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            VolumePill(volume = volume, accent = accent, onVolumeChange = onVolumeChange)
        }
        MediaIconToggle(
            icon = Icons.Outlined.Shuffle,
            contentDescription = "Shuffle",
            active = shuffleOn,
            accent = accent,
            ink = ink,
            onClick = onToggleShuffle,
        )
        MediaIconToggle(
            icon = if (repeatMode == MediaRepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
            contentDescription = "Repeat",
            active = repeatMode != MediaRepeatMode.OFF,
            accent = accent,
            ink = ink,
            onClick = onCycleRepeat,
        )
    }
}

@Composable
private fun MediaIconToggle(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    // Spring the fill instead of snapping so the toggle reads as a response.
    val container by animateColorAsState(
        targetValue = if (active) accent else cs.onSurface.copy(alpha = 0.06f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "media_toggle_$contentDescription",
    )
    Tap(onClick = onClick) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(PillShape)
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (active) ink else cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun VolumePill(
    volume: Float,
    accent: Color,
    onVolumeChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val backendVolPct = (volume * 100f).toInt().coerceIn(0, 100)

    // Local drag state: the slider tracks the finger immediately, regardless
    // of whether HA echoes volume_level back. Cleared on release; if the
    // backend responds the bar latches onto the new echoed value.
    var dragVol by remember { mutableStateOf<Int?>(null) }
    val displayed = dragVol ?: backendVolPct

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(cs.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = cs.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            SquigglySlider(
                value = displayed,
                onValueChange = { dragVol = it },
                onValueChangeFinished = {
                    dragVol = null
                    onVolumeChange(it / 100f)
                },
                color = accent,
                dimColor = cs.onSurface.copy(alpha = 0.12f),
                trackHeight = 14.dp,
            )
        }
        Text(
            text = "$displayed%",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = cs.onSurface,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}
