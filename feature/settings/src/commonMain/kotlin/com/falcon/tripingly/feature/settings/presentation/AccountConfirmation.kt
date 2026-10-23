package com.falcon.tripingly.feature.settings.presentation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                stringResource(
                    when (state.dialog) {
                        AccountDialog.SignOut -> Res.string.settings_sign_out
                        AccountDialog.Delete -> Res.string.settings_delete_title
                        AccountDialog.Reauth -> Res.string.settings_reauth_title
                    },
                ),
            )
        },
        text = {
            Text(
                stringResource(
                    when (state.dialog) {
                        AccountDialog.SignOut -> Res.string.settings_sign_out_message
                        AccountDialog.Delete -> Res.string.settings_delete_message
                        AccountDialog.Reauth -> Res.string.settings_reauth_message
                    },
                ),
            )
        },
        confirmButton = {
            when {
                state.isDeleting -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                state.dialog == AccountDialog.SignOut -> TextButton(onClick = { onAction(Action.Account.ConfirmSignOut) }) {
                    Text(stringResource(Res.string.settings_sign_out_confirm))
                }
                state.dialog == AccountDialog.Delete -> TextButton(onClick = { onAction(Action.Account.ConfirmDelete) }) {
                    Text(stringResource(Res.string.settings_delete_confirm), color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    TextButton(onClick = { onAction(Action.Account.ConfirmWithGoogle) }) {
                        Text(stringResource(Res.string.settings_reauth_google))
                    }
                    if (appleAvailable) {
                        TextButton(onClick = { onAction(Action.Account.ConfirmWithApple) }) {
                            Text(stringResource(Res.string.settings_reauth_apple))
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !state.isDeleting) { Text(stringResource(Res.string.settings_cancel)) }
        },
    )
}
