package com.uc.homehealth.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log of actions the user performed via this app while connected to HA.
 * Persisted to Room ([ActivityDao]) so the feed survives leaving the app / process death
 * (it used to live only for the process lifetime and was cleared on cold start).
 *
 * Only app-initiated actions are recorded here — HA's own state changes are not logged.
 */
@Singleton
class ActivityLog @Inject constructor(
    private val dao: ActivityDao,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val events: Flow<List<HaNotification>> =
        dao.observeRecent(MAX_EVENTS).map { rows -> rows.map { it.toNotification() } }

    fun record(kind: String, title: String, body: String) {
        scope.launch {
            dao.insert(
                ActivityEntity(
                    kind = kind,
                    title = title,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                )
            )
            dao.trim(MAX_EVENTS)
        }
    }

    /** Delete one recorded event by id (swipe-to-delete). Suspends until Room completes. */
    suspend fun delete(id: Long) {
        dao.delete(id)
    }

    /** Wipe the whole on-device feed. Suspends until Room completes. */
    suspend fun clear() {
        dao.clear()
    }

    companion object {
        private const val MAX_EVENTS = 200
    }
}
