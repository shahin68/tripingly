package com.falcon.tripingly.feature.auth.presentation.gate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.AppBottomSheet
import com.falcon.tripingly.core.designsystem.component.ErrorScreen
import com.falcon.tripingly.core.designsystem.component.SheetHeader
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.auth.generated.resources.Res
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_consent_title
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_error_load_documents
import com.falcon.tripingly.feature.auth.generated.resources.onboarding_sign_out
import com.falcon.tripingly.feature.auth.generated.resources.reconsent_message
import com.falcon.tripingly.feature.auth.presentation.onboarding.ConsentFooter
import com.falcon.tripingly.feature.auth.presentation.onboarding.ConsentStep
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel
import com.falcon.tripingly.feature.auth.presentation.onboarding.OnboardingViewModel.Action
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * New terms to accept while the app is open (Shahin, 2026-11-22): the onboarding's consent step in a sheet over the app,
 * which can't be swiped away. Accepting brings the session back to signed in and the sheet slides down; Sign out is
 * the other way out, as on the onboarding page.
 */
@Composable
internal fun ReconsentSheet(isShown: Boolean) {
    AppBottomSheet(value = Unit.takeIf { isShown }, onDismiss = {}, dismissEnabled = false, followsKeyboard = false) { _, _ ->
        // Made only once the sheet shows: the onboarding's view model loads the documents for this account.
        val viewModel: OnboardingViewModel = koinViewModel()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val onAction = viewModel::onAction
        ShowMessage(message = state.error?.asString(), onShown = { onAction(Action.OnDismissError) })
        Column {
            SheetHeader(
                title = stringResource(Res.string.onboarding_consent_title),
                subtitle = stringResource(Res.string.reconsent_message),
                onClose = null,
                actions = { TextButton(onClick = { onAction(Action.OnSignOutClick) }) { Text(stringResource(Res.string.onboarding_sign_out)) } },
            )
            if (!state.isLoadingDocuments && state.documents.isEmpty()) {
                ErrorScreen(onRetry = { onAction(Action.OnRetryDocumentsClick) }, message = stringResource(Res.string.onboarding_error_load_documents))
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = MaterialTheme.spacing.medium)) {
                    ConsentStep(state)
                }
                if (!state.isLoadingDocuments) ConsentFooter(state, onAction)
            }
        }
    }
}
