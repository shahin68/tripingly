package com.falcon.tripingly.feature.home.domain.usecase

import com.falcon.tripingly.feature.home.domain.repository.TripRepository

class UpdateTripNameUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String, name: String) {
        repository.updateTripName(id, name)
    }
}
