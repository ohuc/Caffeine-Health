package com.uc.homehealth.ui.components.energy

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.uc.homehealth.ui.theme.MontserratFamily
import com.uc.homehealth.ui.theme.customColors
import kotlin.math.max

/**
 * Actual vs forecast PV output across the day, made legible rather than abstract: smoothed
 * curves (Catmull-Rom), a faint mid gridline, an hour axis (00–24), and a "now" marker when
 * viewing today. Series share one auto-scaled kW axis; actual = filled accent (the same
 * `sand` as the card's hero stat), forecast = dashed line. All chrome is keyed to
 * onSurface so the chart stays legible on a light card in light theme.
 */
@Composable
fun ProductionChart(
    actualKw: List<Float>,
    forecastKw: List<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 150.dp,
    nowFraction: Float? = null,
) {
    val cs = MaterialTheme.colorScheme
    val actualColor = MaterialTheme.customColors.sand
    val forecastColor = cs.onSurfaceVariant
    val ink = cs.onSurface
    val holePunch = cs.surface
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val w = size.width
            val h = size.height
            val bottom = h - 2.dp.toPx()
            val top = 6.dp.toPx()
            val peak = max(actualKw.maxOrNull() ?: 0f, forecastKw.maxOrNull() ?: 0f).coerceAtLeast(0.1f)

            fun pointsOf(values: List<Float>): List<Offset> = values.mapIndexed { i, v ->
                Offset(
                    x = if (values.size <= 1) 0f else w * i / (values.size - 1),
                    y = bottom - (v / peak).coerceIn(0f, 1f) * (bottom - top),
                )
            }

            // Catmull-Rom smoothing sampled into short segments (version-agnostic, no quadTo).
            fun smooth(points: List<Offset>): Path {
                val path = Path()
                if (points.size < 2) return path
                path.moveTo(points[0].x, points[0].y)
                for (i in 0 until points.size - 1) {
                    val p0 = points.getOrElse(i - 1) { points[i] }
                    val p1 = points[i]
                    val p2 = points[i + 1]
                    val p3 = points.getOrElse(i + 2) { points[i + 1] }
                    val steps = 8
                    for (s in 1..steps) {
                        val t = s / steps.toFloat()
                        val t2 = t * t
                        val t3 = t2 * t
                        val x = 0.5f * (2 * p1.x + (p2.x - p0.x) * t +
                            (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 +
                            (3 * p1.x - p0.x - 3 * p2.x + p3.x) * t3)
                        val y = 0.5f * (2 * p1.y + (p2.y - p0.y) * t +
                            (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 +
                            (3 * p1.y - p0.y - 3 * p2.y + p3.y) * t3)
                        path.lineTo(x, y.coerceIn(top, bottom))
                    }
                }
                return path
            }

            // Frame: baseline + faint mid gridline with its kW meaning carried by the peak.
            drawLine(ink.copy(alpha = 0.12f), Offset(0f, bottom), Offset(w, bottom), strokeWidth = 1.dp.toPx())
            val midY = bottom - 0.5f * (bottom - top)
            drawLine(
                ink.copy(alpha = 0.08f),
                Offset(0f, midY),
                Offset(w, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())),
            )

            // Actual — filled smooth area + crisp top line.
            if (actualKw.size >= 2) {
                val pts = pointsOf(actualKw)
                val line = smooth(pts)
                val fill = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, bottom)
                    lineTo(pts.first().x, bottom)
                    close()
                }
                drawPath(
                    fill,
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(0f to actualColor.copy(alpha = 0.32f), 1f to actualColor.copy(alpha = 0f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(line, color = actualColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
            }

            // Forecast — smooth dashed line.
            if (forecastKw.size >= 2) {
                drawPath(
                    smooth(pointsOf(forecastKw)),
                    color = forecastColor,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 6.dp.toPx())),
                    ),
                )
            }

            // "Now" marker — vertical hairline + a dot pinned to the strongest series.
            if (nowFraction != null) {
                val x = (nowFraction.coerceIn(0f, 1f)) * w
                drawLine(
                    ink.copy(alpha = 0.25f),
                    Offset(x, top),
                    Offset(x, bottom),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())),
                )
                fun valueAt(values: List<Float>): Float? {
                    if (values.size < 2) return null
                    val pos = nowFraction.coerceIn(0f, 1f) * (values.size - 1)
                    val i = pos.toInt().coerceAtMost(values.size - 2)
                    val f = pos - i
                    return values[i] + (values[i + 1] - values[i]) * f
                }
                val v = valueAt(actualKw) ?: valueAt(forecastKw)
                if (v != null) {
                    val y = bottom - (v / peak).coerceIn(0f, 1f) * (bottom - top)
                    drawCircle(actualColor, radius = 3.5.dp.toPx(), center = Offset(x, y))
                    drawCircle(holePunch, radius = 1.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }

        // Hour axis.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach { label ->
                Text(
                    text = label,
                    fontFamily = MontserratFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
