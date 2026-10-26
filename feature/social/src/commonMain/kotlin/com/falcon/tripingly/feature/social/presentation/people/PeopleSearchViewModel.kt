package com.falcon.tripingly.feature.social.presentation.people

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_search_people
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Finding people by name from the Profile tab's search field; the results replace the profile while it's in use. */
class PeopleSearchViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var search: Job? = null

    init {
        // Someone blocked here leaves the results at once.
        viewModelScope.launch {
            userRepository.blocks.collect { userId ->
                _uiState.update { state -> state.copy(results = state.results.filterNot { it.id == userId }.toImmutableList()) }
            }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.QueryChanged -> search(action.query)
            Action.DismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Asks the server only once typing pauses; a short or cleared query clears the results at once. */
    private fun search(query: String) {
        if (query == _uiState.value.query) return
        search?.cancel()
        _uiState.update { it.copy(query = query) }
        if (!_uiState.value.isSearching) {
            _uiState.update { it.copy(results = persistentListOf(), isLoading = false) }
            return
        }
        search = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            _uiState.update { it.copy(isLoading = true) }
            val result = userRepository.search(query)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(results = result.data.toImmutableList(), isLoading = false)
                    is AppResult.Error -> it.copy(isLoading = false, message = result.error.toUiText(Res.string.social_error_search_people))
                }
            }
        }
    }

    @Immutable
    data class State(
        val query: String = "",
        val results: ImmutableList<UserSummary> = persistentListOf(),
        val isLoading: Boolean = false,
        val message: UiText? = null,
    ) {
        /** Long enough to search: the results replace the profile. */
        val isSearching: Boolean get() = query.trim().removePrefix("@").length >= UserRepository.MIN_SEARCH_LENGTH
    }

    sealed interface Action {
        data class QueryChanged(val query: String) : Action
        data object DismissMessage : Action
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}
