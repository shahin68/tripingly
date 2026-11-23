package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.place.SearchResultType

/**
 * What a search result is, at a glance (Shahin, 2026-11-20: several "Bratislava" results looked the same): our
 * places by their category, as on their pins; addresses, streets, towns, regions and countries by their kind.
 */
@Composable
@ReadOnlyComposable
fun PlaceSearchResult.icon(): ImageVector = place?.category?.icon() ?: type.icon

private val SearchResultType.icon: ImageVector
    get() = when (this) {
        SearchResultType.House -> Icons.Default.Home
        SearchResultType.Street -> Icons.Default.Signpost
        SearchResultType.Locality -> Icons.Default.Cottage
        SearchResultType.District -> Icons.Default.Apartment
        SearchResultType.City -> Icons.Default.LocationCity
        SearchResultType.County, SearchResultType.State -> Icons.Default.Map
        SearchResultType.Country -> Icons.Default.Public
        SearchResultType.Place, SearchResultType.Other -> Icons.Default.Place
    }
