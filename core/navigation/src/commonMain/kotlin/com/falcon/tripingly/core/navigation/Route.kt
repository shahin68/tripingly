package com.falcon.tripingly.core.navigation

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

/** A person's profile; null [username] is my own. */
@Serializable
data class Profile(val username: String?) : Route

/** One of my people lists, by its `PeopleList` name. */
@Serializable
data class People(val list: String) : Route

@Serializable
data object Settings : Route

val NavConfig = SavedStateConfiguration {
    this.serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Home::class, Home.serializer())
            subclass(TripMap::class, TripMap.serializer())
            subclass(Profile::class, Profile.serializer())
            subclass(People::class, People.serializer())
            subclass(Settings::class, Settings.serializer())
        }
    }
}
