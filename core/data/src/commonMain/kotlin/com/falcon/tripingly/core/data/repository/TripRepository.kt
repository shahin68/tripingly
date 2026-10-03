package com.falcon.tripingly.core.data.repository

import com.falcon.tripingly.core.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface TripRepository {
    fun getAllTrips(): Flow<List<Trip>>
    suspend fun getTripById(id: String): Trip?
    suspend fun saveTrip(trip: Trip)
    suspend fun updateTripName(id: String, name: String)
    suspend fun updateTripDates(id: String, startDate: LocalDate, endDate: LocalDate)
    suspend fun deleteTrip(id: String)
}
