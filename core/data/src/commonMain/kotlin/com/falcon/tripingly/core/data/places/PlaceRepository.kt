package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.config.AppConfig
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.coroutines.flow.Flow

/** Places on the server: search (our places, then OpenStreetMap addresses and cities) and the map's places. */
interface PlaceRepository {
    /**
     * From the first letter; a blank query returns nothing without asking the server. Results [near] a
     * point (the map's center) rank first; the point isn't stored.
     */
    suspend fun search(query: String, near: GeoPoint? = null): AppResult<List<PlaceSearchResult>, DataError.Network>

    /**
     * The places of map [squares]. Squares asked for at about the same time, by any caller, go to the server
     * together (up to 16 a request). Answers are kept and shared, so a square waits for the server only the
     * first time; one older than the server's refresh time ([AppConfig.placesRefresh]) is answered as it is
     * and reloaded in the background, and [changedSquares] says when that brought something new. A caller
     * that stops waiting doesn't stop the request: its answer is kept for the next one. Fails when any of
     * the squares has never loaded and can't be loaded now.
     */
    suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network>

    /** Squares whose places changed when they were reloaded in the background. */
    val changedSquares: Flow<PlaceSquare>

    /** The [squares] already loaded, without asking the server. */
    suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView>

    suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network>
}
