package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint

/** The visible part of the map, as asked for by the places in view. */
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    operator fun contains(other: GeoBounds): Boolean =
        other.south >= south && other.north <= north && other.west >= west && other.east <= east

    operator fun contains(point: GeoPoint): Boolean =
        point.lat in south..north && point.lng in west..east
}

enum class PlaceCategory { Cafe, Restaurant, Bar, Attraction, Museum, Historic, Park, Nature, Landmark, Other }

/** A place drawn on the map: one people liked on Tripinly, or one from OpenStreetMap when zoomed in. */
data class MapPlace(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val location: GeoPoint,
    /** Liked on Tripinly; drawn with the Tripinly pin. */
    val isTripinly: Boolean,
    val likeCount: Int,
)

/** Several Tripinly places drawn as one bubble while zoomed out. */
data class PlaceCluster(
    val count: Int,
    val location: GeoPoint,
)

data class PlacesInView(
    val places: List<MapPlace>,
    val clusters: List<PlaceCluster>,
)

/** What the place sheet shows beyond the pin. */
data class PlaceDetails(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val likeCount: Int,
    /** OpenStreetMap `opening_hours`, e.g. "Mo-Su 08:00-21:00". */
    val openingHours: String?,
    val website: String?,
)
