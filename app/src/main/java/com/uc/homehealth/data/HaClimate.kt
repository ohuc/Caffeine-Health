package com.uc.homehealth.data

data class HaClimate(
    val id: String,
    val name: String,
    val currentTemp: Float?,
    val targetTemp: Float?,
    val mode: String,                  // hvac state: "heat" | "cool" | "off" | "auto" | "heat_cool" | "fan_only" | "dry"
    val action: String?,               // "heating" | "cooling" | "idle" | "off" | null
    val supportedModes: List<String>,
    val tempStep: Float,               // typical: 0.5 or 1.0
    val minTemp: Float,                // entity-reported min, falls back to HA default (7°C) when unknown
    val maxTemp: Float,                // entity-reported max, falls back to HA default (35°C) when unknown
    val fanMode: String? = null,       // current fan speed (null when the entity has no fan support)
    val fanModes: List<String> = emptyList(),  // supported fan speeds; empty == no fan ButtonGroup
    val isAvailable: Boolean = true,
) {
    val supportsFan: Boolean get() = fanModes.isNotEmpty()
}
