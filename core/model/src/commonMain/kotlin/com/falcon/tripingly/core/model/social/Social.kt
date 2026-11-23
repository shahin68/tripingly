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

/**
 * Explore's filters: [period] is a key from the server's list (`GET /app-config` `explorePeriods`), null for the
 * server's own default; [near] keeps trips with a stop around that point (used for the request only, never stored).
 */
data class ExploreFilter(val period: String? = null, val near: GeoPoint? = null)

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
    /** The main comment this replies to; null for a main comment. */
    val parentId: String? = null,
    /** Replies I can see; 0 on replies. */
    val replyCount: Int = 0,
    /** Usernames of the trip people the body @mentions; other handles are plain text. */
    val mentions: List<String> = emptyList(),
)

/** [PlaceImage]: a place's Wikimedia Commons image. Places aren't liked but marked hot ([HotState]). */
enum class LikeTarget { Trip, Photo, Comment, PlaceImage }

/** A like as the server counted it. */
data class LikeState(val liked: Boolean, val likeCount: Int)

/** A place's hot mark as the server counted it; a stop's flame marks its place. */
data class HotState(val hot: Boolean, val hotCount: Int)

enum class ReportTarget { User, Trip, Marker, Photo, Comment, PlaceImage }

/** A popular place around the user (`GET /places/nearby`), or a spot found by name (`GET /places/spots`). */
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
    /** From the user; null for a spot found by name without the location. */
    val distanceMeters: Int?,
    /** From OpenStreetMap's address, shown instead of a distance without the location; null when OSM has none. */
    val city: String? = null,
)

/** Someone in people search or Nearby's people row, with what they are to me. */
data class Person(val user: UserSummary, val isFriend: Boolean, val isFollowing: Boolean)

/** A page of people, with how many match in all, counted up to [PeoplePage.TOTAL_CAP] ("999+"). */
data class PeoplePage(val people: List<Person>, val total: Int, val nextCursor: String?) {
    companion object {
        const val TOTAL_CAP = 999
    }
}

/** A change to a stop's comments by someone else, live from the trip. */
sealed interface CommentChange {
    val markerId: String

    data class Added(val comment: Comment) : CommentChange {
        override val markerId: String get() = comment.markerId
    }

    data class Deleted(override val markerId: String, val commentId: String) : CommentChange
}
