package com.falcon.tripingly.core.presentation.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
sealed interface Route : NavKey

@Serializable
data object Home : Route

@Serializable
data class TripMap(val tripId: String) : Route

val NavConfig = SavedStateConfiguration {
    this.serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(TripMap::class, TripMap.serializer())
        }
    }
}
