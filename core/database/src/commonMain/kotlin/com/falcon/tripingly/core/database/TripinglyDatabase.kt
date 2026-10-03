package com.falcon.tripingly.core.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.falcon.tripingly.core.database.dao.TripDao
import com.falcon.tripingly.core.database.entity.TripEntity
import com.falcon.tripingly.core.database.dao.MarkerDao
import com.falcon.tripingly.core.database.entity.MarkerEntity

@Database(entities = [TripEntity::class, MarkerEntity::class], version = 2)
@ConstructedBy(TripinglyDatabaseConstructor::class)
abstract class TripinglyDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun markerDao(): MarkerDao
}

@Suppress("KotlinNoActualForExpect")
expect object TripinglyDatabaseConstructor : RoomDatabaseConstructor<TripinglyDatabase> {
    override fun initialize(): TripinglyDatabase
}
