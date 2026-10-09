package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.trip.GeoPoint

/** Places on the server: search (our places, then OpenStreetMap addresses and cities) and the map's places. */
interface PlaceRepository {
    /**
     * From the first letter; a blank query returns nothing without asking the server. Results [near] a
     * point (the map's center) rank first; the point isn't stored.
     */
    suspend fun search(query: String, near: GeoPoint? = null): AppResult<List<PlaceSearchResult>, DataError.Network>

    /**
     * The places to draw for the visible map. The server answers for a slightly larger area, so a small
     * pan or zoom inside that area is answered again without asking it.
     */
    suspend fun placesInView(bounds: GeoBounds, zoom: Float): AppResult<PlacesInView, DataError.Network>

    suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network>
}
