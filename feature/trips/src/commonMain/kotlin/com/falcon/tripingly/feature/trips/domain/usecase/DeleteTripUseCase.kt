package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.data.repository.TripRepository

class DeleteTripUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteTrip(id)
    }
}
