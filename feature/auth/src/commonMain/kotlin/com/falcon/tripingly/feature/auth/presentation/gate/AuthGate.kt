package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingRoute
import com.falcon.tripingly.feature.auth.presentation.signin.SignInRoute
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows sign-in or onboarding until the user is signed in and onboarded, then
 * [content]. After a sign-out [content] leaves the composition, so screens
 * start fresh for the next user. New terms while in the app keep [content] where it
 * is (the same place in the composition, so nothing restarts) with a sheet over it.
 */
@Composable
fun AuthGate(onStarted: () -> Unit = {}, content: @Composable () -> Unit) {
    val viewModel: AuthGateViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val session = state.session
    // Started once a screen is known: signed in, sign-in, onboarding, or the start's error with Retry.
    if (session !is SessionState.Restoring || state.restoreError != null) {
        val started by rememberUpdatedState(onStarted)
        LaunchedEffect(Unit) { started() }
    }
    if (session is SessionState.SignedIn || state.isReconsent) {
        content()
        ReconsentSheet(isShown = state.isReconsent)
    } else {
        when (session) {
            SessionState.Restoring -> LaunchScreen(state = state, onAction = viewModel::onAction)
            is SessionState.SignedOut -> SignInRoute(reason = session.reason)
            is SessionState.Onboarding -> OnboardingRoute()
            is SessionState.SignedIn -> Unit
        }
    }
}

/**
 * While the saved session is read (a moment: the saved account opens the app) or, without one, asked for: only the
 * background, no spinner (Shahin, 2026-11-22). The error screen with Retry when it couldn't be asked for.
 */
@Composable
internal fun LaunchScreen(
    state: AuthGateViewModel.State,
    onAction: (AuthGateViewModel.Action) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val error = state.restoreError
        ShowMessage(message = state.message?.asString(), onShown = { onAction(AuthGateViewModel.Action.OnMessageShown) })
        // A retry keeps the error on screen until it is answered, so nothing flashes.
        if (error != null) {
            ErrorScreen(
                onRetry = { onAction(AuthGateViewModel.Action.OnRetryClick) },
                message = error.asString(),
                contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
            )
        }
    }
}

@Preview
@Composable
private fun LaunchScreenPreview(@PreviewParameter(LaunchScreenPreviewStates::class) state: AuthGateViewModel.State) {
    TripinglyTheme { LaunchScreen(state = state, onAction = {}) }
}

@Preview
@Composable
private fun LaunchScreenDarkPreview() {
    TripinglyTheme(darkTheme = true) { LaunchScreen(state = LaunchScreenPreviewStates().values.last(), onAction = {}) }
}
