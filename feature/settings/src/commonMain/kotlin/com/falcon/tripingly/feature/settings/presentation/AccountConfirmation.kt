package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.settings.generated.resources.Res
import com.falcon.tripingly.feature.settings.generated.resources.settings_cancel
import com.falcon.tripingly.feature.settings.generated.resources.settings_delete_confirm
import com.falcon.tripingly.feature.settings.generated.resources.settings_delete_message
import com.falcon.tripingly.feature.settings.generated.resources.settings_delete_title
import com.falcon.tripingly.feature.settings.generated.resources.settings_reauth_apple
import com.falcon.tripingly.feature.settings.generated.resources.settings_reauth_google
import com.falcon.tripingly.feature.settings.generated.resources.settings_reauth_message
import com.falcon.tripingly.feature.settings.generated.resources.settings_reauth_title
import com.falcon.tripingly.feature.settings.generated.resources.settings_sign_out
import com.falcon.tripingly.feature.settings.generated.resources.settings_sign_out_confirm
import com.falcon.tripingly.feature.settings.generated.resources.settings_sign_out_message
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialog
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.AccountDialogState
import com.falcon.tripingly.feature.settings.presentation.SettingsViewModel.Action
import org.jetbrains.compose.resources.stringResource

/** Confirming sign-out or deletion, and the fresh sign-in the server may want before it deletes. */
@Composable
internal fun AccountConfirmation(state: AccountDialogState, appleAvailable: Boolean, onAction: (Action) -> Unit) {
    val dismiss = { onAction(Action.Account.Dismiss) }
    when (state.dialog) {
        AccountDialog.SignOut -> AppDialog(
            title = stringResource(Res.string.settings_sign_out),
            message = stringResource(Res.string.settings_sign_out_message),
            confirmLabel = stringResource(Res.string.settings_sign_out_confirm),
            onConfirm = { onAction(Action.Account.ConfirmSignOut) },
            onDismiss = dismiss,
        )
        AccountDialog.Delete -> AppDialog(
            title = stringResource(Res.string.settings_delete_title),
            message = stringResource(Res.string.settings_delete_message),
            confirmLabel = stringResource(Res.string.settings_delete_confirm),
            isDestructive = true,
            isBusy = state.isDeleting,
            onConfirm = { onAction(Action.Account.ConfirmDelete) },
            onDismiss = dismiss,
        )
        // Two ways to confirm (Google, and Apple on iOS) instead of one confirm button.
        AccountDialog.Reauth -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(Res.string.settings_reauth_title)) },
            text = { Text(stringResource(Res.string.settings_reauth_message)) },
            confirmButton = {
                if (state.isDeleting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { onAction(Action.Account.ConfirmWithGoogle) }) { Text(stringResource(Res.string.settings_reauth_google)) }
                    if (appleAvailable) {
                        TextButton(onClick = { onAction(Action.Account.ConfirmWithApple) }) { Text(stringResource(Res.string.settings_reauth_apple)) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = dismiss, enabled = !state.isDeleting) { Text(stringResource(Res.string.settings_cancel)) }
            },
        )
    }
}

@Preview
@Composable
private fun AccountConfirmationPreview(@PreviewParameter(AccountDialogPreviewStates::class) state: AccountDialogState) {
    TripinglyTheme { AccountConfirmation(state = state, appleAvailable = false, onAction = {}) }
}

/** The fresh sign-in on iOS, which offers Apple too. */
@Preview
@Composable
private fun AccountConfirmationApplePreview() {
    TripinglyTheme(darkTheme = true) { AccountConfirmation(state = AccountDialogState(AccountDialog.Reauth), appleAvailable = true, onAction = {}) }
}
