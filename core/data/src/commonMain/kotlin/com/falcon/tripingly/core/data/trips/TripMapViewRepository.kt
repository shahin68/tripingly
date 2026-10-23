package com.falcon.tripingly.core.data.trips

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

/** Three values per trip in the UI state DataStore, under `tripMap:<tripId>:…`. */
internal class DataStoreTripMapViewRepository(private val store: DataStore<Preferences>) : TripMapViewRepository {

    override suspend fun get(tripId: String): TripMapView? {
        val keys = Keys(tripId)
        val values = store.data.first()
        return TripMapView(
            dayId = values[keys.day] ?: return null,
            isRouteShown = values[keys.route] ?: false,
            travelMode = values[keys.mode]?.let { name -> TravelMode.entries.firstOrNull { it.name == name } } ?: TravelMode.Walking,
        )
    }

    override suspend fun save(tripId: String, view: TripMapView) {
        val keys = Keys(tripId)
        store.edit {
            it[keys.day] = view.dayId
            it[keys.route] = view.isRouteShown
            it[keys.mode] = view.travelMode.name
        }
    }

    override suspend fun remove(tripId: String) {
        val keys = Keys(tripId)
        store.edit {
            it.remove(keys.day)
            it.remove(keys.route)
            it.remove(keys.mode)
        }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private class Keys(tripId: String) {
        val day = stringPreferencesKey("tripMap:$tripId:day")
        val route = booleanPreferencesKey("tripMap:$tripId:route")
        val mode = stringPreferencesKey("tripMap:$tripId:mode")
    }
}
