package com.falcon.tripingly.feature.auth.presentation.signin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.signin_error_failed
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage

/** Google only (Android), with Apple (iOS), signing in, a failed sign-in, and developer sign-in (local and staging builds). */
internal class SignInScreenPreviewStates : PreviewParameterProvider<SignInViewModel.State> {
    override val values = sequenceOf(
        SignInViewModel.State(),
        SignInViewModel.State(appleAvailable = true),
        SignInViewModel.State(appleAvailable = true, isLoading = true),
        SignInViewModel.State(error = UiMessage.Resource(Res.string.signin_error_failed)),
        SignInViewModel.State(developerSignInAvailable = true, developerSubject = "tester"),
    )
}
