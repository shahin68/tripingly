package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

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

/**
 * A fixed square of the map that places load by, the same grid as the server's: `360 / 2^level` degrees a
 * side, square [x] spans longitudes `x·side … (x+1)·side` and [y] latitudes `y·side … (y+1)·side`. Places
 * for map zoom [zoom] load in squares of level `zoom − 2` (about four map tiles a side), so a phone screen
 * shows one to four of them.
 */
data class PlaceSquare(val zoom: Int, val x: Int, val y: Int) {
    val level: Int get() = levelAt(zoom)

    /** How the server names it: `level/x/y`. */
    val id: String get() = "$level/$x/$y"

    val bounds: GeoBounds
        get() {
            val side = 360.0 / 2.0.pow(level)
            return GeoBounds(maxOf(-90.0, y * side), x * side, minOf(90.0, (y + 1) * side), (x + 1) * side)
        }

    companion object {
        /**
         * The squares under [bounds] at map zoom [zoom], plus [ring] more squares on each side. A box across
         * the antimeridian (west east of east) takes squares on both sides of it.
         */
        fun covering(bounds: GeoBounds, zoom: Int, ring: Int = 0): List<PlaceSquare> {
            // The server's zoom range.
            val zoom = zoom.coerceIn(0, 22)
            val level = levelAt(zoom)
            val side = 360.0 / 2.0.pow(level)
            val half = 1 shl (level - 1)
            val ys = (floor(bounds.south / side).toInt() - ring).coerceAtLeast(floor(-90 / side).toInt())..
                (ceil(bounds.north / side).toInt() - 1 + ring).coerceAtMost(ceil(90 / side).toInt() - 1)
            val firstX = floor(bounds.west / side).toInt() - ring
            val lastX = ceil(bounds.east / side).toInt() - 1 + ring
            val xs = if (bounds.west <= bounds.east) (firstX..lastX).toList() else (firstX until half) + (-half..lastX)
            return xs.filter { it in -half until half }.distinct().flatMap { x -> ys.map { y -> PlaceSquare(zoom, x, y) } }
        }

        private fun levelAt(zoom: Int) = (zoom - 2).coerceAtLeast(1)
    }
}
