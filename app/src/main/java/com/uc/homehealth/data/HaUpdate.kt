package com.uc.homehealth.data

// Which kind of thing an `update.*` entity represents — drives the grouped section
// headers ("System" / "HACS" / "Add-ons" / "Firmware") on the Updates screen and the
// fallback logo icon. Classified in HaHomeRepository by joining the entity registry
// platform with the entity_id and device_class.
enum class UpdateCategory(val label: String) {
    SYSTEM("System"),     // Home Assistant Core / Operating System / Supervisor
    HACS("HACS"),         // HACS-tracked integrations, cards, themes, …
    ADDON("Add-ons"),     // Supervisor add-ons
    FIRMWARE("Firmware"), // device/integration firmware (ESPHome, Zigbee, routers, …)
}

// One Home Assistant `update.*` entity resolved into the shape the Updates UI consumes.
// State `on` = update available; `off`/done items are dropped upstream so the card
// animates out of the list. [updatePercentage] is null when the integration reports no
// progress (indeterminate). [entityPictureUrl] is already absolute (resolved against the
// active HA base URL). [supportsBackup] reflects the BACKUP feature bit, gating the
// "create backup first" toggle to system/add-on updates that actually support it.
data class HaUpdate(
    val entityId: String,
    val title: String,
    val installedVersion: String?,
    val latestVersion: String?,
    val inProgress: Boolean,
    val updatePercentage: Int?,
    val releaseSummary: String?,
    val releaseUrl: String?,
    val entityPictureUrl: String?,
    val skippedVersion: String?,
    val supportsBackup: Boolean,
    val category: UpdateCategory,
    // Client-side: HA's reason a recent install attempt failed (e.g. "requires HA 2026.5.0").
    // Null while idle/installing/succeeded. Cleared when the user retries.
    val errorMessage: String? = null,
) {
    // True when the entity is currently this version's "skipped" marker — used to split
    // skipped updates into their own collapsed section.
    val isSkipped: Boolean get() = skippedVersion != null && skippedVersion == latestVersion
}
