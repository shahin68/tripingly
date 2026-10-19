package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.error.DataError
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.AccountRepository
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.DataExport
import com.falcon.tripingly.core.model.account.LegalDocument
import com.falcon.tripingly.core.model.account.NotificationSettings
import com.falcon.tripingly.core.model.account.NotificationType
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.core.model.account.TripVisibility
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.core.ui.toUiText
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_delete
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_export
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_notifications
import com.falcon.tripingly.feature.settings.generated.resources.settings_error_visibility
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings: default trip visibility, which pushes arrive, a copy of my data, legal documents,
 * sign-out and account deletion. The server holds every setting; nothing is kept on the device.
 */
class SettingsViewModel(
    private val sessionRepository: SessionRepository,
    private val accountRepository: AccountRepository,
    /** The app's version, shown under About. */
    appVersion: String,
    private val languageTag: () -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(State(appVersion = appVersion))
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collect { session ->
                val account = (session as? SessionState.SignedIn)?.account ?: return@collect
                _uiState.update { it.copy(username = account.username, defaultVisibility = account.defaultTripVisibility) }
            }
        }
        loadNotifications()
        viewModelScope.launch {
            when (val result = accountRepository.dataExport()) {
                is AppResult.Success -> _uiState.update { it.copy(export = result.data, hasLoadedExport = true) }
                // Not worth a banner: the button to ask for a copy still works.
                is AppResult.Error -> _uiState.update { it.copy(hasLoadedExport = true) }
            }
        }
        viewModelScope.launch {
            val documents = accountRepository.legalDocuments(languageTag())
            if (documents is AppResult.Success) _uiState.update { it.copy(legalDocuments = documents.data.toImmutableList()) }
        }
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.OnVisibilityChange -> setVisibility(action.visibility)
            is Action.OnNotificationChange -> setNotification(action.type, action.enabled)
            Action.OnRetryNotifications -> loadNotifications()
            Action.OnRequestExport -> requestExport()
            Action.OnSignOut -> _uiState.update { it.copy(isConfirmingSignOut = true) }
            Action.OnDismissSignOut -> _uiState.update { it.copy(isConfirmingSignOut = false) }
            Action.OnConfirmSignOut -> {
                _uiState.update { it.copy(isConfirmingSignOut = false) }
                viewModelScope.launch { sessionRepository.signOut() }
            }
            Action.OnDeleteAccount -> _uiState.update { it.copy(isConfirmingDelete = true) }
            Action.OnDismissDelete -> _uiState.update { it.copy(isConfirmingDelete = false) }
            Action.OnConfirmDelete -> deleteAccount()
            Action.OnDismissReauth -> _uiState.update { it.copy(isReauthNeeded = false) }
            Action.OnDismissMessage -> _uiState.update { it.copy(message = null) }
        }
    }

    private fun setVisibility(visibility: TripVisibility) {
        val previous = _uiState.value.defaultVisibility
        if (visibility == previous) return
        // Shown at once; the session's account follows the server's answer.
        _uiState.update { it.copy(defaultVisibility = visibility) }
        viewModelScope.launch {
            val result = accountRepository.setDefaultTripVisibility(visibility)
            if (result is AppResult.Error) {
                _uiState.update { it.copy(defaultVisibility = previous, message = result.error.toUiText(Res.string.settings_error_visibility)) }
            }
        }
    }

    private fun loadNotifications() {
        _uiState.update { it.copy(hasNotificationsFailed = false) }
        viewModelScope.launch {
            when (val result = accountRepository.notificationSettings()) {
                is AppResult.Success -> _uiState.update { it.copy(notifications = result.data) }
                is AppResult.Error -> _uiState.update { it.copy(hasNotificationsFailed = true) }
            }
        }
    }

    private fun setNotification(type: NotificationType, enabled: Boolean) {
        // The switch moves at once and moves back if the server refuses.
        fun NotificationSettings.with(on: Boolean) = NotificationSettings(if (on) this.enabled + type else this.enabled - type)
        _uiState.update { state -> state.copy(notifications = state.notifications?.with(enabled)) }
        viewModelScope.launch {
            val result = accountRepository.setNotification(type, enabled)
            if (result is AppResult.Error) {
                _uiState.update { state ->
                    state.copy(
                        notifications = state.notifications?.with(!enabled),
                        message = result.error.toUiText(Res.string.settings_error_notifications),
                    )
                }
            }
        }
    }

    private fun requestExport() {
        if (_uiState.value.isRequestingExport) return
        _uiState.update { it.copy(isRequestingExport = true) }
        viewModelScope.launch {
            when (val result = accountRepository.requestDataExport()) {
                is AppResult.Success -> _uiState.update { it.copy(export = result.data, isRequestingExport = false) }
                // Once a day: the server's message says when the next one can be made.
                is AppResult.Error -> _uiState.update {
                    it.copy(isRequestingExport = false, message = result.error.toUiText(Res.string.settings_error_export))
                }
            }
        }
    }

    private fun deleteAccount() {
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            // On success the session ends and the app shows the sign-in screen.
            val result = accountRepository.deleteAccount()
            if (result is AppResult.Error) {
                val error = result.error
                _uiState.update {
                    if (error is DataError.Network.Api && error.code == REAUTH_REQUIRED) {
                        it.copy(isDeleting = false, isConfirmingDelete = false, isReauthNeeded = true)
                    } else {
                        it.copy(isDeleting = false, message = error.toUiText(Res.string.settings_error_delete))
                    }
                }
            }
        }
    }

    @Immutable
    data class State(
        val appVersion: String,
        val username: String? = null,
        val defaultVisibility: TripVisibility = TripVisibility.PUBLIC,
        /** Null until loaded. */
        val notifications: NotificationSettings? = null,
        val hasNotificationsFailed: Boolean = false,
        val export: DataExport? = null,
        val hasLoadedExport: Boolean = false,
        val isRequestingExport: Boolean = false,
        val legalDocuments: ImmutableList<LegalDocument> = persistentListOf(),
        val isConfirmingSignOut: Boolean = false,
        val isConfirmingDelete: Boolean = false,
        val isDeleting: Boolean = false,
        /** The server wants a fresh Google or Apple sign-in before it deletes the account. */
        val isReauthNeeded: Boolean = false,
        val message: UiText? = null,
    )

    sealed interface Action {
        data class OnVisibilityChange(val visibility: TripVisibility) : Action
        data class OnNotificationChange(val type: NotificationType, val enabled: Boolean) : Action
        data object OnRetryNotifications : Action
        data object OnRequestExport : Action
        data object OnSignOut : Action
        data object OnConfirmSignOut : Action
        data object OnDismissSignOut : Action
        data object OnDeleteAccount : Action
        data object OnConfirmDelete : Action
        data object OnDismissDelete : Action
        data object OnDismissReauth : Action
        data object OnDismissMessage : Action
    }

    private companion object {
        const val REAUTH_REQUIRED = "REAUTH_REQUIRED"
    }
}
