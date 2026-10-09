package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint

/** A place search hit: one of our places, or an address or city from OpenStreetMap. */
data class PlaceSearchResult(
    val name: String,
    /** Street, city and country, to tell results with the same name apart; null for our own places. */
    val address: String?,
    val location: GeoPoint,
)
