package com.falcon.tripingly.feature.trips.presentation.screen

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.data.places.PlaceRepository
import com.falcon.tripingly.core.data.trips.TripErrorCodes
import com.falcon.tripingly.core.data.trips.TripRepository
import com.falcon.tripingly.core.data.trips.hasFieldError
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.model.place.PlaceSearchResult
import com.falcon.tripingly.core.model.trip.Destination
import com.falcon.tripingly.core.model.trip.NewTrip
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.trips.generated.resources.Res
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_error_members
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_blocked
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_not_found
import com.falcon.tripingly.feature.trips.generated.resources.create_trip_member_not_friend
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Action
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.Dialog
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.DialogState
import com.falcon.tripingly.feature.trips.presentation.screen.HomeViewModel.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The New trip sheet's form ([CreateTripForm], kept in the view model so it survives a rotation): the fields, the
 * debounced destination search, the friends to add, and creating the trip.
 */
internal class HomeCreateTrip(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<State>,
    private val tripRepository: TripRepository,
    private val placeRepository: PlaceRepository,
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    /** The view model's dialog save: [refused] shapes the dialog when the server says no. */
    private val save: (refused: (DialogState, DataError.Network) -> DialogState, change: suspend () -> AppResult<*, DataError.Network>) -> Unit,
) {
    private var destinationSearch: Job? = null
    private var friendsLoad: Job? = null

    private val form: CreateTripForm? get() = (ui.value.dialog?.dialog as? Dialog.Create)?.form

    fun onAction(action: Action.CreateTrip) {
        when (action) {
            is Action.CreateTrip.DatesChanged -> update { it.copy(startDate = action.startDate, endDate = action.endDate) }
            is Action.CreateTrip.DestinationQueryChanged -> searchDestination(action.query)
            is Action.CreateTrip.DestinationSelected -> selectDestination(action.result)
            is Action.CreateTrip.VisibilityChanged -> update { it.copy(visibility = action.visibility) }
            is Action.CreateTrip.FriendToggled -> if (form?.isMember(action.user) == true) removeMember(action.user.username) else addMember(action.user)
            is Action.CreateTrip.MemberRemoved -> removeMember(action.username)
            Action.CreateTrip.Confirm -> create()
        }
    }

    /** A fresh sheet with the user's default visibility; the friends row fills in when they arrive. */
    fun open() {
        clearSearches()
        ui.update { it.copy(dialog = DialogState(Dialog.Create(CreateTripForm(visibility = defaultVisibility())))) }
        friendsLoad?.cancel()
        friendsLoad = scope.launch {
            // A failed load shows the same hint as having no friends; adding people is optional here.
            val friends = (userRepository.friends() as? AppResult.Success)?.data.orEmpty()
            update { it.copy(friends = friends.toImmutableList()) }
        }
    }

    private fun defaultVisibility(): TripVisibility = when (val session = sessionRepository.session.value) {
        is SessionState.SignedIn -> session.account.defaultTripVisibility
        is SessionState.Onboarding -> session.account.defaultTripVisibility
        else -> TripVisibility.PUBLIC
    }

    /** Changes the open sheet's form; nothing once it's closed. */
    private fun update(change: (CreateTripForm) -> CreateTripForm) = ui.update { state ->
        val dialog = state.dialog ?: return@update state
        val create = dialog.dialog as? Dialog.Create ?: return@update state
        state.copy(dialog = dialog.copy(dialog = create.copy(form = change(create.form))))
    }

    /** Asks the server only once typing pauses; every keystroke before that cancels the pending search. A cleared field clears the results at once. */
    private fun searchDestination(query: String) {
        // Picking a result puts its name in the field; that change isn't a new search.
        if (query == form?.destination?.name) return
        destinationSearch?.cancel()
        update { it.copy(destination = null) }
        if (query.isBlank()) {
            update { it.copy(destinationResults = persistentListOf(), isSearchingDestination = false) }
            return
        }
        destinationSearch = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            update { it.copy(isSearchingDestination = true) }
            val results = (placeRepository.searchDestinations(query) as? AppResult.Success)?.data.orEmpty()
            update { it.copy(destinationResults = results.toImmutableList(), isSearchingDestination = false) }
        }
    }

    private fun selectDestination(result: PlaceSearchResult) {
        destinationSearch?.cancel()
        update {
            it.copy(
                destination = Destination(result.name, result.location),
                destinationResults = persistentListOf(),
                isSearchingDestination = false,
            )
        }
        form?.destinationQuery?.setTextAndPlaceCursorAtEnd(result.name)
    }

    private fun addMember(user: UserSummary) = update { form ->
        form.copy(members = (if (form.isMember(user)) form.members else form.members + user).toImmutableList())
    }

    /** Removing the last person the server refused also clears the message about them. */
    private fun removeMember(username: String) {
        val form = form ?: return
        val errors = form.memberErrors - username
        update { it.copy(members = it.members.filterNot { member -> member.username == username }.toImmutableList(), memberErrors = errors.toImmutableMap()) }
        if (form.memberErrors.isNotEmpty() && errors.isEmpty()) ui.update { it.copy(dialog = it.dialog?.copy(error = null)) }
    }

    /** Stops the destination search and hides its results, e.g. when another dialog opens or the sheet closes. */
    fun clearSearches() {
        destinationSearch?.cancel()
        update { it.copy(destinationResults = persistentListOf(), isSearchingDestination = false) }
    }

    private fun create() {
        val form = form?.takeIf { it.canCreate } ?: return
        val usernames = form.members.map { it.username }
        update { it.copy(memberErrors = persistentMapOf()) }
        save(
            { dialog, error ->
                val memberErrors = memberErrors(error, usernames)
                val create = dialog.dialog as? Dialog.Create
                if (memberErrors.isEmpty() || create == null) {
                    dialog.copy(error = error.toDialogError())
                } else {
                    dialog.copy(
                        dialog = create.copy(form = create.form.copy(memberErrors = memberErrors.toImmutableMap())),
                        error = UiText.Resource(Res.string.create_trip_error_members),
                    )
                }
            },
        ) {
            tripRepository.createTrip(
                NewTrip(
                    name = form.name.text.toString().trim(),
                    startDate = form.startDate,
                    endDate = form.endDate ?: form.startDate,
                    visibility = form.visibility,
                    destination = form.destination,
                    memberUsernames = usernames,
                ),
            )
        }
    }

    /** `fields.memberUsernames.<index>` errors, by the username that was sent at that index. */
    private fun memberErrors(error: DataError.Network, usernames: List<String>): Map<String, UiText> =
        usernames.withIndex().mapNotNull { (index, username) ->
            val field = "memberUsernames.$index"
            when {
                error.hasFieldError(field, TripErrorCodes.MEMBER_NOT_FOUND) -> username to UiText.Resource(Res.string.create_trip_member_not_found)
                error.hasFieldError(field, TripErrorCodes.MEMBER_BLOCKED) -> username to UiText.Resource(Res.string.create_trip_member_blocked)
                error.hasFieldError(field, TripErrorCodes.MEMBER_NOT_FRIEND) -> username to UiText.Resource(Res.string.create_trip_member_not_friend)
                else -> null
            }
        }.toMap()

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
