package com.falcon.tripingly.feature.trips.domain.usecase

import com.falcon.tripingly.core.data.repository.TripRepository

class UpdateTripNameUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String, name: String) {
        repository.updateTripName(id, name)
    }
}
