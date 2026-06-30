package com.uc.homehealth.ui.components.energy

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.uc.homehealth.data.CloudGrid
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bakes a [CloudGrid] (real Open-Meteo low/mid/high cloud cover at 9×9 points around the
 * home) into a cloud texture the weather mode stretches over the map. Ported from Helios's
 * cloud shader approach: per-pixel fractal value-noise is **threshold-carved** by the
 * bilinearly-sampled coverage, so clouds get ragged organic shapes — high coverage grows
 * connected masses, low coverage leaves only wisps — instead of a uniform blob grid.
 * Band opacities follow Helios: low 0.20 / mid 0.40 / high 0.60.
 */
internal fun buildCloudField(grid: CloudGrid, sizePx: Int = 176): ImageBitmap? {
    val n = sqrt(grid.cells.size.toFloat()).roundToInt()
    if (n < 2 || n * n != grid.cells.size) return null

    // Row-major band grids (row 0 = southmost, matching how the grid is requested).
    val low = FloatArray(n * n) { grid.cells[it].lowPct / 100f }
    val mid = FloatArray(n * n) { grid.cells[it].midPct / 100f }
    val high = FloatArray(n * n) { grid.cells[it].highPct / 100f }

    fun bilinear(band: FloatArray, gx: Float, gy: Float): Float {
        val x0 = floor(gx).toInt().coerceIn(0, n - 1)
        val y0 = floor(gy).toInt().coerceIn(0, n - 1)
        val x1 = (x0 + 1).coerceAtMost(n - 1)
        val y1 = (y0 + 1).coerceAtMost(n - 1)
        val fx = (gx - x0).coerceIn(0f, 1f)
        val fy = (gy - y0).coerceIn(0f, 1f)
        val a = band[y0 * n + x0]
        val b = band[y0 * n + x1]
        val c = band[y1 * n + x0]
        val d = band[y1 * n + x1]
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
    }

    val pixels = IntArray(sizePx * sizePx)
    val bands = arrayOf(Triple(low, 0.20f, 11), Triple(mid, 0.40f, 47), Triple(high, 0.60f, 83))
    for (py in 0 until sizePx) {
        val v = py / (sizePx - 1f)
        // Texture row 0 is the NORTH edge; grid row 0 is the south row.
        val gy = (1f - v) * (n - 1)
        for (px in 0 until sizePx) {
            val u = px / (sizePx - 1f)
            val gx = u * (n - 1)
            var alpha = 0f
            for ((band, maxAlpha, seed) in bands) {
                val cov = bilinear(band, gx, gy)
                if (cov < 0.03f) continue
                val nz = fbm(u * 7f, v * 7f, seed)
                // Noise-threshold carve (Helios): coverage lowers the cut so noise peaks
                // become clouds; soft edge for fluffy rims. The small ambient term keeps
                // genuinely overcast areas visible even where the noise dips.
                val carve = ((nz - (1f - cov) * 0.85f) / 0.35f).coerceIn(0f, 1f)
                alpha += maxAlpha * (0.25f * cov + 0.75f * carve * smooth(carve))
            }
            alpha = alpha.coerceAtMost(0.82f)
            val a = (alpha * 255).toInt()
            // Cool white (#DDE3EE), premultiplied-free ARGB — Compose handles blending.
            pixels[py * sizePx + px] = (a shl 24) or 0xDDE3EE
        }
    }
    val bmp = Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

private fun smooth(t: Float): Float = t * t * (3f - 2f * t)

// Deterministic hash → [0,1) value noise; no Math.random so the field is stable per grid.
private fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374_761_393 + y * 668_265_263 + seed * 1_442_695_041
    h = (h xor (h shr 13)) * 1_274_126_177
    return ((h xor (h shr 16)) and 0x7FFFFFFF) / Int.MAX_VALUE.toFloat()
}

private fun valueNoise(x: Float, y: Float, seed: Int): Float {
    val xi = floor(x).toInt()
    val yi = floor(y).toInt()
    val fx = smooth(x - xi)
    val fy = smooth(y - yi)
    val a = hash(xi, yi, seed)
    val b = hash(xi + 1, yi, seed)
    val c = hash(xi, yi + 1, seed)
    val d = hash(xi + 1, yi + 1, seed)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private fun fbm(x: Float, y: Float, seed: Int): Float =
    0.55f * valueNoise(x, y, seed) +
        0.30f * valueNoise(x * 2.3f, y * 2.3f, seed + 1) +
        0.15f * valueNoise(x * 4.9f, y * 4.9f, seed + 2)
