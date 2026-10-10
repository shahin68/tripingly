package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** The cache's behaviour without Room: list trips keep their cached days, markers and members. */
internal class InMemoryTripLocalDataSource : TripLocalDataSource {
    val trips = MutableStateFlow<Map<String, TripDetails>>(emptyMap())

    override fun observeMyTrips(): Flow<List<Trip>> = trips.map { all ->
        all.values.map { it.trip }.filter { it.role != TripRole.VIEWER }
            .sortedWith(compareByDescending<Trip> { it.updatedAt }.thenByDescending { it.id })
    }

    override fun observeTrip(tripId: String): Flow<TripDetails?> = trips.map { it[tripId] }

    override suspend fun trip(tripId: String): TripDetails? = trips.value[tripId]

    override suspend fun replaceMyTrips(trips: List<Trip>) {
        this.trips.update { cached ->
            val kept = cached.filterValues { it.trip.role == TripRole.VIEWER }
            kept + trips.associate { trip ->
                trip.id to (cached[trip.id]?.copy(trip = trip) ?: TripDetails(trip, emptyList(), emptyList()))
            }
        }
    }

    override suspend fun saveTrip(details: TripDetails) {
        trips.update { it + (details.trip.id to details) }
    }

    override suspend fun deleteTrip(tripId: String) {
        trips.update { it - tripId }
    }

    override suspend fun saveMarkers(markers: List<TripMarker>) = markers.forEach { saveMarker(it) }

    override suspend fun savePlaceOfMarker(markerId: String, placeId: String, name: String) {
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(
                    days = details.days.map { day ->
                        day.copy(markers = day.markers.map { if (it.id == markerId) it.copy(placeId = placeId, name = it.name.ifEmpty { name }) else it })
                    },
                )
            }
        }
    }

    private suspend fun saveMarker(marker: TripMarker) {
        val details = trips.value[marker.tripId] ?: return
        saveTrip(
            details.copy(
                days = details.days.map { day ->
                    val others = day.markers.filterNot { it.id == marker.id }
                    day.copy(markers = if (day.id == marker.dayId) (others + marker).sortedBy { it.position } else others)
                },
            ),
        )
    }

    override suspend fun tripIdOfMarker(markerId: String): String? =
        trips.value.values.firstOrNull { details -> details.days.any { day -> day.markers.any { it.id == markerId } } }?.trip?.id

    override suspend fun deleteMarkers(markerIds: List<String>) {
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(days = details.days.map { day -> day.copy(markers = day.markers.filterNot { it.id in markerIds }) })
            }
        }
    }

    override suspend fun reorderMarkers(markerIds: List<String>) {
        val order = markerIds.withIndex().associate { it.value to it.index }
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(
                    days = details.days.map { day ->
                        day.copy(
                            markers = day.markers.map { marker -> order[marker.id]?.let { marker.copy(position = it) } ?: marker }
                                .sortedBy { it.position },
                        )
                    },
                )
            }
        }
    }

    override suspend fun clear() {
        trips.value = emptyMap()
    }
}
