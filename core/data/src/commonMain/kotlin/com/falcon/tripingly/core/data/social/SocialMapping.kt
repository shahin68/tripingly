package com.falcon.tripingly.core.data.social

import com.falcon.tripingly.core.data.trips.toTrip
import com.falcon.tripingly.core.data.trips.toUser
import com.falcon.tripingly.core.model.social.Comment
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.network.model.CommentDto
import com.falcon.tripingly.core.network.model.CreateReportDto
import com.falcon.tripingly.core.network.model.LikeStateDto
import com.falcon.tripingly.core.network.model.ProfileDto
import com.falcon.tripingly.core.network.model.TripSummaryDto
import kotlin.time.Instant

internal fun TripSummaryDto.toPublicTrip() = PublicTrip(
    trip = toTrip(),
    likeCount = likeCount.toInt(),
    likedByMe = likedByMe,
    copyCount = copyCount.toInt(),
)

internal fun ProfileDto.toProfile() = Profile(
    user = user.toUser(),
    followerCount = followerCount.toInt(),
    followingCount = followingCount.toInt(),
    isFollowing = isFollowing,
    followsMe = followsMe,
    isFriend = isFriend,
    publicTripCount = publicTripCount.toInt(),
    trips = trips.map { it.toPublicTrip() },
    nextCursor = nextCursor,
)

internal fun CommentDto.toComment() = Comment(
    id = id,
    markerId = markerId,
    body = body,
    author = author.toUser(),
    likeCount = likeCount.toInt(),
    likedByMe = likedByMe,
    canDelete = canDelete,
    createdAt = runCatching { Instant.parse(createdAt) }.getOrDefault(Instant.DISTANT_PAST),
)

internal fun LikeStateDto.toLikeState() = LikeState(liked = liked, likeCount = likeCount.toInt())

/** The name in `/likes/{targetType}/…` and in `like.count_changed`. */
internal val LikeTarget.wireName: String get() = name.lowercase()

internal fun ReportTarget.toDto() = when (this) {
    ReportTarget.User -> CreateReportDto.TargetType.USER
    ReportTarget.Trip -> CreateReportDto.TargetType.TRIP
    ReportTarget.Marker -> CreateReportDto.TargetType.MARKER
    ReportTarget.Photo -> CreateReportDto.TargetType.PHOTO
    ReportTarget.Comment -> CreateReportDto.TargetType.COMMENT
    ReportTarget.PlaceImage -> CreateReportDto.TargetType.PLACE_IMAGE
}

internal fun ReportReason.toDto() = when (this) {
    ReportReason.Spam -> CreateReportDto.Reason.SPAM
    ReportReason.Harassment -> CreateReportDto.Reason.HARASSMENT
    ReportReason.Nudity -> CreateReportDto.Reason.NUDITY
    ReportReason.Violence -> CreateReportDto.Reason.VIOLENCE
    ReportReason.Hate -> CreateReportDto.Reason.HATE
    ReportReason.Copyright -> CreateReportDto.Reason.COPYRIGHT
    ReportReason.Other -> CreateReportDto.Reason.OTHER
}
