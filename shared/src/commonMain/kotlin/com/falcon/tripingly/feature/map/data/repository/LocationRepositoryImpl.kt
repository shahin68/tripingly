package com.falcon.tripingly.feature.map.data.repository

import com.falcon.tripingly.core.coroutines.CoroutineDispatchers
import com.falcon.tripingly.core.domain.error.DataError
import com.falcon.tripingly.core.domain.result.AppResult
import com.falcon.tripingly.feature.map.data.datasource.LocationDataSource
import com.falcon.tripingly.feature.map.domain.model.Coordinates
import com.falcon.tripingly.feature.map.domain.repository.LocationRepository
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class LocationRepositoryImpl(
    private val locationDataSource: LocationDataSource,
    private val dispatchers: CoroutineDispatchers
) : LocationRepository {

    override suspend fun getCurrentLocation(): AppResult<Coordinates, DataError.Location> {
        return withContext(dispatchers.io) {
            try {
                locationDataSource.getLastKnownOrCurrentLocation()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppResult.Error(DataError.Location.Unknown(e.message))
            }
        }
    }
}
