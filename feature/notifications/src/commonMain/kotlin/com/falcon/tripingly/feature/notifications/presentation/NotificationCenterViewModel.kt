package com.falcon.tripingly.feature.notifications.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.push.CenterChange
import com.falcon.tripingly.core.data.push.NotificationCenterRepository
import com.falcon.tripingly.core.data.users.UserRepository
import com.falcon.tripingly.core.model.notification.NotificationEntry
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.notifications.generated.resources.Res
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_block
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_clear
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_load
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_remove
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_error_remove_follower
import com.falcon.tripingly.feature.notifications.generated.resources.notifications_follower_removed
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
 * live (a group growing moves up); entries read or removed anywhere else (a stop's comments, another device) change
 * here too. While it is shown everything in it counts as seen, which turns off the red indicators; a tap marks its
 * entry read and opens what it's about. An entry can be removed (only the entry, never what it is about), all of them
 * cleared, and its person blocked or removed as a follower.
 */
class NotificationCenterViewModel(
    private val repository: NotificationCenterRepository,
    private val users: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State())
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private var isShown = false

    init {
        load(cursor = null)
        viewModelScope.launch {
            repository.arrivals.collect { entry ->
                update { entries -> listOf(entry) + entries.filterNot { it.id == entry.id } }
                if (isShown) repository.markSeen()
            }
        }
        viewModelScope.launch {
            repository.changes.collect { change ->
                when (change) {
                    is CenterChange.Read -> update { entries -> entries.map { if (it.id in change.ids) it.copy(isRead = true) else it } }
                    is CenterChange.Removed -> update { entries -> entries.filterNot { it.id in change.ids } }
                    CenterChange.Cleared -> _uiState.update { it.copy(entries = persistentListOf(), nextCursor = null) }
                }
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
            is Action.OnOpen -> update { entries -> entries.map { if (it.id == action.entry.id) it.copy(isRead = true) else it } }
            is Action.OnRemove -> remove(action.entry)
            is Action.OnClearAll -> _uiState.update { it.copy(confirming = Confirm.ClearAll) }
            is Action.OnBlock -> _uiState.update { it.copy(confirming = Confirm.Block(action.user)) }
            is Action.OnRemoveFollower -> _uiState.update { it.copy(confirming = Confirm.RemoveFollower(action.user)) }
            is Action.OnConfirm -> confirm()
            is Action.OnDismissConfirm -> _uiState.update { it.copy(confirming = null) }
            is Action.OnMessageShown -> _uiState.update { it.copy(message = null) }
        }
    }

    /** Goes at once; comes back where it was if the server refuses. */
    private fun remove(entry: NotificationEntry) {
        val index = _uiState.value.entries.indexOfFirst { it.id == entry.id }.takeIf { it >= 0 } ?: return
        update { entries -> entries - entry }
        viewModelScope.launch {
            val result = repository.remove(entry.id)
            if (result is AppResult.Error) {
                update { entries -> entries.toMutableList().apply { add(index.coerceAtMost(size), entry) } }
                _uiState.update { it.copy(message = result.error.toUiText(Res.string.notifications_error_remove)) }
            }
        }
    }

    private fun confirm() {
        val confirming = _uiState.value.confirming ?: return
        _uiState.update { it.copy(confirming = null) }
        when (confirming) {
            Confirm.ClearAll -> {
                val before = _uiState.value.entries
                _uiState.update { it.copy(entries = persistentListOf()) }
                viewModelScope.launch {
                    val result = repository.clear()
                    if (result is AppResult.Error) {
                        _uiState.update { it.copy(entries = before, message = result.error.toUiText(Res.string.notifications_error_clear)) }
                    } else {
                        _uiState.update { it.copy(nextCursor = null) }
                    }
                }
            }
            // The server hides what they caused from now on.
            is Confirm.Block -> viewModelScope.launch {
                when (val result = users.setBlocked(confirming.user.id, blocked = true)) {
                    is AppResult.Success -> update { entries -> entries.filterNot { it.actor?.id == confirming.user.id } }
                    is AppResult.Error -> _uiState.update { it.copy(message = result.error.toUiText(Res.string.notifications_error_block)) }
                }
            }
            is Confirm.RemoveFollower -> viewModelScope.launch {
                val result = users.removeFollower(confirming.user.id)
                _uiState.update {
                    it.copy(
                        message = when (result) {
                            is AppResult.Success -> UiText.Note(UiText.Resource(Res.string.notifications_follower_removed, confirming.user.username))
                            is AppResult.Error -> result.error.toUiText(Res.string.notifications_error_remove_follower)
                        },
                    )
                }
            }
        }
    }

    private fun update(change: (List<NotificationEntry>) -> List<NotificationEntry>) =
        _uiState.update { it.copy(entries = change(it.entries).toImmutableList()) }

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
        /** The question asked before something that can't be taken back. */
        val confirming: Confirm? = null,
        /** A failure, or a note ("no longer follows you"), shown once. */
        val message: UiText? = null,
    )

    sealed interface Confirm {
        data object ClearAll : Confirm
        data class Block(val user: UserSummary) : Confirm
        data class RemoveFollower(val user: UserSummary) : Confirm
    }

    sealed interface Action {
        /** The center came on screen. */
        data object OnShown : Action
        data object OnHidden : Action
        data object OnLoadMore : Action
        data object OnRetry : Action
        data class OnOpen(val entry: NotificationEntry) : Action
        /** Swiped away: only the entry goes. */
        data class OnRemove(val entry: NotificationEntry) : Action
        data object OnClearAll : Action
        data class OnBlock(val user: UserSummary) : Action
        data class OnRemoveFollower(val user: UserSummary) : Action
        data object OnConfirm : Action
        data object OnDismissConfirm : Action
        data object OnMessageShown : Action
    }
}
