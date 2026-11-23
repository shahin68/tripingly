package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_leave
import com.falcon.tripingly.feature.map.generated.resources.map_error_report
import com.falcon.tripingly.feature.map.generated.resources.map_error_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_unsave_trip
import com.falcon.tripingly.feature.map.generated.resources.map_join_declined
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.generated.resources.map_join_withdrawn
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import com.falcon.tripingly.feature.map.generated.resources.map_trip_saved
import com.falcon.tripingly.feature.map.generated.resources.map_trip_unsaved
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.feature.map.generated.resources.map_error_like
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** Someone else's trip: saving it, asking to join, and reports of the trip, its stops and photos. */
internal class MapSharing(
    private val context: MapContext,
    private val tripRepository: TripRepository,
    private val socialRepository: SocialRepository,
    /** Back to where the trip was opened from, once I left it. */
    private val onLeft: () -> Unit,
) {
    private var likeJob: Job? = null

    /** What the server last confirmed, before the taps on their way. */
    private var confirmedLike: LikeState? = null

    init {
        // A like from another screen (Explore, a profile), or a count the server sends live, shows here too; a tap still
        // on its way here wins.
        context.scope.launch {
            merge(
                socialRepository.myLikes.mapNotNull { change -> change.state.takeIf { change.target == LikeTarget.Trip && change.targetId == context.tripId } },
                socialRepository.likeCounts.mapNotNull { count ->
                    if (count.target == LikeTarget.Trip && count.targetId == context.tripId) LikeState(shownLike().liked, count.count) else null
                },
            ).collect { like -> if (likeJob?.isActive != true) context.update { it.copy(tripLike = like) } }
        }
    }

    /** The heart: shows at once and stays; a newer tap cancels the one on its way, and only a refusal puts back the last confirmed one. */
    fun toggleLike() {
        val shown = shownLike()
        if (likeJob?.isActive != true) confirmedLike = shown
        likeJob?.cancel()
        val liked = !shown.liked
        context.update { it.copy(tripLike = LikeState(liked, (shown.likeCount + if (liked) 1 else -1).coerceAtLeast(0))) }
        likeJob = context.scope.launch {
            val result = socialRepository.setLiked(LikeTarget.Trip, context.tripId, liked)
            if (result is AppResult.Error) context.update { it.copy(tripLike = confirmedLike, message = result.error.toUiText(Res.string.map_error_like)) }
        }
    }

    private fun shownLike(): LikeState = context.model.value.tripLike
        ?: LikeState(context.trip.value?.likedByMe == true, context.trip.value?.likeCount ?: 0)

    /** Someone else's trip goes into My Trips (read-only, the owner's live trip), or out again. */
    fun toggleSave() {
        val trip = context.trip.value?.trip ?: return
        val saved = !trip.savedByMe
        update(
            done = if (saved) Res.string.map_trip_saved else Res.string.map_trip_unsaved,
            failed = if (saved) Res.string.map_error_save_trip else Res.string.map_error_unsave_trip,
        ) {
            tripRepository.setSaved(trip, saved)
        }
    }

    /**
     * Ask to join waits for the server, spinning on the pill; once it agreed, the trip's cached request shows it sent
     * (also next time), and an error puts the button back. The spin goes by itself either way.
     */
    fun requestToJoin() {
        if (context.model.value.isUpdatingSharing) return
        context.update { it.copy(isAskingToJoin = true) }
        update(Res.string.map_join_requested, Res.string.map_error_join) {
            tripRepository.requestToJoin(context.tripId).also { result ->
                when (result) {
                    is AppResult.Error -> context.update { it.copy(isAskingToJoin = false) }
                    // Spinning until the cached trip has the request, so the pill doesn't flash Ask to join in between.
                    is AppResult.Success -> context.scope.launch {
                        context.trip.first { it?.myJoinRequest != null }
                        context.update { it.copy(isAskingToJoin = false) }
                    }
                }
            }
        }
    }

    fun cancelJoinRequest() {
        if (context.model.value.isUpdatingSharing) return
        context.update { it.copy(isAskingToJoin = false) }
        update(Res.string.map_join_withdrawn, Res.string.map_error_cancel_join) { tripRepository.cancelJoinRequest(context.tripId) }
    }

    /** Leave trip asks first; nothing changes until I agree, then the map closes once the server agreed. */
    fun askToLeave() = context.update { it.copy(dialog = Dialog.LeaveTrip) }

    fun leave() {
        if (context.model.value.isUpdatingSharing) return
        context.update { it.copy(dialog = null, isUpdatingSharing = true) }
        context.scope.launch {
            val result = tripRepository.leaveTrip(context.tripId)
            context.update { it.copy(isUpdatingSharing = false, message = (result as? AppResult.Error)?.error?.toUiText(Res.string.map_error_leave)) }
            if (result is AppResult.Success) onLeft()
        }
    }

    fun report(target: ReportTarget, id: String) = context.update { it.copy(dialog = Dialog.Report(target, id)) }

    /** A place's Commons image is reported as the place's image, every other photo as a photo. */
    fun reportPhoto(photo: Photo) = report(if (photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo, photo.id)

    fun sendReport(reason: String) {
        val report = context.model.value.dialog as? Dialog.Report ?: return
        if (report.isSending) return
        context.update { it.copy(dialog = report.copy(isSending = true)) }
        context.scope.launch {
            val result = socialRepository.report(report.target, report.id, reason, report.details.text.toString().trim())
            context.update {
                when (result) {
                    is AppResult.Success -> it.copy(dialog = null, message = UiText.Note(UiText.Resource(Res.string.map_reported)))
                    is AppResult.Error -> it.copy(dialog = report, message = result.error.toUiText(Res.string.map_error_report))
                }
            }
        }
    }

    /** Saving or joining, shown once the server agreed (the cached trip then shows the new state). */
    private fun update(done: StringResource, failed: StringResource, change: suspend () -> AppResult<Unit, DataError.Network>) {
        if (context.model.value.isUpdatingSharing) return
        context.update { it.copy(isUpdatingSharing = true) }
        context.scope.launch {
            val result = change()
            context.update {
                it.copy(
                    isUpdatingSharing = false,
                    message = when (result) {
                        is AppResult.Success -> UiText.Note(UiText.Resource(done))
                        is AppResult.Error -> if (result.error.hasCode(TripErrorCodes.JOIN_REQUEST_DECLINED)) {
                            UiText.Resource(Res.string.map_join_declined)
                        } else {
                            result.error.toUiText(failed)
                        }
                    },
                )
            }
        }
    }
}
