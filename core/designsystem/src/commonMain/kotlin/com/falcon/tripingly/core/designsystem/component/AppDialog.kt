package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_cancel
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The app's dialog: a [title], a [message] and any [content] under it, with Cancel and [confirmLabel].
 * [isDestructive] paints the confirm button red (delete, leave); while [isBusy] both buttons wait and the confirm
 * button spins. [error] is the server's answer when it refused the change.
 */
@Composable
fun AppDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    error: String? = null,
    isDestructive: Boolean = false,
    isBusy: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String = stringResource(Res.string.common_cancel),
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                content()
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled && !isBusy,
                colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
            ) {
                if (isBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current) else Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isBusy) { Text(dismissLabel) } },
    )
}

@Preview
@Composable
private fun AppDialogPreview() {
    TripinglyTheme {
        AppDialog(
            title = "Delete trip?",
            message = "Summer in Vienna and its stops are deleted for everyone.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun AppDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        AppDialog(title = "Rename trip", confirmLabel = "Save", isBusy = true, onConfirm = {}, onDismiss = {}) {
            RoundedTextField(value = "Summer in Vienna", onValueChange = {}, placeholder = "Trip name")
        }
    }
}
