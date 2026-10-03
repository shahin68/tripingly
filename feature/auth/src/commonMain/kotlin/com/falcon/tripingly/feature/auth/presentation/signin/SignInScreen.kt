package com.falcon.tripingly.feature.auth.presentation.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ErrorBanner
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.account.SignOutReason
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.app_name
import com.falcon.tripingly.feature.auth.generated.resources.signin_apple
import com.falcon.tripingly.feature.auth.generated.resources.signin_developer_button
import com.falcon.tripingly.feature.auth.generated.resources.signin_developer_subject
import com.falcon.tripingly.feature.auth.generated.resources.signin_developer_title
import com.falcon.tripingly.feature.auth.generated.resources.signin_google
import com.falcon.tripingly.feature.auth.generated.resources.signin_reason_account_suspended
import com.falcon.tripingly.feature.auth.generated.resources.signin_reason_session_expired
import com.falcon.tripingly.feature.auth.generated.resources.signin_reason_under_age
import com.falcon.tripingly.feature.auth.generated.resources.signin_tagline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun SignInRoute(reason: SignOutReason?) {
    val viewModel: SignInViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SignInScreen(state = state, reason = reason, onAction = viewModel::onAction)
}

@Composable
internal fun SignInScreen(
    state: SignInViewModel.State,
    reason: SignOutReason?,
    onAction: (SignInViewModel.Action) -> Unit,
) {
    var reasonDismissed by rememberSaveable(reason) { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Spacer(Modifier.height(MaterialTheme.spacing.huge))
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displaySmall)
            Text(
                text = stringResource(Res.string.signin_tagline),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(MaterialTheme.spacing.large))

            if (reason != null && !reasonDismissed) {
                ErrorBanner(errorMessage = stringResource(reason.message()), onDismiss = { reasonDismissed = true })
            }
            state.error?.let { error ->
                ErrorBanner(errorMessage = error.asString(), onDismiss = { onAction(SignInViewModel.Action.OnDismissError) })
            }

            Button(
                onClick = { onAction(SignInViewModel.Action.OnGoogleClick) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.signin_google))
            }
            if (state.appleAvailable) {
                // Apple's guidelines: Sign in with Apple is at least as prominent as other options.
                Button(
                    onClick = { onAction(SignInViewModel.Action.OnAppleClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.signin_apple))
                }
            }
            if (state.isLoading) CircularProgressIndicator()

            if (state.developerSignInAvailable) {
                DeveloperSignIn(state = state, onAction = onAction)
            }
        }
    }
}

/** Local and staging builds only: signs in with any test subject, no Google or Apple account needed. */
@Composable
private fun DeveloperSignIn(state: SignInViewModel.State, onAction: (SignInViewModel.Action) -> Unit) {
    Spacer(Modifier.height(MaterialTheme.spacing.large))
    HorizontalDivider()
    Text(stringResource(Res.string.signin_developer_title), style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(
        value = state.developerSubject,
        onValueChange = { onAction(SignInViewModel.Action.OnDeveloperSubjectChange(it)) },
        label = { Text(stringResource(Res.string.signin_developer_subject)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onAction(SignInViewModel.Action.OnDeveloperSignInClick) }),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onAction(SignInViewModel.Action.OnDeveloperSignInClick) },
        enabled = !state.isLoading && state.developerSubject.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(Res.string.signin_developer_button))
    }
}

private fun SignOutReason.message() = when (this) {
    SignOutReason.SESSION_EXPIRED -> Res.string.signin_reason_session_expired
    SignOutReason.ACCOUNT_SUSPENDED -> Res.string.signin_reason_account_suspended
    SignOutReason.UNDER_AGE -> Res.string.signin_reason_under_age
}

@Preview
@Composable
private fun SignInScreenPreview() {
    TripinglyTheme {
        SignInScreen(
            state = SignInViewModel.State(appleAvailable = true, developerSignInAvailable = true),
            reason = SignOutReason.SESSION_EXPIRED,
            onAction = {},
        )
    }
}
