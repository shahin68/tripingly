package com.falcon.tripingly.feature.home.domain.usecase

import com.falcon.tripingly.feature.home.domain.repository.TripRepository

class DeleteTripUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String) {
        repository.deleteTrip(id)
    }
}
