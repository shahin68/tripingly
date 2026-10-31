package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_blocked
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_unblock
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings' blocked people, in a sheet: everyone I blocked, a page at a time, each with Unblock. An unblocked
 * person leaves the list at once and comes back only if the server refuses.
 */
class BlockedPeopleViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var cursor: String? = null
    private var loading: Job? = null

    fun onAction(action: Action) {
        when (action) {
            Action.Open -> {
                _uiState.update { State(isOpen = true) }
                load(next = false)
            }
            Action.Close -> {
                loading?.cancel()
                _uiState.update { it.copy(isOpen = false) }
            }
            Action.Retry -> load(next = false)
            Action.LoadMore -> if (cursor != null && loading?.isActive != true) load(next = true)
            is Action.Unblock -> unblock(action.user)
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun load(next: Boolean) {
        if (!next) cursor = null
        _uiState.update { it.copy(loadError = null) }
        loading = viewModelScope.launch {
            when (val result = userRepository.blocked(cursor)) {
                is AppResult.Success -> {
                    cursor = result.data.nextCursor
                    _uiState.update { state ->
                        val people = if (next) state.people.orEmpty() + result.data.items else result.data.items
                        state.copy(people = people.distinctBy { it.id }.toImmutableList(), hasMore = cursor != null)
                    }
                }
                is AppResult.Error -> _uiState.update { state ->
                    val message = result.error.toUiText(Res.string.settings_error_blocked)
                    if (state.people == null) state.copy(loadError = message) else state.copy(message = message)
                }
            }
        }
    }

    private fun unblock(user: UserSummary) {
        val before = _uiState.value.people ?: return
        _uiState.update { state -> state.copy(people = before.filterNot { it.id == user.id }.toImmutableList()) }
        viewModelScope.launch {
            val result = userRepository.setBlocked(user.id, blocked = false)
            if (result is AppResult.Error) {
                _uiState.update { state ->
                    // Back in its place among the people still shown.
                    val people = state.people?.let { shown -> before.filter { it == user || it in shown } }
                    state.copy(people = people?.toImmutableList(), message = result.error.toUiText(Res.string.settings_error_unblock))
                }
            }
        }
    }

    @Immutable
    data class State(
        val isOpen: Boolean = false,
        /** Null until the first page arrives. */
        val people: ImmutableList<UserSummary>? = null,
        /** More pages to load at the end of the list. */
        val hasMore: Boolean = false,
        val loadError: UiText? = null,
        val message: UiText? = null,
    )

    sealed interface Action {
        data object Open : Action
        data object Close : Action
        data object Retry : Action
        data object LoadMore : Action
        data class Unblock(val user: UserSummary) : Action
        data object DismissMessage : Action
    }
}
