package com.falcon.tripingly.core.location

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.location.model.Coordinates
import com.falcon.tripingly.core.location.LocationRepository
import kotlinx.coroutines.flow.Flow

class GetCurrentLocationUseCase(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<AppResult<Coordinates, DataError.Location>> {
        return locationRepository.getLocationStream()
    }
}
