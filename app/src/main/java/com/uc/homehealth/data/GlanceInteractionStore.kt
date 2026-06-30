package com.uc.homehealth.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.ln

// One decayed interaction counter: an exponentially-decayed sum of past events plus the
// timestamp it was last updated, so the live value is value · e^(−λ·age). Frequency
// (accumulation) and recency (decay) collapse into a single number — the "frecency" idea.
data class DecayedScore(val value: Double = 0.0, val updatedAtMs: Long = 0L)

// The two learned signals, each keyed "itemKey@bucket":
//  - engagement: the user acted on the item (ran a scene, tapped a card)
//  - impressions: the item was merely shown
// Comparing them is what lets the feed demote things shown-but-never-tapped (Stage 4).
data class GlanceStats(
    val engagement: Map<String, DecayedScore> = emptyMap(),
    val impressions: Map<String, DecayedScore> = emptyMap(),
)

// ~14-day half-life: an unused habit fades to half weight in two weeks.
private const val HALF_LIFE_MS = 14.0 * 24 * 60 * 60 * 1000
private val DECAY_LAMBDA = ln(2.0) / HALF_LIFE_MS
private const val WEIGHT = 1.0

// Roughly how much accumulated, recent repetition a scene needs before it's confident
// enough to suggest (≈ run it on ~2–3 recent days in the same time bucket).
internal const val GLANCE_SUGGESTION_MIN_SCORE = 2.0

// Stable item key for a scene's learned counter.
internal fun glanceSceneKey(sceneId: String): String = "scene:$sceneId"

// A decayed counter's value brought forward to [nowMs].
internal fun glanceScoreNow(score: DecayedScore, nowMs: Long): Double =
    score.value * exp(-DECAY_LAMBDA * (nowMs - score.updatedAtMs).coerceAtLeast(0L))

// Decayed value of [itemKey] in the given time-of-day [bucket] (the context), at [nowMs].
internal fun glanceScoreFor(map: Map<String, DecayedScore>, itemKey: String, bucket: Int, nowMs: Long): Double =
    map[glanceBucketKey(itemKey, bucket)]?.let { glanceScoreNow(it, nowMs) } ?: 0.0

// Coarse time-of-day buckets so habits can be context-specific (a morning scene vs an
// evening one). Mirrors the dashboard greeting's ranges.
internal fun glanceTimeBucket(nowMs: Long): Int {
    val hour = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
    return when (hour) {
        in 5..10 -> 0   // morning
        in 11..16 -> 1  // afternoon
        in 17..21 -> 2  // evening
        else -> 3       // night
    }
}

internal fun glanceBucketKey(id: String, bucket: Int): String = "$id@$bucket"

/**
 * On-device record of how the user interacts with the dashboard, used to personalize the
 * "at a glance" surface. Stays entirely local (no network) — interaction history is
 * private and never leaves the device. Tracks two decayed "frecency" signals per
 * time-of-day bucket: engagement (acted on) and impressions (merely shown). Scene runs,
 * card taps, and card views all flow through the same generic keys, so the same machinery
 * powers both the "usually now" suggestion and the show-until-ignored cards.
 */
@Singleton
class GlanceInteractionStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private companion object {
        const val TAG = "HomeHealth_Glance"
        val ENGAGEMENT = stringPreferencesKey("glance_engagement_v1")
        val IMPRESSIONS = stringPreferencesKey("glance_impressions_v1")
        val gson = Gson()
        val MAP_TYPE = object : TypeToken<Map<String, DecayedScore>>() {}.type
    }

    val stats: Flow<GlanceStats> = dataStore.data
        .map { GlanceStats(decode(it[ENGAGEMENT]), decode(it[IMPRESSIONS])) }
        .distinctUntilChanged()

    suspend fun recordSceneRun(sceneId: String) {
        if (sceneId.isNotBlank()) recordEngagement(glanceSceneKey(sceneId))
    }

    // The user acted on [itemKey] (ran/opened/tapped) — the positive signal.
    suspend fun recordEngagement(itemKey: String) {
        Log.d(TAG, "engagement ← $itemKey")
        bump(ENGAGEMENT, itemKey)
    }

    // [itemKey] was shown to the user — the "seen it" signal, used to demote things that
    // are repeatedly shown but never engaged with.
    suspend fun recordImpression(itemKey: String) {
        Log.d(TAG, "impression ← $itemKey")
        bump(IMPRESSIONS, itemKey)
    }

    // Wipe all learned interaction history (backs a future "reset learning" affordance).
    suspend fun clear() {
        dataStore.edit { it.remove(ENGAGEMENT); it.remove(IMPRESSIONS) }
    }

    // Decay the matching counter to the present, then add one event's weight. O(1) and
    // tiny — no event log to prune.
    private suspend fun bump(prefKey: Preferences.Key<String>, itemKey: String) {
        if (itemKey.isBlank()) return
        val now = System.currentTimeMillis()
        val key = glanceBucketKey(itemKey, glanceTimeBucket(now))
        dataStore.edit { prefs ->
            val map = decode(prefs[prefKey]).toMutableMap()
            val carried = map[key]?.let { glanceScoreNow(it, now) } ?: 0.0
            map[key] = DecayedScore(carried + WEIGHT, now)
            prefs[prefKey] = gson.toJson(map)
        }
    }

    private fun decode(raw: String?): Map<String, DecayedScore> =
        if (raw.isNullOrBlank()) emptyMap()
        else runCatching { gson.fromJson<Map<String, DecayedScore>>(raw, MAP_TYPE) }.getOrDefault(emptyMap())
}
