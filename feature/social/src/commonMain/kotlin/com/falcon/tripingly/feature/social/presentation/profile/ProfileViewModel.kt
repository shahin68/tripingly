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
import com.falcon.tripingly.core.model.social.Profile
import com.falcon.tripingly.core.model.social.PublicTrip
import com.falcon.tripingly.core.model.social.ReportReason
import com.falcon.tripingly.core.model.social.ReportTarget
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_block
import com.falcon.tripingly.feature.social.generated.resources.social_error_follow
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_profile
import com.falcon.tripingly.feature.social.generated.resources.social_error_unfollow
import com.falcon.tripingly.feature.social.presentation.component.PublicTripActions
import com.falcon.tripingly.feature.social.presentation.component.ReportState
import com.falcon.tripingly.feature.social.presentation.component.sendReport
import com.falcon.tripingly.core.model.trip.TripRole
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * A person's profile: counts, follow, their public trips (on mine, all my trips), report and block. [username] null is my own
 * profile, the Profile tab, which also shows my friends, the people I follow and my followers (each a
 * [com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel]).
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
    private var following: Job? = null

    /** The follow as the server last confirmed it: the profile before the first tap of a burst. */
    private lateinit var confirmedFollow: Profile

    private val tripActions = PublicTripActions(
        scope = viewModelScope,
        socialRepository = socialRepository,
        tripRepository = tripRepository,
        trip = { id -> _uiState.value.trips.firstOrNull { it.trip.id == id } },
        updateTrip = { id, change -> _uiState.update { state -> state.copy(trips = state.trips.map { if (it.trip.id == id) change(it) else it }.toImmutableList()) } },
        showMessage = ::showMessage,
    )

    init {
        load()
        watchFollows()
        if (_uiState.value.isMe) watchMyTrips()
    }

    fun onAction(action: Action) {
        when (action) {
            Action.Retry -> load()
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
            is Action.Person -> when (action) {
                Action.Person.ToggleFollow -> toggleFollow()
                Action.Person.Block -> _uiState.update { it.copy(isConfirmingBlock = true) }
                Action.Person.ConfirmBlock -> block()
                Action.Person.DismissBlock -> _uiState.update { it.copy(isConfirmingBlock = false) }
                Action.Person.Report -> _uiState.update { state -> state.copy(report = state.profile?.user?.id?.let { ReportState(ReportTarget.User, it) }) }
            }
            is Action.Trips -> when (action) {
                is Action.Trips.Click -> sendEvent(Event.OpenTrip(action.trip.trip.id))
                is Action.Trips.Like -> tripActions.toggleLike(action.trip.trip.id)
                is Action.Trips.ToggleSave -> tripActions.toggleSave(action.trip.trip.id)
                is Action.Trips.AskToJoin -> tripActions.requestToJoin(action.trip.trip.id)
                is Action.Trips.Report -> _uiState.update { it.copy(report = ReportState(ReportTarget.Trip, action.trip.trip.id)) }
                Action.Trips.LoadMore -> loadMore()
            }
            is Action.Report -> when (action) {
                is Action.Report.Send -> sendReport(action.reason)
                Action.Report.Dismiss -> _uiState.update { it.copy(report = null) }
            }
        }
    }

    private fun load() {
        val username = username ?: return
        _uiState.update { it.copy(isLoading = true, isNotFound = false, loadError = null) }
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
                        // Nothing shown yet: the error screen; a profile already shown stays, with a banner.
                        val message = result.error.toUiText(Res.string.social_error_load_profile)
                        if (it.profile == null) it.copy(isLoading = false, loadError = message) else it.copy(isLoading = false, message = message)
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

    /**
     * My own profile follows My Trips: a trip I create, change or delete there (private ones too) shows here at once,
     * without a refresh.
     */
    private fun watchMyTrips() {
        viewModelScope.launch {
            tripRepository.observeMyTrips()
                .map { trips -> trips.filter { it.role == TripRole.OWNER }.associate { it.id to it.updatedAt } }
                .distinctUntilChanged()
                .drop(1)
                .collect { refreshProfile(withTrips = true) }
        }
    }

    /** The profile's counts and follow state again; the trips list stays as it is, unless [withTrips] (its first page then). */
    private suspend fun refreshProfile(withTrips: Boolean = false) {
        val username = username ?: return
        val result = userRepository.profile(username)
        if (result !is AppResult.Success) return
        if (withTrips) tripsCursor = result.data.nextCursor
        _uiState.update { it.copy(profile = result.data, trips = if (withTrips) result.data.trips.toImmutableList() else it.trips) }
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

    /** Like a heart: shown at once and kept; a newer tap wins, and only a refusal puts back what the server last confirmed. */
    private fun toggleFollow() {
        val shown = _uiState.value.profile ?: return
        if (following?.isActive != true) confirmedFollow = shown
        following?.cancel()
        val follow = !shown.isFollowing
        _uiState.update {
            it.copy(
                profile = shown.copy(
                    isFollowing = follow,
                    isFriend = follow && shown.followsMe,
                    followerCount = (shown.followerCount + if (follow) 1 else -1).coerceAtLeast(0),
                ),
            )
        }
        following = viewModelScope.launch {
            val result = userRepository.setFollowing(shown.user.id, follow)
            if (result is AppResult.Error) {
                val failed = if (follow) Res.string.social_error_follow else Res.string.social_error_unfollow
                _uiState.update { state ->
                    val before = confirmedFollow
                    state.copy(
                        profile = state.profile?.copy(isFollowing = before.isFollowing, isFriend = before.isFriend, followerCount = before.followerCount),
                        message = result.error.toUiText(failed),
                    )
                }
            }
        }
    }

    /** Blocking hides us from each other, so the profile closes once the server took it. */
    private fun block() {
        val user = _uiState.value.profile?.user ?: return
        _uiState.update { it.copy(isConfirmingBlock = false) }
        viewModelScope.launch {
            when (val result = userRepository.setBlocked(user.id, blocked = true)) {
                is AppResult.Success -> sendEvent(Event.Close)
                is AppResult.Error -> _uiState.update { it.copy(message = result.error.toUiText(Res.string.social_error_block)) }
            }
        }
    }

    private fun sendReport(reason: ReportReason) {
        val report = _uiState.value.report?.takeUnless { it.isSending } ?: return
        _uiState.update { it.copy(report = report.copy(isSending = true)) }
        viewModelScope.launch {
            val sent = socialRepository.sendReport(report, reason, ::showMessage)
            _uiState.update { state -> state.copy(report = state.report?.copy(isSending = false)?.takeUnless { sent }) }
        }
    }

    private fun showMessage(message: UiText) = _uiState.update { it.copy(message = message) }

    private fun sendEvent(event: Event) {
        viewModelScope.launch { _events.send(event) }
    }


    @Immutable
    data class State(
        val isMe: Boolean,
        val profile: Profile? = null,
        val trips: ImmutableList<PublicTrip> = persistentListOf(),
        val isLoading: Boolean = true,
        /** Unknown, or a block either way. */
        val isNotFound: Boolean = false,
        /** Why the profile couldn't load: the error screen with a retry. */
        val loadError: UiText? = null,
        val isConfirmingBlock: Boolean = false,
        val report: ReportState? = null,
        val message: UiText? = null,
    )

    sealed interface Action {
        data object Retry : Action
        data object DismissMessage : Action

        /** The person: follow, block and report. */
        sealed interface Person : Action {
            data object ToggleFollow : Person
            data object Block : Person
            data object ConfirmBlock : Person
            data object DismissBlock : Person
            data object Report : Person
        }

        sealed interface Trips : Action {
            data class Click(val trip: PublicTrip) : Trips
            data class Like(val trip: PublicTrip) : Trips
            data class ToggleSave(val trip: PublicTrip) : Trips
            data class AskToJoin(val trip: PublicTrip) : Trips
            data class Report(val trip: PublicTrip) : Trips
            data object LoadMore : Trips
        }

        sealed interface Report : Action {
            data class Send(val reason: ReportReason) : Report
            data object Dismiss : Report
        }
    }

    sealed interface Event {
        data class OpenTrip(val tripId: String) : Event
        data object Close : Event
    }
}
