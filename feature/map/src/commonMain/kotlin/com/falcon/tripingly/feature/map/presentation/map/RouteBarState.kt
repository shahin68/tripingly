package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.ui.UiText

/**
 * The header while routing between two points: where from ([from]: a stop's title, the place's name or "Your
 * location") and where to ([to]). The end being picked is null: its pill is the search field while search is open.
 * [isEditing]: that end was set and is picked again, so ✕ cancels that instead of closing the route. The marks take
 * [mode]'s colour, like the route.
 */
@Immutable
data class RouteBarState(val from: UiText?, val to: UiText?, val mode: TravelMode = TravelMode.Walking, val isEditing: Boolean = false)
