package com.falcon.tripingly.core.model.place

/** How many places of one kind a route passes or a circle holds: a filter chip's number. */
data class CategoryCount(val category: PlaceCategory, val count: Int)

/** A place in the circle around a point, [distanceMeters] from it. */
data class PlaceAround(val place: MapPlace, val distanceMeters: Int)

/** The places around a point (hot first, Tripinly spots before OpenStreetMap, then nearest) and how many of each kind. */
data class PlacesAround(val places: List<PlaceAround>, val counts: List<CategoryCount>)
