package com.falcon.tripingly.core.data.trips

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.trip.CopiedFrom
import com.falcon.tripingly.core.model.trip.InvitePreview
import com.falcon.tripingly.core.model.trip.MarkerUpdate
import com.falcon.tripingly.core.model.trip.NewMarker
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.Trip
import com.falcon.tripingly.core.model.trip.TripDay
import com.falcon.tripingly.core.model.trip.TripDetails
import com.falcon.tripingly.core.model.trip.TripInvite
import com.falcon.tripingly.core.model.trip.TripMarker
import com.falcon.tripingly.core.model.trip.TripMember
import com.falcon.tripingly.core.model.trip.TripRole
import com.falcon.tripingly.core.model.trip.TripUpdate
import com.falcon.tripingly.core.model.trip.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * In-memory trips, markers and invites for `-Ptripinly.useFakeApi=true` builds
 * and tests, following the server's rules: days follow the dates, only the owner
 * manages the trip, the last day can't be deleted, the username `unknown` doesn't
 * exist. Set [nextError] to make the next call fail.
 */
class FakeTripBackend(
    private val me: UserSummary = UserSummary("00000000-0000-4000-8000-000000000001", "me", "Me"),
    private val now: () -> Instant = { Clock.System.now() },
) : TripRepository, MarkerRepository, TripInviteRepository {

    private val trips = MutableStateFlow<Map<String, TripDetails>>(emptyMap())
    private val invites = mutableMapOf<String, MutableList<TripInvite>>()
    private var ids = 0
    private val _failures = MutableSharedFlow<MarkerChangeFailure>(extraBufferCapacity = 64)

    /** Fails the next call with this error, then clears itself. */
    var nextError: DataError.Network? = null

    /** Adds a trip as if another session had created it. */
    fun seed(details: TripDetails) {
        trips.update { it + (details.trip.id to details) }
    }

    override fun observeMyTrips(): Flow<List<Trip>> = trips.map { all ->
        all.values.map { it.trip }.filter { it.role != TripRole.VIEWER }.sortedByDescending { it.updatedAt }
    }

    override suspend fun refreshMyTrips(): AppResult<Unit, DataError.Network> = call {}

    override fun observeTrip(tripId: String): Flow<TripDetails?> = trips.map { it[tripId] }

    override suspend fun refreshTrip(tripId: String): AppResult<TripDetails, DataError.Network> = call { trips.value[tripId] ?: return notFound() }

    override suspend fun createTrip(trip: NewTrip): AppResult<TripDetails, DataError.Network> = call {
        val id = nextId()
        val start = trip.startDate
        val end = trip.endDate
        val dayCount = if (start != null && end != null) start.daysUntil(end) + 1 else 1
        save(
            TripDetails(
                trip = Trip(
                    id = id,
                    name = trip.name.trim(),
                    startDate = trip.startDate,
                    endDate = trip.endDate,
                    visibility = trip.visibility ?: TripVisibility.PUBLIC,
                    role = TripRole.OWNER,
                    owner = me,
                    dayCount = 0,
                    markerCount = 0,
                    updatedAt = now(),
                ),
                days = List(dayCount) { position -> TripDay(nextId(), position, trip.startDate?.plusDays(position), emptyList()) },
                members = listOf(TripMember(me, TripRole.OWNER)),
            ),
        )
    }

    override suspend fun updateTrip(tripId: String, update: TripUpdate): AppResult<TripDetails, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        var days = details.days
        update.dates?.let { dates ->
            val count = dates.startDate.daysUntil(dates.endDate) + 1
            if (days.drop(count).any { it.markers.isNotEmpty() }) {
                return AppResult.Error(
                    DataError.Network.Api(
                        400,
                        TripErrorCodes.VALIDATION_FAILED,
                        "Some days you'd remove still have stops.",
                        mapOf("fields.endDate" to TripErrorCodes.DAYS_NOT_EMPTY),
                    ),
                )
            }
            days = List(count) { position ->
                (days.getOrNull(position) ?: TripDay(nextId(), position, null, emptyList()))
                    .copy(date = dates.startDate.plusDays(position))
            }
        }
        save(
            details.copy(
                trip = details.trip.copy(
                    name = update.name?.trim() ?: details.trip.name,
                    startDate = update.dates?.startDate ?: details.trip.startDate,
                    endDate = update.dates?.endDate ?: details.trip.endDate,
                    visibility = update.visibility ?: details.trip.visibility,
                ),
                days = days,
            ),
        )
    }

    override suspend fun deleteTrip(tripId: String): AppResult<Unit, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        trips.update { it - tripId }
    }

    override suspend fun copyTrip(tripId: String): AppResult<TripDetails, DataError.Network> = call {
        val source = trips.value[tripId] ?: return notFound()
        if (source.trip.owner.id == me.id) {
            return AppResult.Error(DataError.Network.Api(422, TripErrorCodes.TRIP_NOT_COPYABLE, "This trip can't be copied."))
        }
        val id = nextId()
        save(
            source.copy(
                trip = source.trip.copy(id = id, role = TripRole.OWNER, owner = me, updatedAt = now()),
                days = source.days.map { day ->
                    val dayId = nextId()
                    day.copy(id = dayId, markers = day.markers.map { it.copy(id = nextId(), tripId = id, dayId = dayId) })
                },
                members = listOf(TripMember(me, TripRole.OWNER)),
                copiedFrom = CopiedFrom(source.trip.id, source.trip.owner),
            ),
        )
    }

    override suspend fun addDay(tripId: String): AppResult<TripDetails, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        val position = details.days.size
        save(
            details.copy(
                trip = details.trip.copy(endDate = details.trip.startDate?.plusDays(position)),
                days = details.days + TripDay(nextId(), position, details.trip.startDate?.plusDays(position), emptyList()),
            ),
        )
    }

    override suspend fun deleteDay(tripId: String, dayId: String): AppResult<TripDetails, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        if (details.days.none { it.id == dayId }) return notFound()
        if (details.days.size <= 1) {
            return AppResult.Error(
                DataError.Network.Api(400, TripErrorCodes.VALIDATION_FAILED, "A trip needs at least one day.", mapOf("fields.id" to TripErrorCodes.LAST_DAY)),
            )
        }
        val days = details.days.filterNot { it.id == dayId }.mapIndexed { position, day ->
            day.copy(position = position, date = details.trip.startDate?.plusDays(position))
        }
        save(details.copy(trip = details.trip.copy(endDate = details.trip.startDate?.plusDays(days.size - 1)), days = days))
    }

    override suspend fun addMember(tripId: String, username: String): AppResult<TripDetails, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        val name = username.trim().removePrefix("@").lowercase()
        if (name == "unknown") return notFound()
        if (details.members.any { it.user.username == name }) return AppResult.Success(details)
        save(details.copy(members = details.members + TripMember(UserSummary(nextId(), name, name), TripRole.EDITOR)))
    }

    override suspend fun removeMember(tripId: String, userId: String): AppResult<TripDetails, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        save(details.copy(members = details.members.filterNot { it.user.id == userId && it.role != TripRole.OWNER }))
    }

    override suspend fun leaveTrip(tripId: String): AppResult<Unit, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (details.trip.role != TripRole.EDITOR) return forbidden()
        trips.update { it - tripId }
    }

    override val failures: Flow<MarkerChangeFailure> get() = _failures.asSharedFlow()

    /** Changes apply at once here; a [nextError] undoes the change and reports it on [failures]. */
    override suspend fun addMarker(tripId: String, dayId: String, marker: NewMarker): TripMarker {
        val created = TripMarker(
            id = nextId(),
            tripId = tripId,
            dayId = dayId,
            placeId = "",
            name = marker.name.trim(),
            location = marker.location,
            time = marker.time,
            position = trips.value[tripId]?.days?.firstOrNull { it.id == dayId }?.markers?.size ?: 0,
        )
        run(MarkerChange.Add(created))
        return created
    }

    override suspend fun updateMarker(markerId: String, update: MarkerUpdate) {
        val (_, marker) = markerOf(markerId) ?: return
        run(MarkerChange.Update(marker, marker, update))
    }

    override suspend fun deleteMarker(markerId: String) {
        val (_, marker) = markerOf(markerId) ?: return
        run(MarkerChange.Delete(marker))
    }

    override suspend fun clearDay(tripId: String, dayId: String) {
        val markers = dayOf(dayId)?.second?.markers ?: return
        if (markers.isNotEmpty()) run(MarkerChange.ClearDay(tripId, dayId, markers))
    }

    override suspend fun reorderMarkers(tripId: String, dayId: String, markerIds: List<String>) {
        val before = dayOf(dayId)?.second?.markers?.map { it.id } ?: return
        run(MarkerChange.Reorder(tripId, dayId, before, markerIds))
    }

    override suspend fun retry(failure: MarkerChangeFailure) = run(failure.change)

    private suspend fun run(change: MarkerChange) {
        val result = when (change) {
            is MarkerChange.Add -> createMarker(change.marker)
            is MarkerChange.Update -> changeMarker(change.markerId, change.update)
            is MarkerChange.Delete -> removeMarker(change.markerId)
            is MarkerChange.ClearDay -> clearMarkers(change.dayId)
            is MarkerChange.Reorder -> orderMarkers(change.dayId, change.after)
        }
        if (result is AppResult.Error) _failures.emit(MarkerChangeFailure(change.tripId, change.markerName, result.error, change))
    }

    private fun createMarker(created: TripMarker): AppResult<TripMarker, DataError.Network> = call {
        val (details, day) = dayOf(created.dayId) ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        val placed = created.copy(placeId = nextId(), position = day.markers.size)
        save(details.withDay(day.copy(markers = day.markers + placed)))
        placed
    }

    private fun changeMarker(markerId: String, update: MarkerUpdate): AppResult<TripMarker, DataError.Network> = call {
        val (details, marker) = markerOf(markerId) ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        val targetDayId = update.dayId ?: marker.dayId
        if (details.days.none { it.id == targetDayId }) return notFound()
        val moved = targetDayId != marker.dayId
        val updated = marker.copy(
            name = update.name?.trim() ?: marker.name,
            location = update.location ?: marker.location,
            dayId = targetDayId,
            position = if (moved) details.days.first { it.id == targetDayId }.markers.size else marker.position,
        )
        save(
            details.copy(
                days = details.days.map { day ->
                    val others = day.markers.filterNot { it.id == markerId }
                    val markers = if (day.id == targetDayId) {
                        (others + updated).sortedBy { it.position }
                    } else {
                        others
                    }
                    day.copy(markers = markers.mapIndexed { position, it -> it.copy(position = position) })
                },
            ),
        )
        updated
    }

    private fun removeMarker(markerId: String): AppResult<Unit, DataError.Network> = call {
        val (details, marker) = markerOf(markerId) ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        val day = details.days.first { it.id == marker.dayId }
        save(
            details.withDay(
                day.copy(markers = day.markers.filterNot { it.id == markerId }.mapIndexed { i, it -> it.copy(position = i) }),
            ),
        )
    }

    private fun clearMarkers(dayId: String): AppResult<Unit, DataError.Network> = call {
        val (details, day) = dayOf(dayId) ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        save(details.withDay(day.copy(markers = emptyList())))
    }

    private fun orderMarkers(dayId: String, markerIds: List<String>): AppResult<Unit, DataError.Network> = call {
        val (details, day) = dayOf(dayId) ?: return notFound()
        if (!details.trip.role.canEdit) return forbidden()
        if (markerIds.toSet() != day.markers.map { it.id }.toSet()) {
            return AppResult.Error(
                DataError.Network.Api(400, TripErrorCodes.VALIDATION_FAILED, "Send every stop of the day.", mapOf("fields.markerIds" to "mustMatchDayMarkers")),
            )
        }
        val byId = day.markers.associateBy { it.id }
        save(details.withDay(day.copy(markers = markerIds.mapIndexed { i, id -> byId.getValue(id).copy(position = i) })))
    }

    override suspend fun copyMarker(markerId: String, targetDayId: String): AppResult<TripMarker, DataError.Network> {
        val (_, marker) = markerOf(markerId) ?: return notFound()
        val (target, day) = dayOf(targetDayId) ?: return notFound()
        return createMarker(
            marker.copy(id = nextId(), tripId = target.trip.id, dayId = day.id, coverThumbUrl = null, photoCount = 0),
        )
    }

    override suspend fun invites(tripId: String): AppResult<List<TripInvite>, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        invites[tripId].orEmpty().toList()
    }

    override suspend fun createInvite(tripId: String): AppResult<TripInvite, DataError.Network> = call {
        val details = trips.value[tripId] ?: return notFound()
        if (!details.trip.role.canManage) return forbidden()
        val id = nextId()
        TripInvite(id, "https://tripinly.example/invites/$id", now() + 7.days)
            .also { invites.getOrPut(tripId) { mutableListOf() } += it }
    }

    override suspend fun revokeInvite(tripId: String, inviteId: String): AppResult<Unit, DataError.Network> = call {
        if (invites[tripId]?.removeAll { it.id == inviteId } != true) return notFound()
    }

    override suspend fun preview(token: String): AppResult<InvitePreview, DataError.Network> = call {
        val (tripId, invite) = inviteOf(token) ?: return notFound()
        val trip = trips.value.getValue(tripId).trip
        InvitePreview(trip.id, trip.name, trip.owner, trip.startDate, trip.endDate, invite.expiresAt, alreadyMember = trip.role != TripRole.VIEWER)
    }

    override suspend fun accept(token: String): AppResult<TripDetails, DataError.Network> = call {
        val (tripId, _) = inviteOf(token) ?: return notFound()
        val details = trips.value.getValue(tripId)
        if (details.trip.role != TripRole.VIEWER) return AppResult.Success(details)
        save(details.copy(trip = details.trip.copy(role = TripRole.EDITOR), members = details.members + TripMember(me, TripRole.EDITOR)))
    }

    private inline fun <T> call(block: () -> T): AppResult<T, DataError.Network> {
        nextError?.let {
            nextError = null
            return AppResult.Error(it)
        }
        return AppResult.Success(block())
    }

    /** Stores the trip with its derived counts and returns it. */
    private fun save(details: TripDetails): TripDetails {
        val markers = details.days.flatMap { it.markers }
        val saved = details.copy(
            trip = details.trip.copy(
                dayCount = details.days.size,
                markerCount = markers.size,
                coverThumbUrl = markers.firstNotNullOfOrNull { it.coverThumbUrl },
                updatedAt = now(),
            ),
        )
        trips.update { it + (saved.trip.id to saved) }
        return saved
    }

    private fun dayOf(dayId: String): Pair<TripDetails, TripDay>? =
        trips.value.values.firstNotNullOfOrNull { details -> details.days.firstOrNull { it.id == dayId }?.let { details to it } }

    private fun markerOf(markerId: String): Pair<TripDetails, TripMarker>? =
        trips.value.values.firstNotNullOfOrNull { details ->
            details.days.flatMap { it.markers }.firstOrNull { it.id == markerId }?.let { details to it }
        }

    private fun inviteOf(token: String): Pair<String, TripInvite>? =
        invites.entries.firstNotNullOfOrNull { (tripId, list) ->
            list.firstOrNull { it.url.endsWith("/$token") }?.let { tripId to it }
        }

    private fun TripDetails.withDay(day: TripDay) = copy(days = days.map { if (it.id == day.id) day else it })

    private fun nextId(): String = "00000000-0000-4000-9000-" + (++ids).toString().padStart(12, '0')

    private fun LocalDate.plusDays(days: Int) = plus(DatePeriod(days = days))

    private fun <T> notFound(): AppResult<T, DataError.Network> =
        AppResult.Error(DataError.Network.Api(404, TripErrorCodes.NOT_FOUND, "This trip isn't available."))

    private fun <T> forbidden(): AppResult<T, DataError.Network> =
        AppResult.Error(DataError.Network.Api(403, TripErrorCodes.FORBIDDEN, "You can't change this trip."))
}
