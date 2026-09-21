package com.falcon.tripingly.feature.home.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.falcon.tripingly.feature.home.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity)

    @Query("SELECT * FROM trips ORDER BY startDate DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("UPDATE trips SET name = :name WHERE id = :tripId")
    suspend fun updateTripName(tripId: String, name: String)

    @Query("UPDATE trips SET startDate = :startDate, endDate = :endDate WHERE id = :tripId")
    suspend fun updateTripDates(tripId: String, startDate: Long, endDate: Long)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: String)
}
