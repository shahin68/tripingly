package com.falcon.tripingly.core.data.trips

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.falcon.tripingly.core.model.route.TravelMode
import com.falcon.tripingly.core.model.trip.TripMapView
import kotlinx.coroutines.flow.first

/** Remembers how each trip's map was left ([TripMapView]), on the device. Wiped when the session ends. */
interface TripMapViewRepository {
    suspend fun get(tripId: String): TripMapView?
    suspend fun save(tripId: String, view: TripMapView)

    /** The trip was deleted or left. */
    suspend fun remove(tripId: String)
    suspend fun clear()
}

/** Five values per trip in the UI state DataStore, under `tripMap:<tripId>:…`. */
internal class DataStoreTripMapViewRepository(private val store: DataStore<Preferences>) : TripMapViewRepository {

    override suspend fun get(tripId: String): TripMapView? {
        val keys = Keys(tripId)
        val values = store.data.first()
        return TripMapView(
            dayId = values[keys.day] ?: return null,
            latitude = values[keys.latitude] ?: return null,
            longitude = values[keys.longitude] ?: return null,
            zoom = values[keys.zoom] ?: return null,
            isRouteShown = values[keys.route] ?: false,
            travelMode = values[keys.mode]?.let { name -> TravelMode.entries.firstOrNull { it.name == name } } ?: TravelMode.Walking,
        )
    }

    override suspend fun save(tripId: String, view: TripMapView) {
        val keys = Keys(tripId)
        store.edit {
            it[keys.day] = view.dayId
            it[keys.latitude] = view.latitude
            it[keys.longitude] = view.longitude
            it[keys.zoom] = view.zoom
            it[keys.route] = view.isRouteShown
            it[keys.mode] = view.travelMode.name
        }
    }

    override suspend fun remove(tripId: String) {
        val keys = Keys(tripId)
        store.edit {
            it.remove(keys.day)
            it.remove(keys.latitude)
            it.remove(keys.longitude)
            it.remove(keys.zoom)
            it.remove(keys.route)
            it.remove(keys.mode)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private class Keys(tripId: String) {
        val day = stringPreferencesKey("tripMap:$tripId:day")
        val latitude = doublePreferencesKey("tripMap:$tripId:lat")
        val longitude = doublePreferencesKey("tripMap:$tripId:lng")
        val zoom = floatPreferencesKey("tripMap:$tripId:zoom")
        val route = booleanPreferencesKey("tripMap:$tripId:route")
        val mode = stringPreferencesKey("tripMap:$tripId:mode")
    }
}
