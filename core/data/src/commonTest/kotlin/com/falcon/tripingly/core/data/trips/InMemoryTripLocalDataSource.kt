package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.model.trip.JoinRequestStatus
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
        all.values.map { it.trip }.filter { it.role != TripRole.VIEWER || it.savedByMe }
            .sortedWith(compareByDescending<Trip> { it.updatedAt }.thenByDescending { it.id })
    }

    override fun observeTrip(tripId: String): Flow<TripDetails?> = trips.map { it[tripId] }

    override suspend fun trip(tripId: String): TripDetails? = trips.value[tripId]

    override suspend fun replaceMyTrips(trips: List<Trip>) {
        this.trips.update { cached ->
            val kept = cached.filterValues { it.trip.role == TripRole.VIEWER && !it.trip.savedByMe }
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

    override suspend fun saveSavedByMe(trip: Trip, saved: Boolean) {
        trips.update { all ->
            val details = all[trip.id] ?: TripDetails(trip, emptyList(), emptyList())
            all + (trip.id to details.copy(trip = details.trip.copy(savedByMe = saved)))
        }
    }

    override suspend fun saveMyJoinRequest(tripId: String, status: JoinRequestStatus?) {
        trips.update { all -> all[tripId]?.let { all + (tripId to it.copy(myJoinRequest = status)) } ?: all }
    }

    override suspend fun removePendingJoinRequest(tripId: String) {
        trips.update { all ->
            all[tripId]?.let { all + (tripId to it.copy(pendingJoinRequestCount = maxOf(0, it.pendingJoinRequestCount - 1))) } ?: all
        }
    }

    override suspend fun saveMarkers(markers: List<TripMarker>) {
        markers.forEach { saveMarker(it) }
        recountMarkers()
    }

    override suspend fun savePlaceOfMarker(markerId: String, placeId: String, name: String, hotCount: Int, hotByMe: Boolean, canMarkHot: Boolean) {
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(
                    days = details.days.map { day ->
                        day.copy(markers = day.markers.map { if (it.id == markerId) it.copy(placeId = placeId, name = it.name.ifEmpty { name }, hotCount = hotCount, hotByMe = hotByMe, canMarkHot = canMarkHot) else it })
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

    override suspend fun marker(markerId: String): TripMarker? =
        trips.value.values.firstNotNullOfOrNull { details -> details.days.firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.id == markerId } } }

    override suspend fun markerAtPlace(placeId: String): TripMarker? =
        trips.value.values.firstNotNullOfOrNull { details -> details.days.firstNotNullOfOrNull { day -> day.markers.firstOrNull { it.placeId == placeId } } }

    override suspend fun savePlaceHot(placeId: String, hotCount: Int, hotByMe: Boolean?) =
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(
                    days = details.days.map { day ->
                        day.copy(markers = day.markers.map { if (it.placeId == placeId) it.copy(hotCount = hotCount, hotByMe = hotByMe ?: it.hotByMe) else it })
                    },
                )
            }
        }

    override suspend fun addToCommentCount(markerId: String, delta: Int) =
        updateMarker(markerId) { it.copy(commentCount = maxOf(0, it.commentCount + delta)) }

    private fun updateMarker(markerId: String, change: (TripMarker) -> TripMarker) {
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(days = details.days.map { day -> day.copy(markers = day.markers.map { if (it.id == markerId) change(it) else it }) })
            }
        }
    }

    override suspend fun deleteMarkers(markerIds: List<String>) {
        trips.update { all ->
            all.mapValues { (_, details) ->
                details.copy(days = details.days.map { day -> day.copy(markers = day.markers.filterNot { it.id in markerIds }) })
            }
        }
        recountMarkers()
    }

    /** Like Room: My Trips' stop count follows the cached stops of trips opened before. */
    private fun recountMarkers() {
        trips.update { all ->
            all.mapValues { (_, details) ->
                if (details.days.isEmpty()) details
                else details.copy(trip = details.trip.copy(markerCount = details.days.sumOf { it.markers.size }))
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
