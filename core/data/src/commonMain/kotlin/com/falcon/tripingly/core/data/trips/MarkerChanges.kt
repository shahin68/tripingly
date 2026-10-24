package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.model.place.OsmRef
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.TripMarker

/** A marker change shown on the device before the server confirms it (public for the test fakes). */
sealed interface MarkerChange {
    val tripId: String
    val markerId: String
    val markerName: String

    /** [osm]: the address's OpenStreetMap feature, sent with a stop added from search. */
    data class Add(val marker: TripMarker, val osm: OsmRef? = null) : MarkerChange {
        override val tripId get() = marker.tripId
        override val markerId get() = marker.id
        override val markerName get() = marker.name
    }

    data class Update(val before: TripMarker, val after: TripMarker, val update: MarkerUpdate) : MarkerChange {
        override val tripId get() = before.tripId
        override val markerId get() = before.id
        override val markerName get() = after.name
    }

    /** [confirm]: other people's photos, comments and likes on it go too. */
    data class Delete(val marker: TripMarker, val confirm: Boolean = false) : MarkerChange {
        override val tripId get() = marker.tripId
        override val markerId get() = marker.id
        override val markerName get() = marker.name
    }

    /** Deletes all of a day's [markers]; [confirm] as for [Delete]. */
    data class ClearDay(
        override val tripId: String,
        val dayId: String,
        val markers: List<TripMarker>,
        val confirm: Boolean = false,
    ) : MarkerChange {
        override val markerId get() = dayId
        override val markerName get() = ""
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
class MarkerChangeFailure(
    val tripId: String,
    /** The stop's name, empty for a reorder or a cleared day. */
    val markerName: String,
    val error: DataError.Network,
    val change: MarkerChange,
) {
    /** The connection failed, so trying again can help; a refusal by the server won't change. */
    val isConnectionProblem: Boolean get() = error.isConnectionProblem()

    /** A delete the server refused because of other people's content; [MarkerRepository.deleteAnyway] sends it again. */
    val needsConfirm: Boolean get() = error.hasCode(TripErrorCodes.STOP_HAS_OTHERS_CONTENT)
}

/** [change] once more, deleting other people's content too; null for a change that isn't a delete. */
fun MarkerChange.confirmed(): MarkerChange? = when (this) {
    is MarkerChange.Delete -> copy(confirm = true)
    is MarkerChange.ClearDay -> copy(confirm = true)
    else -> null
}

internal fun DataError.Network.isConnectionProblem(): Boolean =
    this is DataError.Network.NoInternet ||
        this is DataError.Network.RequestTimeout ||
        this is DataError.Network.ServerError

/** Shows [change] in the cache. */
internal suspend fun TripLocalDataSource.apply(change: MarkerChange) {
    when (change) {
        is MarkerChange.Add -> saveMarkers(listOf(change.marker))
        is MarkerChange.Update -> saveMarkers(listOf(change.after))
        is MarkerChange.Delete -> deleteMarkers(listOf(change.markerId))
        is MarkerChange.ClearDay -> deleteMarkers(change.markers.map { it.id })
        is MarkerChange.Reorder -> reorderMarkers(change.after)
    }
}

/** Takes [change] back out of the cache. */
internal suspend fun TripLocalDataSource.undo(change: MarkerChange) {
    when (change) {
        is MarkerChange.Add -> deleteMarkers(listOf(change.markerId))
        is MarkerChange.Update -> saveMarkers(listOf(change.before))
        is MarkerChange.Delete -> saveMarkers(listOf(change.marker))
        is MarkerChange.ClearDay -> saveMarkers(change.markers)
        is MarkerChange.Reorder -> reorderMarkers(change.before)
    }
}
