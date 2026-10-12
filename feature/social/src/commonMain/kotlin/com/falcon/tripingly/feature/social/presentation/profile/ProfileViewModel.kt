package com.falcon.tripingly.feature.social.presentation.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.social.SocialRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasCode
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.social.LikeTarget
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_not_copyable
import com.falcon.tripingly.feature.social.generated.resources.social_reported
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A person's profile: counts, follow, their public trips, report and block. [username] null is my own
 * profile, which also leads to my friends, the people I follow and my followers.
 */
class ProfileViewModel(
    username: String?,
    private val userRepository: UserRepository,
    private val socialRepository: SocialRepository,
    private val tripRepository: TripRepository,
    sessionRepository: SessionRepository,
) : ViewModel() {

    private val myUsername = when (val session = sessionRepository.session.value) {
        is SessionState.SignedIn -> session.account.username
        else -> null
    }
    private val username: String? = username ?: myUsername

    private val _uiState = MutableStateFlow(State(isMe = username == null || username == myUsername))
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _events = Channel<Event>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var tripsCursor: String? = null
    private var loadingMore: Job? = null

    init {
        load()
        watchFollows()
    }

    fun onAction(action: Action) {
        when (action) {
            Action.OnRetry -> load()
            Action.OnToggleFollow -> toggleFollow()
            Action.OnBlock -> _uiState.update { it.copy(isConfirmingBlock = true) }
            Action.OnConfirmBlock -> block()
            Action.OnDismissBlock -> _uiState.update { it.copy(isConfirmingBlock = false) }
            Action.OnReportUser -> _uiState.update { state -> state.copy(reporting = state.profile?.user?.id?.let { Reporting(ReportTarget.User, it) }) }
            is Action.OnReportTrip -> _uiState.update { it.copy(reporting = Reporting(ReportTarget.Trip, action.trip.trip.id)) }
            is Action.OnSendReport -> sendReport(action.reason, action.details)
            Action.OnDismissReport -> _uiState.update { it.copy(reporting = null) }
            is Action.OnLikeTrip -> likeTrip(action.trip)
            is Action.OnCopyTrip -> copyTrip(action.trip)
            is Action.OnTripClick -> sendEvent(Event.OpenTrip(action.trip.trip.id))
            is Action.OnOpenList -> sendEvent(Event.OpenList(action.list))
            Action.OnLoadMore -> loadMore()
            Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun load() {
        val username = username ?: return
        _uiState.update { it.copy(isLoading = true, isNotFound = false) }
        viewModelScope.launch {
            when (val result = userRepository.profile(username)) {
                is AppResult.Success -> {
                    tripsCursor = result.data.nextCursor
                    _uiState.update {
                        it.copy(profile = result.data, trips = result.data.trips.toImmutableList(), isLoading = false)
                    }
                }
                is AppResult.Error -> _uiState.update {
                    if (result.error.hasCode(TripErrorCodes.NOT_FOUND)) {
                        it.copy(isLoading = false, isNotFound = true)
                    } else {
                        it.copy(isLoading = false, message = result.error.toUiText())
                    }
                }
            }
        }
    }

    /**
     * A follow that changes what this profile shows (its counts, "Follows you", my Follow button) reloads it,
     * unless it's my own tap here, which already shows.
     */
    private fun watchFollows() {
        viewModelScope.launch {
            userRepository.followChanges.collect { change ->
                val profile = _uiState.value.profile ?: return@collect
                val shown = profile.user.id
                if (shown != change.followerId && shown != change.followeeId) return@collect
                if (change.followeeId == shown && change.following == profile.isFollowing && !_uiState.value.isMe) return@collect
                refreshProfile()
            }
        }
    }

    /** The profile's counts and follow state again; the trips list stays as it is. */
    private suspend fun refreshProfile() {
        val username = username ?: return
        val result = userRepository.profile(username)
        if (result is AppResult.Success) _uiState.update { it.copy(profile = result.data) }
    }

    private fun loadMore() {
        val username = username ?: return
        val cursor = tripsCursor ?: return
        if (loadingMore?.isActive == true) return
        loadingMore = viewModelScope.launch {
            val result = userRepository.profile(username, cursor)
            if (result is AppResult.Success) {
                tripsCursor = result.data.nextCursor
                _uiState.update { state ->
                    val known = state.trips.map { it.trip.id }.toSet()
                    state.copy(trips = (state.trips + result.data.trips.filterNot { it.trip.id in known }).toImmutableList())
                }
            }
        }
    }

    /** Shown at once and put back when the server refuses. */
    private fun toggleFollow() {
        val before = _uiState.value.profile ?: return
        val following = !before.isFollowing
        _uiState.update {
            it.copy(
                profile = before.copy(
                    isFollowing = following,
                    isFriend = following && before.followsMe,
                    followerCount = (before.followerCount + if (following) 1 else -1).coerceAtLeast(0),
                ),
            )
        }
        viewModelScope.launch {
            val result = userRepository.setFollowing(before.user.id, following)
            if (result is AppResult.Error) _uiState.update { it.copy(profile = before, message = result.error.toUiText()) }
        }
    }

    /** Blocking hides us from each other, so the profile closes once the server took it. */
    private fun block() {
        val user = _uiState.value.profile?.user ?: return
        _uiState.update { it.copy(isConfirmingBlock = false) }
        viewModelScope.launch {
            when (val result = userRepository.setBlocked(user.id, blocked = true)) {
                is AppResult.Success -> sendEvent(Event.Close)
                is AppResult.Error -> _uiState.update { it.copy(message = result.error.toUiText()) }
            }
        }
    }

    private fun sendReport(reason: ReportReason, details: String) {
        val reporting = _uiState.value.reporting ?: return
        if (_uiState.value.isReporting) return
        _uiState.update { it.copy(isReporting = true) }
        viewModelScope.launch {
            val result = socialRepository.report(reporting.target, reporting.id, reason, details)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isReporting = false, reporting = null, message = UiText.Resource(Res.string.social_reported))
                    is AppResult.Error -> it.copy(isReporting = false, message = result.error.toUiText())
                }
            }
        }
    }

    private fun likeTrip(trip: PublicTrip) {
        val liked = !trip.likedByMe
        updateTrip(trip.trip.id) { it.copy(likedByMe = liked, likeCount = (it.likeCount + if (liked) 1 else -1).coerceAtLeast(0)) }
        viewModelScope.launch {
            when (val result = socialRepository.setLiked(LikeTarget.Trip, trip.trip.id, liked)) {
                is AppResult.Success -> updateTrip(trip.trip.id) { it.copy(likedByMe = result.data.liked, likeCount = result.data.likeCount) }
                is AppResult.Error -> {
                    updateTrip(trip.trip.id) { it.copy(likedByMe = trip.likedByMe, likeCount = trip.likeCount) }
                    _uiState.update { it.copy(message = result.error.toUiText()) }
                }
            }
        }
    }

    private fun updateTrip(tripId: String, change: (PublicTrip) -> PublicTrip) {
        _uiState.update { state -> state.copy(trips = state.trips.map { if (it.trip.id == tripId) change(it) else it }.toImmutableList()) }
    }

    private fun copyTrip(trip: PublicTrip) {
        if (_uiState.value.copyingTripId != null) return
        _uiState.update { it.copy(copyingTripId = trip.trip.id) }
        viewModelScope.launch {
            val result = tripRepository.copyTrip(trip.trip.id)
            _uiState.update { it.copy(copyingTripId = null) }
            when (result) {
                is AppResult.Success -> sendEvent(Event.OpenTrip(result.data.trip.id))
                is AppResult.Error -> _uiState.update {
                    it.copy(
                        message = if (result.error.hasCode(TripErrorCodes.TRIP_NOT_COPYABLE)) {
                            UiText.Resource(Res.string.social_error_not_copyable)
                        } else {
                            result.error.toUiText()
                        },
                    )
                }
            }
        }
    }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }

    data class Reporting(val target: ReportTarget, val id: String)

    @Immutable
    data class State(
        val isMe: Boolean,
        val profile: Profile? = null,
        val trips: ImmutableList<PublicTrip> = persistentListOf(),
        val isLoading: Boolean = true,
        /** Unknown, or a block either way. */
        val isNotFound: Boolean = false,
        val copyingTripId: String? = null,
        val isConfirmingBlock: Boolean = false,
        val reporting: Reporting? = null,
        val isReporting: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data object OnRetry : Action
        data object OnToggleFollow : Action
        data object OnBlock : Action
        data object OnConfirmBlock : Action
        data object OnDismissBlock : Action
        data object OnReportUser : Action
        data class OnReportTrip(val trip: PublicTrip) : Action
        data class OnSendReport(val reason: ReportReason, val details: String) : Action
        data object OnDismissReport : Action
        data class OnLikeTrip(val trip: PublicTrip) : Action
        data class OnCopyTrip(val trip: PublicTrip) : Action
        data class OnTripClick(val trip: PublicTrip) : Action
        data class OnOpenList(val list: PeopleList) : Action
        data object OnLoadMore : Action
        data object OnDismissMessage : Action
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data class OpenList(val list: PeopleList) : Event
        data object Close : Event
    }
}
