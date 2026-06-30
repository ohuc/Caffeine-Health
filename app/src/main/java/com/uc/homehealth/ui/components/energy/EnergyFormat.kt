package com.uc.homehealth.ui.components.energy

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compact energy amount → (value, unit), rolling kWh up to MWh so big totals never overflow. */
internal fun kwhValueUnit(kwh: Float): Pair<String, String> = when {
    abs(kwh) >= 1000f -> trimDecimals(kwh / 1000f) to "MWh"
    abs(kwh) >= 100f -> kwh.roundToInt().toString() to "kWh"
    else -> trimDecimals(kwh) to "kWh"
}

/** Compact power → (value, unit): W below 1 kW, kW above, MW above 1000 kW. */
internal fun powerValueUnit(kw: Float): Pair<String, String> = when {
    abs(kw) >= 1000f -> trimDecimals(kw / 1000f) to "MW"
    abs(kw) >= 1f -> trimDecimals(kw) to "kW"
    else -> (kw * 1000f).roundToInt().toString() to "W"
}

internal fun trimDecimals(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)
