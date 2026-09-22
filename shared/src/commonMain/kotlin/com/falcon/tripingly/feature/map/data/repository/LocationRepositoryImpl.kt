package com.falcon.tripingly.feature.map.data.repository

import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
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
