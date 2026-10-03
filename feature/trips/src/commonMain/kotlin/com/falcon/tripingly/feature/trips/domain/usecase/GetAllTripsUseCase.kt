package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.model.Trip
import com.falcon.tripingly.core.data.repository.TripRepository
import kotlinx.coroutines.flow.Flow

class GetAllTripsUseCase(
    private val repository: TripRepository
) {
    operator fun invoke(): Flow<List<Trip>> = repository.getAllTrips()
}
