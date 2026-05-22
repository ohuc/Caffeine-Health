package com.uc.homehealth.ui.components

import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.uc.homehealth.data.HaMedia
import com.uc.homehealth.data.MediaRepeatMode
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.InterFamily

// Bundle of media-control callbacks plumbed through the room sheet. Each
// closure takes the target entityId so a single callbacks object can serve
// every media widget in the room.
data class MediaCardCallbacks(
    val onPlayPause: (entityId: String) -> Unit = {},
    val onSkipPrev: (entityId: String) -> Unit = {},
    val onSkipNext: (entityId: String) -> Unit = {},
    val onToggleShuffle: (entityId: String, current: Boolean) -> Unit = { _, _ -> },
    val onCycleRepeat: (entityId: String, current: MediaRepeatMode) -> Unit = { _, _ -> },
    val onOpenQueue: (entityId: String) -> Unit = {},
    val onCast: (entityId: String) -> Unit = {},
    val onSeek: (entityId: String, progress: Float) -> Unit = { _, _ -> },
    val onVolumeChange: (entityId: String, volume: Float) -> Unit = { _, _ -> },
)

// Hero (C) media card. Full-width "Now Playing" surface with rotating
// material shape art (or album art if HA exposes one), big serif title,
// squiggly progress, M3 Expressive ButtonGroup transport with bouncy
// neighbor compression, ButtonGroup utility chips, and a volume pill.
//
// Background was simplified from the original purple gradient to a flat
// surfaceContainerHigh so it matches the surrounding Climate / More tabs.
@Composable
fun MediaCardHero(
    media: HaMedia,
    onPlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: () -> Unit,
    onSeek: (Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val accent = Color(0xFFE8B4D6)
    val ink = Color(0xFF2A0F22)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(cs.surfaceContainerHigh)
            .padding(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroRow(media = media, accent = accent)
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
            UtilityChipRow(
                shuffleOn = media.shuffleOn,
                repeatMode = media.repeatMode,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onOpenQueue = onOpenQueue,
                onCast = onCast,
            )
            VolumePill(
                volume = media.volume,
                accent = accent,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

@Composable
private fun HeroRow(media: HaMedia, accent: Color) {
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
                    .clip(RoundedCornerShape(26.dp))
                    .background(cs.surfaceContainerHigh),
            )
        } else {
            RotatingMediaShape(fillColor = accent, size = 84.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Now playing · ${media.friendlyName}".uppercase(),
                fontFamily = InterFamily,
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
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
            dimColor = Color.White.copy(alpha = 0.10f),
            trackHeight = 22.dp,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = media.elapsedLabel,
                fontFamily = InterFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
            )
            Text(
                text = media.remainingLabel,
                fontFamily = InterFamily,
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
    val radius by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 30.dp,
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
                fontFamily = InterFamily,
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
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.06f)),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UtilityChipRow(
    shuffleOn: Boolean,
    repeatMode: MediaRepeatMode,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onCast: () -> Unit,
) {
    val repeatActive = repeatMode != MediaRepeatMode.OFF
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val shuffleSource = remember { MutableInteractionSource() }
        val repeatSource = remember { MutableInteractionSource() }
        val queueSource = remember { MutableInteractionSource() }
        val castSource = remember { MutableInteractionSource() }

        UtilityChip(
            icon = Icons.Outlined.Shuffle,
            label = "Shuffle",
            active = shuffleOn,
            accent = Color(0xFFE8B4D6),
            ink = Color(0xFF2A0F22),
            onClick = onToggleShuffle,
            modifier = Modifier
                .weight(1f)
                .animateWidth(shuffleSource),
            interactionSource = shuffleSource,
        )
        UtilityChip(
            icon = if (repeatMode == MediaRepeatMode.ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
            label = "Repeat",
            active = repeatActive,
            accent = Color(0xFFE8B4D6),
            ink = Color(0xFF2A0F22),
            onClick = onCycleRepeat,
            modifier = Modifier
                .weight(1f)
                .animateWidth(repeatSource),
            interactionSource = repeatSource,
        )
        UtilityChip(
            icon = Icons.AutoMirrored.Outlined.QueueMusic,
            label = "Queue",
            active = false,
            accent = Color(0xFFE8B4D6),
            ink = Color(0xFF2A0F22),
            onClick = onOpenQueue,
            modifier = Modifier
                .weight(1f)
                .animateWidth(queueSource),
            interactionSource = queueSource,
        )
        UtilityChip(
            icon = Icons.Outlined.Cast,
            label = "Cast",
            active = false,
            accent = Color(0xFFE8B4D6),
            ink = Color(0xFF2A0F22),
            onClick = onCast,
            modifier = Modifier
                .weight(1f)
                .animateWidth(castSource),
            interactionSource = castSource,
        )
    }
}

@Composable
private fun UtilityChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accent: Color,
    ink: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
) {
    val cs = MaterialTheme.colorScheme
    // Match the skip buttons: a soft tinted background by default so the
    // chip is clearly visible against the card. When active (shuffle/repeat
    // on), fill with the mauve accent + ink text/icon — same vocabulary as
    // the Play/Pause FAB so "active media control" reads consistently.
    val containerColor = if (active) accent else Color.White.copy(alpha = 0.06f)
    val contentColor = if (active) ink else cs.onSurface
    Tap(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(containerColor)
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = contentColor,
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
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
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
                dimColor = Color.White.copy(alpha = 0.10f),
                trackHeight = 14.dp,
            )
        }
        Text(
            text = "$displayed%",
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = cs.onSurface,
            modifier = Modifier.widthIn(min = 32.dp),
        )
    }
}
