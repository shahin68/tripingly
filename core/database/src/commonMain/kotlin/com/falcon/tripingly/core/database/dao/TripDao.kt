package com.falcon.tripingly.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.core.database.entity.TripDayEntity
import com.falcon.tripingly.core.database.entity.TripEntity
import com.falcon.tripingly.core.database.entity.TripMemberEntity
import com.falcon.tripingly.core.database.entity.TripWithDetails
import kotlinx.coroutines.flow.Flow

/**
 * The trips cache. Trip rows are upserted (never replaced), so writing a trip
 * from the list keeps the days, markers and members cached for it.
 */
@Dao
abstract class TripDao {
    /** My Trips: owned and collaborating, most recently updated first, like `GET /me/trips`. */
    @Query("SELECT * FROM trips WHERE role != 'viewer' ORDER BY updatedAt DESC, id DESC")
    abstract fun observeMyTrips(): Flow<List<TripEntity>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract fun observeTrip(tripId: String): Flow<TripWithDetails?>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    abstract suspend fun trip(tripId: String): TripEntity?

    @Query("SELECT * FROM markers WHERE id = :markerId")
    abstract suspend fun marker(markerId: String): MarkerEntity?

    @Upsert
    abstract suspend fun upsertTrips(trips: List<TripEntity>)

    @Insert
    abstract suspend fun insertDays(days: List<TripDayEntity>)

    @Upsert
    abstract suspend fun upsertMarkers(markers: List<MarkerEntity>)

    @Insert
    abstract suspend fun insertMembers(members: List<TripMemberEntity>)

    @Query("DELETE FROM trips WHERE role != 'viewer' AND id NOT IN (:keepIds)")
    abstract suspend fun deleteMyTripsExcept(keepIds: List<String>)

    @Query("DELETE FROM trips WHERE id = :tripId")
    abstract suspend fun deleteTrip(tripId: String)

    @Query("DELETE FROM trip_days WHERE tripId = :tripId")
    abstract suspend fun deleteDays(tripId: String)

    @Query("DELETE FROM trip_members WHERE tripId = :tripId")
    abstract suspend fun deleteMembers(tripId: String)

    @Query("DELETE FROM markers WHERE id IN (:markerIds)")
    abstract suspend fun deleteMarkers(markerIds: List<String>)

    /** The name only fills a marker the app added without one. */
    @Query("UPDATE markers SET placeId = :placeId, name = CASE WHEN name = '' THEN :name ELSE name END WHERE id = :markerId")
    abstract suspend fun updateMarkerPlace(markerId: String, placeId: String, name: String)

    @Query("UPDATE markers SET position = :position WHERE id = :markerId")
    abstract suspend fun updateMarkerPosition(markerId: String, position: Int)

    @Query("DELETE FROM trips")
    abstract suspend fun deleteAll()

    /** Replaces the My Trips list; trips no longer in it are dropped with everything cached for them. */
    @Transaction
    open suspend fun replaceMyTrips(trips: List<TripEntity>) {
        deleteMyTripsExcept(trips.map { it.id })
        // The list doesn't say where a trip was copied from or where it goes; keep what the trip itself said.
        upsertTrips(
            trips.map { summary ->
                val cached = trip(summary.id) ?: return@map summary
                summary.copy(
                    copiedFromTripId = cached.copiedFromTripId,
                    copiedFromOwnerId = cached.copiedFromOwnerId,
                    copiedFromOwnerUsername = cached.copiedFromOwnerUsername,
                    copiedFromOwnerDisplayName = cached.copiedFromOwnerDisplayName,
                    destinationName = cached.destinationName,
                    destinationLat = cached.destinationLat,
                    destinationLng = cached.destinationLng,
                )
            },
        )
    }

    /** Replaces one trip with everything in it. */
    @Transaction
    open suspend fun replaceTrip(
        trip: TripEntity,
        days: List<TripDayEntity>,
        markers: List<MarkerEntity>,
        members: List<TripMemberEntity>,
    ) {
        upsertTrips(listOf(trip))
        deleteDays(trip.id) // and their markers
        deleteMembers(trip.id)
        insertDays(days)
        upsertMarkers(markers)
        insertMembers(members)
    }

    /** Applies a day's new marker order. */
    @Transaction
    open suspend fun reorderMarkers(markerIds: List<String>) {
        markerIds.forEachIndexed { position, id -> updateMarkerPosition(id, position) }
    }
}
