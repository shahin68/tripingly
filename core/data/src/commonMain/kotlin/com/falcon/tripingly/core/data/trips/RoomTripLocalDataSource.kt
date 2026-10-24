package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.database.dao.TripDao
import com.falcon.tripingly.core.database.entity.DayWithMarkers
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.core.database.entity.TripDayEntity
import com.falcon.tripingly.core.database.entity.TripEntity
import com.falcon.tripingly.core.database.entity.TripMemberEntity
import com.falcon.tripingly.core.database.entity.TripWithDetails
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
import com.falcon.tripingly.core.model.trip.JoinRequestStatus
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

internal class RoomTripLocalDataSource(
    private val dao: TripDao,
) : TripLocalDataSource {

    override fun observeMyTrips(): Flow<List<Trip>> =
        dao.observeMyTrips().map { trips -> trips.map { it.toTrip() } }

    override fun observeTrip(tripId: String): Flow<TripDetails?> =
        dao.observeTrip(tripId).map { it?.toDetails() }

    override suspend fun trip(tripId: String): TripDetails? = observeTrip(tripId).first()

    override suspend fun replaceMyTrips(trips: List<Trip>) {
        dao.replaceMyTrips(trips.map { it.toEntity() })
    }

    override suspend fun saveTrip(details: TripDetails) {
        val trip = details.trip
        dao.replaceTrip(
            trip = trip.toEntity(details),
            days = details.days.map { TripDayEntity(it.id, trip.id, it.position, it.date?.toString()) },
            markers = details.days.flatMap { day -> day.markers.map { it.toEntity() } },
            members = details.members.mapIndexed { index, member ->
                TripMemberEntity(
                    tripId = trip.id,
                    userId = member.user.id,
                    username = member.user.username,
                    displayName = member.user.displayName,
                    role = member.role.wireName,
                    position = index,
                )
            },
        )
    }

    override suspend fun deleteTrip(tripId: String) = dao.deleteTrip(tripId)

    override suspend fun saveSavedByMe(trip: Trip, saved: Boolean) = dao.saveSavedByMe(trip.toEntity(), saved)

    override suspend fun saveMyJoinRequest(tripId: String, status: JoinRequestStatus?) =
        dao.updateMyJoinRequest(tripId, status?.name?.lowercase())

    override suspend fun removePendingJoinRequest(tripId: String) = dao.decrementPendingJoinRequests(tripId)

    override suspend fun saveMarkers(markers: List<TripMarker>) = dao.saveMarkers(markers.map { it.toEntity() })

    override suspend fun savePlaceOfMarker(marker: TripMarker) = with(marker) {
        dao.updateMarkerPlace(id, placeId, name, coverPhotoId, coverThumbUrl, hotCount, hotByMe, canMarkHot)
    }

    override suspend fun tripIdOfMarker(markerId: String): String? = dao.marker(markerId)?.tripId

    override suspend fun marker(markerId: String): TripMarker? = dao.marker(markerId)?.toMarker()

    override suspend fun markerAtPlace(placeId: String): TripMarker? = dao.markerAtPlace(placeId)?.toMarker()

    override suspend fun savePlaceHot(placeId: String, hotCount: Int, hotByMe: Boolean?) {
        if (hotByMe == null) dao.updatePlaceHotCount(placeId, hotCount) else dao.updatePlaceHot(placeId, hotCount, hotByMe)
    }

    override suspend fun addToCommentCount(markerId: String, delta: Int) = dao.addToMarkerCommentCount(markerId, delta)

    override suspend fun deleteMarkers(markerIds: List<String>) = dao.removeMarkers(markerIds)

    override suspend fun reorderMarkers(markerIds: List<String>) = dao.reorderMarkers(markerIds)

    override suspend fun clear() = dao.deleteAll()

    /** Without [details] (a list entry), what only the trip itself says stays empty. */
    private fun Trip.toEntity(details: TripDetails? = null) = TripEntity(
        id = id,
        name = name,
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        visibility = visibility.wireName,
        role = role.wireName,
        ownerId = owner.id,
        ownerUsername = owner.username,
        ownerDisplayName = owner.displayName,
        coverPhotoId = coverPhotoId,
        coverThumbUrl = coverThumbUrl,
        dayCount = dayCount,
        markerCount = markerCount,
        editorCount = editorCount,
        createdAt = createdAt.toEpochMilliseconds(),
        updatedAt = updatedAt.toEpochMilliseconds(),
        savedByMe = savedByMe,
        myJoinRequest = details?.myJoinRequest?.name?.lowercase(),
        pendingJoinRequestCount = details?.pendingJoinRequestCount ?: 0,
        destinationName = details?.destination?.name,
        destinationLat = details?.destination?.location?.lat,
        destinationLng = details?.destination?.location?.lng,
    )
}

internal fun TripEntity.toTrip() = Trip(
    id = id,
    name = name,
    startDate = startDate?.let(LocalDate::parse),
    endDate = endDate?.let(LocalDate::parse),
    visibility = if (visibility == "private") TripVisibility.PRIVATE else TripVisibility.PUBLIC,
    role = tripRoleOf(role),
    owner = UserSummary(ownerId, ownerUsername, ownerDisplayName),
    coverPhotoId = coverPhotoId,
    coverThumbUrl = coverThumbUrl,
    dayCount = dayCount,
    markerCount = markerCount,
    editorCount = editorCount,
    savedByMe = savedByMe,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

private fun TripWithDetails.toDetails() = TripDetails(
    trip = trip.toTrip(),
    days = days.sortedBy { it.day.position }.map { it.toDay() },
    members = members.sortedBy { it.position }.map {
        TripMember(UserSummary(it.userId, it.username, it.displayName), tripRoleOf(it.role))
    },
    myJoinRequest = when (trip.myJoinRequest) {
        "pending" -> JoinRequestStatus.PENDING
        "declined" -> JoinRequestStatus.DECLINED
        else -> null
    },
    pendingJoinRequestCount = trip.pendingJoinRequestCount,
    destination = trip.destinationName?.let { name ->
        Destination(name, GeoPoint(trip.destinationLat ?: 0.0, trip.destinationLng ?: 0.0))
    },
)

private fun DayWithMarkers.toDay() = TripDay(
    id = day.id,
    position = day.position,
    date = day.date?.let(LocalDate::parse),
    markers = markers.sortedBy { it.position }.map { it.toMarker() },
)

private fun MarkerEntity.toMarker() = TripMarker(
    id = id,
    tripId = tripId,
    dayId = dayId,
    placeId = placeId,
    name = name,
    location = GeoPoint(lat, lng),
    time = time,
    position = position,
    coverPhotoId = coverPhotoId,
    coverThumbUrl = coverThumbUrl,
    photoCount = photoCount,
    hotCount = hotCount,
    hotByMe = hotByMe,
    canMarkHot = canMarkHot,
    commentCount = commentCount,
    createdById = createdById,
)

private fun TripMarker.toEntity() = MarkerEntity(
    id = id,
    tripId = tripId,
    dayId = dayId,
    placeId = placeId,
    name = name,
    lat = location.lat,
    lng = location.lng,
    time = time,
    position = position,
    coverPhotoId = coverPhotoId,
    coverThumbUrl = coverThumbUrl,
    photoCount = photoCount,
    hotCount = hotCount,
    hotByMe = hotByMe,
    canMarkHot = canMarkHot,
    commentCount = commentCount,
    createdById = createdById,
)

internal val TripRole.wireName: String get() = name.lowercase()
internal val TripVisibility.wireName: String get() = name.lowercase()

internal fun tripRoleOf(wireName: String): TripRole = when (wireName) {
    "owner" -> TripRole.OWNER
    "editor" -> TripRole.EDITOR
    else -> TripRole.VIEWER
}
