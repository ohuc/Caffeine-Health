package com.uc.homehealth.data

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Runs [FlightScheduleEngine.processDueSchedules] at the right moments:
 *  - each scheduled flight's target day, shortly after local midnight (earliest point
 *    where FlightRadar24's "nearest instance" search can resolve to that day's flight);
 *  - then on a recheck cadence while entries await date confirmation.
 *
 * Transient failures (no network, HA down) become WorkManager retries with backoff;
 * the NetworkType.CONNECTED constraint already holds runs until connectivity returns.
 */
@HiltWorker
class FlightScheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val engine: FlightScheduleEngine,
    private val scheduler: FlightScheduleScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val transient = runCatching { engine.processDueSchedules() }
            .getOrElse { e ->
                Log.e("HomeHealth_FlightSched", "Worker crashed: ${e.message}")
                true
            }
        if (transient && runAttemptCount < MAX_TRANSIENT_RETRIES) {
            // Keep the unique work alive with exponential backoff. Don't reschedule here:
            // a REPLACE enqueue would cancel this retry chain.
            return Result.retry()
        }
        // All store mutations are committed; arm the next wake-up. The REPLACE enqueue
        // cancels this (already finished) unique work — harmless, the new request stands.
        scheduler.reschedule()
        return Result.success()
    }

    companion object {
        private const val MAX_TRANSIENT_RETRIES = 6
    }
}

@Singleton
class FlightScheduleScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: FlightScheduleStore,
) {

    companion object {
        private const val WORK_NAME = "flight_schedule_sync"

        // Fire at 00:15 local on the target day — early enough that a same-day red-eye
        // can't depart and land before we ever send the command. A previous day's
        // overnight flight may still be airborne then; the engine's date check catches
        // that and the recheck cadence retries after it lands.
        private const val FIRE_OFFSET_AFTER_MIDNIGHT_MS = 15L * 60_000

        // While an entry awaits confirmation, look again every ~2¾ h (≈9 looks/day —
        // enough to catch a landed wrong instance without hammering HA).
        private const val RECHECK_INTERVAL_MS = 9_900_000L
    }

    /**
     * (Re)arms the single unique work request at the earliest moment any entry needs
     * attention; cancels it when nothing is left. Called after every store mutation,
     * from the worker when it finishes, and on every process start (catch-up).
     */
    suspend fun reschedule() {
        val workManager = WorkManager.getInstance(context)
        val now = System.currentTimeMillis()
        val active = store.all().filter { it.status != ScheduledFlightStatus.FAILED }
        if (active.isEmpty()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val fireAt = active.minOf { fireAtMs(it, now) }
        val request = OneTimeWorkRequestBuilder<FlightScheduleWorker>()
            .setInitialDelay(max(0L, fireAt - now), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun fireAtMs(entry: ScheduledFlightAdd, now: Long): Long {
        val today = LocalDate.now().toEpochDay()
        if (entry.targetEpochDay > today) {
            return LocalDate.ofEpochDay(entry.targetEpochDay)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli() + FIRE_OFFSET_AFTER_MIDNIGHT_MS
        }
        // Due (or in the verification window): pace by the last look so back-to-back
        // reschedule() calls can't hot-loop the worker.
        return max(now + 5_000, entry.lastCheckedMs + RECHECK_INTERVAL_MS)
    }
}
