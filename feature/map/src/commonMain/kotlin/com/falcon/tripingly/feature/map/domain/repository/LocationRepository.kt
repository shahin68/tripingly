package com.falcon.tripingly.feature.map.domain.repository

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getLocationStream(): Flow<AppResult<Coordinates, DataError.Location>>
}