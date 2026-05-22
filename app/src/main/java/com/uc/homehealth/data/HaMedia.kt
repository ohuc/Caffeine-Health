package com.uc.homehealth.data

// Snapshot of a Home Assistant media_player entity, shaped for MediaCardHero.
// Returned by HomeRepository.getMediaPlayer(entityId). When not authenticated
// or when the entity is missing, the repository falls back to a static demo
// snapshot so the card has something to render in demo mode.
data class HaMedia(
    val entityId: String,
    val friendlyName: String,    // "Bathroom speaker"
    val title: String,           // "silent.mp3"
    val source: String,          // "local · 192.168.1.10"
    val isPlaying: Boolean,
    val isOff: Boolean,
    val progress: Float,         // 0f..1f
    val elapsedLabel: String,    // "0:00"
    val remainingLabel: String,  // "-0:01"
    val volume: Float,           // 0f..1f
    val shuffleOn: Boolean,
    val repeatMode: MediaRepeatMode,
    val entityPictureUrl: String?,
)

enum class MediaRepeatMode { OFF, ALL, ONE;
    companion object {
        fun fromHa(value: String?): MediaRepeatMode = when (value?.lowercase()) {
            "all" -> ALL
            "one" -> ONE
            else -> OFF
        }
        fun next(current: MediaRepeatMode): MediaRepeatMode = when (current) {
            OFF -> ALL
            ALL -> ONE
            ONE -> OFF
        }
        fun toHa(mode: MediaRepeatMode): String = when (mode) {
            OFF -> "off"
            ALL -> "all"
            ONE -> "one"
        }
    }
}
