package com.uc.homehealth.ui.components

import android.os.Build
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
    fun toggle(isOn: Boolean) = when {
        sdk >= 34 -> fire(if (isOn) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.TOGGLE_OFF)
        else -> fire(if (isOn) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
    }
    fun confirm()     = fire(HapticFeedbackConstants.CONFIRM)
    fun reject()      = fire(HapticFeedbackConstants.REJECT)
    fun gestureStart() = fire(HapticFeedbackConstants.GESTURE_START)
    fun gestureEnd()   = fire(HapticFeedbackConstants.GESTURE_END)
    fun segmentTick()  = fire(if (sdk >= 34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
    fun longPress()    = fire(HapticFeedbackConstants.LONG_PRESS)

    private fun fire(constant: Int) {
        @Suppress("DEPRECATION")
        view.performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
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
