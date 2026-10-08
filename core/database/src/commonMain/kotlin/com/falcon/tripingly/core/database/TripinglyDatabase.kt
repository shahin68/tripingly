package com.falcon.tripingly.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.falcon.tripingly.core.database.dao.TripDao
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.core.database.entity.TripDayEntity
import com.falcon.tripingly.core.database.entity.TripEntity
import com.falcon.tripingly.core.database.entity.TripMemberEntity

/**
 * A cache of the server's data, so schema changes are destructive (the builders use
 * `fallbackToDestructiveMigration`): nothing here is lost that the server doesn't have.
 */
@Database(
    entities = [TripEntity::class, TripDayEntity::class, MarkerEntity::class, TripMemberEntity::class],
    version = 3,
)
@ConstructedBy(TripinglyDatabaseConstructor::class)
abstract class TripinglyDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}

@Suppress("KotlinNoActualForExpect")
expect object TripinglyDatabaseConstructor : RoomDatabaseConstructor<TripinglyDatabase> {
    override fun initialize(): TripinglyDatabase
}
