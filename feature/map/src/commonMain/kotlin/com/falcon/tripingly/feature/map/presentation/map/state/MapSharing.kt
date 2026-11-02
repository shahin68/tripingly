package com.falcon.tripingly.feature.map.presentation.map.state

import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.photo.Photo
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_error_cancel_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_join
import com.falcon.tripingly.feature.map.generated.resources.map_error_report
import com.falcon.tripingly.feature.map.generated.resources.map_error_save_trip
import com.falcon.tripingly.feature.map.generated.resources.map_error_unsave_trip
import com.falcon.tripingly.feature.map.generated.resources.map_join_declined
import com.falcon.tripingly.feature.map.generated.resources.map_join_requested
import com.falcon.tripingly.feature.map.generated.resources.map_join_withdrawn
import com.falcon.tripingly.feature.map.generated.resources.map_reported
import com.falcon.tripingly.feature.map.generated.resources.map_trip_saved
import com.falcon.tripingly.feature.map.generated.resources.map_trip_unsaved
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

/** Someone else's trip: saving it, asking to join, and reports of the trip, its stops and photos. */
internal class MapSharing(
    private val context: MapContext,
    private val tripRepository: TripRepository,
    private val socialRepository: SocialRepository,
) {
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

    fun requestToJoin() =
        update(Res.string.map_join_requested, Res.string.map_error_join) { tripRepository.requestToJoin(context.tripId) }

    fun cancelJoinRequest() =
        update(Res.string.map_join_withdrawn, Res.string.map_error_cancel_join) { tripRepository.cancelJoinRequest(context.tripId) }

    fun report(target: ReportTarget, id: String) = context.update { it.copy(dialog = Dialog.Report(target, id)) }

    /** A place's Commons image is reported as the place's image, every other photo as a photo. */
    fun reportPhoto(photo: Photo) = report(if (photo.credit != null) ReportTarget.PlaceImage else ReportTarget.Photo, photo.id)

    fun sendReport(reason: ReportReason) {
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
