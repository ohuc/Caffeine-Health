package com.uc.homehealth

import com.uc.homehealth.data.HaState
import com.uc.homehealth.data.HaStateAttributes
import com.uc.homehealth.data.PulseAnalyzer
import com.uc.homehealth.data.PulseCategoryKind
import com.uc.homehealth.data.PulseGrade
import com.uc.homehealth.data.PulseReport
import com.uc.homehealth.data.PulseSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class PulseAnalyzerTest {

    private val now = 1_750_000_000_000L
    private val dayMs = 24 * 3_600_000L

    private fun iso(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC).toString()

    private fun state(
        id: String,
        st: String,
        deviceClass: String? = null,
        unit: String? = null,
        name: String? = null,
        lastUpdated: String? = null,
    ) = HaState(
        entity_id = id,
        state = st,
        attributes = HaStateAttributes(
            friendly_name = name,
            brightness = null,
            rgb_color = null,
            color_temp_kelvin = null,
            current_temperature = null,
            temperature = null,
            current_humidity = null,
            humidity = null,
            device_class = deviceClass,
            unit_of_measurement = unit,
        ),
        last_changed = null,
        last_updated = lastUpdated ?: iso(now - 60_000L),
    )

    private fun analyze(
        vararg states: HaState,
        updates: List<String> = emptyList(),
        drops: List<Long> = emptyList(),
    ): PulseReport = PulseAnalyzer.analyze(
        states = states.associateBy { it.entity_id },
        pendingUpdates = updates,
        dropTimesMs = drops,
        nowMs = now,
    )

    private fun PulseReport.category(kind: PulseCategoryKind) =
        categories.firstOrNull { it.kind == kind }

    // ── Healthy home ─────────────────────────────────────────────────────────

    @Test
    fun `healthy home scores 100`() {
        val report = analyze(
            state("light.kitchen", "on"),
            state("sensor.kitchen_battery", "84", deviceClass = "battery", unit = "%"),
            state("sensor.kitchen_temp", "21.5", deviceClass = "temperature", unit = "°C"),
        )
        assertEquals(100, report.score)
        assertEquals(PulseGrade.HEALTHY, report.grade)
        assertEquals(0, report.issueCount)
        assertTrue(report.category(PulseCategoryKind.DEVICES)!!.healthy)
        assertEquals("All 1 battery OK", report.category(PulseCategoryKind.BATTERIES)!!.summary)
    }

    // ── Batteries ────────────────────────────────────────────────────────────

    @Test
    fun `low and critical batteries deduct and rank by severity`() {
        val report = analyze(
            state("sensor.remote_battery", "8", deviceClass = "battery", unit = "%", name = "Remote"),
            state("sensor.door_battery", "15", deviceClass = "battery", unit = "%", name = "Door"),
            state("sensor.ok_battery", "55", deviceClass = "battery", unit = "%"),
        )
        // critical 6 + low 3 = 9
        assertEquals(91, report.score)
        val cat = report.category(PulseCategoryKind.BATTERIES)!!
        assertEquals(2, cat.issues.size)
        assertEquals("Remote", cat.issues[0].name) // 8% sorts before 15%
        assertEquals(PulseSeverity.CRITICAL, cat.issues[0].severity)
        assertEquals(PulseSeverity.WARN, cat.issues[1].severity)
    }

    @Test
    fun `battery deductions are capped`() {
        val cells = (1..10).map {
            state("sensor.b$it", "5", deviceClass = "battery", unit = "%")
        }.toTypedArray()
        val report = analyze(*cells)
        // 10 × 6 = 60 raw, capped at 25
        assertEquals(75, report.score)
    }

    @Test
    fun `non-percent battery sensors are ignored`() {
        val report = analyze(
            state("sensor.car_battery_voltage", "12.4", deviceClass = "battery", unit = "V"),
        )
        assertNull(report.category(PulseCategoryKind.BATTERIES))
    }

    // ── Devices ──────────────────────────────────────────────────────────────

    @Test
    fun `unavailable controllable device is critical, sensors are not devices`() {
        val report = analyze(
            state("light.hall", "unavailable", name = "Hall"),
            state("sensor.orphan", "unavailable"),
        )
        val cat = report.category(PulseCategoryKind.DEVICES)!!
        assertEquals(1, cat.issues.size)
        assertEquals("Hall", cat.issues[0].name)
        assertEquals("Unreachable", cat.issues[0].detail)
        assertEquals(92, report.score)
    }

    @Test
    fun `device deductions are capped`() {
        val lights = (1..6).map { state("light.l$it", "unavailable") }.toTypedArray()
        // 6 × 8 = 48 raw, capped at 30
        assertEquals(70, analyze(*lights).score)
    }

    // ── Stale sensors ────────────────────────────────────────────────────────

    @Test
    fun `sensor silent past threshold is flagged as info`() {
        val report = analyze(
            state("sensor.dead", "21.0", name = "Dead", lastUpdated = iso(now - 3 * dayMs)),
            state("sensor.fresh", "20.0"),
        )
        val cat = report.category(PulseCategoryKind.SENSORS)!!
        assertEquals(1, cat.issues.size)
        assertEquals("Silent for 3d", cat.issues[0].detail)
        assertEquals(PulseSeverity.INFO, cat.issues[0].severity)
        assertEquals(98, report.score)
    }

    @Test
    fun `unavailable sensors are not double-counted as stale`() {
        val report = analyze(
            state("sensor.gone", "unavailable", lastUpdated = iso(now - 5 * dayMs)),
        )
        val cat = report.category(PulseCategoryKind.SENSORS)
        // Only sensor is unavailable → excluded from the stale scan → category omitted.
        assertNull(cat)
    }

    // ── Updates ──────────────────────────────────────────────────────────────

    @Test
    fun `pending updates deduct and are capped`() {
        assertEquals(94, analyze(updates = listOf("Core", "OS", "HACS")).score)
        assertEquals(90, analyze(updates = (1..9).map { "u$it" }).score)
        assertEquals("Everything up to date", analyze().category(PulseCategoryKind.UPDATES)!!.summary)
    }

    // ── Connectivity ─────────────────────────────────────────────────────────

    @Test
    fun `drops beyond the daily allowance deduct, old drops expire`() {
        val drops = List(5) { now - it * 3_600_000L } // 5 drops in the last 5 hours
        val report = analyze(drops = drops)
        // (5 − 2) × 3 = 9
        assertEquals(91, report.score)
        val cat = report.category(PulseCategoryKind.CONNECTIVITY)!!
        assertEquals(PulseSeverity.WARN, cat.issues.single().severity)

        val ancient = analyze(drops = listOf(now - 8 * dayMs))
        assertEquals(100, ancient.score)
        assertTrue(ancient.category(PulseCategoryKind.CONNECTIVITY)!!.healthy)
    }

    @Test
    fun `few drops surface as info without deduction`() {
        val report = analyze(drops = listOf(now - 2 * dayMs))
        assertEquals(100, report.score)
        val cat = report.category(PulseCategoryKind.CONNECTIVITY)!!
        assertFalse(cat.healthy)
        assertEquals(PulseSeverity.INFO, cat.issues.single().severity)
    }

    // ── Server vitals ────────────────────────────────────────────────────────

    @Test
    fun `server category appears only with system monitor sensors`() {
        assertNull(analyze().category(PulseCategoryKind.SERVER))

        val report = analyze(
            state("sensor.processor_use", "94", unit = "%"),
            state("sensor.memory_use_percent", "41", unit = "%"),
        )
        val cat = report.category(PulseCategoryKind.SERVER)!!
        assertEquals("CPU 94% · RAM 41%", cat.summary)
        assertEquals(1, cat.issues.size) // only the hot CPU
        assertEquals(95, report.score)
    }

    // ── Grades & omission ────────────────────────────────────────────────────

    @Test
    fun `grade bands`() {
        assertEquals(PulseGrade.HEALTHY, PulseAnalyzer.gradeFor(90))
        assertEquals(PulseGrade.FAIR, PulseAnalyzer.gradeFor(89))
        assertEquals(PulseGrade.FAIR, PulseAnalyzer.gradeFor(70))
        assertEquals(PulseGrade.NEEDS_CARE, PulseAnalyzer.gradeFor(69))
    }

    @Test
    fun `categories with nothing to measure are omitted`() {
        val report = analyze(state("light.kitchen", "on"))
        assertNull(report.category(PulseCategoryKind.BATTERIES))
        assertNull(report.category(PulseCategoryKind.SENSORS))
        assertNull(report.category(PulseCategoryKind.SERVER))
        assertEquals(1, report.sampleSize)
    }
}
