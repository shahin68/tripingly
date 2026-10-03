package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.model.Trip
import com.falcon.tripingly.core.data.repository.TripRepository

class GetTripByIdUseCase(
    private val repository: TripRepository
) {
    suspend operator fun invoke(id: String): Trip? = repository.getTripById(id)
}
