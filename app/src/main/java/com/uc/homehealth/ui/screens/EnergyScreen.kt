package com.uc.homehealth.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import com.uc.homehealth.data.HaEntitySummary
import com.uc.homehealth.data.HaEntityValue
import com.uc.homehealth.energy.Solar
import com.uc.homehealth.energy.SolarSeries
import com.uc.homehealth.ui.components.AppBottomSheet
import com.uc.homehealth.ui.components.RollingNumberText
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.energy.EnergyTimeline
import com.uc.homehealth.ui.components.energy.ProductionChart
import com.uc.homehealth.ui.components.energy.kwhValueUnit
import com.uc.homehealth.ui.components.energy.RadialSundial
import com.uc.homehealth.ui.components.energy.SunArcHero
import com.uc.homehealth.ui.components.energy.TimelineDay
import com.uc.homehealth.ui.components.glanceInkOn
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.PillShape
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.EnergyUiState
import com.uc.homehealth.ui.viewmodel.EnergyViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlin.math.abs

// The four configurable energy roles. Kept local to the screen — the setup sheet binds a
// chosen sensor entity to each. Labels are short so the connected button group fits 4-up.
private enum class EnergyRole(val label: String) {
    SOLAR("Solar"),
    BATTERY_SOC("Battery %"),
    BATTERY_POWER("Battery W"),
    GRID("Grid"),
}

@Composable
fun EnergyScreen(viewModel: EnergyViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Live clock tick — advances the sun arc + sundial without resubscribing data.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            kotlinx.coroutines.delay(30_000)
        }
    }

    // Day + hour scrub (Helios's time scrubber, as M3 components): null = follow the live
    // clock. Picking a day or dragging the hour slider re-targets the hero sun, sundial,
    // and chart cursor to that moment.
    val zone = ZoneId.systemDefault()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var scrubHour by remember { mutableStateOf<Float?>(null) }
    val today = now.atZone(zone).toLocalDate()
    val displayInstant = remember(now, selectedDate, scrubHour, today) {
        val d = selectedDate
        val h = scrubHour
        if (d == null && h == null) {
            now
        } else {
            val time = h?.let { java.time.LocalTime.ofSecondOfDay((it * 3600f).toLong().coerceIn(0L, 86_399L)) }
                ?: now.atZone(zone).toLocalTime()
            (d ?: today).atTime(time).atZone(zone).toInstant()
        }
    }

    EnergyScreenContent(
        state = state,
        displayInstant = displayInstant,
        today = today,
        selectedDate = selectedDate ?: today,
        scrubHour = scrubHour,
        onSelectDay = { date -> selectedDate = if (date == today) null else date },
        onScrubHour = { scrubHour = it },
        onBackToLive = { selectedDate = null; scrubHour = null },
        onConfigure = viewModel::openSetup,
        onRequestClouds = viewModel::ensureCloudGrid,
        onHomeTap = viewModel::openHomeDetail,
    )
}

/**
 * The energy entity-setup sheet, hosted at the top level of the nav host (above the bottom
 * nav) so its scrim covers the whole screen — same treatment as the room sheet. Rendered
 * from Navigation.kt with the activity-scoped [EnergyViewModel].
 */
@Composable
fun EnergySetupSheetOverlay(viewModel: EnergyViewModel, hazeState: HazeState? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible by viewModel.showSetup.collectAsStateWithLifecycle()
    EnergySetupSheet(
        visible = visible,
        state = state,
        hazeState = hazeState,
        onDismiss = viewModel::dismissSetup,
        onAssign = { role, id ->
            when (role) {
                EnergyRole.SOLAR -> viewModel.setSolarEntity(id)
                EnergyRole.BATTERY_SOC -> viewModel.setBatterySocEntity(id)
                EnergyRole.BATTERY_POWER -> viewModel.setBatteryPowerEntity(id)
                EnergyRole.GRID -> viewModel.setGridEntity(id)
            }
        },
        onSetKwp = viewModel::setPvPeakKwp,
    )
}

/**
 * Sun-&-sky detail sheet, opened by tapping the home pin on the map (Helios's home
 * dashboard, in our language): serif header, filled accent stat tiles, the sundial promoted
 * to the sheet's hero with its compass-style numbered ring, and sunrise/sunset below.
 * Hosted at the top level of the nav host so its scrim covers the bottom nav.
 */
@Composable
fun EnergyHomeDetailSheetOverlay(viewModel: EnergyViewModel, hazeState: HazeState? = null) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visible by viewModel.showHomeDetail.collectAsStateWithLifecycle()
    AppBottomSheet(visible = visible, onDismiss = viewModel::dismissHomeDetail, hazeState = hazeState) {
        val custom = MaterialTheme.customColors
        val cs = MaterialTheme.colorScheme
        val zone = ZoneId.systemDefault()
        var now by remember { mutableStateOf(Instant.now()) }
        LaunchedEffect(visible) {
            while (visible) {
                now = Instant.now()
                kotlinx.coroutines.delay(30_000)
            }
        }
        val local = now.atZone(zone)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .padding(top = Spacing.s, bottom = Spacing.l),
        ) {
            Text(
                text = local.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM")),
                fontFamily = InstrumentSerifFamily,
                fontSize = 26.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Sun & sky right now",
                fontFamily = MontserratFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.m))

            val irr = SolarSeries.ghiAt(state.forecast, now.epochSecond)
            val cloud = SolarSeries.cloudAt(state.forecast, now.epochSecond)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Irradiance",
                    accent = custom.sand,
                    value = irr?.let { HaEntityValue("ui:irradiance", it.toInt().toString(), "W/m²") },
                )
                MetricTile(
                    modifier = Modifier.weight(1f),
                    label = "Cloud cover",
                    accent = custom.sky,
                    value = cloud?.let { HaEntityValue("ui:cloud", it.toInt().toString(), "%") },
                )
            }
            Spacer(Modifier.height(Spacing.m))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                MetricTile(modifier = Modifier.weight(1f), label = "Production", accent = custom.coral, value = state.solar)
                MetricTile(modifier = Modifier.weight(1f), label = "Battery", accent = custom.mint, value = state.batterySoc)
            }

            Spacer(Modifier.height(Spacing.m))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                RadialSundial(
                    home = state.home,
                    instant = now,
                    forecast = state.forecast,
                    peakKwp = state.config.pvPeakKwp,
                    liveProductionKw = solarKw(state.solar),
                    showHourNumerals = true,
                    modifier = Modifier.size(300.dp),
                )
            }

            val sunTimes = state.home?.let { Solar.sunriseSunset(now, it.latitude, it.longitude) }
            if (sunTimes != null) {
                Spacer(Modifier.height(Spacing.s))
                val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SunTimeLabel(
                        icon = Icons.Outlined.WbSunny,
                        text = sunTimes.first.atZone(zone).toLocalTime().format(fmt),
                    )
                    SunTimeLabel(
                        icon = Icons.Outlined.WbTwilight,
                        text = sunTimes.second.atZone(zone).toLocalTime().format(fmt),
                    )
                }
            }
        }
    }
}

@Composable
private fun SunTimeLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.customColors.sand, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            text = text,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun EnergyScreenContent(
    state: EnergyUiState,
    displayInstant: Instant,
    today: LocalDate,
    selectedDate: LocalDate,
    scrubHour: Float?,
    onSelectDay: (LocalDate) -> Unit,
    onScrubHour: (Float) -> Unit,
    onBackToLive: () -> Unit,
    onConfigure: () -> Unit,
    onRequestClouds: () -> Unit,
    onHomeTap: () -> Unit,
) {
    val custom = MaterialTheme.customColors
    val zone = ZoneId.systemDefault()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = Spacing.xl)
            .padding(top = Spacing.l, bottom = 120.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Energy",
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 40.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Live solar, battery & grid",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConfigureButton(onClick = onConfigure)
        }

        Spacer(Modifier.height(Spacing.l))

        if (!state.hasData) {
            // Reconnect (app resumed, WS re-establishing) is NOT "not configured": show a
            // loading surface instead of falsely flashing the setup empty-state.
            if (state.isReconnecting) EnergyReconnectingCard() else EnergyEmptyState(onConfigure = onConfigure)
            return@Column
        }

        // Hero: sun arc + glowing disc + chips over the home (or the weather cloud view).
        SunArcHero(
            home = state.home,
            instant = displayInstant,
            forecast = state.forecast,
            peakKwp = state.config.pvPeakKwp,
            solar = state.solar,
            batterySoc = state.batterySoc,
            batteryPower = state.batteryPower,
            grid = state.grid,
            cloudGrid = state.cloudGrid,
            onRequestClouds = onRequestClouds,
            onHomeTap = onHomeTap,
        )

        Spacer(Modifier.height(Spacing.s))

        // 5-day selector — tap a day to re-target the whole screen. With no PV size set the
        // kWh figures are omitted entirely — never "0 kWh" for data that doesn't exist.
        val hasPvSize = state.config.pvPeakKwp > 0f
        val timelineDays = remember(state.forecast, state.config.pvPeakKwp) {
            SolarSeries.coveredDates(state.forecast, zone).map { date ->
                val samples = SolarSeries
                    .samplesForDate(state.forecast, state.config.pvPeakKwp, date, zone)
                TimelineDay(
                    date = date,
                    dow = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()),
                    dom = date.dayOfMonth.toString(),
                    kwh = samples.fold(0f) { a, s -> a + s.productionKw },
                )
            }
        }
        if (timelineDays.isNotEmpty()) {
            EnergyTimeline(
                days = timelineDays,
                selected = selectedDate,
                today = today,
                onSelect = onSelectDay,
                showKwh = hasPvSize,
            )
            Spacer(Modifier.height(Spacing.xs))
            // Hour scrubber — one time-control group with the day pills above. Dragging
            // moves the hero sun, sundial hand, and chart cursor to that moment; the Live
            // chip appears whenever the view has left "now".
            TimeScrubber(
                displayInstant = displayInstant,
                scrubHour = scrubHour,
                isLive = scrubHour == null && selectedDate == today,
                onScrub = onScrubHour,
                onBackToLive = onBackToLive,
            )
            Spacer(Modifier.height(Spacing.m))
        }

        // The radial sundial lives only in the home-detail sheet (tap the map pin) — showing
        // it here too duplicated the concept and gave the page a second competing hero.

        // Production — ONE surface for the whole concept: the selected day's forecast total
        // (hero stat, serif) + the actual-vs-forecast curve. Never rendered as a flat zero
        // line: with no PV size set it becomes a setup prompt instead.
        val forecastKw = remember(state.forecast, state.config.pvPeakKwp, selectedDate) {
            SolarSeries.samplesForDate(state.forecast, state.config.pvPeakKwp, selectedDate, zone)
                .sortedBy { it.hourOfDay }
                .map { it.productionKw }
        }
        val actualKw = remember(state.productionHistory, state.solar, selectedDate, today) {
            if (selectedDate != today) emptyList()
            else {
                val scale = if (state.solar?.unit?.trim().equals("kW", ignoreCase = true)) 1f else 0.001f
                state.productionHistory.map { it * scale }
            }
        }
        val hasCurve = forecastKw.any { it > 0f } || actualKw.any { it > 0f }
        when {
            hasCurve -> {
                // The chart cursor IS the scrub position — it follows the hour slider on
                // any day (live "now" is just the unscrubbed case on today).
                val nowFraction = displayInstant.atZone(zone).toLocalTime().toSecondOfDay() / 86_400f
                ProductionCard(
                    forecastKwh = forecastKw.sum(),
                    actualKw = actualKw,
                    forecastKw = forecastKw,
                    isToday = selectedDate == today,
                    nowFraction = nowFraction,
                )
                Spacer(Modifier.height(Spacing.m))
            }
            state.config.pvPeakKwp <= 0f -> {
                PvSizePromptCard(onConfigure = onConfigure)
                Spacer(Modifier.height(Spacing.m))
            }
        }

        // Detailed numbers — 2×2 grid of live metric tiles.
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MetricTile(modifier = Modifier.weight(1f), label = "Solar", accent = custom.sand, value = state.solar)
            MetricTile(modifier = Modifier.weight(1f), label = "Battery", accent = custom.mint, value = state.batterySoc)
        }
        Spacer(Modifier.height(Spacing.m))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            MetricTile(modifier = Modifier.weight(1f), label = "Battery power", accent = custom.lavender, value = state.batteryPower)
            MetricTile(modifier = Modifier.weight(1f), label = "Grid", accent = custom.cyan, value = state.grid)
        }
    }
}

/** Live solar production in kW (sensor may report W or kW), or null when unavailable. */
private fun solarKw(value: HaEntityValue?): Float? {
    val n = value?.numeric ?: return null
    return if (value.unit?.trim().equals("kW", ignoreCase = true)) n else n / 1000f
}

/**
 * Helios's time scrubber as stock Material 3 components: an expressive [Slider] over the
 * 24h of the selected day (serif clock readout on the left), with a "Live" pill that
 * springs in whenever the view has been scrubbed away from now.
 */
@Composable
private fun TimeScrubber(
    displayInstant: Instant,
    scrubHour: Float?,
    isLive: Boolean,
    onScrub: (Float) -> Unit,
    onBackToLive: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val haptic = rememberAppHaptics()
    val zone = ZoneId.systemDefault()
    val time = displayInstant.atZone(zone).toLocalTime()
    val sliderValue = scrubHour ?: (time.toSecondOfDay() / 3600f)
    var lastTickedHour by remember { mutableStateOf(time.hour) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        RollingNumberText(
            text = String.format(Locale.getDefault(), "%02d:%02d", time.hour, time.minute),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                color = cs.onSurface,
            ),
            labelPrefix = "scrub_time",
        )
        Slider(
            value = sliderValue,
            onValueChange = { value ->
                // Detent haptic at every whole-hour crossing while dragging.
                val hour = value.toInt()
                if (hour != lastTickedHour) {
                    lastTickedHour = hour
                    haptic.segmentTick()
                }
                onScrub(value)
            },
            valueRange = 0f..23.999f,
            colors = SliderDefaults.colors(
                thumbColor = custom.sand,
                activeTrackColor = custom.sand,
                inactiveTrackColor = cs.surfaceContainerHigh,
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = Spacing.m),
        )
        androidx.compose.animation.AnimatedVisibility(visible = !isLive) {
            Tap(onClick = { haptic.confirm(); onBackToLive() }) {
                Row(
                    modifier = Modifier
                        .background(custom.sand, PillShape)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(glanceInkOn(custom.sand), CircleShape))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Live",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = glanceInkOn(custom.sand),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigureButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    Tap(onClick = { haptic.navigation(); onClick() }) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(cs.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Tune, contentDescription = "Configure energy", tint = cs.onSurface)
        }
    }
}

// Filled accent tile in the dashboard's glance-tile language: brand color fill, luminance
// ink, label caption, rolling serif metric. The dot-on-gray version read as washed out.
@Composable
private fun MetricTile(
    modifier: Modifier,
    label: String,
    accent: Color,
    value: HaEntityValue?,
) {
    val (display, unit) = formatMetric(value)
    val ink = glanceInkOn(accent)
    Column(
        modifier = modifier
            .background(accent, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = ink.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingNumberText(
                text = display,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = InstrumentSerifFamily,
                    color = ink,
                ),
                labelPrefix = "energy_$label",
            )
            if (unit.isNotEmpty()) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = unit,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = ink.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/**
 * The production surface: ONE card for the whole concept — caption, the selected day's
 * forecast total as the hero stat (serif), and the actual-vs-forecast curve below it.
 */
@Composable
private fun ProductionCard(
    forecastKwh: Float,
    actualKw: List<Float>,
    forecastKw: List<Float>,
    isToday: Boolean,
    nowFraction: Float?,
) {
    val custom = MaterialTheme.customColors
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isToday) "Production · today" else "Production · selected day",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = cs.onSurfaceVariant,
                )
                if (forecastKwh > 0f) {
                    val (fv, fu) = kwhValueUnit(forecastKwh)
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumberText(
                            text = fv,
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = InstrumentSerifFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 38.sp,
                                color = custom.sand,
                            ),
                            labelPrefix = "production_total",
                        )
                        Spacer(Modifier.size(5.dp))
                        Text(
                            text = "$fu forecast",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                }
            }
            // Legend, so the two curves aren't abstract lines — swatches match the chart's
            // theme-aware series colors (sand accent / onSurfaceVariant).
            Column(horizontalAlignment = Alignment.End) {
                if (actualKw.any { it > 0f }) LegendRow(color = custom.sand, label = "Actual", dashed = false)
                if (forecastKw.any { it > 0f }) LegendRow(color = cs.onSurfaceVariant, label = "Forecast", dashed = true)
            }
        }
        Spacer(Modifier.height(10.dp))
        ProductionChart(actualKw = actualKw, forecastKw = forecastKw, nowFraction = nowFraction)
    }
}

@Composable
private fun LegendRow(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 3.dp)) {
        if (dashed) {
            Row {
                repeat(2) { i ->
                    Box(
                        Modifier
                            .padding(start = if (i == 0) 0.dp else 2.dp)
                            .size(width = 6.dp, height = 2.dp)
                            .background(color, PillShape),
                    )
                }
            }
        } else {
            Box(
                Modifier
                    .size(width = 14.dp, height = 3.dp)
                    .background(color, PillShape),
            )
        }
        Spacer(Modifier.size(5.dp))
        Text(
            text = label,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact, tappable nudge shown in place of the production card until a PV size is set. */
@Composable
private fun PvSizePromptCard(onConfigure: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onConfigure) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
                .padding(18.dp),
        ) {
            Text(
                text = "Unlock production forecasts",
                fontFamily = InstrumentSerifFamily,
                fontSize = 20.sp,
                color = cs.onSurface,
            )
            Text(
                text = "Set your PV peak power (kWp) in setup to chart predicted output.",
                fontFamily = MontserratFamily,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * Shown while the WebSocket re-establishes after the app comes back from the background:
 * the M3 expressive morphing loader + a calm label, in place of the dashboard content.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EnergyReconnectingCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Reconnecting",
            fontFamily = InstrumentSerifFamily,
            fontSize = 24.sp,
            color = cs.onSurface,
        )
        Text(
            text = "Restoring your live energy data…",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EnergyEmptyState(onConfigure: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(22.dp),
    ) {
        Text(
            text = "Set up your energy dashboard",
            fontFamily = InstrumentSerifFamily,
            fontSize = 24.sp,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Pick the Home Assistant sensors for your solar production, battery, and grid to see them live here.",
            fontFamily = MontserratFamily,
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))
        val haptic = rememberAppHaptics()
        // Stock M3 button (default expressive pill) rather than a hand-rolled Tap pill.
        Button(onClick = { haptic.navigation(); onConfigure() }) {
            Text(
                text = "Choose sensors",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

// ── Setup sheet ──────────────────────────────────────────────────────────────

@Composable
private fun EnergySetupSheet(
    visible: Boolean,
    state: EnergyUiState,
    hazeState: HazeState?,
    onDismiss: () -> Unit,
    onAssign: (EnergyRole, String) -> Unit,
    onSetKwp: (Float) -> Unit,
) {
    // The results list handles the gesture-nav inset itself so it scrolls edge-to-edge
    // under the pill instead of stopping above it.
    AppBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        hazeState = hazeState,
        applyNavigationBarPadding = false,
    ) {
        val cs = MaterialTheme.colorScheme
        val haptic = rememberAppHaptics()
        var activeRole by remember { mutableStateOf(EnergyRole.SOLAR) }
        var query by remember { mutableStateOf("") }
        var kwpText by remember(state.config.pvPeakKwp) {
            mutableStateOf(if (state.config.pvPeakKwp > 0f) state.config.pvPeakKwp.toString() else "")
        }
        // Optimistic selection: the tapped row highlights the same frame, while the
        // DataStore write + flow rebuild catch up in the background.
        val localAssigned = remember { androidx.compose.runtime.mutableStateMapOf<EnergyRole, String>() }
        fun assignedFor(role: EnergyRole): String = localAssigned[role] ?: assignedId(state, role)

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Energy setup",
                fontFamily = InstrumentSerifFamily,
                fontSize = 26.sp,
                color = cs.onSurface,
                modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.s),
            )
            if (state.autoWired) {
                Text(
                    text = "Connected to your Home Assistant Energy dashboard — sensors are " +
                        "wired automatically. Picking one below overrides it for that role.",
                    fontFamily = MontserratFamily,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl).padding(top = 2.dp),
                )
            }

            OutlinedTextField(
                value = kwpText,
                onValueChange = { text ->
                    kwpText = text
                    text.toFloatOrNull()?.let(onSetKwp)
                },
                label = { Text("PV peak power (kWp)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.s),
            )

            // Role selector — connected M3 Expressive button group (same pattern as the
            // Activity screen's filters). A dot marks roles that already have a sensor.
            @OptIn(ExperimentalMaterial3ExpressiveApi::class)
            ButtonGroup(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl)
                    .padding(top = Spacing.xs, bottom = Spacing.s),
            ) {
                val roles = EnergyRole.values()
                roles.forEachIndexed { index, role ->
                    ToggleButton(
                        checked = role == activeRole,
                        onCheckedChange = { checked ->
                            if (checked && role != activeRole) {
                                haptic.toggle(true)
                                activeRole = role
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            roles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    ) {
                        Text(
                            text = role.label,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        if (assignedFor(role).isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(5.dp)
                                    .background(LocalContentColor.current, CircleShape),
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search sensors") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
            )

            val sensors = remember(state.allEntities, query) {
                state.allEntities
                    .filter { it.domain == "sensor" }
                    .filter { query.isBlank() || it.friendlyName.contains(query, ignoreCase = true) || it.entityId.contains(query, ignoreCase = true) }
            }
            val currentAssigned = assignedFor(activeRole)
            val navBottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
                .asPaddingValues().calculateBottomPadding()

            // Fills the rest of the sheet and scrolls under the gesture pill; the inset
            // lives in contentPadding so the last row still settles clear of it.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = Spacing.xl),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = Spacing.xs,
                    bottom = navBottom + Spacing.m,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (currentAssigned.isNotBlank()) {
                    item(key = "clear-selection") {
                        ClearSelectionRow(
                            onClick = {
                                haptic.tick()
                                localAssigned[activeRole] = ""
                                onAssign(activeRole, "")
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                items(sensors, key = { it.entityId }) { entity ->
                    val selected = entity.entityId == currentAssigned
                    SensorRow(
                        entity = entity,
                        selected = selected,
                        onClick = {
                            haptic.tick()
                            // Tapping the selected sensor again deselects it.
                            val next = if (selected) "" else entity.entityId
                            localAssigned[activeRole] = next
                            onAssign(activeRole, next)
                        },
                        // Animate filtered results in/out and slide survivors into place.
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

/** Pinned row that clears the active role's sensor (falls back to auto-wiring). */
@Composable
private fun ClearSelectionRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Tap(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Clear selection",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SensorRow(
    entity: HaEntitySummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    // Spring the selection tint instead of snapping, so the pick reads as a response.
    val container by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) cs.primary.copy(alpha = 0.18f) else cs.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "sensor_row_bg",
    )
    Tap(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(container, MaterialTheme.shapes.small)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.friendlyName,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entity.entityId,
                    fontFamily = MontserratFamily,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = cs.primary)
            }
        }
    }
}

// ── helpers ──────────────────────────────────────────────────────────────────

private fun assignedId(state: EnergyUiState, role: EnergyRole): String = when (role) {
    EnergyRole.SOLAR -> state.config.solarProductionId
    EnergyRole.BATTERY_SOC -> state.config.batterySocId
    EnergyRole.BATTERY_POWER -> state.config.batteryPowerId
    EnergyRole.GRID -> state.config.gridPowerId
}

/** Format a live energy value into a display number + unit, converting W→kW when large. */
private fun formatMetric(value: HaEntityValue?): Pair<String, String> {
    if (value == null) return "—" to ""
    val num = value.numeric
    val unit = value.unit?.trim().orEmpty()
    if (num == null) return value.state to unit
    return when {
        unit.equals("W", ignoreCase = true) && abs(num) >= 1000f ->
            String.format(Locale.getDefault(), "%.2f", num / 1000f) to "kW"
        unit.equals("W", ignoreCase = true) ->
            num.toInt().toString() to "W"
        else -> {
            val s = if (num % 1f == 0f) num.toInt().toString() else String.format(Locale.getDefault(), "%.1f", num)
            s to unit
        }
    }
}

