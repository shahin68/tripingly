package com.falcon.tripingly.core.data.places

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.common.result.map
import com.falcon.tripingly.core.common.result.onSuccess
import com.falcon.tripingly.core.model.place.GeoBounds
import com.falcon.tripingly.core.model.place.MapPlace
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceCluster
import com.falcon.tripingly.core.model.place.PlaceDetails
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.PlacesInView
import com.falcon.tripingly.core.model.place.SearchResultType
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.network.model.InViewResponseDto
import com.falcon.tripingly.core.network.model.PlaceDetailDto
import com.falcon.tripingly.core.network.model.PlaceItemDto
import com.falcon.tripingly.core.network.model.SearchResultDto
import com.falcon.tripingly.core.network.places.PlacesApi
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal class DefaultPlaceRepository(
    private val api: PlacesApi,
) : PlaceRepository {

    /** The last answer and the area it covers, kept as long as the server keeps its own copy. */
    private var lastInView: InViewAnswer? = null

    override suspend fun search(query: String, near: GeoPoint?): AppResult<List<PlaceSearchResult>, DataError.Network> {
        val trimmed = query.trim().take(MAX_QUERY_LENGTH)
        if (trimmed.isEmpty()) return AppResult.Success(emptyList())
        return api.search(trimmed, near?.lat, near?.lng).map { response ->
            response.items.map { PlaceSearchResult(it.name, it.address, GeoPoint(it.location.lat, it.location.lng), it.toType()) }
        }
    }

    override suspend fun placesInView(bounds: GeoBounds, zoom: Float): AppResult<PlacesInView, DataError.Network> {
        // Across the antimeridian the server can't take one box; nothing is drawn there.
        if (bounds.west >= bounds.east || bounds.south >= bounds.north) {
            return AppResult.Success(PlacesInView(emptyList(), emptyList()))
        }
        val zoomLevel = floor(zoom).toInt().coerceIn(0, MAX_ZOOM)
        lastInView?.let {
            val isFresh = it.receivedAt.elapsedNow() < IN_VIEW_KEEP_FOR
            if (isFresh && it.zoomLevel == zoomLevel && bounds in it.covered) return AppResult.Success(it.places)
        }

        val bbox = listOf(bounds.west, bounds.south, bounds.east, bounds.north)
            .joinToString(",") { coordinate(it.coerceIn(-180.0, 180.0)) }
        return api.inView(bbox, zoom.toDouble().coerceIn(0.0, MAX_ZOOM.toDouble())).map { it.toPlacesInView() }
            .onSuccess { lastInView = InViewAnswer(snap(bounds, zoomLevel), zoomLevel, it, TimeSource.Monotonic.markNow()) }
    }

    override suspend fun place(id: String): AppResult<PlaceDetails, DataError.Network> =
        api.place(id).map { it.toPlaceDetails() }

    /**
     * The area the server answers for: it widens the asked box to a grid of quarter map tiles. Same
     * arithmetic as the server's, so a box inside it gets the same answer.
     */
    private fun snap(bounds: GeoBounds, zoomLevel: Int): GeoBounds {
        val step = 360.0 / 2.0.pow(zoomLevel + 2)
        return GeoBounds(
            south = maxOf(-90.0, floor(bounds.south / step) * step),
            west = maxOf(-180.0, floor(bounds.west / step) * step),
            north = minOf(90.0, ceil(bounds.north / step) * step),
            east = minOf(180.0, ceil(bounds.east / step) * step),
        )
    }

    /** Six decimals (about 10 cm) without an exponent: the server reads plain decimals only. */
    private fun coordinate(value: Double): String {
        val micros = (value * 1_000_000).roundToLong()
        val sign = if (micros < 0) "-" else ""
        val whole = abs(micros) / 1_000_000
        val fraction = (abs(micros) % 1_000_000).toString().padStart(6, '0')
        return "$sign$whole.$fraction"
    }

    private fun InViewResponseDto.toPlacesInView() = PlacesInView(
        places = places.map { it.toMapPlace() },
        clusters = clusters.map { PlaceCluster(it.count.toInt(), GeoPoint(it.location.lat, it.location.lng)) },
    )

    private fun PlaceItemDto.toMapPlace() = MapPlace(
        id = id,
        name = name,
        category = category.toCategory(),
        location = GeoPoint(location.lat, location.lng),
        isTripinly = isTripinly,
        likeCount = likeCount.toInt(),
    )

    private fun SearchResultDto.toType() = when {
        source == SearchResultDto.Source.PLACE -> SearchResultType.Place
        else -> when (type) {
            "house" -> SearchResultType.House
            "street" -> SearchResultType.Street
            "locality" -> SearchResultType.Locality
            "district" -> SearchResultType.District
            "city" -> SearchResultType.City
            "county" -> SearchResultType.County
            "state" -> SearchResultType.State
            "country" -> SearchResultType.Country
            else -> SearchResultType.Other
        }
    }

    private fun PlaceDetailDto.toPlaceDetails() = PlaceDetails(
        id = id,
        name = name,
        category = PlaceItemDto.Category.valueOf(category.name).toCategory(),
        likeCount = likeCount.toInt(),
        openingHours = tags.openingHours,
        website = tags.website,
    )

    private fun PlaceItemDto.Category.toCategory() = when (this) {
        PlaceItemDto.Category.CAFE -> PlaceCategory.Cafe
        PlaceItemDto.Category.RESTAURANT -> PlaceCategory.Restaurant
        PlaceItemDto.Category.BAR -> PlaceCategory.Bar
        PlaceItemDto.Category.ATTRACTION -> PlaceCategory.Attraction
        PlaceItemDto.Category.MUSEUM -> PlaceCategory.Museum
        PlaceItemDto.Category.HISTORIC -> PlaceCategory.Historic
        PlaceItemDto.Category.PARK -> PlaceCategory.Park
        PlaceItemDto.Category.NATURE -> PlaceCategory.Nature
        PlaceItemDto.Category.LANDMARK -> PlaceCategory.Landmark
        PlaceItemDto.Category.OTHER -> PlaceCategory.Other
    }

    private class InViewAnswer(
        val covered: GeoBounds,
        val zoomLevel: Int,
        val places: PlacesInView,
        val receivedAt: TimeSource.Monotonic.ValueTimeMark,
    )

    private companion object {
        // The server's limit for `q`.
        const val MAX_QUERY_LENGTH = 100
        // The server's highest zoom.
        const val MAX_ZOOM = 22
        // The server caches an area for 60 s, so newly liked or imported places show after that.
        val IN_VIEW_KEEP_FOR = 60.seconds
    }
}
