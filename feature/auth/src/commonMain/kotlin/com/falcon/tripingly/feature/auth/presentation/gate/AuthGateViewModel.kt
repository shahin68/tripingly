package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.falcon.tripingly.core.common.result.AppResult
import com.falcon.tripingly.core.data.account.SessionRepository
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.common.toUiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Restores the session at start and tells the gate which screen to show. */
internal class AuthGateViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    data class State(
        val session: SessionState = SessionState.Restoring,
        /** Set when restoring failed for a reason other than a refused session (offline). */
        val restoreError: UiMessage? = null,
        val isRetrying: Boolean = false,
        /** A retry from the error screen failed again: its reason also shows in the banner (Shahin, 2026-11-22). */
        val message: UiMessage? = null,
        /**
         * The server asks to accept new terms while the app is open: the app stays and a sheet asks over it, instead of
         * the onboarding page taking its place (Shahin, 2026-11-22). Only for someone already in the app with a complete
         * profile; a start that is still onboarding shows the onboarding page.
         */
        val isReconsent: Boolean = false,
    )

    sealed interface Action {
        data object OnRetryClick : Action
        data object OnMessageShown : Action
    }

    private val restore = MutableStateFlow(RestoreStatus())

    /** Each session with whether it is a re-consent: onboarding that came while signed in, profile complete. */
    private val sessions = sessionRepository.session.runningFold(SessionState.Restoring as SessionState to false) { (before, wasReconsent), now ->
        val stillInApp = before is SessionState.SignedIn || wasReconsent
        now to (now is SessionState.Onboarding && now.account.onboarding.missingProfileFields.isEmpty() && stillInApp)
    }

    // Eagerly: the gate is always on screen, and a re-consent depends on what came before, which a restart would lose.
    val uiState: StateFlow<State> = combine(sessions, restore) { (session, isReconsent), status ->
        State(session = session, restoreError = status.error, isRetrying = status.running, message = status.message, isReconsent = isReconsent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    init {
        restore()
    }

    fun onAction(action: Action) {
        when (action) {
            Action.OnRetryClick -> restore()
            Action.OnMessageShown -> restore.update { it.copy(message = null) }
        }
    }

    private fun restore() {
        if (restore.value.running) return
        val isRetry = restore.value.error != null
        restore.update { it.copy(running = true) }
        viewModelScope.launch {
            val result = sessionRepository.restore()
            val error = (result as? AppResult.Error)?.error?.toUiMessage()
            restore.value = RestoreStatus(error = error, message = error?.takeIf { isRetry })
        }
    }

    private data class RestoreStatus(val running: Boolean = false, val error: UiMessage? = null, val message: UiMessage? = null)
}
