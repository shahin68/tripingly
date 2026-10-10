package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
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
     * The places of map [squares]. Squares asked for at about the same time, by any caller, go to the server
     * together (up to 16 a request), and answers are kept a while and shared, so a square is asked for once.
     * A caller that stops waiting doesn't stop the request: its answer is kept for the next one. Fails when
     * any of the squares can't be loaded.
     */
    suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network>

    /** The [squares] already loaded, without asking the server. */
    suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView>

    suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network>
}
