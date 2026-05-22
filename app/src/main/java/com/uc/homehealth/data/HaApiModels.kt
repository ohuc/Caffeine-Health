package com.uc.homehealth.data

import com.google.gson.JsonObject

data class HaArea(
    val area_id: String,
    val name: String,
    val icon: String?,
)

data class HaDeviceEntry(
    val id: String,
    val area_id: String?,
    val name: String?,
)

data class HaEntityEntry(
    val entity_id: String,
    val area_id: String?,
    val device_id: String?,
    val name: String?,
    val original_name: String?,
    val platform: String?,
    val disabled_by: String?,
)

data class HaStateAttributes(
    val friendly_name: String?,
    val brightness: Float?,
    val rgb_color: List<Int>?,
    val color_temp_kelvin: Int?,
    val min_color_temp_kelvin: Int? = null,
    val max_color_temp_kelvin: Int? = null,
    val supported_color_modes: List<String>? = null,
    val current_temperature: Float?,
    val temperature: Float?,
    val current_humidity: Int?,
    val humidity: Int?,
    val device_class: String?,
    val unit_of_measurement: String?,
    val hvac_action: String? = null,      // "heating" | "cooling" | "idle" | "off"
    val hvac_modes: List<String> = emptyList(),
    val target_temp_step: Float? = null,
    val min_temp: Float? = null,
    val max_temp: Float? = null,
    // Full raw attribute payload — kept so domain code (e.g. flight tracking) can
    // pull integration-specific fields without bloating this typed model.
    val raw: JsonObject? = null,
)

data class HaState(
    val entity_id: String,
    val state: String,
    val attributes: HaStateAttributes,
)

enum class WsConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    READY,
    ERROR,
    // Token rejected by HA. Don't auto-reconnect — each retry counts as a failed
    // login attempt against HA's auth ban threshold (default 5 fails → ip_bans.yaml entry).
    AUTH_INVALID,
    // HA returned 403 on the WS upgrade or REST diagnostic — this IP is in HA's ip_bans.yaml.
    // Reconnecting won't help and only renews the ban timer; user must clear the ban.
    IP_BANNED,
}
