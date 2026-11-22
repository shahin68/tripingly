package com.falcon.tripingly.core.model.place

import com.falcon.tripingly.core.model.trip.GeoPoint

/**
 * A search's results, best first, and the box of the city the map's centre is in when the server knows it: places,
 * streets and addresses come from there only, cities and countries from anywhere (Shahin, 2026-11-20).
 */
data class PlaceSearch(
    val results: List<PlaceSearchResult>,
    val city: GeoBounds? = null,
)

/** A place search hit: one of our places, or an address or city from OpenStreetMap. */
data class PlaceSearchResult(
    val name: String,
    /** Street, city and country, to tell results with the same name apart; null for our own places. */
    val address: String?,
    val location: GeoPoint,
    /** What it is, so the map can show a country whole and a café up close. */
    val type: SearchResultType = SearchResultType.Other,
    /** One of our places, shown with its card; null for an address or city. */
    val place: MapPlace? = null,
    /** The OpenStreetMap feature of an address or city. */
    val osm: OsmRef? = null,
)

enum class SearchResultType { Place, House, Street, Locality, District, City, County, State, Country, Other }
