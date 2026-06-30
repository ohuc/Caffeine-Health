package com.uc.homehealth.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Stable
class AppHaptics(private val view: View) {

    fun tick()        = fire(if (sdk >= 23) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.KEYBOARD_TAP)
    fun navigation()  = fire(HapticFeedbackConstants.VIRTUAL_KEY)
    // TOGGLE_ON/OFF (API 34+) are the semantically-correct constants, but several
    // OEM devices have no haptic mapping for them and silently do nothing. Fall back
    // to the well-supported CONFIRM/CONTEXT_CLICK when the primary isn't performed.
    fun toggle(isOn: Boolean) {
        if (sdk >= 34) {
            val performed = fire(if (isOn) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
            if (!performed) fire(if (isOn) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            fire(if (isOn) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }
    fun confirm()     = fire(HapticFeedbackConstants.CONFIRM)
    fun reject()      = fire(HapticFeedbackConstants.REJECT)
    fun gestureStart() = fire(HapticFeedbackConstants.GESTURE_START)
    fun gestureEnd()   = fire(HapticFeedbackConstants.GESTURE_END)
    fun segmentTick()  = fire(if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun longPress()    = fire(HapticFeedbackConstants.LONG_PRESS)

    /**
     * Distinctive "entered edit mode" buzz fired when a room tab is long-pressed — a crisp
     * double-tick reminiscent of the Android easter-egg long-press. Uses a custom Vibrator
     * waveform (needs the VIBRATE permission); falls back to the stock LONG_PRESS haptic if
     * no vibrator / permission is available so the gesture still feels responsive everywhere.
     */
    fun editLongPress() {
        val played = runCatching {
            val vib = vibrator() ?: return@runCatching false
            if (!vib.hasVibrator()) return@runCatching false
            // timings/amplitudes: short strong tap, gap, softer tap — the "tk-tk" pattern.
            if (sdk >= 26) {
                vib.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 22, 45, 30),
                        intArrayOf(0, 210, 0, 120),
                        -1,
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 22, 45, 30), -1)
            }
            true
        }.getOrDefault(false)
        if (!played) fire(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun vibrator(): Vibrator? {
        val ctx: Context = view.context
        return if (sdk >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private fun fire(constant: Int): Boolean {
        @Suppress("DEPRECATION")
        return view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
    }

    companion object {
        private val sdk = Build.VERSION.SDK_INT
    }
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val view = LocalView.current
    return remember(view) {
        view.isHapticFeedbackEnabled = true
        AppHaptics(view)
    }
}
