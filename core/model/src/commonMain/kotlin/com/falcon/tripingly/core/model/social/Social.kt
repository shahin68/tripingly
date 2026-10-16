package com.falcon.tripingly.core.model.social

import com.falcon.tripingly.core.model.place.PlaceCategory
import com.falcon.tripingly.core.model.place.PlaceImageSource
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlin.time.Instant

/** Someone else's public trip, as Explore and profiles list it. */
data class PublicTrip(
    val trip: Trip,
    val likeCount: Int,
    val likedByMe: Boolean,
    /** People who saved it to their trips; whether I did is [Trip.savedByMe]. */
    val saveCount: Int,
)

/** A person's profile (`GET /users/{username}`) with the first page of their public trips. */
data class Profile(
    val user: UserSummary,
    val followerCount: Int,
    val followingCount: Int,
    /** I follow them. */
    val isFollowing: Boolean,
    val followsMe: Boolean,
    /** We follow each other. */
    val isFriend: Boolean,
    val publicTripCount: Int,
    val trips: List<PublicTrip>,
    val nextCursor: String?,
)

/** The people lists of the signed-in user. */
enum class PeopleList {
    /** People I follow who follow me back. */
    Friends,
    Following,
    Followers,
}

data class Comment(
    val id: String,
    val markerId: String,
    val body: String,
    val author: UserSummary,
    val likeCount: Int,
    val likedByMe: Boolean,
    /** The author, or the trip's owner. */
    val canDelete: Boolean,
    val createdAt: Instant,
)

enum class LikeTarget { Trip, Marker, Photo, Comment, Place }

/** A like as the server counted it. */
data class LikeState(val liked: Boolean, val likeCount: Int)

enum class ReportTarget { User, Trip, Marker, Photo, Comment, PlaceImage }

enum class ReportReason { Spam, Harassment, Nudity, Violence, Hate, Copyright, Other }

/** A popular place around the user (`GET /places/nearby`). */
data class NearbyPlace(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val location: GeoPoint,
    /** Liked on Tripinly; the others are OpenStreetMap sights that top up a thin area. */
    val isTripinly: Boolean,
    val likeCount: Int,
    val likedByMe: Boolean,
    /** The cover's ID: a trip photo's or a Commons image's. */
    val coverImageId: String?,
    val coverSource: PlaceImageSource?,
    val coverThumbUrl: String?,
    val distanceMeters: Int,
)

/** A change to a stop's comments by someone else, live from the trip. */
sealed interface CommentChange {
    val markerId: String

    data class Added(val comment: Comment) : CommentChange {
        override val markerId: String get() = comment.markerId
    }

    data class Deleted(override val markerId: String, val commentId: String) : CommentChange
}
