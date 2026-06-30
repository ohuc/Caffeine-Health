package com.uc.homehealth.data

// PM2.5 air-quality band, using the IKEA VINDRIKTNING's onboard LED thresholds (µg/m³):
// ≤35 green ("clean"), 36–85 amber ("moderate"), ≥86 red ("poor"). The three duration
// sensors the air-quality widget consumes (clean/moderate/poor) mirror these bands.
enum class AirQualityBand(val label: String) {
    CLEAN("Clean"),
    MODERATE("Moderate"),
    POOR("Poor"),
    UNKNOWN("—");

    companion object {
        fun fromPm25(value: Float?): AirQualityBand = when {
            value == null -> UNKNOWN
            value <= 35f -> CLEAN
            value <= 85f -> MODERATE
            else -> POOR
        }
    }
}
