package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository

class GetCurrentLocationUseCase(
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(): AppResult<Coordinates, DataError.Location> {
        return locationRepository.getCurrentLocation()
    }
}
