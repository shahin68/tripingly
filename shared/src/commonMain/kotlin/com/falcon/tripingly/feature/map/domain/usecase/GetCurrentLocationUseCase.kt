package com.falcon.tripingly.feature.map.domain.usecase

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentLocationUseCase(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<AppResult<Coordinates, DataError.Location>> {
        return locationRepository.getLocationStream()
    }
}
