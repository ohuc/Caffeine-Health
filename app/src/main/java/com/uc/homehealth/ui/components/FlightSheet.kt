package com.uc.homehealth.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditCalendar
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import com.uc.homehealth.R
import com.uc.homehealth.data.HaAutomation
import com.uc.homehealth.data.HaFlight
import com.uc.homehealth.data.ScheduledFlightAdd
import com.uc.homehealth.data.ScheduledFlightStatus
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.atan2

private enum class FlightTab(val label: String, val icon: ImageVector) {
    Tracking("Tracking", Icons.Outlined.MyLocation),
    Automations("Automations", Icons.Outlined.SmartToy),
    Extras("Extras", Icons.Outlined.Place),
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FlightSheetOverlay(
    visible: Boolean,
    flights: List<HaFlight>,
    isAddingFlight: Boolean,
    flightAddError: String? = null,
    flightAutomationIds: List<String> = emptyList(),
    allAutomations: List<HaAutomation> = emptyList(),
    isFlightRadar24Available: Boolean = true,
    scheduledFlights: List<ScheduledFlightAdd> = emptyList(),
    hazeState: HazeState? = null,
    onDismiss: () -> Unit,
    onAddFlight: (String) -> Unit,
    onRemoveFlight: (HaFlight) -> Unit = {},
    onDismissFlightAddError: () -> Unit = {},
    onScheduleFlight: (String, Long) -> Unit = { _, _ -> },
    onCancelScheduledFlight: (String) -> Unit = {},
    onAddFlightAutomation: (String) -> Unit = {},
    onRemoveFlightAutomation: (String) -> Unit = {},
    onTriggerFlightAutomation: (String) -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf(FlightTab.Tracking) }

    AppBottomSheet(visible = visible, onDismiss = onDismiss, hazeState = hazeState) {
        if (!isFlightRadar24Available) {
            InstallFlightRadar24Card()
            Spacer(modifier = Modifier.height(8.dp))
            return@AppBottomSheet
        }
        FlightTabBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        AnimatedContent(
            targetState = selectedTab,
            // weight(1f, fill = false) gives the tab body bounded height (= remaining
            // sheet space) without forcing it to fill. Required so that the
            // verticalScroll inside TrackingTab has a finite viewport — otherwise it
            // would receive infinite height constraints and crash.
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                if (forward) {
                    (slideInHorizontally { it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally { -it } + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(180))) togetherWith
                        (slideOutHorizontally { it } + fadeOut(tween(150)))
                }
            },
            label = "flight_tab",
        ) { current ->
            Column(modifier = Modifier.fillMaxWidth()) {
                when (current) {
                    FlightTab.Tracking -> TrackingTab(
                        flights = flights,
                        isAddingFlight = isAddingFlight,
                        flightAddError = flightAddError,
                        scheduledFlights = scheduledFlights,
                        onAddFlight = onAddFlight,
                        onRemoveFlight = onRemoveFlight,
                        onDismissError = onDismissFlightAddError,
                        onScheduleFlight = onScheduleFlight,
                        onCancelScheduledFlight = onCancelScheduledFlight,
                    )
                    FlightTab.Automations -> AutomationsTab(
                        flightAutomationIds = flightAutomationIds,
                        allAutomations = allAutomations,
                        onAddAutomation = onAddFlightAutomation,
                        onRemoveAutomation = onRemoveFlightAutomation,
                        onTriggerAutomation = onTriggerFlightAutomation,
                    )
                    FlightTab.Extras -> PlaceholderTab(text = "Extras coming soon")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private const val FR24_REPO_URL = "https://github.com/AlexandrErohin/home-assistant-flightradar24"
private val InstallHaBlue = Color(0xFF18BCF2)

@Composable
private fun InstallFlightRadar24Card() {
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Outlined.Flight,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Flight tracking",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
        }
        Text(
            text = "The FlightRadar24 integration isn't installed on your Home Assistant. " +
                "Install it to track flights from the dashboard.",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = cs.onSurfaceVariant,
        )
        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(FR24_REPO_URL)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = InstallHaBlue,
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_home_assistant),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Get the FlightRadar24 integration",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                )
                Text(
                    text = "Opens the install guide on GitHub",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FlightTabBar(
    selected: FlightTab,
    onSelect: (FlightTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val tabs = FlightTab.values().toList()

    ButtonGroup(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = tab == selected
            ToggleButton(
                checked = active,
                onCheckedChange = {
                    if (!active) {
                        haptic.navigation()
                        onSelect(tab)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    tabs.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                colors = ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = cs.primary,
                    checkedContentColor = cs.onPrimary,
                    containerColor = cs.surfaceContainerHigh,
                    contentColor = cs.onSurface,
                ),
                // Tighter than the ToggleButton default — at 1/3 sheet width the
                // stock padding left "Automations" too little room and it wrapped.
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    // Single line, shrink-to-fit: short labels render at 13sp, long ones
                    // ("Automations") step down instead of wrapping mid-word.
                    Text(
                        text = tab.label,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 9.sp,
                            maxFontSize = 13.sp,
                            stepSize = 0.25.sp,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackingTab(
    flights: List<HaFlight>,
    isAddingFlight: Boolean,
    flightAddError: String?,
    scheduledFlights: List<ScheduledFlightAdd>,
    onAddFlight: (String) -> Unit,
    onRemoveFlight: (HaFlight) -> Unit,
    onDismissError: () -> Unit,
    onScheduleFlight: (String, Long) -> Unit,
    onCancelScheduledFlight: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val coroutineScope = rememberCoroutineScope()

    // Pager state survives flight-list changes because it keys on size only;
    // we drive currentPage explicitly when a flight is added/removed.
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { flights.size })

    // When a freshly-added flight lands, slide to it so the user sees the new card.
    LaunchedEffect(flights.size) {
        if (flights.isNotEmpty()) {
            val target = flights.lastIndex
            if (pagerState.currentPage != target) {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    // Hoist input text so the field survives moving between top and bottom positions
    // (AnimatedVisibility tears the OutlinedTextField down and re-creates it otherwise,
    // which would wipe whatever the user typed).
    var inputText by rememberSaveable { mutableStateOf("") }
    // Epoch-day the user picked for a future-dated add; -1 = none (track now).
    var pickedDay by rememberSaveable { mutableStateOf(-1L) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val submitFlight: (String) -> Unit = { q ->
        val day = pickedDay
        if (day > LocalDate.now().toEpochDay()) onScheduleFlight(q, day) else onAddFlight(q)
        inputText = ""
        pickedDay = -1L
    }

    // Scrollable wrapper — when a flight is tracked, the carousel + input together
    // exceed the visible sheet area while the IME is open, so the user needs to
    // scroll the input into view. Per-input BringIntoViewRequester (below) does
    // this automatically on focus.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
    // Title + count (count animates between empty / single / "x / n").
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Outlined.Flight,
                contentDescription = null,
                tint = cs.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = "Tracking",
                fontFamily = InstrumentSerifFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
                lineHeight = 32.sp,
                color = cs.onSurface,
            )
        }
        val baseCount = when {
            flights.isEmpty() -> "No flights tracked yet"
            flights.size == 1 -> "1 flight tracked"
            else -> "${pagerState.currentPage + 1} / ${flights.size} tracked"
        }
        val countText =
            if (scheduledFlights.isEmpty()) baseCount
            else "$baseCount · ${scheduledFlights.size} scheduled"
        AnimatedContent(
            targetState = countText,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "flight_count",
        ) { text ->
            Text(
                text = text,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    // ── Add-flight failure banner ─────────────────────────────────────────
    AnimatedVisibility(
        visible = flightAddError != null,
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(160)),
    ) {
        // Snapshot the message so the exit transition keeps showing the old text
        // after the upstream state clears.
        val lastMessage = remember(flightAddError) { flightAddError ?: "" }
        FlightAddErrorBanner(message = lastMessage, onDismiss = onDismissError)
    }

    // ── Add-flight input, position #1 (above the carousel, shown when empty) ─
    AnimatedVisibility(
        visible = flights.isEmpty(),
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
    ) {
        AddFlightInput(
            text = inputText,
            onTextChange = { inputText = it },
            isLoading = isAddingFlight,
            pickedDay = pickedDay,
            onPickDate = { showDatePicker = true },
            onClearDate = { pickedDay = -1L },
            onSubmit = submitFlight,
        )
    }

    // ── Body: empty / loading card / pager. Cross-fades + scales between them. ─
    val flightsKey: Int = when {
        flights.isEmpty() && isAddingFlight -> -1   // loading skeleton card
        flights.isEmpty() -> 0                       // nothing
        else -> 1                                    // pager
    }
    AnimatedContent(
        targetState = flightsKey,
        transitionSpec = {
            (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220))) togetherWith
                (fadeOut(tween(160)) + scaleOut(targetScale = 0.96f, animationSpec = tween(160)))
        },
        label = "tracking_body",
    ) { key ->
        when (key) {
            -1 -> Column {
                Spacer(modifier = Modifier.height(12.dp))
                LoadingFlightCard()
            }
            0 -> Spacer(modifier = Modifier.height(0.dp))
            else -> Column {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    pageSpacing = 10.dp,
                    key = { i -> flights[i].id },
                ) { page ->
                    val flight = flights[page]
                    val handleRemove: () -> Unit = {
                        // For the current page (and only when other pages exist),
                        // slide to a neighbour first so removal feels continuous
                        // instead of the page just blinking out.
                        coroutineScope.launch {
                            if (flights.size > 1 && page == pagerState.currentPage) {
                                val target = if (page == flights.lastIndex) page - 1 else page + 1
                                pagerState.animateScrollToPage(target)
                            }
                            onRemoveFlight(flight)
                        }
                    }
                    if (isScheduledOnly(flight)) {
                        ScheduledFlightCard(flight = flight, onRemove = handleRemove)
                    } else {
                        FlightCard(flight = flight, onRemove = handleRemove)
                    }
                }
                // Page indicator dots + "X / N" pill — only meaningful with >1 flight.
                AnimatedVisibility(
                    visible = flights.size > 1,
                    enter = fadeIn(tween(220)) + expandVertically(animationSpec = tween(220)),
                    exit = fadeOut(tween(160)) + shrinkVertically(animationSpec = tween(180)),
                ) {
                    PageIndicator(
                        currentPage = pagerState.currentPage,
                        pageCount = flights.size,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }

    // ── Add-flight input, position #2 (below the carousel, shown when populated) ─
    AnimatedVisibility(
        visible = flights.isNotEmpty(),
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(120)),
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            AddFlightInput(
                text = inputText,
                onTextChange = { inputText = it },
                isLoading = isAddingFlight,
                pickedDay = pickedDay,
                onPickDate = { showDatePicker = true },
                onClearDate = { pickedDay = -1L },
                onSubmit = submitFlight,
            )
        }
    }

    // ── Scheduled queue — future-dated adds waiting on-device ─────────────
    AnimatedVisibility(
        visible = scheduledFlights.isNotEmpty(),
        enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(160)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SCHEDULED",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = cs.onSurfaceVariant,
            )
            scheduledFlights.forEach { entry ->
                ScheduledAddRow(entry = entry, onCancel = { onCancelScheduledFlight(entry.id) })
            }
            Text(
                text = "FlightRadar24 can't pre-book a date, so the track command is sent " +
                    "on the flight's day. If that number flies more than once that day, " +
                    "the first match gets tracked.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }

    // A little tail padding so the input doesn't sit flush with the IME gap.
    Spacer(modifier = Modifier.height(12.dp))
    } // end verticalScroll Column

    // ── Future-date picker (today and earlier mean "track now") ───────────
    if (showDatePicker) {
        val todayEpochDay = remember { LocalDate.now().toEpochDay() }
        val datePickerState = rememberDatePickerState(
            // DatePicker works in UTC-midnight millis; epoch-day * 86_400_000 is exactly that.
            initialSelectedDateMillis = (if (pickedDay >= 0) pickedDay else todayEpochDay + 1) * 86_400_000L,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis / 86_400_000L >= todayEpochDay
                override fun isSelectableYear(year: Int): Boolean = year >= LocalDate.now().year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        val day = ms / 86_400_000L
                        pickedDay = if (day > todayEpochDay) day else -1L
                    }
                    showDatePicker = false
                }) { Text("Set date") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun FlightAddErrorBanner(message: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(cs.errorContainer)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Flight,
            contentDescription = null,
            tint = cs.onErrorContainer,
            modifier = Modifier.padding(top = 1.dp).size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            color = cs.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        Tap(onClick = onDismiss) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Dismiss",
                    tint = cs.onErrorContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { i ->
            val selected = i == currentPage
            val dotWidth by animateDpAsState(
                targetValue = if (selected) 18.dp else 6.dp,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                label = "dot_width_$i",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(CircleShape)
                    .background(if (selected) cs.onSurface else cs.onSurfaceVariant.copy(alpha = 0.30f)),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        ) {
            Text(
                text = "${currentPage + 1} / $pageCount",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
                color = cs.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingFlightCard() {
    // Placeholder card shown while HA is fetching the first flight's data.
    // Replaces the empty hint so the layout doesn't jump when the real card lands.
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
        Text(
            text = "Fetching flight…",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AddFlightInput(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    pickedDay: Long,           // epoch-day for a future-dated add; -1 = track now
    onPickDate: () -> Unit,
    onClearDate: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = MaterialTheme.customColors.lavender
    val submit: () -> Unit = {
        val q = text.trim()
        if (q.isNotEmpty() && !isLoading) {
            onSubmit(q)
        }
    }
    val canSubmit = text.trim().isNotEmpty() && !isLoading
    val hasDate = pickedDay >= 0

    // Bring this field into the scrollable viewport when it gains focus, so it
    // ends up just above the keyboard instead of clipped off the bottom of the sheet.
    val bringIntoView = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusChanged { state ->
                if (state.isFocused) {
                    coroutineScope.launch { bringIntoView.bringIntoView() }
                }
            },
        singleLine = true,
        placeholder = {
            Text(
                text = "Flight number, callsign, or registration",
                fontFamily = MontserratFamily,
                fontSize = 13.sp,
            )
        },
        shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { submit() }),
        trailingIcon = {
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                ContainedLoadingIndicator(modifier = Modifier.padding(end = 8.dp).size(36.dp))
            }
            AnimatedVisibility(
                visible = !isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tap(onClick = onPickDate) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (hasDate) accent.copy(alpha = 0.22f) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EditCalendar,
                                contentDescription = "Track on a future date",
                                tint = if (hasDate) accent else cs.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    FilledIconButton(
                        onClick = submit,
                        enabled = canSubmit,
                        colors = IconButtonDefaults.filledIconButtonColors(),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Send,
                            contentDescription = if (hasDate) "Schedule tracking" else "Track this flight",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(),
    )

    // Future-date chip + the honest mechanics: FR24 has no date input, so the actual
    // command is held on-device and sent on the chosen day.
    AnimatedVisibility(
        visible = hasDate,
        enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
        exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(120)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .padding(start = 12.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.EditCalendar,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (pickedDay >= 0) LocalDate.ofEpochDay(pickedDay).format(ScheduledDayFormatter) else "",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = cs.onSurface,
                )
                Tap(onClick = onClearDate) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear date",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
            Text(
                text = "Nothing is tracked yet — the app sends the track command to " +
                    "FlightRadar24 on that day and verifies it caught the right flight.",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = cs.onSurfaceVariant,
            )
        }
    }
    } // end Column
}

// ── Scheduled-add queue row ─────────────────────────────────────────────
// One future-dated add waiting on-device. The copy is deliberately explicit about
// the mechanics: FR24 only tracks "the nearest instance" of a number, so the real
// command is sent on the target day and the result is then date-verified.
@Composable
private fun ScheduledAddRow(
    entry: ScheduledFlightAdd,
    onCancel: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val accent = MaterialTheme.customColors.lavender
    val haptic = rememberAppHaptics()
    val failed = entry.status == ScheduledFlightStatus.FAILED
    val dayLabel = LocalDate.ofEpochDay(entry.targetEpochDay).format(ScheduledDayFormatter)
    val subline = when (entry.status) {
        ScheduledFlightStatus.PENDING ->
            "Will be sent to FlightRadar24 on $dayLabel — not tracked until then"
        ScheduledFlightStatus.SENT ->
            entry.statusDetail ?: "Sent to FlightRadar24 — confirming it's the $dayLabel flight"
        ScheduledFlightStatus.FAILED ->
            entry.statusDetail ?: "Couldn't start tracking on $dayLabel"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (failed) cs.errorContainer else accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.EditCalendar,
                contentDescription = null,
                tint = if (failed) cs.onErrorContainer else accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = entry.query,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = dayLabel,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (failed) cs.error else accent,
                    maxLines = 1,
                )
            }
            Text(
                text = subline,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = if (failed) cs.error else cs.onSurfaceVariant,
                maxLines = 3,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Tap(onClick = {
            haptic.confirm()
            onCancel()
        }) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = if (failed) "Dismiss" else "Cancel scheduled tracking for ${entry.query}",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ── Automations tab — shows added automations + picker panel ─────────────

@Composable
private fun AutomationsTab(
    flightAutomationIds: List<String>,
    allAutomations: List<HaAutomation>,
    onAddAutomation: (String) -> Unit,
    onRemoveAutomation: (String) -> Unit,
    onTriggerAutomation: (String) -> Unit,
) {
    var showPicker by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = showPicker,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { it } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally { -it } + fadeOut(tween(150)))
            } else {
                (slideInHorizontally { -it } + fadeIn(tween(180))) togetherWith
                    (slideOutHorizontally { it } + fadeOut(tween(150)))
            }
        },
        label = "automation_panel",
    ) { isPicking ->
        if (isPicking) {
            AutomationPickerPanel(
                allAutomations = allAutomations,
                selectedIds = flightAutomationIds.toSet(),
                onBack = { showPicker = false },
                onPick = { entityId ->
                    onAddAutomation(entityId)
                    showPicker = false
                },
            )
        } else {
            AutomationsMainContent(
                flightAutomationIds = flightAutomationIds,
                allAutomations = allAutomations,
                onRemoveAutomation = onRemoveAutomation,
                onTriggerAutomation = onTriggerAutomation,
                onAddTap = { showPicker = true },
            )
        }
    }
}

@Composable
private fun AutomationsMainContent(
    flightAutomationIds: List<String>,
    allAutomations: List<HaAutomation>,
    onRemoveAutomation: (String) -> Unit,
    onTriggerAutomation: (String) -> Unit,
    onAddTap: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    var editMode by remember { mutableStateOf(false) }

    LaunchedEffect(flightAutomationIds.isEmpty()) {
        if (flightAutomationIds.isEmpty() && editMode) editMode = false
    }

    val automationMap = remember(allAutomations) { allAutomations.associateBy { it.entityId } }
    val addedAutomations = remember(flightAutomationIds, automationMap) {
        flightAutomationIds.mapNotNull { id ->
            automationMap[id] ?: HaAutomation(id, id.substringAfter('.').replace('_', ' ')
                .split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Header
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = cs.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Automations",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 30.sp,
                    lineHeight = 32.sp,
                    color = cs.onSurface,
                )
            }
            Text(
                text = if (addedAutomations.isEmpty()) "No automations added yet"
                else "${addedAutomations.size} automation${if (addedAutomations.size == 1) "" else "s"} linked",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Edit pill
        if (addedAutomations.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Tap(onClick = {
                    if (editMode) haptic.tick() else haptic.confirm()
                    editMode = !editMode
                }) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50.dp))
                            .background(if (editMode) cs.primary else cs.surfaceContainerHigh)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (editMode) Icons.Outlined.Check else Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = if (editMode) cs.onPrimary else cs.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (editMode) "Done" else "Edit",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (editMode) cs.onPrimary else cs.onSurface,
                        )
                    }
                }
            }
        }

        // Automation rows
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            addedAutomations.forEach { automation ->
                AutomationRow(
                    automation = automation,
                    editMode = editMode,
                    onTrigger = { onTriggerAutomation(automation.entityId) },
                    onRemove = { onRemoveAutomation(automation.entityId) },
                )
            }
        }

        // Add Your Flight Automations button
        if (!editMode) {
            Tap(onClick = onAddTap, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(cs.surfaceContainerHigh)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            tint = cs.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = "Add Your Automations",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = cs.onSurface,
                        )
                        Text(
                            text = if (addedAutomations.isEmpty())
                                "Add flight-related automations to toggle here"
                            else
                                "Link another automation to this flight tracker",
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun AutomationRow(
    automation: HaAutomation,
    editMode: Boolean,
    onTrigger: () -> Unit,
    onRemove: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(cs.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = automation.friendlyName,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                text = automation.entityId,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (editMode) {
            Tap(onClick = onRemove) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(cs.errorContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove",
                        tint = cs.onErrorContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        } else {
            PillToggle(
                isOn = automation.isEnabled,
                onToggle = { onTrigger() },
                color = cs.primary,
                ink = cs.onPrimary,
            )
        }
    }
}

@Composable
private fun AutomationPickerPanel(
    allAutomations: List<HaAutomation>,
    selectedIds: Set<String>,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    val filtered = remember(allAutomations, query, selectedIds) {
        allAutomations
            .filter { it.entityId !in selectedIds }
            .filter { query.isBlank() || it.friendlyName.contains(query, ignoreCase = true) || it.entityId.contains(query, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tap(onClick = onBack) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = cs.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Add an automation",
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 28.sp,
                    lineHeight = 26.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = "Choose a flight-related automation",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Search automations",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            },
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            colors = OutlinedTextFieldDefaults.colors(),
        )

        // Automation list
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank()) "All automations already added" else "No automations match \"$query\"",
                    fontFamily = MontserratFamily,
                    fontSize = 13.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.entityId }) { automation ->
                    Tap(
                        onClick = { onPick(automation.entityId) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(cs.surfaceContainerHigh)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(cs.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SmartToy,
                                    contentDescription = null,
                                    tint = cs.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(
                                    text = automation.friendlyName,
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = cs.onSurface,
                                    maxLines = 1,
                                )
                                Text(
                                    text = automation.entityId,
                                    fontFamily = MontserratFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = cs.onSurfaceVariant,
                                    maxLines = 1,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                tint = cs.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PlaceholderTab(text: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

// ── Design tokens unique to the flight card ────────────────────────────
private val RouteAmber = Color(0xFFF2C541)
private val RemainingDim = Color.White.copy(alpha = 0.18f)
private val LiveBg = Color(0xFFA8E6A1)
private val LiveInk = Color(0xFF0D2510)
private val AirlineFallbackBg = Color(0xFFFFD24A)
private val AirlineFallbackInk = Color(0xFF1A1500)
private val ScheduledTint = Color(0xFFFFC56B)

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ScheduledDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

@Composable
private fun FlightCard(flight: HaFlight, onRemove: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val derived = remember(flight) { deriveFlightDisplay(flight) }
    val photoUrl = flight.aircraftPhotoMedium ?: flight.aircraftPhotoSmall ?: flight.aircraftPhotoLarge

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Header row — TRACKED FLIGHT on the left, status chip + remove on the right.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "TRACKED FLIGHT",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = cs.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (derived.isLive) LiveChip() else StatusChip(label = derived.statusLabel)
                Tap(onClick = {
                    haptic.confirm()
                    onRemove()
                }) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Stop tracking ${flight.flightNumber}",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // 2. Flight identity row — flight column on the left, photo + ARRIVES grouped on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = derived.flightNumber,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 40.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.5).sp,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AirlineBadge(monogram = derived.monogram)
                    Text(
                        text = derived.airlineSubline,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = flight.aircraftModel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cs.surfaceContainer),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "ARRIVES",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp,
                        color = cs.onSurfaceVariant,
                    )
                    Text(
                        text = derived.arrivalTime,
                        fontFamily = InstrumentSerifFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Normal,
                        fontSize = 24.sp,
                        lineHeight = 24.sp,
                        letterSpacing = (-0.5).sp,
                        color = cs.onSurface,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    if (derived.etaText.isNotEmpty()) {
                        Text(
                            text = derived.etaText,
                            fontFamily = MontserratFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.5.sp,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 3. Route arc (Column-stacked: canvas + labels row to avoid overlap)
        RouteArc(
            progress = derived.progress,
            originCode = derived.originCode,
            originTime = derived.originTime,
            destCode = derived.destCode,
            destTime = derived.destTime,
            punchColor = cs.surfaceContainerHigh,
        )

        // 4. Stat tiles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatTile(
                label = "ALTITUDE",
                value = formatThousands(flight.altitudeFt),
                unit = "ft",
                tint = MaterialTheme.customColors.sky,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "SPEED",
                value = flight.groundSpeedKts.toString(),
                unit = "kts",
                tint = MaterialTheme.customColors.mint,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "REMAINING",
                value = formatRemaining(flight.distanceKm),
                unit = "km",
                tint = cs.primary,
                modifier = Modifier.weight(1f),
            )
        }

        // 5. Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = derived.aircraftLabel,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = derived.footerRight,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

// ── Scheduled card — FR24 schedule entry without live position data ────
// FR24 returns a "schedule" tracked_type for flights that exist in the
// timetable but have not yet been assigned an aircraft / departed. In that
// state the integration only guarantees id, flight_number, callsign and
// tracked_type, so the live layout (route arc + altitude/speed/remaining)
// collapses to zeros and dashes. This compact variant drops everything
// that needs live data.
private fun isScheduledOnly(f: HaFlight): Boolean {
    if (f.trackedType?.equals("schedule", ignoreCase = true) == true) return true
    // Defensive fallback: integration changed but every live signal is empty.
    return f.altitudeFt == 0 && f.groundSpeedKts == 0 && f.distanceKm == 0f &&
        f.originIata.isNullOrBlank() && f.destinationIata.isNullOrBlank() &&
        f.realDeparture == null && f.estimatedDeparture == null &&
        f.scheduledDeparture == null
}

@Composable
private fun ScheduledFlightCard(flight: HaFlight, onRemove: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val haptic = rememberAppHaptics()
    val callsign = flight.callsign?.takeIf { it.isNotBlank() }
    val airlineName = flight.airline?.takeIf { it.isNotBlank() }
    val subline = listOfNotNull(callsign, airlineName).joinToString(" · ")
        .ifEmpty { flight.flightNumber }
    val monogram = (flight.airlineIata?.takeIf { it.isNotBlank() }
        ?: flight.flightNumber.takeWhile { it.isLetter() }.take(2).uppercase()
            .ifEmpty { flight.flightNumber.take(2).uppercase() })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "SCHEDULED FLIGHT",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
                color = cs.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ScheduledChip()
                Tap(onClick = {
                    haptic.confirm()
                    onRemove()
                }) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Stop tracking ${flight.flightNumber}",
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Identity row — flight number + airline subline, plane silhouette on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = flight.flightNumber,
                    fontFamily = InstrumentSerifFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 40.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.5).sp,
                    color = cs.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AirlineBadge(monogram = monogram)
                    Text(
                        text = subline,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ScheduledTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flight,
                    contentDescription = null,
                    tint = ScheduledTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // Awaiting row — pulsing amber dot + explanation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ScheduledTint.copy(alpha = 0.15f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PulsingDot(color = ScheduledTint)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Awaiting departure",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = cs.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = "Live tracking begins when the aircraft is in the air.",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 11.sp,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ScheduledChip() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(ScheduledTint.copy(alpha = 0.22f))
            .padding(start = 9.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        PulsingDot(color = ScheduledTint, size = 5.dp)
        Text(
            text = "Scheduled",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.2.sp,
            color = ScheduledTint,
        )
    }
}

@Composable
private fun PulsingDot(color: Color, size: Dp = 6.dp) {
    val infinite = rememberInfiniteTransition(label = "sched-pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sched-pulse-alpha",
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

// ── Pulsing "Live" chip ────────────────────────────────────────────────
@Composable
private fun LiveChip() {
    val infinite = rememberInfiniteTransition(label = "live-chip")
    val scale by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-scale",
    )
    val dotAlpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-alpha",
    )

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveBg)
            .padding(start = 8.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(LiveInk.copy(alpha = dotAlpha)),
        )
        Text(
            text = "Live",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.2.sp,
            color = LiveInk,
        )
    }
}

// ── Neutral status chip for non-live flights ───────────────────────────
@Composable
private fun StatusChip(label: String) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(cs.surfaceContainerHigh)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.2.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

// ── Airline monogram badge ─────────────────────────────────────────────
@Composable
private fun AirlineBadge(
    monogram: String,
    size: Dp = 18.dp,
    accent: Color = AirlineFallbackBg,
    ink: Color = AirlineFallbackInk,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(5.dp))
            .background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = monogram,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 0.3.sp,
            color = ink,
            maxLines = 1,
        )
    }
}

// ── Stat tile ──────────────────────────────────────────────────────────
@Composable
private fun StatTile(
    label: String,
    value: String,
    unit: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = label,
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 9.5.sp,
                letterSpacing = 0.7.sp,
                color = tint.copy(alpha = 0.7f),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                    letterSpacing = (-0.6).sp,
                    color = tint,
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = tint.copy(alpha = 0.75f),
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}

// ── Route arc: bezier curve with travelling plane ──────────────────────
@Composable
private fun RouteArc(
    progress: Float,
    originCode: String,
    originTime: String,
    destCode: String,
    destTime: String,
    punchColor: Color,
) {
    val cs = MaterialTheme.colorScheme
    val planePainter = rememberVectorPainter(Icons.Filled.Flight)

    val infinite = rememberInfiniteTransition(label = "route-arc")
    val dashPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dash-phase",
    )
    val bobT by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bob",
    )

    val arcHeight = 72.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(arcHeight),
        ) {
            val w = size.width
            val h = size.height
            if (w <= 0f) return@Canvas

            val inset = 22.dp.toPx()
            val topInset = 12.dp.toPx()
            val baseline = h - inset
            val x0 = inset
            val y0 = baseline
            val xc = w / 2f
            val yc = topInset
            val x2 = w - inset
            val y2 = baseline

            fun bx(t: Float) = (1 - t) * (1 - t) * x0 + 2 * (1 - t) * t * xc + t * t * x2
            fun by(t: Float) = (1 - t) * (1 - t) * y0 + 2 * (1 - t) * t * yc + t * t * y2

            val p = progress.coerceIn(0f, 1f)
            val px = bx(p)
            val py = by(p)

            // Tangent at p
            val dx = 2 * (1 - p) * (xc - x0) + 2 * p * (x2 - xc)
            val dy = 2 * (1 - p) * (yc - y0) + 2 * p * (y2 - yc)
            val tangentDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            // Icons.Filled.Flight points up → +90° aligns it with the tangent direction.
            val planeRotation = tangentDeg + 90f

            // 1. Altitude fill polygon under the completed portion.
            if (p > 0.005f) {
                val fillPath = Path().apply {
                    moveTo(x0, baseline)
                    val n = 60
                    for (i in 0..n) {
                        val t = (i.toFloat() / n) * p
                        lineTo(bx(t), by(t))
                    }
                    lineTo(px, baseline)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to RouteAmber.copy(alpha = 0.32f),
                            1f to RouteAmber.copy(alpha = 0f),
                        ),
                        startY = topInset,
                        endY = baseline,
                    ),
                )
            }

            // 2. Remaining (dashed, animated scroll).
            if (p < 0.995f) {
                val remainingPath = Path().apply {
                    val n = 30
                    var first = true
                    for (i in 0..n) {
                        val t = p + (i.toFloat() / n) * (1f - p)
                        if (first) {
                            moveTo(bx(t), by(t)); first = false
                        } else lineTo(bx(t), by(t))
                    }
                }
                drawPath(
                    path = remainingPath,
                    color = RemainingDim,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            intervals = floatArrayOf(2.dp.toPx(), 7.dp.toPx()),
                            phase = -dashPhase.dp.toPx(),
                        ),
                    ),
                )
            }

            // 3. Completed arc (solid).
            if (p > 0.005f) {
                val completedPath = Path().apply {
                    val n = 60
                    var first = true
                    for (i in 0..n) {
                        val t = (i.toFloat() / n) * p
                        if (first) {
                            moveTo(bx(t), by(t)); first = false
                        } else lineTo(bx(t), by(t))
                    }
                }
                drawPath(
                    path = completedPath,
                    color = RouteAmber,
                    style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // 4. Origin dot (filled + punched-out core).
            drawCircle(RouteAmber, radius = 5.dp.toPx(), center = Offset(x0, y0))
            drawCircle(punchColor, radius = 2.dp.toPx(), center = Offset(x0, y0))

            // 5. Destination dot (hollow ring).
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = 5.dp.toPx(),
                center = Offset(x2, y2),
                style = Stroke(width = 1.6.dp.toPx()),
            )

            // 6. Plane glow (radial gradient under sprite).
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to RouteAmber.copy(alpha = 0.55f),
                        0.7f to RouteAmber.copy(alpha = 0f),
                    ),
                    center = Offset(px, py),
                    radius = 16.dp.toPx(),
                ),
                radius = 16.dp.toPx(),
                center = Offset(px, py),
            )

            // 7. Plane sprite — rotate around its center, translate to (px, py), bob -1.5dp.
            val planeSizePx = 22.dp.toPx()
            val bobYpx = -1.5f * bobT * 1.dp.toPx()
            translate(left = px - planeSizePx / 2f, top = py - planeSizePx / 2f + bobYpx) {
                rotate(
                    degrees = planeRotation,
                    pivot = Offset(planeSizePx / 2f, planeSizePx / 2f),
                ) {
                    with(planePainter) {
                        draw(
                            size = Size(planeSizePx, planeSizePx),
                            colorFilter = ColorFilter.tint(Color.White),
                        )
                    }
                }
            }
        }

        // City code labels — anchored under the arc's baseline endpoints
        // (origin dot is at 22dp inset, so labels sit ~14dp from card edge to
        // roughly center under the dot for 3-letter IATA codes).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 14.dp, end = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = originCode,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = originTime,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = destCode,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.4.sp,
                    color = cs.onSurface,
                )
                Text(
                    text = destTime,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

// ── Display-state derivation from HaFlight ─────────────────────────────
private data class FlightDisplay(
    val flightNumber: String,
    val airlineSubline: String,
    val monogram: String,
    val originCode: String,
    val originTime: String,
    val destCode: String,
    val destTime: String,
    val arrivalTime: String,
    val etaText: String,
    val progress: Float,
    val aircraftLabel: String,
    val footerRight: String,
    val isLive: Boolean,
    val statusLabel: String,
)

private fun deriveFlightDisplay(flight: HaFlight): FlightDisplay {
    val zone = ZoneId.systemDefault()
    fun fmt(epochSec: Long?): String =
        epochSec?.let { Instant.ofEpochSecond(it).atZone(zone).format(TimeFormatter) } ?: "—"

    val depSec = flight.realDeparture ?: flight.estimatedDeparture ?: flight.scheduledDeparture
    val arrSec = flight.estimatedArrival ?: flight.scheduledArrival ?: flight.realArrival
    val nowSec = System.currentTimeMillis() / 1000

    val progress: Float = if (flight.onGround) {
        if (flight.realArrival != null && nowSec >= flight.realArrival) 1f else 0f
    } else if (depSec != null && arrSec != null && arrSec > depSec) {
        ((nowSec - depSec).toFloat() / (arrSec - depSec).toFloat()).coerceIn(0f, 1f)
    } else 0.5f

    val etaText: String = if (arrSec != null) {
        val minsLeft = ((arrSec - nowSec) / 60).toInt()
        when {
            minsLeft <= 0 -> "Arriving"
            minsLeft < 60 -> "in $minsLeft min"
            else -> "in ${minsLeft / 60}h ${minsLeft % 60}m"
        }
    } else ""

    val airlineName = flight.airline?.takeIf { it.isNotBlank() }
    val callsign = flight.callsign?.takeIf { it.isNotBlank() }
    val airlineSubline = when {
        airlineName != null && callsign != null -> "$airlineName · $callsign"
        airlineName != null -> airlineName
        callsign != null -> callsign
        else -> flight.aircraftRegistration ?: ""
    }

    val monogram = (flight.airlineIata?.takeIf { it.isNotBlank() }
        ?: airlineName?.take(2)?.uppercase()
        ?: flight.flightNumber.take(2).uppercase())

    val aircraftLabel = flight.aircraftModel
        ?: flight.aircraftRegistration
        ?: "Aircraft"

    val footerRight = listOfNotNull(
        flight.aircraftRegistration?.takeIf { it.isNotBlank() && it != flight.aircraftModel },
        flight.trackedType?.takeIf { it.isNotBlank() },
    ).joinToString(" · ").ifEmpty {
        flight.destinationCity ?: flight.destinationName ?: ""
    }

    val isLive = !flight.onGround && flight.altitudeFt > 0
    val statusLabel = when {
        flight.onGround && flight.realArrival != null -> "Landed"
        flight.onGround -> "Scheduled"
        else -> "Tracking"
    }

    return FlightDisplay(
        flightNumber = flight.flightNumber,
        airlineSubline = airlineSubline,
        monogram = monogram,
        originCode = flight.originIata ?: flight.originCity?.take(3)?.uppercase() ?: "—",
        originTime = fmt(depSec),
        destCode = flight.destinationIata ?: flight.destinationCity?.take(3)?.uppercase() ?: "—",
        destTime = fmt(arrSec),
        arrivalTime = fmt(arrSec),
        etaText = etaText,
        progress = progress,
        aircraftLabel = aircraftLabel,
        footerRight = footerRight,
        isLive = isLive,
        statusLabel = statusLabel,
    )
}

private fun formatThousands(value: Int): String =
    if (value >= 1000) "%,d".format(value) else value.toString()

private fun formatRemaining(km: Float): String =
    when {
        km >= 100f -> "%,d".format(km.toInt())
        km >= 10f -> km.toInt().toString()
        else -> "%.1f".format(km)
    }
