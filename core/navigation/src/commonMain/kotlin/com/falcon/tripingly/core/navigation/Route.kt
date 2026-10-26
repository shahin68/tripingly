package com.falcon.tripingly.core.navigation

import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

@Serializable
sealed interface Route : NavKey

/** The tab shell: Trips, Explore, Nearby and Profile. */
@Serializable
data object Home : Route

@Serializable
data class TripMap(val tripId: String) : Route

/** Someone else's profile (my own is the Profile tab). */
@Serializable
data class Profile(val username: String) : Route

@Serializable
data object Settings : Route

/** Tripinly Pro: what it unlocks, buying it and restoring earlier purchases. */
@Serializable
data object Paywall : Route

val NavConfig = SavedStateConfiguration {
    this.serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(TripMap::class, TripMap.serializer())
            subclass(Profile::class, Profile.serializer())
            subclass(Settings::class, Settings.serializer())
            subclass(Paywall::class, Paywall.serializer())
        }
    }
}
