package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.database.dao.TripDao
import com.falcon.tripingly.core.database.entity.DayWithMarkers
import com.falcon.tripingly.core.database.entity.MarkerEntity
import com.falcon.tripingly.core.database.entity.TripDayEntity
import com.falcon.tripingly.core.database.entity.TripEntity
import com.falcon.tripingly.core.database.entity.TripMemberEntity
import com.falcon.tripingly.core.database.entity.TripWithDetails
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.CopiedFrom
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.GeoPoint
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
        dao.replaceMyTrips(trips.map { it.toEntity(copiedFrom = null, destination = null) })
    }

    override suspend fun saveTrip(details: TripDetails) {
        val trip = details.trip
        dao.replaceTrip(
            trip = trip.toEntity(details.copiedFrom, details.destination),
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

    override suspend fun saveMarkers(markers: List<TripMarker>) = dao.upsertMarkers(markers.map { it.toEntity() })

    override suspend fun savePlaceOfMarker(markerId: String, placeId: String, name: String) =
        dao.updateMarkerPlace(markerId, placeId, name)

    override suspend fun tripIdOfMarker(markerId: String): String? = dao.marker(markerId)?.tripId

    override suspend fun deleteMarkers(markerIds: List<String>) = dao.deleteMarkers(markerIds)

    override suspend fun reorderMarkers(markerIds: List<String>) = dao.reorderMarkers(markerIds)

    override suspend fun clear() = dao.deleteAll()

    private fun Trip.toEntity(copiedFrom: CopiedFrom?, destination: Destination?) = TripEntity(
        id = id,
        name = name,
        startDate = startDate?.toString(),
        endDate = endDate?.toString(),
        visibility = visibility.wireName,
        role = role.wireName,
        ownerId = owner.id,
        ownerUsername = owner.username,
        ownerDisplayName = owner.displayName,
        coverThumbUrl = coverThumbUrl,
        dayCount = dayCount,
        markerCount = markerCount,
        updatedAt = updatedAt.toEpochMilliseconds(),
        copiedFromTripId = copiedFrom?.tripId,
        copiedFromOwnerId = copiedFrom?.owner?.id,
        copiedFromOwnerUsername = copiedFrom?.owner?.username,
        copiedFromOwnerDisplayName = copiedFrom?.owner?.displayName,
        destinationName = destination?.name,
        destinationLat = destination?.location?.lat,
        destinationLng = destination?.location?.lng,
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
    coverThumbUrl = coverThumbUrl,
    dayCount = dayCount,
    markerCount = markerCount,
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

private fun TripWithDetails.toDetails() = TripDetails(
    trip = trip.toTrip(),
    days = days.sortedBy { it.day.position }.map { it.toDay() },
    members = members.sortedBy { it.position }.map {
        TripMember(UserSummary(it.userId, it.username, it.displayName), tripRoleOf(it.role))
    },
    copiedFrom = trip.copiedFromTripId?.let { tripId ->
        CopiedFrom(
            tripId = tripId,
            owner = UserSummary(
                id = trip.copiedFromOwnerId.orEmpty(),
                username = trip.copiedFromOwnerUsername.orEmpty(),
                displayName = trip.copiedFromOwnerDisplayName.orEmpty(),
            ),
        )
    },
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
    coverThumbUrl = coverThumbUrl,
    photoCount = photoCount,
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
    coverThumbUrl = coverThumbUrl,
    photoCount = photoCount,
)

internal val TripRole.wireName: String get() = name.lowercase()
internal val TripVisibility.wireName: String get() = name.lowercase()

internal fun tripRoleOf(wireName: String): TripRole = when (wireName) {
    "owner" -> TripRole.OWNER
    "editor" -> TripRole.EDITOR
    else -> TripRole.VIEWER
}
