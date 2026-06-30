package com.uc.homehealth.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * On-device Room database. Currently holds only the local activity feed; add new
 * entities to the [Database.entities] list (and bump the version with a migration)
 * as more on-device persistence is needed.
 */
@Database(
    entities = [ActivityEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
}
