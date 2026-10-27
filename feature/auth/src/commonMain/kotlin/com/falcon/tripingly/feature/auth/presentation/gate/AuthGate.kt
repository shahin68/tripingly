package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingRoute
import com.falcon.tripingly.feature.auth.presentation.signin.SignInRoute
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shows sign-in or onboarding until the user is signed in and onboarded, then
 * [content]. After a sign-out [content] leaves the composition, so screens
 * start fresh for the next user.
 */
@Composable
fun AuthGate(content: @Composable () -> Unit) {
    val viewModel: AuthGateViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val session = state.session) {
        SessionState.Restoring -> LaunchScreen(state = state, onAction = viewModel::onAction)
        is SessionState.SignedOut -> SignInRoute(reason = session.reason)
        is SessionState.Onboarding -> OnboardingRoute()
        is SessionState.SignedIn -> content()
    }
}

/** While the saved session is checked: a spinner, or the error screen with Retry when it couldn't be. */
@Composable
internal fun LaunchScreen(
    state: AuthGateViewModel.State,
    onAction: (AuthGateViewModel.Action) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val error = state.restoreError
        if (error == null || state.isRetrying) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            ErrorScreen(
                onRetry = { onAction(AuthGateViewModel.Action.OnRetryClick) },
                message = error.asString(),
                modifier = Modifier.safeDrawingPadding(),
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
