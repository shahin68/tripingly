package com.falcon.tripingly.feature.home.domain.usecase

import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow

class GetAllTripsUseCase(
    private val repository: TripRepository
) {
    operator fun invoke(): Flow<List<Trip>> = repository.getAllTrips()
}
