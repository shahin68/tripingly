package com.falcon.tripingly.feature.map.presentation.mvi

import com.falcon.tripingly.feature.map.domain.model.Coordinates

sealed interface MapUiEvent {
    data class AnimateCamera(val coordinates: Coordinates, val zoom: Float) : MapUiEvent
    data object RequestPermission : MapUiEvent
    data class ShowSnackbar(val message: String) : MapUiEvent
}
