package com.uc.homehealth

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.uc.homehealth.data.HaWebSocketClient
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HomeHealthApplication : Application() {

    @Inject lateinit var wsClient: HaWebSocketClient

    override fun onCreate() {
        super.onCreate()
        // Tie the HA WebSocket to the process foreground: opens on first activity start,
        // closes on last activity stop. Stops the 10s reconnect storm when the screen is off.
        ProcessLifecycleOwner.get().lifecycle.addObserver(wsClient)
    }
}
