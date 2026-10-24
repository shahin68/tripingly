package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.UserSummary
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
    /** How well known the place is, 0–100 (Wikimedia page views and OpenStreetMap tags); 0 when unknown. */
    val prominence: Int,
    /** The place's image, a Tripinly photo or a Wikimedia Commons image, or null; cache it by this id. */
    val coverImageId: String? = null,
    /** Its thumbnail (signed, valid about an hour), drawn inside a photo pin. */
    val coverThumbUrl: String? = null,
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
    /** For the card, the cover first: trip photos people liked here, then Wikimedia Commons images. */
    val images: List<PlaceImage>,
    /** Who made this custom spot, credited as "Added by"; null for OSM places and when the server keeps it hidden. */
    val addedBy: UserSummary? = null,
)

enum class PlaceImageSource { Trip, Commons }

/**
 * One of a place's images. A trip photo's [id] is the photo's ID; a Commons image has an ID of its own. Both
 * URLs are signed and change about every hour; [displayUrl] is uncropped.
 */
data class PlaceImage(
    val id: String,
    val source: PlaceImageSource,
    val thumbUrl: String?,
    val displayUrl: String?,
    val width: Int?,
    val height: Int?,
    /** Shown with every Commons image; null for trip photos. */
    val credit: ImageCredit?,
)

/** Who to credit for a Wikimedia Commons image, and under which licence. */
data class ImageCredit(
    val author: String?,
    /** The work's title, set only where its licence asks for it. */
    val title: String?,
    /** A licence name such as "CC BY-SA 4.0", or [PUBLIC_DOMAIN]. */
    val license: String,
    val licenseUrl: String?,
    /** The file's page on Commons. */
    val sourceUrl: String,
) {
    val isPublicDomain: Boolean get() = license == PUBLIC_DOMAIN

    companion object {
        /** How the server names a public-domain licence. */
        const val PUBLIC_DOMAIN = "Public domain"
    }
}

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
