package com.falcon.tripingly.feature.home.domain.usecase

import com.falcon.tripingly.feature.home.domain.model.Trip
import com.falcon.tripingly.feature.home.domain.repository.TripRepository

class GetTripByIdUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String): Trip? = repository.getTripById(id)
}
