package com.uc.homehealth.ui.components.energy

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.components.Tap
import com.uc.homehealth.ui.components.glanceInkOn
import com.uc.homehealth.ui.components.rememberAppHaptics
import com.uc.homehealth.ui.theme.InstrumentSerifFamily
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors
import java.time.LocalDate

/** One day in the 5-day selector: its date, labels, and predicted kWh total. */
data class TimelineDay(
    val date: LocalDate,
    val dow: String,
    val dom: String,
    val kwh: Float,
)

/**
 * The 5-day selector, in this app's own language (not Helios's chart strip): a row of day
 * pills where the selected one springs wider, fills with the sand accent, and reveals its
 * predicted total — the same expressive pill behaviour as the bottom nav. Serif date numerals,
 * uniform heights, no decorative mini-charts.
 */
@Composable
fun EnergyTimeline(
    days: List<TimelineDay>,
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    // False when no PV size is configured — the selected pill then keeps its weekday label
    // instead of showing a kWh figure that doesn't exist. Never "0 kWh".
    showKwh: Boolean = true,
) {
    if (days.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val accent = MaterialTheme.customColors.sand
    val ink = glanceInkOn(accent)
    val haptic = rememberAppHaptics()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { day ->
            val isSelected = day.date == selected
            val isToday = day.date == today
            val weight by animateFloatAsState(
                targetValue = if (isSelected) 1.9f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "day_weight",
            )
            val container by animateColorAsState(
                targetValue = if (isSelected) accent else cs.surfaceContainerHigh,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "day_container",
            )
            val numeral by animateColorAsState(
                targetValue = if (isSelected) ink else cs.onSurface,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "day_numeral",
            )
            Tap(
                onClick = {
                    if (!isSelected) haptic.segmentTick()
                    onSelect(day.date)
                },
                modifier = Modifier.weight(weight),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(container, RoundedCornerShape(18.dp))
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = day.dom,
                        fontFamily = InstrumentSerifFamily,
                        fontSize = 24.sp,
                        color = numeral,
                    )
                    // Same two-line structure in every pill (uniform heights): the selected
                    // pill swaps its weekday for the day's predicted total.
                    val caption = if (isSelected && showKwh) {
                        val (kv, ku) = kwhValueUnit(day.kwh)
                        "$kv $ku"
                    } else {
                        day.dow
                    }
                    Text(
                        text = caption,
                        fontFamily = MontserratFamily,
                        fontWeight = if (isToday && !isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelected) ink.copy(alpha = 0.8f) else cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
