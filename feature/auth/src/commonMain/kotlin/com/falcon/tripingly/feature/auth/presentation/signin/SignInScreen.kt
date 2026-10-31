package com.falcon.tripingly.feature.auth.presentation.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.FormField
import com.falcon.tripingly.core.designsystem.component.PrimaryButton
import com.falcon.tripingly.core.designsystem.component.SecondaryButton
import com.falcon.tripingly.core.designsystem.component.ShowMessage
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
import com.falcon.tripingly.feature.auth.generated.resources.signin_reason_account_deleted
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
    SignInScreen(state = state, developerSubject = viewModel.developerSubject, reason = reason, onAction = viewModel::onAction)
}

/** The app's mark, name and tagline, with the sign-in buttons under them; one centred column in landscape too. */
@Composable
internal fun SignInScreen(
    state: SignInViewModel.State,
    developerSubject: TextFieldState,
    reason: SignOutReason?,
    onAction: (SignInViewModel.Action) -> Unit,
) {
    var reasonDismissed by rememberSaveable(reason) { mutableStateOf(false) }
    ShowMessage(
        message = reason?.takeUnless { reasonDismissed }?.let { stringResource(it.message()) },
        onShown = { reasonDismissed = true },
    )
    ShowMessage(message = state.error?.asString(), onShown = { onAction(SignInViewModel.Action.OnDismissError) })
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth().padding(MaterialTheme.spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                    Icon(Icons.Outlined.Explore, contentDescription = null, modifier = Modifier.padding(MaterialTheme.spacing.medium).size(MarkSize))
                }
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.displaySmall)
                Text(
                    text = stringResource(Res.string.signin_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(MaterialTheme.spacing.large))
                PrimaryButton(
                    text = stringResource(Res.string.signin_google),
                    onClick = { onAction(SignInViewModel.Action.OnGoogleClick) },
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.appleAvailable) {
                    // Apple's guidelines: Sign in with Apple is at least as prominent as other options.
                    PrimaryButton(
                        text = stringResource(Res.string.signin_apple),
                        onClick = { onAction(SignInViewModel.Action.OnAppleClick) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.isLoading) CircularProgressIndicator(Modifier.size(SpinnerSize), strokeWidth = 2.dp)
                if (state.developerSignInAvailable) DeveloperSignIn(state = state, subject = developerSubject, onAction = onAction)
            }
        }
    }
}

/** Local and staging builds only: signs in with any test subject, no Google or Apple account needed. */
@Composable
private fun DeveloperSignIn(state: SignInViewModel.State, subject: TextFieldState, onAction: (SignInViewModel.Action) -> Unit) {
    HorizontalDivider(Modifier.padding(vertical = MaterialTheme.spacing.small))
    FormField(stringResource(Res.string.signin_developer_title)) {
        AppTextField(
            state = subject,
            placeholder = stringResource(Res.string.signin_developer_subject),
            onImeAction = { onAction(SignInViewModel.Action.OnDeveloperSignInClick) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            text = stringResource(Res.string.signin_developer_button),
            onClick = { onAction(SignInViewModel.Action.OnDeveloperSignInClick) },
            enabled = !state.isLoading && subject.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val ContentMaxWidth = 480.dp
private val MarkSize = 40.dp
private val SpinnerSize = 24.dp

private fun SignOutReason.message() = when (this) {
    SignOutReason.SESSION_EXPIRED -> Res.string.signin_reason_session_expired
    SignOutReason.ACCOUNT_SUSPENDED -> Res.string.signin_reason_account_suspended
    SignOutReason.UNDER_AGE -> Res.string.signin_reason_under_age
    SignOutReason.ACCOUNT_DELETED -> Res.string.signin_reason_account_deleted
}

@Preview
@Composable
private fun SignInScreenPreview(@PreviewParameter(SignInScreenPreviewStates::class) state: SignInViewModel.State) {
    TripinglyTheme { SignInScreen(state = state, developerSubject = rememberTextFieldState("tester"), reason = null, onAction = {}) }
}

/** Signed out because the session expired. */
@Preview
@Composable
private fun SignInScreenReasonPreview() {
    TripinglyTheme(darkTheme = true) { SignInScreen(state = SignInViewModel.State(appleAvailable = true), developerSubject = rememberTextFieldState(), reason = SignOutReason.SESSION_EXPIRED, onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun SignInScreenLandscapePreview() {
    TripinglyTheme(darkTheme = true) { SignInScreen(state = SignInViewModel.State(appleAvailable = true, developerSignInAvailable = true), developerSubject = rememberTextFieldState(), reason = null, onAction = {}) }
}
