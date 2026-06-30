package com.uc.homehealth.data

data class HaRoom(
    val id: String,
    val name: String,
    val icon: String,        // icon key: sofa, bed, cooking, desk, water, door
    val colorHex: String,    // per-room accent hex e.g. "#E8B4D6"
    val inkHex: String,      // text-on-accent hex e.g. "#3a1a2c"
    val deviceCount: Int,
    val activeCount: Int,    // devices currently on
    // null == no temperature/humidity sensor resolved for the room (and no user override).
    // Kept nullable rather than defaulting to 0 so the UI can omit the reading entirely
    // instead of showing a misleading "0° / 0%".
    val temp: Float?,
    val humidity: Int?,
    // Human-readable reasons this room needs attention (e.g. "Ceiling Light — unavailable").
    // Empty == healthy. Only meaningful controllable devices contribute; noisy diagnostic
    // entities are excluded so rooms don't false-alarm.
    val alerts: List<String> = emptyList(),
) {
    val hasAlert: Boolean get() = alerts.isNotEmpty()
}
