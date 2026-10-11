package com.falcon.tripingly.core.location

import com.falcon.tripingly.core.common.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.location.LocationDataSource
import com.falcon.tripingly.core.location.LocationRepository
import com.falcon.tripingly.core.location.model.Coordinates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

class LocationRepositoryImpl(
    private val locationDataSource: LocationDataSource,
    private val dispatchers: CoroutineDispatchers
) : LocationRepository {

    override fun getLocationStream(): Flow<AppResult<Coordinates, DataError.Location>> {
        return locationDataSource.getLocationUpdatesStream()
            .flowOn(dispatchers.io)
    }
}
