package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint

/** A few cities and sights, for building and demoing without the server. */
class FakePlaceRepository : PlaceRepository {

    private val places = listOf(
        PlaceSearchResult("Paris", "France", GeoPoint(48.8566, 2.3522), SearchResultType.City),
        PlaceSearchResult("Vienna", "Austria", GeoPoint(48.2082, 16.3738), SearchResultType.City),
        PlaceSearchResult("Budapest", "Hungary", GeoPoint(47.4979, 19.0402), SearchResultType.City),
        PlaceSearchResult("Berlin", "Germany", GeoPoint(52.52, 13.405), SearchResultType.City),
    )

    private val sights = listOf(
        MapPlace("fake-eiffel", "Eiffel Tower", PlaceCategory.Landmark, GeoPoint(48.8584, 2.2945), isTripinly = true, likeCount = 12),
        MapPlace("fake-louvre", "Louvre", PlaceCategory.Museum, GeoPoint(48.8606, 2.3376), isTripinly = true, likeCount = 8),
        MapPlace("fake-stephansdom", "Stephansdom", PlaceCategory.Attraction, GeoPoint(48.2085, 16.3731), isTripinly = true, likeCount = 5),
        MapPlace("fake-cafe-central", "Café Central", PlaceCategory.Cafe, GeoPoint(48.2104, 16.3655), isTripinly = false, likeCount = 0),
    )

    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<PlaceSearchResult>, DataError.Network> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return AppResult.Success(emptyList())
        return AppResult.Success(places.filter { it.name.startsWith(trimmed, ignoreCase = true) })
    }

    override suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network> =
        AppResult.Success(loadedPlacesIn(squares))

    override suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView> =
        squares.associateWith { square ->
            val bounds = square.bounds
            PlacesInView(sights.filter { it.location.lat >= bounds.south && it.location.lat < bounds.north && it.location.lng >= bounds.west && it.location.lng < bounds.east }, emptyList())
        }

    override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> {
        val place = sights.firstOrNull { it.id == id } ?: return AppResult.Error(DataError.Network.Api(404, "NOT_FOUND", "This place isn't available."))
        return AppResult.Success(PlaceDetails(place.id, place.name, place.category, place.likeCount, "Mo-Su 09:00-18:00", null))
    }
}
