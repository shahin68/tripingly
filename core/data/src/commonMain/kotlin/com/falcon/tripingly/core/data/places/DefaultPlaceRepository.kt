package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.places.PlacesApi

internal class DefaultPlaceRepository(
    private val api: PlacesApi,
) : PlaceRepository {

    override suspend fun search(query: String): AppResult<List<PlaceSearchResult>, DataError.Network> {
        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
        if (trimmed.isEmpty()) return AppResult.Success(emptyList())
        return api.search(trimmed).map { response ->
            response.items.map { PlaceSearchResult(it.name, it.address, GeoPoint(it.location.lat, it.location.lng)) }
        }
    }

    private companion object {
        // The server's limit for `q`.
        const val MAX_QUERY_LENGTH = 100
    }
}
