package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.model.Page
import com.falcon.tripingly.core.model.place.CategoryCount
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceAround
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearch
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlaceSquare
import com.falcon.tripingly.core.model.place.PlacesAround
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.social.NearbyPlace
import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt

/** A few cities and sights, for building and demoing without the server. */
class FakePlaceRepository : PlaceRepository {
    private val places = listOf(
        PlaceSearchResult("Paris", "France", GeoPoint(48.8566, 2.3522), SearchResultType.City),
        PlaceSearchResult("Vienna", "Austria", GeoPoint(48.2082, 16.3738), SearchResultType.City),
        PlaceSearchResult("Budapest", "Hungary", GeoPoint(47.4979, 19.0402), SearchResultType.City),
        PlaceSearchResult("Berlin", "Germany", GeoPoint(52.52, 13.405), SearchResultType.City),
        // OpenStreetMap has no address for it: the app shows how far it is instead.
        PlaceSearchResult("KFC", null, GeoPoint(48.2000, 16.3738), SearchResultType.Place),
    )

    private val sights = listOf(
        MapPlace("fake-eiffel", "Eiffel Tower", PlaceCategory("landmark"), GeoPoint(48.8584, 2.2945), isTripinly = true, hotCount = 12, prominence = 100),
        MapPlace("fake-louvre", "Louvre", PlaceCategory("museum"), GeoPoint(48.8606, 2.3376), isTripinly = true, hotCount = 8, prominence = 97),
        MapPlace("fake-stephansdom", "Stephansdom", PlaceCategory("attraction"), GeoPoint(48.2085, 16.3731), isTripinly = true, hotCount = 5, prominence = 90),
        MapPlace("fake-cafe-central", "Café Central", PlaceCategory("cafe"), GeoPoint(48.2104, 16.3655), isTripinly = false, hotCount = 0, prominence = 40),
    )

    override suspend fun search(query: String, near: GeoPoint?): AppResult<PlaceSearch, DataError.Network> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return AppResult.Success(PlaceSearch(emptyList()))
        return AppResult.Success(PlaceSearch(places.filter { it.name.startsWith(trimmed, ignoreCase = true) }))
    }

    override suspend fun searchDestinations(query: String): AppResult<List<PlaceSearchResult>, DataError.Network> =
        search(query, near = null).map { it.results }

    override suspend fun placesIn(squares: Collection<PlaceSquare>): AppResult<Map<PlaceSquare, PlacesInView>, DataError.Network> =
        AppResult.Success(loadedPlacesIn(squares))

    override val changedSquares: Flow<PlaceSquare> = emptyFlow()

    override suspend fun loadedPlacesIn(squares: Collection<PlaceSquare>): Map<PlaceSquare, PlacesInView> =
        squares.associateWith { square ->
            val bounds = square.bounds
            // A square of some kinds, or of a name, lists only places of those kinds or named so, as the server's tiles do.
            PlacesInView(
                sights.filter {
                    it.location.lat >= bounds.south && it.location.lat < bounds.north && it.location.lng >= bounds.west && it.location.lng < bounds.east &&
                        (square.categories.isEmpty() || it.category in square.categories) &&
                        (square.name == null || it.name.contains(square.name!!, ignoreCase = true))
                },
                emptyList(),
            )
        }

    override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> {
        val place = sights.firstOrNull { it.id == id } ?: return AppResult.Error(DataError.Network.Api(404, "NOT_FOUND", "This place isn't available."))
        return AppResult.Success(PlaceDetails(place.id, place.name, place.category, place.hotCount, "Mo-Su 09:00-18:00", null, images = emptyList(), pin = place))
    }

    /** The sights within [radiusMeters] of [point] but [excludePlaceId], of [categories], nearest first, with every kind counted. */
    override suspend fun around(point: GeoPoint, radiusMeters: Int, categories: Set<PlaceCategory>, excludePlaceId: String?): AppResult<PlacesAround, DataError.Network> {
        val inCircle = sights.filter { it.id != excludePlaceId }.map { PlaceAround(it, distanceMeters(point, it.location)) }.filter { it.distanceMeters <= radiusMeters }
        val counts = inCircle.groupingBy { it.place.category }.eachCount().map { (category, count) -> CategoryCount(category, count) }.sortedByDescending { it.count }
        val shown = inCircle.filter { categories.isEmpty() || it.place.category in categories }.sortedBy { it.distanceMeters }
        return AppResult.Success(PlacesAround(shown, counts))
    }

    /** The sights by distance, as the crow flies. */
    override suspend fun nearby(point: GeoPoint, cursor: String?): AppResult<Page<NearbyPlace>, DataError.Network> =
        AppResult.Success(
            Page(
                sights.map { place ->
                    NearbyPlace(
                        id = place.id,
                        name = place.name,
                        category = place.category,
                        location = place.location,
                        isTripinly = place.isTripinly,
                        hotCount = place.hotCount,
                        hotByMe = false,
                        coverImageId = null,
                        coverSource = null,
                        coverThumbUrl = null,
                        distanceMeters = distanceMeters(point, place.location),
                    )
                }.sortedBy { it.distanceMeters },
                nextCursor = null,
            ),
        )

    /** The sights named like [query], hot ones first; with [near], each with its distance. */
    override suspend fun spots(query: String, near: GeoPoint?, cursor: String?): AppResult<Page<NearbyPlace>, DataError.Network> {
        val trimmed = query.trim()
        if (trimmed.length < 2) return AppResult.Success(Page(emptyList(), nextCursor = null))
        return AppResult.Success(
            Page(
                sights.filter { it.name.contains(trimmed, ignoreCase = true) }.sortedByDescending { it.hotCount }.map { place ->
                    NearbyPlace(
                        id = place.id,
                        name = place.name,
                        category = place.category,
                        location = place.location,
                        isTripinly = place.isTripinly,
                        hotCount = place.hotCount,
                        hotByMe = false,
                        coverImageId = null,
                        coverSource = null,
                        coverThumbUrl = null,
                        distanceMeters = near?.let { distanceMeters(it, place.location) },
                    )
                },
                nextCursor = null,
            ),
        )
    }

    private fun distanceMeters(a: GeoPoint, b: GeoPoint): Int {
        val lat = (b.lat - a.lat) * METERS_PER_DEGREE
        val lng = (b.lng - a.lng) * METERS_PER_DEGREE * cos(a.lat * PI / 180)
        return sqrt(lat * lat + lng * lng).toInt()
    }

    private companion object {
        const val METERS_PER_DEGREE = 111_320.0
    }
}
