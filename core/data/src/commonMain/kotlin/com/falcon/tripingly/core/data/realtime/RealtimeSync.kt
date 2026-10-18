package com.falcon.tripingly.core.data.realtime

import com.falcon.tripingly.core.data.photos.DefaultPhotoRepository
import com.falcon.tripingly.core.data.trips.PendingTripWrites
import com.falcon.tripingly.core.data.trips.TripLocalDataSource
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.toMarker
import com.falcon.tripingly.core.network.realtime.RealtimeClient
import com.falcon.tripingly.core.network.realtime.RealtimeEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Applies the live connection's events to the cache, where the screens see them. Stops come with the
 * event and are saved as they are; other trip changes reload the trip, once per burst. This user's own
 * changes come back as events too and are skipped: the app applied them already. After a reconnect,
 * what was missed meanwhile is reloaded.
 */
internal class RealtimeSync(
    private val realtime: RealtimeClient,
    private val trips: TripRepository,
    private val local: TripLocalDataSource,
    private val pending: PendingTripWrites,
    private val photos: DefaultPhotoRepository,
    /** The signed-in user's id. */
    private val currentUserId: () -> String?,
    private val scope: CoroutineScope,
) : LiveUpdates {

    private var listening: Job? = null

    /** The first connection after sign-in or launch needs no catching up; the screens load what they show. */
    private var hasConnected = false

    private val tripRefreshes = Coalescer(scope) { trips.refreshTrip(it) }
    private val myTripsRefreshes = Coalescer(scope) { trips.refreshMyTrips() }

    override fun start() {
        if (listening == null) {
            listening = scope.launch {
                launch { realtime.events.collect { apply(it) } }
                realtime.connections.collect { onConnected() }
            }
        }
        realtime.start()
    }

    override fun stop() = realtime.stop()

    internal suspend fun apply(event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.InTrip -> applyInTrip(event)
            is RealtimeEvent.ToMe -> photos.applyLive(event)
            is RealtimeEvent.MyTripsChanged -> myTripsRefreshes.request(MY_TRIPS)
            // For the screens showing people; nothing cached changes.
            is RealtimeEvent.FollowsChanged -> Unit
        }
    }

    private suspend fun applyInTrip(event: RealtimeEvent.InTrip) {
        val byMe = event.actorId != null && event.actorId == currentUserId()
        when (event) {
            is RealtimeEvent.MarkerSaved -> if (!byMe) {
                saveInCachedTrip(event.tripId) {
                    // The event's likedByMe is always false; whether I like it stays as it was.
                    val likedByMe = local.marker(event.marker.id)?.likedByMe ?: false
                    local.saveMarkers(listOf(event.marker.toMarker().copy(likedByMe = likedByMe)))
                }
            }
            is RealtimeEvent.MarkerDeleted -> if (!byMe) saveInCachedTrip(event.tripId) { local.deleteMarkers(listOf(event.markerId)) }
            is RealtimeEvent.MarkersReordered -> if (!byMe) saveInCachedTrip(event.tripId) { local.reorderMarkers(event.markerIds) }
            is RealtimeEvent.TripChanged -> if (!byMe && local.trip(event.tripId) != null) tripRefreshes.request(event.tripId)
            is RealtimeEvent.TripDeleted -> local.deleteTrip(event.tripId)
            // My own comments were counted when the server took them.
            is RealtimeEvent.CommentAdded -> if (!byMe) local.addToCommentCount(event.comment.markerId, 1)
            is RealtimeEvent.CommentDeleted -> if (!byMe) local.addToCommentCount(event.markerId, -1)
            is RealtimeEvent.LikeCountChanged ->
                if (event.targetType == PLACE) local.savePlaceLike(event.targetId, event.count, likedByMe = null) else photos.applyLive(event, byMe)
            is RealtimeEvent.PhotoSaved,
            is RealtimeEvent.PhotoFailed,
            is RealtimeEvent.PhotoDeleted,
            is RealtimeEvent.PhotosReordered,
            is RealtimeEvent.MarkerCoverChanged,
            -> photos.applyLive(event, byMe)
        }
    }

    /** Only trips on the device keep stops; while this device's own changes are on their way, it reloads after. */
    private suspend fun saveInCachedTrip(tripId: String, save: suspend () -> Unit) {
        if (local.trip(tripId) == null) return
        pending.saveIfNothingPending(tripId, save)
    }

    private fun onConnected() {
        if (!hasConnected) {
            hasConnected = true
            return
        }
        myTripsRefreshes.request(MY_TRIPS)
        realtime.watchedTrips.forEach { tripRefreshes.request(it) }
        photos.catchUp()
    }

    /** The next account's first connection is a first one again. */
    fun clear() {
        hasConnected = false
    }

    private companion object {
        const val MY_TRIPS = "myTrips"
        const val PLACE = "place"
    }
}
