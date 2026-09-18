package com.falcon.tripingly.feature.map.data.datasource

import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic low-level data source interface for acquiring device coordinates.
 */
interface LocationDataSource {
    fun getLocationUpdatesStream(): Flow<AppResult<Coordinates, DataError.Location>>
}
