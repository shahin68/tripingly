package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.GeoPoint

/** A few cities, for building and demoing without the server. */
class FakePlaceRepository : PlaceRepository {

    private val places = listOf(
        PlaceSearchResult("Paris", "France", GeoPoint(48.8566, 2.3522)),
        PlaceSearchResult("Vienna", "Austria", GeoPoint(48.2082, 16.3738)),
        PlaceSearchResult("Budapest", "Hungary", GeoPoint(47.4979, 19.0402)),
        PlaceSearchResult("Berlin", "Germany", GeoPoint(52.52, 13.405)),
    )

    override suspend fun search(query: String): AppResult<List<PlaceSearchResult>, DataError.Network> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return AppResult.Success(emptyList())
        return AppResult.Success(places.filter { it.name.contains(trimmed, ignoreCase = true) })
    }
}
