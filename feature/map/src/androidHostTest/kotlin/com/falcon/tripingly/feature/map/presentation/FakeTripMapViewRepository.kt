package com.falcon.tripingly.feature.map.presentation

import com.falcon.tripingly.core.data.trips.TripMapViewRepository
import com.falcon.tripingly.core.model.trip.TripMapView

/** In memory; on the device the app keeps these in DataStore. */
class FakeTripMapViewRepository : TripMapViewRepository {
    val views = mutableMapOf<String, TripMapView>()

    override suspend fun get(tripId: String) = views[tripId]

    override suspend fun save(tripId: String, view: TripMapView) {
        views[tripId] = view
    }

    val seenJoinRequesters = mutableMapOf<String, Set<String>>()

    override suspend fun seenJoinRequesters(tripId: String) = seenJoinRequesters[tripId].orEmpty()

    override suspend fun saveSeenJoinRequesters(tripId: String, userIds: Set<String>) {
        seenJoinRequesters[tripId] = userIds
    }

    override suspend fun remove(tripId: String) {
        views.remove(tripId)
        seenJoinRequesters.remove(tripId)
    }

    override suspend fun clear() {
        views.clear()
        seenJoinRequesters.clear()
    }
}
