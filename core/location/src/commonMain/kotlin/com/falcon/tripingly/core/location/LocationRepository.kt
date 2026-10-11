package com.falcon.tripingly.core.location

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.location.model.Coordinates
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getLocationStream(): Flow<AppResult<Coordinates, DataError.Location>>
}