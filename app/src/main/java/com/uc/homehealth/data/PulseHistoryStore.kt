package com.uc.homehealth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Rolling log of UNEXPECTED WebSocket drops (READY → ERROR transitions) feeding Pulse's
// Connectivity row. Deliberate disconnects (logout, app background) are not recorded.
// Shares the app's single Preferences DataStore (own key namespace, newline-joined
// epoch-millis list — same encoding UserPreferences uses for id lists).
@Singleton
class PulseHistoryStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val WS_DROPS = stringPreferencesKey("pulse_ws_drops")
        private const val LIST_SEP = "\n"
        private const val MAX_ENTRIES = 100
        private const val KEEP_MS = 7L * 24 * 3_600_000
    }

    val drops: Flow<List<Long>> = dataStore.data
        .map { prefs -> decode(prefs[WS_DROPS]) }
        .distinctUntilChanged()

    suspend fun recordDrop(nowMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            val next = (decode(prefs[WS_DROPS]) + nowMs)
                .filter { nowMs - it <= KEEP_MS }
                .takeLast(MAX_ENTRIES)
            prefs[WS_DROPS] = next.joinToString(LIST_SEP)
        }
    }

    private fun decode(raw: String?): List<Long> =
        raw?.split(LIST_SEP)?.mapNotNull { it.toLongOrNull() } ?: emptyList()
}
