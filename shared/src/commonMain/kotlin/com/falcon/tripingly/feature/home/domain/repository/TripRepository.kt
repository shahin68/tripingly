package com.falcon.tripingly.feature.home.domain.repository

import com.falcon.tripingly.feature.home.domain.model.Trip
import kotlinx.coroutines.flow.Flow

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    suspend fun getTripById(id: String): Trip?
    suspend fun saveTrip(trip: Trip)
    suspend fun deleteTrip(id: String)
}
