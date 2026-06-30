package com.uc.homehealth

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.uc.homehealth.data.FlightScheduleScheduler
import com.uc.homehealth.data.HaWebSocketClient
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class HomeHealthApplication : Application(), Configuration.Provider {

    @Inject lateinit var wsClient: HaWebSocketClient
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var flightScheduleScheduler: FlightScheduleScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // On-demand WorkManager init (auto-initializer removed in the manifest) so
    // @HiltWorker workers get their dependencies injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Tie the HA WebSocket to the process foreground: opens on first activity start,
        // closes on last activity stop. Stops the 10s reconnect storm when the screen is off.
        ProcessLifecycleOwner.get().lifecycle.addObserver(wsClient)
        // Re-arm the date-scheduled flight-tracking work on every process start — covers
        // missed runs after a force-stop and acts as the catch-up path when the app is
        // opened on (or after) a scheduled flight's day.
        appScope.launch { flightScheduleScheduler.reschedule() }
    }
}
