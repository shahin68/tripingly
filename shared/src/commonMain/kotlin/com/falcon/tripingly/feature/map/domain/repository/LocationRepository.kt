package com.falcon.tripingly.feature.map.domain.repository

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates

interface LocationRepository {
    suspend fun getCurrentLocation(): AppResult<Coordinates, DataError.Location>
}
