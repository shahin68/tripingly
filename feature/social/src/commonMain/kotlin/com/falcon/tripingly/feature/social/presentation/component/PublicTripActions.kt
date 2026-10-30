package com.falcon.tripingly.feature.social.presentation.component

import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.model.social.LikeState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_join
import com.falcon.tripingly.feature.social.generated.resources.social_error_like
import com.falcon.tripingly.feature.social.generated.resources.social_error_save_trip
import com.falcon.tripingly.feature.social.generated.resources.social_error_unsave_trip
import com.falcon.tripingly.feature.social.generated.resources.social_join_declined
import com.falcon.tripingly.feature.social.generated.resources.social_join_requested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Likes, saves and requests to join on public trip cards, the same in Explore and on a profile. A like or a save shows at
 * once and stays: a newer tap on the same card cancels the change on its way, and only a refusal puts back what
 * the server last confirmed, with a message.
 */
internal class PublicTripActions(
    private val scope: CoroutineScope,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    /** The trip as the list shows it now. */
    private val trip: (tripId: String) -> PublicTrip?,
    private val updateTrip: (tripId: String, change: (PublicTrip) -> PublicTrip) -> Unit,
    private val showMessage: (UiText) -> Unit,
) {
    private val likeJobs = mutableMapOf<String, Job>()
    private val saveJobs = mutableMapOf<String, Job>()

    /** As the server last confirmed them: what the card showed before the first tap of a burst. */
    private val confirmedLikes = mutableMapOf<String, LikeState>()
    private val confirmedSaves = mutableMapOf<String, Saved>()

    private data class Saved(val saved: Boolean, val count: Int)

    fun toggleLike(tripId: String) {
        val shown = trip(tripId) ?: return
        if (likeJobs[tripId]?.isActive != true) confirmedLikes[tripId] = LikeState(shown.likedByMe, shown.likeCount)
        likeJobs[tripId]?.cancel()
        val liked = !shown.likedByMe
        updateTrip(tripId) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        likeJobs[tripId] = scope.launch {
            val result = socialRepository.setLiked(LikeTarget.Trip, tripId, liked)
            if (result is AppResult.Error) {
                val before = confirmedLikes.getValue(tripId)
                updateTrip(tripId) { it.copy(likedByMe = before.liked, likeCount = before.likeCount) }
                showMessage(result.error.toUiText(Res.string.social_error_like))
            }
        }
    }

    /** Saves to My Trips, or removes it from there. */
    fun toggleSave(tripId: String) {
        val shown = trip(tripId) ?: return
        if (saveJobs[tripId]?.isActive != true) confirmedSaves[tripId] = Saved(shown.trip.savedByMe, shown.saveCount)
        saveJobs[tripId]?.cancel()
        val saved = !shown.trip.savedByMe
        updateTrip(tripId) {
            it.copy(trip = it.trip.copy(savedByMe = saved), saveCount = (it.saveCount + if (saved) 1 else -1).coerceAtLeast(0))
        }
        saveJobs[tripId] = scope.launch {
            val result = tripRepository.setSaved(shown.trip, saved)
            if (result is AppResult.Error) {
                val before = confirmedSaves.getValue(tripId)
                updateTrip(tripId) { it.copy(trip = it.trip.copy(savedByMe = before.saved), saveCount = before.count) }
                showMessage(result.error.toUiText(if (saved) Res.string.social_error_save_trip else Res.string.social_error_unsave_trip))
            }
        }
    }

    /** Asks the owner to let me join; the answer is a note (sent, or declined before) or the server's reason. */
    fun requestToJoin(tripId: String) {
        scope.launch {
            showMessage(
                when (val result = tripRepository.requestToJoin(tripId)) {
                    is AppResult.Success -> UiText.Note(UiText.Resource(Res.string.social_join_requested))
                    is AppResult.Error -> if (result.error.hasCode(TripErrorCodes.JOIN_REQUEST_DECLINED)) {
                        UiText.Resource(Res.string.social_join_declined)
                    } else {
                        result.error.toUiText(Res.string.social_error_join)
                    }
                },
            )
        }
    }
}
