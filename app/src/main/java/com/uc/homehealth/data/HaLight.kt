package com.uc.homehealth.data

data class HaLight(
    val id: String,
    val name: String,
    val brightness: Int,   // 0-100
    val colorHex: String,  // color-temperature hex e.g. "#ffd9a8"
    val isOn: Boolean,
    val isAvailable: Boolean = true,
    val supportsColor: Boolean = false,
    val supportsColorTemp: Boolean = false,
    val colorTempKelvin: Int? = null,
    val minColorTempKelvin: Int? = null,
    val maxColorTempKelvin: Int? = null,
)
