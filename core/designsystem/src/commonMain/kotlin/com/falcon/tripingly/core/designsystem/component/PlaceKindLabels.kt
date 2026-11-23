package com.falcon.tripingly.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceKind
import com.falcon.tripingly.core.model.place.PlaceKinds

/**
 * The server's place kinds, labelled in the app's language (`GET /app-config`), provided once at the app's root.
 * Read like string resources: a language change relabels every open screen at once. Previews, which have no server,
 * get [PreviewPlaceKinds].
 */
val LocalPlaceKinds = staticCompositionLocalOf { PreviewPlaceKinds }

/** The kind's name in the app's language; Spot's for a kind the server's list doesn't have. */
@Composable
@ReadOnlyComposable
fun PlaceCategory.label(): String = LocalPlaceKinds.current.of(this).label

/** What the kind looks like: on its pin, in its card, and where it has no photo. */
@Composable
@ReadOnlyComposable
fun PlaceCategory.icon(): ImageVector = appIcon(LocalPlaceKinds.current.of(this).icon)

/** A few kinds for previews, which have no server. */
val PreviewPlaceKinds = PlaceKinds(
    listOf(
        PlaceKind(PlaceCategory.Other, "Spot", 1, forSpots = true, forFilters = false),
        PlaceKind(PlaceCategory("photo_spot"), "Photo spot", 2, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("cafe"), "Café", 4, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("restaurant"), "Restaurant", 5, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("fast_food"), "Fast food", 8, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("museum"), "Museum", 13, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("historic"), "Historic site", 14, forSpots = true, forFilters = true),
        PlaceKind(PlaceCategory("park"), "Park", 17, forSpots = true, forFilters = true),
    ),
)
