package com.falcon.tripingly.feature.map.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.falcon.tripingly.feature.map.data.local.entity.MarkerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarker(marker: MarkerEntity)

    @Query("SELECT * FROM markers WHERE tripId = :tripId AND dayIndex = :dayIndex ORDER BY orderNumber ASC")
    fun getMarkersForDay(tripId: String, dayIndex: Int): Flow<List<MarkerEntity>>

    @Query("DELETE FROM markers WHERE id = :markerId")
    suspend fun deleteMarkerById(markerId: String)

    @Query("DELETE FROM markers WHERE tripId = :tripId")
    suspend fun deleteMarkersByTripId(tripId: String)

    @Query("DELETE FROM markers WHERE tripId = :tripId AND dayIndex = :dayIndex")
    suspend fun deleteMarkersForDay(tripId: String, dayIndex: Int)
}
