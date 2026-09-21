package com.falcon.tripingly.core.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.falcon.tripingly.feature.home.data.local.dao.TripDao
import com.falcon.tripingly.feature.home.data.local.entity.TripEntity
import com.falcon.tripingly.feature.map.data.local.dao.MarkerDao
import com.falcon.tripingly.feature.map.data.local.entity.MarkerEntity

@Database(entities = [TripEntity::class, MarkerEntity::class], version = 1)
@ConstructedBy(TripinglyDatabaseConstructor::class)
abstract class TripinglyDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun markerDao(): MarkerDao
}

@Suppress("KotlinNoActualForExpect")
expect object TripinglyDatabaseConstructor : RoomDatabaseConstructor<TripinglyDatabase> {
    override fun initialize(): TripinglyDatabase
}
