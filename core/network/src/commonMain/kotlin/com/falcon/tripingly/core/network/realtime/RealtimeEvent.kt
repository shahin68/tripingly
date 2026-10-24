package com.falcon.tripingly.core.network.realtime

import com.falcon.tripingly.core.network.NetworkJson
import com.falcon.tripingly.core.network.model.CommentDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.PhotoDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/** What the server says changed (docs/knowledge/04-realtime-and-push.md). Events the app doesn't use aren't parsed. */
sealed interface RealtimeEvent {

    /** Sent to everyone watching [tripId]; [actorId] is who changed it (null for the system). */
    sealed interface InTrip : RealtimeEvent {
        val tripId: String
        val actorId: String?
    }

    /** `marker.created`, `marker.updated`. */
    data class MarkerSaved(override val tripId: String, override val actorId: String?, val marker: MarkerDto) : InTrip

    data class MarkerDeleted(override val tripId: String, override val actorId: String?, val markerId: String) : InTrip

    data class MarkersReordered(override val tripId: String, override val actorId: String?, val markerIds: List<String>) : InTrip

    data class MarkerCoverChanged(override val tripId: String, override val actorId: String?, val markerId: String) : InTrip

    /** `photo.processing`, `photo.ready`; the actor of `photo.ready` is the uploader. */
    data class PhotoSaved(override val tripId: String, override val actorId: String?, val photo: PhotoDto) : InTrip

    data class PhotoFailed(
        override val tripId: String,
        override val actorId: String?,
        val photoId: String,
        val markerId: String,
    ) : InTrip

    data class PhotoDeleted(
        override val tripId: String,
        override val actorId: String?,
        val photoId: String,
        val markerId: String,
    ) : InTrip

    data class PhotosReordered(
        override val tripId: String,
        override val actorId: String?,
        val markerId: String,
        val photoIds: List<String>,
    ) : InTrip

    /** `comment.created`; the comment is viewer-neutral (`likedByMe` and `canDelete` false). */
    data class CommentAdded(override val tripId: String, override val actorId: String?, val comment: CommentDto) : InTrip

    data class CommentDeleted(
        override val tripId: String,
        override val actorId: String?,
        val commentId: String,
        val markerId: String,
    ) : InTrip

    /** `like.count_changed` for a trip, photo or comment: [targetType] as in `PUT /likes/{targetType}/…`. */
    data class LikeCountChanged(
        override val tripId: String,
        override val actorId: String?,
        val targetType: String,
        val targetId: String,
        val count: Int,
    ) : InTrip

    /** `place.hot_changed`: a place's hot count, sent to every trip with a stop there. */
    data class PlaceHotChanged(
        override val tripId: String,
        override val actorId: String?,
        val placeId: String,
        val hotCount: Int,
    ) : InTrip

    /** The trip itself, its days or its members changed (`trip.updated`, `day.*`, `member.*`). */
    data class TripChanged(override val tripId: String, override val actorId: String?) : InTrip

    data class TripDeleted(override val tripId: String, override val actorId: String?) : InTrip

    /** `trips.changed`, to the user: a trip was added to or removed from My Trips. */
    data class MyTripsChanged(val tripId: String) : RealtimeEvent

    /** Sent to me alone. */
    sealed interface ToMe : RealtimeEvent

    /** `photo.ready` for my photo on a place (place photos have no trip room). */
    data class PlacePhotoSaved(val photo: PhotoDto) : ToMe

    /** `photo.failed` for my photo on a place. */
    data class PlacePhotoFailed(val photoId: String, val placeId: String) : ToMe

    /** `follows.changed`, to both people: [followerId] started ([following]) or stopped following [followeeId]. */
    data class FollowsChanged(val followerId: String, val followeeId: String, val following: Boolean) : RealtimeEvent
}

/** What every event carries on the wire. */
@Serializable
private data class Envelope(
    val tripId: String? = null,
    val actorId: String? = null,
    val data: JsonElement = JsonNull,
)

@Serializable
private data class MarkerData(val marker: MarkerDto)

@Serializable
private data class MarkerIdData(val markerId: String)

@Serializable
private data class MarkerOrderData(val markerIds: List<String>)

@Serializable
private data class PhotoData(val photo: PhotoDto)

@Serializable
private data class PhotoIdData(val photoId: String, val markerId: String? = null, val placeId: String? = null)

@Serializable
private data class PhotoOrderData(val markerId: String, val photoIds: List<String>)

@Serializable
private data class CommentData(val comment: CommentDto)

@Serializable
private data class CommentIdData(val commentId: String, val markerId: String)

@Serializable
private data class LikeCountData(val targetType: String, val targetId: String, val count: Int)

@Serializable
private data class PlaceHotData(val placeId: String, val hotCount: Int)

@Serializable
private data class TripIdData(val tripId: String)

@Serializable
private data class FollowData(val followerId: String, val followeeId: String, val following: Boolean)

/** The event, or null when the app doesn't use it or it can't be read. */
internal fun parseRealtimeEvent(name: String, payload: JsonElement): RealtimeEvent? = try {
    val envelope = NetworkJson.decodeFromJsonElement<Envelope>(payload)
    val data = envelope.data
    val tripId = envelope.tripId
    val actorId = envelope.actorId
    fun <T> inTrip(build: (String) -> T): T? = tripId?.let(build)
    when (name) {
        "marker.created", "marker.updated" ->
            inTrip { RealtimeEvent.MarkerSaved(it, actorId, NetworkJson.decodeFromJsonElement<MarkerData>(data).marker) }
        "marker.deleted" ->
            inTrip { RealtimeEvent.MarkerDeleted(it, actorId, NetworkJson.decodeFromJsonElement<MarkerIdData>(data).markerId) }
        "markers.reordered" ->
            inTrip { RealtimeEvent.MarkersReordered(it, actorId, NetworkJson.decodeFromJsonElement<MarkerOrderData>(data).markerIds) }
        "marker.cover_changed" ->
            inTrip { RealtimeEvent.MarkerCoverChanged(it, actorId, NetworkJson.decodeFromJsonElement<MarkerIdData>(data).markerId) }
        "photo.processing", "photo.ready" -> {
            val photo = NetworkJson.decodeFromJsonElement<PhotoData>(data).photo
            if (tripId != null) RealtimeEvent.PhotoSaved(tripId, actorId, photo) else RealtimeEvent.PlacePhotoSaved(photo)
        }
        "photo.failed" -> {
            val ids = NetworkJson.decodeFromJsonElement<PhotoIdData>(data)
            when {
                tripId != null && ids.markerId != null -> RealtimeEvent.PhotoFailed(tripId, actorId, ids.photoId, ids.markerId)
                ids.placeId != null -> RealtimeEvent.PlacePhotoFailed(ids.photoId, ids.placeId)
                else -> null
            }
        }
        "photo.deleted" -> {
            val ids = NetworkJson.decodeFromJsonElement<PhotoIdData>(data)
            ids.markerId?.let { markerId -> inTrip { RealtimeEvent.PhotoDeleted(it, actorId, ids.photoId, markerId) } }
        }
        "photos.reordered" -> inTrip {
            val order = NetworkJson.decodeFromJsonElement<PhotoOrderData>(data)
            RealtimeEvent.PhotosReordered(it, actorId, order.markerId, order.photoIds)
        }
        "comment.created" ->
            inTrip { RealtimeEvent.CommentAdded(it, actorId, NetworkJson.decodeFromJsonElement<CommentData>(data).comment) }
        "comment.deleted" -> inTrip {
            val ids = NetworkJson.decodeFromJsonElement<CommentIdData>(data)
            RealtimeEvent.CommentDeleted(it, actorId, ids.commentId, ids.markerId)
        }
        "like.count_changed" -> inTrip {
            val like = NetworkJson.decodeFromJsonElement<LikeCountData>(data)
            RealtimeEvent.LikeCountChanged(it, actorId, like.targetType, like.targetId, like.count)
        }
        "place.hot_changed" -> inTrip {
            val hot = NetworkJson.decodeFromJsonElement<PlaceHotData>(data)
            RealtimeEvent.PlaceHotChanged(it, actorId, hot.placeId, hot.hotCount)
        }
        "trip.updated", "day.created", "day.deleted", "member.added", "member.removed" ->
            inTrip { RealtimeEvent.TripChanged(it, actorId) }
        "trip.deleted" -> inTrip { RealtimeEvent.TripDeleted(it, actorId) }
        "trips.changed" -> RealtimeEvent.MyTripsChanged(NetworkJson.decodeFromJsonElement<TripIdData>(data).tripId)
        "follows.changed" -> NetworkJson.decodeFromJsonElement<FollowData>(data).let {
            RealtimeEvent.FollowsChanged(it.followerId, it.followeeId, it.following)
        }
        else -> null
    }
} catch (_: SerializationException) {
    null
}
