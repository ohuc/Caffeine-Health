package com.uc.homehealth.data

import java.time.OffsetDateTime

// ─── Pulse — the home-health report ──────────────────────────────────────────
// Read-only diagnosis of the smart home itself: low batteries, unreachable
// devices, silent sensors, pending updates, connection drops, server vitals.
// Rolled up into one 0–100 score. The analyzer is a pure function so the score
// model is unit-testable on the JVM.

enum class PulseSeverity { INFO, WARN, CRITICAL }

enum class PulseCategoryKind { DEVICES, BATTERIES, SENSORS, UPDATES, CONNECTIVITY, SERVER }

data class PulseIssue(
    val entityId: String,
    val name: String,
    // Short human reason, e.g. "8% battery" / "Unreachable" / "Silent for 3d".
    val detail: String,
    val severity: PulseSeverity,
)

data class PulseCategory(
    val kind: PulseCategoryKind,
    // One-line status shown on the row, healthy or not ("All 14 batteries OK" / "2 low").
    val summary: String,
    val issues: List<PulseIssue> = emptyList(),
) {
    val healthy: Boolean get() = issues.isEmpty()
}

enum class PulseGrade { HEALTHY, FAIR, NEEDS_CARE }

data class PulseReport(
    val score: Int,
    val grade: PulseGrade,
    // Categories with nothing to measure are OMITTED entirely (no battery entities →
    // no Batteries row) — absent data is never rendered as a fake healthy zero.
    val categories: List<PulseCategory>,
    // Number of entities the analysis saw. 0 means state hasn't loaded yet — the UI
    // shows a loading surface instead of a misleading perfect score.
    val sampleSize: Int = 0,
) {
    val issueCount: Int get() = categories.sumOf { it.issues.size }

    // One spoken sentence for the announce composer's "Home status" quick phrase, e.g.
    // "Your home is mostly fine. 1 device unreachable, 2 batteries low."
    fun spokenSummary(): String {
        val opening = when (grade) {
            PulseGrade.HEALTHY -> "Your home is healthy."
            PulseGrade.FAIR -> "Your home is mostly fine."
            PulseGrade.NEEDS_CARE -> "Your home needs care."
        }
        val problems = categories.filter { !it.healthy }.map { cat ->
            // Server's summary is the vitals line — too numeric to speak; the rest
            // ("2 batteries low", "3 updates pending") already read naturally.
            if (cat.kind == PulseCategoryKind.SERVER) "server under load"
            else cat.summary.replaceFirstChar { it.lowercase() }
        }
        return if (problems.isEmpty()) opening else "$opening ${problems.joinToString(", ")}."
    }
}

object PulseAnalyzer {

    // Grade bands.
    const val SCORE_HEALTHY = 90
    const val SCORE_FAIR = 70

    // Detection thresholds.
    private const val BATTERY_LOW_PCT = 20f
    private const val BATTERY_CRITICAL_PCT = 10f
    private const val STALE_AFTER_MS = 48 * 3_600_000L
    private const val SERVER_HOT_PCT = 90f
    private const val DAY_MS = 24 * 3_600_000L
    private const val WEEK_MS = 7 * DAY_MS
    // Up to this many drops per day are treated as normal network churn.
    private const val FREE_DROPS_PER_DAY = 2

    // Score deductions, each capped per category so one runaway problem class
    // can't zero the score on its own.
    private const val COST_UNREACHABLE = 8
    private const val COST_NOT_RESPONDING = 4
    private const val CAP_DEVICES = 30
    private const val COST_BATTERY_LOW = 3
    private const val COST_BATTERY_CRITICAL = 6
    private const val CAP_BATTERIES = 25
    private const val COST_STALE = 2
    private const val CAP_SENSORS = 10
    private const val COST_UPDATE = 2
    private const val CAP_UPDATES = 10
    private const val COST_DROP = 3
    private const val CAP_CONNECTIVITY = 15
    private const val COST_SERVER_HOT = 5
    private const val CAP_SERVER = 10

    // Mirrors HaHomeRepository.ALERT_DOMAINS — the room "needs attention" rule:
    // only real, user-controllable devices count; diagnostic domains are routinely
    // unavailable in HA and would alarm constantly.
    private val DEVICE_DOMAINS = setOf(
        "light", "switch", "climate", "cover", "lock", "fan",
        "media_player", "vacuum", "humidifier", "water_heater",
        "alarm_control_panel", "valve", "lawn_mower", "camera", "siren",
    )

    fun gradeFor(score: Int): PulseGrade = when {
        score >= SCORE_HEALTHY -> PulseGrade.HEALTHY
        score >= SCORE_FAIR -> PulseGrade.FAIR
        else -> PulseGrade.NEEDS_CARE
    }

    /**
     * @param states full HA state map (entity_id → state)
     * @param pendingUpdates titles of updates available and not skipped/installing
     * @param dropTimesMs epoch millis of unexpected WS drops (READY → ERROR)
     * @param nowMs injected clock for pure, testable time math
     */
    fun analyze(
        states: Map<String, HaState>,
        pendingUpdates: List<String>,
        dropTimesMs: List<Long>,
        nowMs: Long,
    ): PulseReport {
        val categories = mutableListOf<PulseCategory>()
        var deductions = 0

        // ── Devices (unreachable controllables) ──
        val devices = states.values.filter { it.entity_id.substringBefore('.') in DEVICE_DOMAINS }
        if (devices.isNotEmpty()) {
            val issues = devices.mapNotNull { st ->
                when (st.state) {
                    "unavailable" -> PulseIssue(st.entity_id, displayName(st), "Unreachable", PulseSeverity.CRITICAL)
                    "unknown" -> PulseIssue(st.entity_id, displayName(st), "Not responding", PulseSeverity.WARN)
                    else -> null
                }
            }.sortedWith(issueOrder)
            deductions += issues.sumOf {
                if (it.severity == PulseSeverity.CRITICAL) COST_UNREACHABLE else COST_NOT_RESPONDING
            }.coerceAtMost(CAP_DEVICES)
            categories += PulseCategory(
                kind = PulseCategoryKind.DEVICES,
                summary = if (issues.isEmpty()) "All ${devices.size} ${plural(devices.size, "device")} reachable"
                else "${issues.size} ${plural(issues.size, "device")} unreachable",
                issues = issues,
            )
        }

        // ── Batteries ──
        val batteries = states.values.mapNotNull { st ->
            val isBattery = st.entity_id.startsWith("sensor.") &&
                st.attributes.device_class == "battery" &&
                st.attributes.unit_of_measurement?.trim() == "%"
            if (!isBattery) null else st.state.toFloatOrNull()?.let { st to it }
        }
        if (batteries.isNotEmpty()) {
            val issues = batteries
                .filter { (_, pct) -> pct <= BATTERY_LOW_PCT }
                .sortedBy { (_, pct) -> pct }
                .map { (st, pct) ->
                    PulseIssue(
                        entityId = st.entity_id,
                        name = displayName(st),
                        detail = "${pct.toInt()}% battery",
                        severity = if (pct <= BATTERY_CRITICAL_PCT) PulseSeverity.CRITICAL else PulseSeverity.WARN,
                    )
                }
            deductions += issues.sumOf {
                if (it.severity == PulseSeverity.CRITICAL) COST_BATTERY_CRITICAL else COST_BATTERY_LOW
            }.coerceAtMost(CAP_BATTERIES)
            categories += PulseCategory(
                kind = PulseCategoryKind.BATTERIES,
                summary = if (issues.isEmpty()) "All ${batteries.size} ${plural(batteries.size, "battery", "batteries")} OK"
                else "${issues.size} ${plural(issues.size, "battery", "batteries")} low",
                issues = issues,
            )
        }

        // ── Sensors (silent / stale) ──
        val sensors = states.values.filter {
            (it.entity_id.startsWith("sensor.") || it.entity_id.startsWith("binary_sensor.")) &&
                it.state != "unavailable" && it.state != "unknown"
        }
        if (sensors.isNotEmpty()) {
            val issues = sensors.mapNotNull { st ->
                val ts = parseTimestamp(st.last_updated ?: st.last_changed) ?: return@mapNotNull null
                val age = nowMs - ts
                if (age < STALE_AFTER_MS) return@mapNotNull null
                PulseIssue(st.entity_id, displayName(st), "Silent for ${age / DAY_MS}d", PulseSeverity.INFO)
            }.sortedBy { it.name }
            deductions += (issues.size * COST_STALE).coerceAtMost(CAP_SENSORS)
            categories += PulseCategory(
                kind = PulseCategoryKind.SENSORS,
                summary = if (issues.isEmpty()) "All sensors reporting"
                else "${issues.size} ${plural(issues.size, "sensor")} silent",
                issues = issues,
            )
        }

        // ── Updates ──
        val updateIssues = pendingUpdates.map { title ->
            PulseIssue("update:$title", title, "Update available", PulseSeverity.INFO)
        }
        deductions += (updateIssues.size * COST_UPDATE).coerceAtMost(CAP_UPDATES)
        categories += PulseCategory(
            kind = PulseCategoryKind.UPDATES,
            summary = if (updateIssues.isEmpty()) "Everything up to date"
            else "${updateIssues.size} ${plural(updateIssues.size, "update")} pending",
            issues = updateIssues,
        )

        // ── Connectivity (unexpected WS drops) ──
        val drops7d = dropTimesMs.filter { nowMs - it in 0..WEEK_MS }
        val drops24h = drops7d.count { nowMs - it <= DAY_MS }
        deductions += ((drops24h - FREE_DROPS_PER_DAY).coerceAtLeast(0) * COST_DROP)
            .coerceAtMost(CAP_CONNECTIVITY)
        categories += PulseCategory(
            kind = PulseCategoryKind.CONNECTIVITY,
            summary = if (drops7d.isEmpty()) "No drops this week"
            else "${drops7d.size} ${plural(drops7d.size, "drop")} this week",
            issues = if (drops7d.isEmpty()) emptyList() else listOf(
                PulseIssue(
                    entityId = "pulse:connectivity",
                    name = "Connection drops",
                    detail = "${drops7d.size} this week · $drops24h in 24h",
                    severity = if (drops24h > FREE_DROPS_PER_DAY) PulseSeverity.WARN else PulseSeverity.INFO,
                ),
            ),
        )

        // ── Server vitals (System Monitor sensors, when installed) ──
        val cpu = findServerPct(states, "processor_use")
        val ram = findServerPct(states, "memory_use_percent", "memory_percent")
        val disk = findServerPct(states, "disk_use_percent")
        val vitals = listOfNotNull(
            cpu?.let { Triple("CPU", it.first, it.second) },
            ram?.let { Triple("RAM", it.first, it.second) },
            disk?.let { Triple("Disk", it.first, it.second) },
        )
        if (vitals.isNotEmpty()) {
            val issues = vitals.filter { (_, _, pct) -> pct >= SERVER_HOT_PCT }.map { (label, st, pct) ->
                PulseIssue(st.entity_id, label, "At ${pct.toInt()}%", PulseSeverity.WARN)
            }
            deductions += (issues.size * COST_SERVER_HOT).coerceAtMost(CAP_SERVER)
            categories += PulseCategory(
                kind = PulseCategoryKind.SERVER,
                summary = vitals.joinToString(" · ") { (label, _, pct) -> "$label ${pct.toInt()}%" },
                issues = issues,
            )
        }

        val score = (100 - deductions).coerceIn(0, 100)
        return PulseReport(
            score = score,
            grade = gradeFor(score),
            categories = categories,
            sampleSize = states.size,
        )
    }

    // CRITICAL first, then WARN, then INFO; alphabetical within a band.
    private val issueOrder = compareByDescending<PulseIssue> { it.severity.ordinal }.thenBy { it.name }

    private fun displayName(st: HaState): String =
        st.attributes.friendly_name
            ?: st.entity_id.substringAfter('.').replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun plural(n: Int, one: String, many: String = one + "s") = if (n == 1) one else many

    private fun parseTimestamp(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }

    private fun findServerPct(states: Map<String, HaState>, vararg needles: String): Pair<HaState, Float>? =
        states.values.firstNotNullOfOrNull { st ->
            val matches = st.entity_id.startsWith("sensor.") &&
                needles.any { st.entity_id.contains(it) } &&
                st.attributes.unit_of_measurement?.trim() == "%"
            if (!matches) null else st.state.toFloatOrNull()?.let { st to it }
        }
}
