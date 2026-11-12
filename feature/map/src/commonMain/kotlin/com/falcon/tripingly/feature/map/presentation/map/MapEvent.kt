package com.falcon.tripingly.feature.map.presentation.map

/** One-shot things the map screen does outside itself. Camera moves have their own stream (see [MapViewModel.cameraCommands]). */
sealed interface MapEvent {
    data object RequestPermission : MapEvent
    data object OpenAppSettings : MapEvent
    data object NavigateBack : MapEvent
    data class OpenProfile(val username: String) : MapEvent
    data object OpenPaywall : MapEvent

    /** The user tapped a route: the plan sheet opens all the way, so its list, Best order and route show. */
    data object ShowWholePlan : MapEvent

    /** The user added a stop: felt, and the plan sheet opens all the way as for [ShowWholePlan]. */
    data object StopAdded : MapEvent

    /** A stop or place went to one of my trips: felt; a note says where. */
    data object AddedToMyTrip : MapEvent
}
