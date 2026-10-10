package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint

/** A place search hit: one of our places, or an address or city from OpenStreetMap. */
data class PlaceSearchResult(
    val name: String,
    /** Street, city and country, to tell results with the same name apart; null for our own places. */
    val address: String?,
    val location: GeoPoint,
    /** What it is, so the map can show a country whole and a café up close. */
    val type: SearchResultType = SearchResultType.Other,
)

enum class SearchResultType { Place, House, Street, Locality, District, City, County, State, Country, Other }
