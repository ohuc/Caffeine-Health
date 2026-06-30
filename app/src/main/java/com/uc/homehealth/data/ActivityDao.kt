package com.uc.homehealth.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {

    /** Newest first; tie-break on id so same-millisecond inserts keep a stable order. */
    @Query("SELECT * FROM activity_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>

    @Insert
    suspend fun insert(event: ActivityEntity): Long

    /** Keep only the newest [keep] rows; drop the rest so the table stays bounded. */
    @Query(
        "DELETE FROM activity_events WHERE id NOT IN " +
            "(SELECT id FROM activity_events ORDER BY timestamp DESC, id DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int)

    /** Remove a single event by id — backs swipe-to-delete in the Activity feed. */
    @Query("DELETE FROM activity_events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM activity_events")
    suspend fun clear()
}
