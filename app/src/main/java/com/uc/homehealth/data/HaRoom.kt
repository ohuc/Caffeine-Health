package com.uc.homehealth.data

data class HaRoom(
    val id: String,
    val name: String,
    val icon: String,        // icon key: sofa, bed, cooking, desk, water, door
    val colorHex: String,    // per-room accent hex e.g. "#E8B4D6"
    val inkHex: String,      // text-on-accent hex e.g. "#3a1a2c"
    val deviceCount: Int,
    val activeCount: Int,    // devices currently on
    val temp: Float,
    val humidity: Int,
    val hasAlert: Boolean,
)
