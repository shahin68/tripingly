package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.SessionState
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.common_retry
import com.falcon.tripingly.feature.auth.generated.resources.error_no_internet
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingRoute
import com.falcon.tripingly.feature.auth.presentation.signin.SignInRoute
import org.jetbrains.compose.resources.stringResource
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

@Composable
internal fun LaunchScreen(
    state: AuthGateViewModel.State,
    onAction: (AuthGateViewModel.Action) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(MaterialTheme.spacing.large)) {
            val error = state.restoreError
            if (error == null || state.isRetrying) {
                CircularProgressIndicator()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                ) {
                    Text(
                        text = error.asString(),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { onAction(AuthGateViewModel.Action.OnRetryClick) }) {
                        Text(stringResource(Res.string.common_retry))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun LaunchScreenOfflinePreview() {
    TripinglyTheme {
        LaunchScreen(
            state = AuthGateViewModel.State(restoreError = UiMessage.Resource(Res.string.error_no_internet)),
            onAction = {},
        )
    }
}
