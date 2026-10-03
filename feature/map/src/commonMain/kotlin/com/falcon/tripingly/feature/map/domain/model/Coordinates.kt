package com.falcon.tripingly.feature.map.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Coordinates(
    val latitude: Double,
    val longitude: Double
) {
    companion object {
        // Default destinations for easy global surfing
        val Paris = Coordinates(latitude = 48.8566, longitude = 2.3522)
        val Tokyo = Coordinates(latitude = 35.6762, longitude = 139.6503)
        val NewYork = Coordinates(latitude = 40.7128, longitude = -74.0060)
        val London = Coordinates(latitude = 51.5074, longitude = -0.1278)
        val Rome = Coordinates(latitude = 41.9028, longitude = 12.4964)
        val Sydney = Coordinates(latitude = -33.8688, longitude = 151.2093)
    }
}
