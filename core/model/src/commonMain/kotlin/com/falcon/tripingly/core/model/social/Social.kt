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

/** Which likes rank Explore: the last 7, 30 or 365 days, all of them, or trips travelled in the current season. */
enum class ExplorePeriod { Week, Month, Year, Season, All }

/** Explore's filters; [near] keeps trips with a stop around that point (used for the request only, never stored). */
data class ExploreFilter(val period: ExplorePeriod = ExplorePeriod.Month, val near: GeoPoint? = null)

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

/** [PlaceImage]: a place's Wikimedia Commons image. Places aren't liked but marked hot ([HotState]). */
enum class LikeTarget { Trip, Photo, Comment, PlaceImage }

/** A like as the server counted it. */
data class LikeState(val liked: Boolean, val likeCount: Int)

/** A place's hot mark as the server counted it; a stop's flame marks its place. */
data class HotState(val hot: Boolean, val hotCount: Int)

enum class ReportTarget { User, Trip, Marker, Photo, Comment, PlaceImage }

enum class ReportReason { Spam, Harassment, Nudity, Violence, Hate, Copyright, Other }

/** A popular place around the user (`GET /places/nearby`). */
data class NearbyPlace(
    val id: String,
    val name: String,
    val category: PlaceCategory,
    val location: GeoPoint,
    /** A hot spot on Tripinly; the others are OpenStreetMap sights that top up a thin area. */
    val isTripinly: Boolean,
    val hotCount: Int,
    val hotByMe: Boolean,
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
