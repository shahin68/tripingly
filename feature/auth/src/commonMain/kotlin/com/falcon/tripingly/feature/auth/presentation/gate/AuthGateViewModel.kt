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
        /**
         * The server asks to accept new terms while the app is open: the app stays and a sheet asks over it, instead of
         * the onboarding page taking its place (Shahin, 2026-11-22). Only for someone already in the app with a complete
         * profile; a start that is still onboarding shows the onboarding page.
         */
        val isReconsent: Boolean = false,
    )

    sealed interface Action {
        data object OnRetryClick : Action
    }

    private val restore = MutableStateFlow(RestoreStatus())

    /** Each session with whether it is a re-consent: onboarding that came while signed in, profile complete. */
    private val sessions = sessionRepository.session.runningFold(SessionState.Restoring as SessionState to false) { (before, wasReconsent), now ->
        val stillInApp = before is SessionState.SignedIn || wasReconsent
        now to (now is SessionState.Onboarding && now.account.onboarding.missingProfileFields.isEmpty() && stillInApp)
    }

    // Eagerly: the gate is always on screen, and a re-consent depends on what came before, which a restart would lose.
    val uiState: StateFlow<State> = combine(sessions, restore) { (session, isReconsent), status ->
        State(session = session, restoreError = status.error, isRetrying = status.running, isReconsent = isReconsent)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, State())

    init {
        restore()
    }

    fun onAction(action: Action) {
        when (action) {
            Action.OnRetryClick -> restore()
        }
    }

    private fun restore() {
        if (restore.value.running) return
        restore.update { it.copy(running = true) }
        viewModelScope.launch {
            val result = sessionRepository.restore()
            restore.value = RestoreStatus(error = (result as? AppResult.Error)?.error?.toUiMessage())
        }
    }

    private data class RestoreStatus(val running: Boolean = false, val error: UiMessage? = null)
}
