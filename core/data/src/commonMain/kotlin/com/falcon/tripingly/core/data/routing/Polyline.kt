package com.falcon.tripingly.core.data.routing

import com.falcon.tripingly.core.model.trip.GeoPoint

/** Decodes an encoded polyline (Google's format, precision 5), the way the server sends routes. */
internal fun decodePolyline(encoded: String): List<GeoPoint> {
    var index = 0
    fun nextValue(): Int {
        var result = 0
        var shift = 0
        var chunk: Int
        do {
            chunk = encoded[index++].code - 63
            result = result or ((chunk and 0x1f) shl shift)
            shift += 5
        } while (chunk >= 0x20)
        return if (result and 1 != 0) (result shr 1).inv() else result shr 1
    }

    val points = mutableListOf<GeoPoint>()
    var lat = 0
    var lng = 0
    while (index < encoded.length) {
        lat += nextValue()
        lng += nextValue()
        points += GeoPoint(lat / 1e5, lng / 1e5)
    }
    return points
}
