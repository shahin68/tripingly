package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.model.route.TravelMode

/**
 * A pin's Go here and Route buttons (Tripinly Pro). [isGoHereOn]: its route from where I am shows. [hasLocation]: where I
 * am is known, else Go here is dimmed (a tap asks for it). [isRouteTo]: another pin is the start, so Route from says Route
 * to here. [isRouteOn]: the pin is the start or an end of the route shown. On buttons take [mode]'s colour, like the route.
 */
@Immutable
data class PinRoutesState(
    val isGoHereOn: Boolean = false,
    val hasLocation: Boolean = true,
    val isRouteTo: Boolean = false,
    val isRouteOn: Boolean = false,
    val mode: TravelMode = TravelMode.Walking,
)
