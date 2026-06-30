package com.uc.homehealth.updates

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.uc.homehealth.MainActivity
import com.uc.homehealth.data.HaUpdate
import com.uc.homehealth.data.HaWebSocketClient
import com.uc.homehealth.data.HomeRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Keeps `update.install` work alive when the user leaves the app. The actual install runs
 * on the Home Assistant server, so this service's job is to (1) reliably dispatch every
 * install command, (2) hold the process + WebSocket open so progress keeps flowing, and
 * (3) show an ongoing notification — then stop itself the moment every tracked update has
 * finished, letting the OS reclaim the process.
 *
 * Type `dataSync`, started from a user tap (so it gets the full Android 15 6h window), and
 * `android:stopWithTask="false"` so it survives the app being swiped from Recents. A
 * force-stop or aggressive OEM "clear all" can still kill it — but HA finishes the update
 * server-side regardless.
 */
@AndroidEntryPoint
class UpdateInstallService : Service() {

    @Inject lateinit var repo: HomeRepository
    @Inject lateinit var wsClient: HaWebSocketClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracked = ConcurrentHashMap.newKeySet<String>()
    private val seenActive = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var latest: List<HaUpdate> = emptyList()
    @Volatile private var observerStarted = false
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ids = intent?.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val backupIds = intent?.getStringArrayListExtra(EXTRA_BACKUP_IDS).orEmpty().toSet()

        // Promote to foreground promptly (must happen within ~5s of startForegroundService).
        promote(buildNotification(count = (tracked.size + ids.size).coerceAtLeast(1)))

        // Keep the WebSocket connected even if the app is backgrounded, so installs keep
        // dispatching and progress keeps flowing while the user is away.
        wsClient.setKeepAlive(true)

        ids.forEach { id ->
            if (tracked.add(id)) {
                val backup = id in backupIds
                // App-scoped dispatch: survives the Activity/ViewModel being torn down.
                scope.launch { repo.installUpdate(id, backup) }
                // Backstop: if HA never reports this one as in-progress (failed dispatch),
                // don't keep the service alive forever waiting on it.
                scope.launch {
                    delay(SEEN_FALLBACK_MS)
                    if (seenActive.add(id)) maybeStop()
                }
            }
        }

        if (!observerStarted) {
            observerStarted = true
            scope.launch {
                repo.getUpdates().collect { updates ->
                    latest = updates
                    updates.forEach {
                        if (it.entityId in tracked && it.inProgress) seenActive.add(it.entityId)
                        // A rejected install (e.g. version too old) is settled — don't wait on it.
                        if (it.entityId in tracked && it.errorMessage != null) seenActive.add(it.entityId)
                    }
                    val active = updates.filter { it.entityId in tracked && it.inProgress }
                    if (active.isNotEmpty()) update(active)
                    maybeStop()
                }
            }
            scope.launch { delay(MAX_RUNTIME_MS); finishAndStop() }
        }

        return START_REDELIVER_INTENT
    }

    // Stop once every tracked update has been seen installing AND none is installing now.
    private fun maybeStop() {
        if (stopping || tracked.isEmpty()) return
        val allSeen = tracked.all { it in seenActive }
        val anyActive = latest.any { it.entityId in tracked && it.inProgress }
        if (allSeen && !anyActive) finishAndStop()
    }

    private fun finishAndStop() {
        if (stopping) return
        stopping = true
        wsClient.setKeepAlive(false)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wsClient.setKeepAlive(false)
        scope.cancel()
        super.onDestroy()
    }

    // ── Notification ────────────────────────────────────────────────────────────
    private fun promote(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    private fun update(active: List<HaUpdate>) {
        if (stopping) return
        val percent = active.singleOrNull()?.updatePercentage
        // POST_NOTIFICATIONS is a runtime permission (minSdk 33): if the user revoked it,
        // notify() throws SecurityException — skip the progress update rather than crash.
        // The foreground service itself keeps running; only the visible progress is lost.
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return
        try {
            manager.notify(NOTIF_ID, buildNotification(active.size, active.map { it.title }, percent))
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call — nothing to do.
        }
    }

    private fun buildNotification(count: Int, names: List<String> = emptyList(), percent: Int? = null): Notification {
        val title = if (count == 1) "Installing ${names.firstOrNull() ?: "update"}" else "Installing $count updates"
        val text = names.joinToString(", ").ifBlank { "Updates keep installing even if you leave the app" }
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(if (percent != null) 100 else 0, percent ?: 0, percent == null)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
            .setName("Software updates")
            .setDescription("Progress while Home Assistant updates install")
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "updates_install"
        private const val NOTIF_ID = 4711
        private const val EXTRA_IDS = "extra_ids"
        private const val EXTRA_BACKUP_IDS = "extra_backup_ids"
        private const val SEEN_FALLBACK_MS = 30_000L
        private const val MAX_RUNTIME_MS = 30 * 60_000L

        /** Start (or add to) the install service. Must be called while the app is foreground. */
        fun start(context: Context, ids: List<String>, backupIds: Set<String>) {
            if (ids.isEmpty()) return
            val intent = Intent(context, UpdateInstallService::class.java).apply {
                putStringArrayListExtra(EXTRA_IDS, ArrayList(ids))
                putStringArrayListExtra(EXTRA_BACKUP_IDS, ArrayList(backupIds))
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
