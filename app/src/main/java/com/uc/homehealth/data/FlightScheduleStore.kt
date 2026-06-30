package com.uc.homehealth.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lifecycle of a date-scheduled flight add. The FR24 integration has no date input —
 * writing a number to its add-to-track text entity latches onto whatever FlightRadar24's
 * search returns *right now* (live first, then the nearest scheduled instance). So
 * "track Friday's DL113" is implemented app-side: the entry waits here until Friday,
 * then a background worker sends the add command and verifies the result.
 */
enum class ScheduledFlightStatus {
    /** Waiting for the target date — nothing has been sent to Home Assistant yet. */
    PENDING,

    /**
     * The add command was sent on the target day. Stays SENT until the tracked entry
     * can be date-verified (FR24 omits all time fields while a flight is schedule-only,
     * so confirmation may only be possible once it departs).
     */
    SENT,

    /** Gave up — [ScheduledFlightAdd.statusDetail] says why. Kept until the user dismisses it. */
    FAILED,
}

@Serializable
data class ScheduledFlightAdd(
    val id: String,
    /** Normalized (upper-cased) flight number / callsign / registration the user entered. */
    val query: String,
    /** The departure date the user picked, as LocalDate.toEpochDay(). */
    val targetEpochDay: Long,
    val status: ScheduledFlightStatus = ScheduledFlightStatus.PENDING,
    /** Human-readable progress/error line shown under the queue row. */
    val statusDetail: String? = null,
    /**
     * Ids of tracked flights that already matched [query] before our first add fired
     * (i.e. the user was tracking this number manually). The verifier must never
     * auto-remove those — FR24's remove-by-number pops the first match it finds.
     */
    val preexistingIds: List<String> = emptyList(),
    /** Last time the worker looked at this entry — paces the recheck cadence. */
    val lastCheckedMs: Long = 0L,
    val createdAtMs: Long,
)

@Singleton
class FlightScheduleStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val SCHEDULES = stringPreferencesKey("scheduled_flight_adds")
        private val json = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(ScheduledFlightAdd.serializer())
    }

    val schedules: Flow<List<ScheduledFlightAdd>> = dataStore.data
        .map { prefs -> decode(prefs[SCHEDULES]) }
        .distinctUntilChanged()

    suspend fun all(): List<ScheduledFlightAdd> = schedules.first()

    suspend fun add(entry: ScheduledFlightAdd) {
        mutate { list -> list + entry }
    }

    suspend fun update(id: String, transform: (ScheduledFlightAdd) -> ScheduledFlightAdd) {
        mutate { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    suspend fun remove(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    private suspend fun mutate(transform: (List<ScheduledFlightAdd>) -> List<ScheduledFlightAdd>) {
        dataStore.edit { prefs ->
            prefs[SCHEDULES] = json.encodeToString(listSerializer, transform(decode(prefs[SCHEDULES])))
        }
    }

    private fun decode(raw: String?): List<ScheduledFlightAdd> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }
}
