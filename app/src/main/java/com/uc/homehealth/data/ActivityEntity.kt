package com.uc.homehealth.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for one action the user performed via this app. Persisted so the Activity
 * feed survives process death (the old [ActivityLog] kept these in memory only).
 */
@Entity(tableName = "activity_events")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,    // light | climate | scene | media | door | auto | ...
    val title: String,
    val body: String,
    val timestamp: Long, // epoch millis
) {
    fun toNotification() = HaNotification(
        id = id,
        kind = kind,
        title = title,
        body = body,
        timestamp = timestamp,
    )
}
