package com.falcon.tripingly.feature.social.presentation.people

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_people
import com.falcon.tripingly.feature.social.generated.resources.social_error_unfollow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** My friends, the people I follow, or my followers, a page at a time: one segment of the Profile tab. Friends and people I follow can be unfollowed from the list. */
class PeopleViewModel(
    val list: PeopleList,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var cursor: String? = null
    private var loading: Job? = null

    init {
        load(first = true)
        // A follow made or ended anywhere can change the list, so it loads again.
        viewModelScope.launch { userRepository.followChanges.collect { load(first = true) } }
        // Someone blocked here leaves the list at once.
        viewModelScope.launch {
            userRepository.blocks.collect { userId -> _uiState.update { state -> state.copy(people = state.people.filterNot { it.id == userId }.toImmutableList()) } }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            Action.OnLoadMore -> if (cursor != null) load(first = false)
            Action.OnRetry -> load(first = true)
            Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
            is Action.Unfollow -> unfollow(action.user)
        }
    }

    /** Leaves the list at once; a refusal loads it again (the person back) with a message. */
    private fun unfollow(user: UserSummary) {
        _uiState.update { state -> state.copy(people = state.people.filterNot { it.id == user.id }.toImmutableList()) }
        viewModelScope.launch {
            val result = userRepository.setFollowing(user.id, following = false)
            if (result is AppResult.Error) {
                _uiState.update { it.copy(message = result.error.toUiText(Res.string.social_error_unfollow)) }
                load(first = true)
            }
        }
    }

    /** A first page replaces any load on its way (e.g. a follow changed during it); a next page waits for it. */
    private fun load(first: Boolean) {
        if (first) loading?.cancel() else if (loading?.isActive == true) return
        loading = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = null) }
            when (val result = userRepository.people(list, if (first) null else cursor)) {
                is AppResult.Success -> {
                    cursor = result.data.nextCursor
                    _uiState.update { state ->
                        val people = if (first) result.data.items else state.people + result.data.items.filterNot { new -> state.people.any { it.id == new.id } }
                        state.copy(people = people.toImmutableList(), isLoading = false, hasLoaded = true)
                    }
                }
                is AppResult.Error -> _uiState.update {
                    // Nobody shown yet: the error screen; people already shown stay, with a banner.
                    val message = result.error.toUiText(Res.string.social_error_load_people)
                    if (it.people.isEmpty()) it.copy(isLoading = false, loadError = message) else it.copy(isLoading = false, message = message)
                }
            }
        }
    }

    @Immutable
    data class State(
        val people: ImmutableList<UserSummary> = persistentListOf(),
        val isLoading: Boolean = false,
        val hasLoaded: Boolean = false,
        /** Why the list couldn't load when nobody is shown: the error screen with a retry. */
        val loadError: UiText? = null,
        val message: UiText? = null,
    )

    sealed interface Action {
        data object OnLoadMore : Action
        data object OnRetry : Action
        data object OnDismissMessage : Action
        data class Unfollow(val user: UserSummary) : Action
    }
}
