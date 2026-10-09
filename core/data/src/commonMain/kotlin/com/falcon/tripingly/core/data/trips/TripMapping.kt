package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.CopiedFrom
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.InvitePreview
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.network.model.CopiedFromDto
import com.falcon.tripingly.core.network.model.DestinationDto
import com.falcon.tripingly.core.network.model.InviteDto
import com.falcon.tripingly.core.network.model.InvitePreviewDto
import com.falcon.tripingly.core.network.model.LocationDto
import com.falcon.tripingly.core.network.model.MarkerDto
import com.falcon.tripingly.core.network.model.TripDayDto
import com.falcon.tripingly.core.network.model.TripDto
import com.falcon.tripingly.core.network.model.TripMemberDto
import com.falcon.tripingly.core.network.model.TripSummaryDto
import com.falcon.tripingly.core.network.model.UserSummaryDto
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal fun TripSummaryDto.toTrip() = Trip(
    id = id,
    name = title,
    startDate = startDate.toLocalDateOrNull(),
    endDate = endDate.toLocalDateOrNull(),
    visibility = visibility.toVisibility(),
    role = when (role) {
        TripSummaryDto.Role.OWNER -> TripRole.OWNER
        TripSummaryDto.Role.EDITOR -> TripRole.EDITOR
        TripSummaryDto.Role.VIEWER -> TripRole.VIEWER
    },
    owner = owner.toUser(),
    coverThumbUrl = coverThumbUrl,
    dayCount = dayCount.toInt(),
    markerCount = markerCount.toInt(),
    updatedAt = updatedAt.toInstant(),
)

private fun TripSummaryDto.Visibility.toVisibility() = when (this) {
    TripSummaryDto.Visibility.PUBLIC -> TripVisibility.PUBLIC
    TripSummaryDto.Visibility.PRIVATE -> TripVisibility.PRIVATE
}

internal fun TripDto.toDetails(): TripDetails {
    val days = days.map { it.toDay() }.sortedBy { it.position }
    val markers = days.flatMap { it.markers }
    return TripDetails(
        trip = Trip(
            id = id,
            name = title,
            startDate = startDate.toLocalDateOrNull(),
            endDate = endDate.toLocalDateOrNull(),
            visibility = when (visibility) {
                TripDto.Visibility.PUBLIC -> TripVisibility.PUBLIC
                TripDto.Visibility.PRIVATE -> TripVisibility.PRIVATE
            },
            role = when (myRole) {
                TripDto.MyRole.OWNER -> TripRole.OWNER
                TripDto.MyRole.EDITOR -> TripRole.EDITOR
                TripDto.MyRole.VIEWER -> TripRole.VIEWER
            },
            owner = owner.toUser(),
            // Like the list: the first cover photo in the trip.
            coverThumbUrl = markers.firstNotNullOfOrNull { it.coverThumbUrl },
            dayCount = days.size,
            markerCount = markers.size,
            updatedAt = updatedAt.toInstant(),
        ),
        days = days,
        members = members.map { it.toMember() },
        copiedFrom = copiedFrom?.toCopiedFrom(),
        destination = destination?.toDestination(),
    )
}

internal fun TripDayDto.toDay() = TripDay(
    id = id,
    position = position.toInt(),
    date = date.toLocalDateOrNull(),
    markers = markers.map { it.toMarker() }.sortedBy { it.position },
)

internal fun MarkerDto.toMarker() = TripMarker(
    id = id,
    tripId = tripId,
    dayId = dayId,
    placeId = placeId,
    name = name,
    location = location.toGeoPoint(),
    time = time,
    position = position.toInt(),
    coverThumbUrl = coverThumbUrl,
    photoCount = photoCount.toInt(),
)

internal fun DestinationDto.toDestination() = Destination(name = name, location = location.toGeoPoint())

internal fun Destination.toDto() = DestinationDto(name = name, location = location.toDto())

internal fun LocationDto.toGeoPoint() = GeoPoint(lat = lat, lng = lng)

internal fun GeoPoint.toDto() = LocationDto(lat = lat, lng = lng)

private fun TripMemberDto.toMember() = TripMember(
    user = user.toUser(),
    role = if (role == TripMemberDto.Role.OWNER) TripRole.OWNER else TripRole.EDITOR,
)

private fun CopiedFromDto.toCopiedFrom() = CopiedFrom(tripId = tripId, owner = owner.toUser())

internal fun UserSummaryDto.toUser() = UserSummary(id = id, username = username, displayName = displayName)

internal fun InviteDto.toInvite() = TripInvite(id = id, url = url, expiresAt = expiresAt.toInstant())

internal fun InvitePreviewDto.toPreview() = InvitePreview(
    tripId = tripId,
    tripName = title,
    owner = owner.toUser(),
    startDate = startDate.toLocalDateOrNull(),
    endDate = endDate.toLocalDateOrNull(),
    expiresAt = expiresAt.toInstant(),
    alreadyMember = alreadyMember,
)

private fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun String.toInstant(): Instant = runCatching { Instant.parse(this) }.getOrDefault(Instant.DISTANT_PAST)
