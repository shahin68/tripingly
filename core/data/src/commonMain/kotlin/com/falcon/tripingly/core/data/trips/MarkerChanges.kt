package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.TripMarker

/** A marker change shown on the device before the server confirms it. */
internal sealed interface MarkerChange {
    val tripId: String
    val markerId: String
    val markerName: String

    data class Add(val marker: TripMarker) : MarkerChange {
        override val tripId get() = marker.tripId
        override val markerId get() = marker.id
        override val markerName get() = marker.name
    }

    data class Update(val before: TripMarker, val after: TripMarker, val update: MarkerUpdate) : MarkerChange {
        override val tripId get() = before.tripId
        override val markerId get() = before.id
        override val markerName get() = after.name
    }

    data class Delete(val marker: TripMarker) : MarkerChange {
        override val tripId get() = marker.tripId
        override val markerId get() = marker.id
        override val markerName get() = marker.name
    }

    data class Reorder(
        override val tripId: String,
        val dayId: String,
        val before: List<String>,
        val after: List<String>,
    ) : MarkerChange {
        override val markerId get() = dayId
        override val markerName get() = ""
    }
}

/**
 * A marker change the server refused or that couldn't reach it. It has already
 * been undone on the device; [MarkerRepository.retry] tries it again.
 */
class MarkerChangeFailure internal constructor(
    val tripId: String,
    /** The stop's name, empty for a reorder. */
    val markerName: String,
    val error: DataError.Network,
    internal val change: MarkerChange,
) {
    /** The connection failed, so trying again can help; a refusal by the server won't change. */
    val isConnectionProblem: Boolean get() = error.isConnectionProblem()
}

internal fun DataError.Network.isConnectionProblem(): Boolean =
    this is DataError.Network.NoInternet ||
        this is DataError.Network.RequestTimeout ||
        this is DataError.Network.ServerError

/** Shows [change] in the cache. */
internal suspend fun TripLocalDataSource.apply(change: MarkerChange) {
    when (change) {
        is MarkerChange.Add -> saveMarker(change.marker)
        is MarkerChange.Update -> saveMarker(change.after)
        is MarkerChange.Delete -> deleteMarker(change.markerId)
        is MarkerChange.Reorder -> reorderMarkers(change.after)
    }
}

/** Takes [change] back out of the cache. */
internal suspend fun TripLocalDataSource.undo(change: MarkerChange) {
    when (change) {
        is MarkerChange.Add -> deleteMarker(change.markerId)
        is MarkerChange.Update -> saveMarker(change.before)
        is MarkerChange.Delete -> saveMarker(change.marker)
        is MarkerChange.Reorder -> reorderMarkers(change.before)
    }
}
