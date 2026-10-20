package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.model.trip.TripMapView

/** In memory, for tests. */
class FakeTripMapViewRepository : TripMapViewRepository {
    private val views = mutableMapOf<String, TripMapView>()

    override suspend fun get(tripId: String): TripMapView? = views[tripId]
    override suspend fun save(tripId: String, view: TripMapView) { views[tripId] = view }
    override suspend fun remove(tripId: String) { views.remove(tripId) }
    override suspend fun clear() = views.clear()
}
