package com.uc.homehealth.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.uc.homehealth.data.PulseCategory
import com.uc.homehealth.data.PulseCategoryKind
import com.uc.homehealth.data.PulseGrade
import com.uc.homehealth.data.PulseIssue
import com.uc.homehealth.data.PulseReport
import com.uc.homehealth.data.PulseSeverity
import com.uc.homehealth.ui.components.OpenInHomeAssistantButton
import com.uc.homehealth.ui.components.RollingNumberText
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.haIconFor
import com.uc.homehealth.ui.components.launchHomeAssistant
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.Spacing
import com.uc.homehealth.ui.theme.customColors
import com.uc.homehealth.ui.viewmodel.PulseViewModel

// ─── Pulse — the home-health report page ─────────────────────────────────────
// One hero (the score ring, serif numerals) over a grouped list of category rows;
// rows with issues expand to list the offenders. Read-only by design.

@Composable
fun PulseScreen(viewModel: PulseViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PulseScreenContent(report = uiState.report, isLoading = uiState.isLoading, haUrl = uiState.haUrl)
}

@Composable
internal fun PulseScreenContent(report: PulseReport?, isLoading: Boolean, haUrl: String = "") {
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        // Header — same voice as Activity/Energy.
        Column(modifier = Modifier.padding(start = Spacing.xl, end = Spacing.xl, top = 16.dp, bottom = 14.dp)) {
            Text(
                text = "Pulse",
                fontFamily = InstrumentSerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 40.sp,
                color = cs.onBackground,
            )
            Text(
                text = when {
                    isLoading || report == null -> "Checking your home’s health"
                    report.issueCount == 0 -> "Your home is running smoothly"
                    report.issueCount == 1 -> "1 thing needs care"
                    else -> "${report.issueCount} things need care"
                },
                fontFamily = MontserratFamily,
                fontSize = 13.sp,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (isLoading || report == null) {
            PulseLoadingCard()
            return@Column
        }

        ScoreHero(report = report)

        Spacer(Modifier.height(Spacing.l))

        // Category rows — expressive grouped list (same 22/6 corner pattern as Activity).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.ml),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            report.categories.forEachIndexed { index, category ->
                CategoryRow(
                    category = category,
                    shape = pulseRowShape(index, report.categories.size),
                )
            }
        }

        // Hand-off to HA — Pulse is read-only by design, so fixing what it found
        // happens in Home Assistant itself (same branded button as Settings).
        if (report.issueCount > 0) {
            val context = LocalContext.current
            val haptics = rememberAppHaptics()
            Spacer(Modifier.height(Spacing.l))
            OpenInHomeAssistantButton(
                title = "Address these in Home Assistant",
                subtitle = if (haUrl.isBlank())
                    "Review and fix what needs care"
                else
                    "Review and fix what needs care · $haUrl",
                modifier = Modifier.padding(horizontal = Spacing.ml),
                onClick = { haptics.navigation(); launchHomeAssistant(context, haUrl) },
            )
        }

        Spacer(Modifier.height(130.dp))
    }
}

// ─── Hero ────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreHero(report: PulseReport) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val gradeColor = when (report.grade) {
        PulseGrade.HEALTHY -> custom.mint
        PulseGrade.FAIR -> custom.warn
        PulseGrade.NEEDS_CARE -> custom.coral
    }
    val gradeLabel = when (report.grade) {
        PulseGrade.HEALTHY -> "Healthy"
        PulseGrade.FAIR -> "Fair"
        PulseGrade.NEEDS_CARE -> "Needs care"
    }
    // ── Entry attention sequence (~2.5s, once per page open) ──
    // The arc draws itself in, then the dial "beats" twice — a small scale breath
    // with a soft grade-colored inner halo that fades to nothing and stays gone.
    // The transient halo is a scoped exception to the flat/no-glow rule (like the
    // Energy sun): it exists only to pull the eye to the score, then the surface
    // returns to fully flat.
    var arcIn by remember { mutableStateOf(false) }
    val beat = remember { Animatable(0f) }
    val haptics = rememberAppHaptics()
    LaunchedEffect(Unit) {
        arcIn = true
        kotlinx.coroutines.delay(550) // let the arc draw in first
        repeat(2) {
            haptics.tick()
            beat.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
            beat.animateTo(0f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }
    val sweep by animateFloatAsState(
        targetValue = if (arcIn) report.score / 100f * 360f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "pulse_score_sweep",
    )
    val ringTrack = cs.onSurface.copy(alpha = 0.08f)

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(216.dp)
                // Heartbeat breath — reads in the layer block so only that phase redraws.
                .graphicsLayer {
                    val scale = 1f + beat.value * 0.035f
                    scaleX = scale
                    scaleY = scale
                },
            contentAlignment = Alignment.Center,
        ) {
            // Score ring: full faint track + grade-colored arc. Flat after the intro.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val ringR = (size.minDimension - stroke) / 2f
                // Transient heartbeat halo: lights the ring's inner edge, gone after
                // the intro (beat settles at 0 and never rises again).
                if (beat.value > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.55f to Color.Transparent,
                                1f to gradeColor.copy(alpha = 0.30f * beat.value),
                            ),
                            center = center,
                            radius = ringR,
                        ),
                        radius = ringR,
                        center = center,
                    )
                }
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = gradeColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Caption + /100 denominator so the number reads as a SCORE, not a count
                // of problems (a bare "77" was ambiguous).
                Text(
                    text = "HOME SCORE",
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.8.sp,
                    color = cs.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingNumberText(
                        text = report.score.toString(),
                        style = TextStyle(
                            fontFamily = InstrumentSerifFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 64.sp,
                            color = cs.onSurface,
                        ),
                        labelPrefix = "pulse_score",
                    )
                    Text(
                        text = "/100",
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp, bottom = 12.dp),
                    )
                }
                Text(
                    text = gradeLabel,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.4.sp,
                    color = gradeColor,
                )
            }
        }
    }
}

// ─── Category rows ───────────────────────────────────────────────────────────

// Grouped-list shape: large corners on the group's outer edges, small where rows meet
// (the same expressive grouped-list pattern the Activity feed uses).
private val GroupCornerLarge = 22.dp
private val GroupCornerSmall = 6.dp

private fun pulseRowShape(index: Int, count: Int): Shape = when {
    count == 1 -> RoundedCornerShape(GroupCornerLarge)
    index == 0 -> RoundedCornerShape(
        topStart = GroupCornerLarge, topEnd = GroupCornerLarge,
        bottomStart = GroupCornerSmall, bottomEnd = GroupCornerSmall,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = GroupCornerSmall, topEnd = GroupCornerSmall,
        bottomStart = GroupCornerLarge, bottomEnd = GroupCornerLarge,
    )
    else -> RoundedCornerShape(GroupCornerSmall)
}

private val PulseCategoryKind.title: String
    get() = when (this) {
        PulseCategoryKind.DEVICES -> "Devices"
        PulseCategoryKind.BATTERIES -> "Batteries"
        PulseCategoryKind.SENSORS -> "Sensors"
        PulseCategoryKind.UPDATES -> "Updates"
        PulseCategoryKind.CONNECTIVITY -> "Connectivity"
        PulseCategoryKind.SERVER -> "Server"
    }

// kind → (theme accent, haIconFor key); severity is carried by the badge + issue dots,
// the row accent just identifies the category (Activity-feed language).
@Composable
private fun categoryStyle(kind: PulseCategoryKind): Pair<Color, String> {
    val custom = MaterialTheme.customColors
    return when (kind) {
        PulseCategoryKind.DEVICES -> custom.sky to "home"
        PulseCategoryKind.BATTERIES -> custom.sand to "energy"
        PulseCategoryKind.SENSORS -> custom.cyan to "pulse"
        PulseCategoryKind.UPDATES -> custom.mint to "update"
        PulseCategoryKind.CONNECTIVITY -> custom.lavender to "wifi"
        PulseCategoryKind.SERVER -> custom.coral to "data"
    }
}

@Composable
private fun CategoryRow(category: PulseCategory, shape: Shape) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val haptics = rememberAppHaptics()
    val (accent, iconKey) = categoryStyle(category.kind)
    var expanded by remember { mutableStateOf(false) }
    val expandable = category.issues.isNotEmpty()
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pulse_chevron_${category.kind}",
    )

    Tap(
        onClick = {
            if (expandable) {
                haptics.tick()
                expanded = !expanded
            }
        },
        enabled = expandable,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(cs.surfaceContainerHigh, shape)
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.14f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = haIconFor(iconKey),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.kind.title,
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        letterSpacing = (-0.1).sp,
                        color = cs.onSurface,
                    )
                    Text(
                        text = category.summary,
                        fontFamily = MontserratFamily,
                        fontSize = 12.sp,
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (category.healthy) {
                    Box(Modifier.size(8.dp).background(custom.mint, CircleShape))
                } else {
                    Text(
                        text = category.issues.size.toString(),
                        fontFamily = MontserratFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = cs.onSurface,
                    )
                    Icon(
                        imageVector = haIconFor("chevron-right"),
                        contentDescription = null,
                        tint = cs.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(chevronAngle),
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(pulseExpandSpring()) + fadeIn(),
                exit = shrinkVertically(pulseExpandSpring()) + fadeOut(),
            ) {
                Column(modifier = Modifier.padding(top = 12.dp, start = 6.dp, end = 2.dp)) {
                    category.issues.forEach { issue -> IssueLine(issue) }
                }
            }
        }
    }
}

private fun pulseExpandSpring() = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntSize.VisibilityThreshold,
)

@Composable
private fun IssueLine(issue: PulseIssue) {
    val cs = MaterialTheme.colorScheme
    val custom = MaterialTheme.customColors
    val dot = when (issue.severity) {
        PulseSeverity.CRITICAL -> custom.coral
        PulseSeverity.WARN -> custom.warn
        PulseSeverity.INFO -> cs.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Box(Modifier.size(7.dp).background(dot, CircleShape))
        Text(
            text = issue.name,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = issue.detail,
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
        )
    }
}

// ─── Loading ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PulseLoadingCard() {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ml)
            .background(cs.surfaceContainerHigh, MaterialTheme.shapes.medium)
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Reading your home",
            fontFamily = InstrumentSerifFamily,
            fontSize = 24.sp,
            color = cs.onSurface,
        )
        Text(
            text = "Gathering batteries, devices & server vitals…",
            fontFamily = MontserratFamily,
            fontSize = 12.sp,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
