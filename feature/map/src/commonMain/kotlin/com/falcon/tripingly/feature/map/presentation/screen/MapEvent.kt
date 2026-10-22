package com.falcon.tripingly.feature.map.presentation.screen

/** One-shot things the map screen does outside itself. Camera moves have their own stream (see [MapViewModel.cameraCommands]). */
sealed interface MapEvent {
    data object RequestPermission : MapEvent
    data object OpenAppSettings : MapEvent
    data object NavigateBack : MapEvent
    data class OpenProfile(val username: String) : MapEvent
    data object OpenPaywall : MapEvent

    /** The user added a stop: the plan sheet opens all the way, so its list and Best order show. */
    data object ShowWholePlan : MapEvent
}
