package com.falcon.tripingly.feature.map.presentation.map

import androidx.compose.runtime.Immutable
import com.falcon.tripingly.core.ui.UiText

/**
 * The header while routing between two points: where from ([from]: a stop's title, the place's name or "Your
 * location") and where to ([to]; null while it's being picked, then the "to" field searches when [search] is open).
 */
@Immutable
data class RouteBarState(val from: UiText, val to: UiText?)
