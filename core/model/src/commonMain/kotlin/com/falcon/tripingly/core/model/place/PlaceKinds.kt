package com.falcon.tripingly.core.model.place

import kotlin.jvm.JvmInline

/**
 * What kind of place it is: a key from the server's list (`GET /app-config` `placeKinds`), such as `cafe`. The app
 * names and draws it through [PlaceKinds]; a key that isn't in the list is shown as Spot.
 */
@JvmInline
value class PlaceCategory(val key: String) {
    companion object {
        /** The catch-all kind, shown as "Spot": a spot's default. */
        val Other = PlaceCategory("other")
    }
}

/**
 * A kind in the server's list: its [label] in the app's language, its [icon] (an ID in the app's icon table), and
 * whether it's offered on the new spot card ([forSpots]) and as a filter chip ([forFilters]).
 */
data class PlaceKind(val category: PlaceCategory, val label: String, val icon: Int, val forSpots: Boolean, val forFilters: Boolean) {
    companion object {
        /** The plain Spot icon, also shown for an icon ID the app doesn't know. */
        const val SPOT_ICON = 1
    }
}

/** The server's place kinds in display order (Shahin, 2026-11-23: lists come from the server, not the app). */
data class PlaceKinds(val all: List<PlaceKind> = emptyList()) {
    private val byKey = all.associateBy { it.category }

    /** The kinds people can pick for a spot they make. */
    val forSpots: List<PlaceKind> get() = all.filter { it.forSpots }

    /** The kinds offered as filter chips. */
    val forFilters: List<PlaceKind> get() = all.filter { it.forFilters }

    /** How [category] is named and drawn; one not in the list (new, or removed) shows as Spot. */
    fun of(category: PlaceCategory?): PlaceKind {
        category?.let(byKey::get)?.let { return it }
        val spot = byKey[PlaceCategory.Other]
        return PlaceKind(category ?: PlaceCategory.Other, spot?.label.orEmpty(), PlaceKind.SPOT_ICON, forSpots = false, forFilters = false)
    }
}
