package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.model.place.PlaceCategory

/** What a kind of place looks like: on its pin, in its card, and where it has no photo. */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.Cafe -> Icons.Default.LocalCafe
        PlaceCategory.Restaurant -> Icons.Default.Restaurant
        PlaceCategory.Bar -> Icons.Default.LocalBar
        PlaceCategory.Attraction -> Icons.Default.Attractions
        PlaceCategory.Museum -> Icons.Default.Museum
        PlaceCategory.Historic -> Icons.Default.Castle
        PlaceCategory.Park -> Icons.Default.Park
        PlaceCategory.Nature -> Icons.Default.Landscape
        PlaceCategory.Landmark -> Icons.Default.AccountBalance
        PlaceCategory.Other -> Icons.Default.Place
    }
