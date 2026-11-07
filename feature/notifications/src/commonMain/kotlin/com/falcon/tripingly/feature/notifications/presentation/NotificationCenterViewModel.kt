package com.falcon.tripingly.feature.notifications.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.push.NotificationCenterRepository
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.notifications.generated.resources.Res
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_load
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The notification center: everything the server told me, newest first, a page at a time. Arrivals come in on top
 * live (an entry coming back, like a new like on the same photo, moves up). While it is shown everything in it
 * counts as seen, which turns off the red indicators; a tap marks its entry read and opens what it's about.
 */
class NotificationCenterViewModel(private val repository: NotificationCenterRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var isShown = false

    init {
        load(cursor = null)
        viewModelScope.launch {
            repository.arrivals.collect { entry ->
                _uiState.update { state -> state.copy(entries = (listOf(entry) + state.entries.filterNot { it.id == entry.id }).toImmutableList()) }
                if (isShown) repository.markSeen()
            }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnShown -> {
                isShown = true
                viewModelScope.launch { repository.markSeen() }
                // What came while the connection was down shows up too.
                if (_uiState.value.hasLoaded) load(cursor = null)
            }
            is Action.OnHidden -> isShown = false
            is Action.OnLoadMore -> _uiState.value.nextCursor?.takeUnless { _uiState.value.isLoading }?.let { load(it) }
            is Action.OnRetry -> load(cursor = null)
            // The link it opens marks it read on the server.
            is Action.OnOpen -> _uiState.update { state ->
                state.copy(entries = state.entries.map { if (it.id == action.entry.id) it.copy(isRead = true) else it }.toImmutableList())
            }
        }
    }

    private fun load(cursor: String?) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            when (val result = repository.entries(cursor)) {
                is AppResult.Success -> _uiState.update { state ->
                    val loaded = result.data.items
                    state.copy(
                        // A fresh first page replaces the list; later pages add what isn't there yet.
                        entries = (if (cursor == null) loaded else state.entries + loaded.filter { new -> state.entries.none { it.id == new.id } }).toImmutableList(),
                        nextCursor = result.data.nextCursor,
                        isLoading = false,
                        hasLoaded = true,
                        loadError = null,
                    )
                }
                is AppResult.Error -> _uiState.update {
                    it.copy(isLoading = false, loadError = result.error.toUiText(Res.string.notifications_error_load))
                }
            }
        }
    }

    @Immutable
    data class State(
        val entries: ImmutableList<NotificationEntry> = persistentListOf(),
        /** More (older) entries wait on the server. */
        val nextCursor: String? = null,
        val isLoading: Boolean = true,
        val hasLoaded: Boolean = false,
        /** Why the entries couldn't load; shown with a retry while there are none. */
        val loadError: UiText? = null,
    )

    sealed interface Action {
        /** The center came on screen. */
        data object OnShown : Action
        data object OnHidden : Action
        data object OnLoadMore : Action
        data object OnRetry : Action
        data class OnOpen(val entry: NotificationEntry) : Action
    }
}
