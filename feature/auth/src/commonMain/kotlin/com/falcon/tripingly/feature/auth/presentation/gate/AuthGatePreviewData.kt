package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.error_no_internet
import com.falcon.tripingly.feature.auth.presentation.common.UiMessage

/** Restoring the session, offline, and retrying. */
internal class LaunchScreenPreviewStates : PreviewParameterProvider<AuthGateViewModel.State> {
    private val offline = AuthGateViewModel.State(restoreError = UiMessage.Resource(Res.string.error_no_internet))
    override val values = sequenceOf(AuthGateViewModel.State(), offline, offline.copy(isRetrying = true))
}
