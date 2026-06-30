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
    // Owning config entry — needed by integration-scoped service calls
    // (e.g. music_assistant.search wants the MA config_entry_id).
    val config_entry_id: String? = null,
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
    val fan_mode: String? = null,         // current fan speed, e.g. "auto" | "low" | "high"
    val fan_modes: List<String> = emptyList(),
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
    // ISO-8601 timestamp of the last state change (e.g. "2026-06-04T12:00:00+00:00").
    // Null when HA omits it. Used by the location widget's "x ago" line.
    val last_changed: String? = null,
    // ISO-8601 timestamp of the last state OR attribute write. Unlike last_changed this
    // bumps even when the value stays the same, so it's the right signal for Pulse's
    // stale-sensor detection (a sensor holding one value for days is fine; one that
    // hasn't WRITTEN for days is likely dead).
    val last_updated: String? = null,
)

// Result of a WebSocket call_service: success plus HA's error message when it fails
// (e.g. "This version requires Home Assistant 2026.5.0 or newer.").
data class ServiceCallResult(val success: Boolean, val error: String?)

// Subset of HA's core config (`get_config` WS command). Used by the Energy card to
// locate the home for the 3D map + solar position math. Fields are nullable because an
// HA instance may not have its location set.
data class HaCoreConfig(
    val latitude: Double?,
    val longitude: Double?,
    val elevation: Double?,
    val timeZone: String?,
)

// The Energy dashboard's source wiring (`energy/get_prefs` WS command) — the single
// source of truth Helios reads instead of per-card entity config. `*EnergyFrom/To` are
// cumulative kWh statistics (differentiated into live W when no rate sensor exists);
// `*Rate` are optional live power sensors; `batterySoc` is the battery's % entity.
data class HaEnergyPrefs(
    val solarEnergyFrom: List<String> = emptyList(),
    val solarRate: List<String> = emptyList(),
    val gridEnergyFrom: List<String> = emptyList(),
    val gridEnergyTo: List<String> = emptyList(),
    val gridRate: List<String> = emptyList(),
    val batteryEnergyFrom: List<String> = emptyList(),
    val batteryEnergyTo: List<String> = emptyList(),
    val batterySoc: List<String> = emptyList(),
    val batteryRate: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() = solarEnergyFrom.isEmpty() && solarRate.isEmpty() && gridEnergyFrom.isEmpty() &&
            gridEnergyTo.isEmpty() && gridRate.isEmpty() && batteryEnergyFrom.isEmpty() &&
            batteryEnergyTo.isEmpty() && batterySoc.isEmpty() && batteryRate.isEmpty()
}

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
