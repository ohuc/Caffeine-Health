package com.uc.homehealth.data

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.uc.homehealth.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fires and verifies date-scheduled flight adds against the FR24 integration.
 *
 * Why this exists (verified against the integration source, api/flight.py):
 *  - `text.flightradar24_add_to_track` has NO date input. It runs a FlightRadar24
 *    search and keeps the FIRST match — live flights first, then the nearest
 *    scheduled instance. The only date control we have is WHEN we send the command,
 *    so the add fires on the target day itself (shortly after local midnight).
 *  - A tracked entry with tracked_type == "schedule" carries only id/number/callsign —
 *    no departure time. The date can therefore only be VERIFIED once the flight goes
 *    live (details fetch adds time_scheduled_departure + airport tz offset). Until
 *    then the entry stays SENT and is rechecked periodically.
 *  - The integration re-resolves tracked numbers by search on every refresh, which is
 *    what makes multi-leg flights (same number, stops) roll onto their next leg by
 *    themselves. The verifier accepts ANY instance departing on the target date, so it
 *    never fights that behavior — including numbers that fly twice on the same day.
 *  - The classic wrong match: the previous day's same-number flight still airborne
 *    overnight. The add latches onto it (live wins), but live entries DO carry times,
 *    so the date check catches it; we remove the wrong instance and retry later.
 *  - Removal is by number and pops the first tracked match, so auto-removal only
 *    happens when the match is unambiguous and wasn't tracked by the user beforehand.
 */
@Singleton
class FlightScheduleEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val authPreferences: AuthPreferences,
    private val authManager: HaAuthManager,
    private val store: FlightScheduleStore,
    private val repo: HomeRepository,
    private val activityLog: ActivityLog,
) {

    companion object {
        private const val TAG = "HomeHealth_FlightSched"
        private const val NOTIFICATION_CHANNEL = "flight_schedule"
        // Poll the tracked sensor for up to ~90s after sending the add — it only
        // refreshes on the integration's coordinator cycle (user-configured interval).
        private const val ADD_POLL_ATTEMPTS = 15
        private const val ADD_POLL_DELAY_MS = 6_000L
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
    }

    /** Thrown when HA rejects our credentials at the request level (401/403). */
    private class AuthRejectedException : Exception("HA rejected the request")

    /**
     * Processes every entry whose target date has arrived. Returns true when a
     * transient failure (network, HA unreachable) left work unfinished — the worker
     * turns that into a WorkManager retry with backoff.
     */
    suspend fun processDueSchedules(): Boolean = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        var transient = false
        val due = store.all().filter { it.status != ScheduledFlightStatus.FAILED && it.targetEpochDay <= today }
        for (entry in due) {
            try {
                processEntry(entry, today)
            } catch (e: AuthRejectedException) {
                // A 401 with the freshest token we can mint means re-login is needed.
                // Every retried request would be another failed-login entry on HA's
                // side (and a ban-counter tick), so stop hard instead of retrying.
                Log.e(TAG, "HA rejected credentials — failing ${entry.query} without retry")
                markFailed(entry, "Home Assistant rejected the app's login — open the app and sign in again")
                transient = false
                break
            } catch (e: Exception) {
                Log.w(TAG, "Transient failure for ${entry.query}: ${e.message}")
                touch(entry.id)
                transient = true
            }
        }
        transient
    }

    private suspend fun processEntry(entry: ScheduledFlightAdd, today: Long) {
        // Verification continues through the day AFTER the target date: an evening
        // flight can go live (and become checkable) only after local midnight.
        val windowExpired = today > entry.targetEpochDay + 1

        val auth = authPreferences.authState.first()
        if (auth.accessToken.isEmpty()) {
            // Demo mode — route through the (fake) repository so the flow stays testable.
            repo.addTrackedFlight(entry.query)
            store.remove(entry.id)
            activityLog.record("auto", "Tracking flight ${entry.query}", "Scheduled for ${formatDay(entry.targetEpochDay)}")
            return
        }

        val token = freshAccessToken(auth)
        if (token == null) {
            // Definitive refresh rejection (revoked token). Same rule as above: do not
            // present stale credentials to HA — each attempt is a logged failed login.
            markFailed(entry, "Session expired — open the app and sign in to Home Assistant again")
            return
        }
        val baseUrl = auth.activeUrl(null).trimEnd('/')
        if (baseUrl.isBlank()) {
            markFailed(entry, "No Home Assistant URL configured")
            return
        }

        val flights = fetchTrackedFlights(baseUrl, token)
        if (flights == null) {
            // Integration entity missing/unavailable. Keep retrying inside the window —
            // HA might just be restarting.
            if (windowExpired) markFailed(entry, "The FlightRadar24 integration wasn't available in Home Assistant")
            else touch(entry.id)
            return
        }

        evaluate(entry, today, windowExpired, baseUrl, token, flights, canAddThisRun = true)
    }

    /**
     * One full look at the tracked list for [entry]: confirm, finalize, repair a wrong
     * instance, or (re-)send the add command. [canAddThisRun] keeps the
     * remove → re-add → re-evaluate chain to a single round per worker run.
     */
    private suspend fun evaluate(
        entry: ScheduledFlightAdd,
        today: Long,
        windowExpired: Boolean,
        baseUrl: String,
        token: String,
        flights: List<HaFlight>,
        canAddThisRun: Boolean,
    ) {
        val matches = flights.filter { matchesQuery(it, entry.query) }
        // On the first run (PENDING) nothing has been sent yet, so EVERY current match
        // is the user's own tracked flight — treat them all as preexisting/untouchable.
        val preexisting =
            if (entry.status == ScheduledFlightStatus.PENDING) matches.map { it.id } else entry.preexistingIds
        val fresh = matches.filter { it.id !in preexisting }

        // 1. Anything (ours or the user's own) departing on the target date → done.
        val confirmed = matches.firstOrNull { departureEpochDay(it) == entry.targetEpochDay }
        if (confirmed != null) {
            store.remove(entry.id)
            Log.i(TAG, "Confirmed ${entry.query} for day ${entry.targetEpochDay} (id=${confirmed.id})")
            activityLog.record("auto", "Tracking flight ${entry.query}", "Confirmed for ${formatDay(entry.targetEpochDay)}")
            notifyUser("Tracking ${entry.query}", "Confirmed: ${formatDay(entry.targetEpochDay)}'s flight is being tracked.")
            return
        }

        val unverifiable = fresh.firstOrNull { departureEpochDay(it) == null } // schedule-only: FR24 sends no times yet
        val wrongDate = fresh.firstOrNull { departureEpochDay(it) != null }    // has times, but a different day

        // 2. Window over — settle up.
        if (windowExpired) {
            if (unverifiable != null) {
                // Tracked as "scheduled" but it never departed while we watched (very
                // delayed, or runs we missed). Leave it tracked, stop monitoring.
                store.remove(entry.id)
                activityLog.record(
                    "auto", "Tracking flight ${entry.query}",
                    "Left tracked — couldn't confirm the ${formatDay(entry.targetEpochDay)} date before it departed",
                )
                return
            }
            if (wrongDate != null && matches.size == 1) postRemoveTrack(baseUrl, token, entry.query)
            markFailed(entry, "FlightRadar24 never listed ${entry.query} for ${formatDay(entry.targetEpochDay)}")
            return
        }

        // 3. Schedule-only instance is tracked — all we can do is wait for departure.
        if (unverifiable != null) {
            setSent(entry, preexisting, "Sent — FlightRadar24 lists it as scheduled. The date is confirmed at departure.")
            return
        }

        // 4. We latched onto a different day's instance (e.g. yesterday's overnight
        //    flight still airborne). Remove it when unambiguous, then retry the add.
        if (wrongDate != null) {
            if (matches.size == 1) {
                Log.i(TAG, "Removing wrong-day instance of ${entry.query} (departs day ${departureEpochDay(wrongDate)})")
                postRemoveTrack(baseUrl, token, entry.query)
            } else {
                // The user also tracks this number — removal-by-number could pop theirs.
                setSent(entry, preexisting, "FlightRadar24 returned a different day's flight — retrying later")
                return
            }
        }

        if (!canAddThisRun) {
            setSent(entry, preexisting, "FlightRadar24 hasn't listed the ${formatDay(entry.targetEpochDay)} flight yet — retrying through the day")
            return
        }

        // 5. Send (or re-send) the add. The preexisting snapshot (taken before the first
        //    add) is persisted so later runs can tell our instance from the user's own.
        store.update(entry.id) {
            it.copy(
                status = ScheduledFlightStatus.SENT,
                preexistingIds = preexisting,
                statusDetail = "Track command sent to FlightRadar24 today",
                lastCheckedMs = System.currentTimeMillis(),
            )
        }
        val updated = entry.copy(status = ScheduledFlightStatus.SENT, preexistingIds = preexisting)
        Log.i(TAG, "Sending add-to-track for ${entry.query} (target day ${entry.targetEpochDay})")
        postTextValue(baseUrl, token, Fr24Entities.ADD, entry.query)

        // The sensor only refreshes on the integration's poll cycle — wait for a result.
        repeat(ADD_POLL_ATTEMPTS) {
            delay(ADD_POLL_DELAY_MS)
            val now = fetchTrackedFlights(baseUrl, token) ?: return@repeat
            val newMatch = now.filter { matchesQuery(it, updated.query) }.any { it.id !in preexisting }
            if (newMatch) {
                evaluate(updated, today, windowExpired = false, baseUrl, token, now, canAddThisRun = false)
                return
            }
        }
        // Nothing appeared: FR24's search found no instance (not in its schedule yet,
        // or a bad number). Keep the entry alive — retried until the window closes.
        setSent(updated, preexisting, "FlightRadar24 hasn't listed the ${formatDay(entry.targetEpochDay)} flight yet — retrying through the day")
    }

    // ── Matching & date verification ─────────────────────────────────────

    private fun matchesQuery(f: HaFlight, query: String): Boolean =
        f.flightNumber.equals(query, ignoreCase = true) ||
            f.callsign?.equals(query, ignoreCase = true) == true ||
            f.aircraftRegistration?.equals(query, ignoreCase = true) == true

    /**
     * The flight's departure date as an epoch-day, in the origin airport's local time
     * when FR24 provides the offset (that is the date printed on a ticket), otherwise
     * in the device zone. Null while FR24 hasn't published times (schedule-only).
     */
    private fun departureEpochDay(f: HaFlight): Long? {
        val dep = f.scheduledDeparture ?: f.realDeparture ?: f.estimatedDeparture ?: return null
        val offset = f.originTzOffsetSec
        return if (offset != null) Math.floorDiv(dep + offset, 86_400L)
        else Instant.ofEpochSecond(dep).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }

    // ── Store helpers ─────────────────────────────────────────────────────

    private suspend fun setSent(entry: ScheduledFlightAdd, preexisting: List<String>, detail: String) {
        store.update(entry.id) {
            it.copy(
                status = ScheduledFlightStatus.SENT,
                preexistingIds = preexisting,
                statusDetail = detail,
                lastCheckedMs = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun touch(id: String) {
        store.update(id) { it.copy(lastCheckedMs = System.currentTimeMillis()) }
    }

    private suspend fun markFailed(entry: ScheduledFlightAdd, reason: String) {
        store.update(entry.id) {
            it.copy(status = ScheduledFlightStatus.FAILED, statusDetail = reason, lastCheckedMs = System.currentTimeMillis())
        }
        activityLog.record("auto", "Flight ${entry.query} not tracked", reason)
        notifyUser("Couldn't track ${entry.query}", reason)
    }

    // ── Token freshness ───────────────────────────────────────────────────

    /**
     * A token safe to present to HA. Refreshes first when the OAuth pair is near
     * expiry (a stale token would 401 → logged failed login + ban-counter tick).
     * Long-lived tokens (tokenExpiry == 0) pass through unchanged. Returns null on a
     * definitive refresh rejection; transient refresh failures propagate.
     */
    private suspend fun freshAccessToken(auth: AuthState): String? {
        val needsRefresh = auth.refreshToken.isNotBlank() &&
            auth.tokenExpiry > 0L &&
            auth.tokenExpiry < System.currentTimeMillis() + 60_000L
        if (!needsRefresh) return auth.accessToken
        return try {
            val tokens = authManager.refreshToken(auth.activeUrl(null), auth.refreshToken)
            authPreferences.saveAuth(
                localUrl = auth.localUrl,
                remoteUrl = auth.remoteUrl,
                homeSsids = auth.homeSsids,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresIn = tokens.expiresIn,
            )
            tokens.accessToken
        } catch (e: HaAuthHttpException) {
            if (e.isDefinitiveRejection) null else throw e
        }
    }

    // ── HA REST ───────────────────────────────────────────────────────────
    // The worker runs while the app (and its WebSocket) is closed, so the engine talks
    // REST. No SSID context in the background → activeUrl(null) prefers the remote URL.

    /** Tracked flights, or null when the FR24 sensor is missing/unavailable. */
    private fun fetchTrackedFlights(baseUrl: String, token: String): List<HaFlight>? {
        val request = Request.Builder()
            .url("$baseUrl/api/states/${Fr24Entities.TRACKED}")
            .header("Authorization", "Bearer $token")
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw AuthRejectedException()
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw IOException("GET states returned HTTP ${resp.code}")
            val root = JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            val state = root["state"]?.takeIf { !it.isJsonNull }?.asString
            if (state in setOf("unavailable", "unknown")) return null
            val attrs = root["attributes"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
            val arr = attrs["flights"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
            return arr.mapNotNull { el -> el.takeIf { it.isJsonObject }?.asJsonObject?.let { HaFlightJson.parse(it) } }
        }
    }

    private fun postRemoveTrack(baseUrl: String, token: String, query: String) =
        postTextValue(baseUrl, token, Fr24Entities.REMOVE, query)

    private fun postTextValue(baseUrl: String, token: String, entityId: String, value: String) {
        val body = JsonObject().apply {
            addProperty("entity_id", entityId)
            addProperty("value", value)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/services/text/set_value")
            .header("Authorization", "Bearer $token")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        okHttpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) throw AuthRejectedException()
            if (!resp.isSuccessful) throw IOException("POST text/set_value returned HTTP ${resp.code}")
        }
    }

    // ── User-facing notification (the worker usually runs with the app closed) ──

    private fun formatDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dayFormatter)

    private fun notifyUser(title: String, text: String) {
        runCatching {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            manager.createNotificationChannel(
                NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Flight tracking")
                    .setDescription("Scheduled flight tracking results")
                    .build(),
            )
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_flight_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build()
            manager.notify((title + text).hashCode(), notification)
        }
    }
}
