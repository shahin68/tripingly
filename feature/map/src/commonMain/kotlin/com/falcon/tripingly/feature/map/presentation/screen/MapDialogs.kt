package com.falcon.tripingly.feature.map.presentation.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_apply
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_hint
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_saved
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_title
import com.falcon.tripingly.feature.map.generated.resources.map_cancel
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_clear_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_delete
import com.falcon.tripingly.feature.map.generated.resources.map_confirm_remove
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_message
import com.falcon.tripingly.feature.map.generated.resources.map_delete_day_title
import com.falcon.tripingly.feature.map.generated.resources.map_delete_others_content_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_continue
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_explain_title
import com.falcon.tripingly.feature.map.generated.resources.map_location_open_settings
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_message
import com.falcon.tripingly.feature.map.generated.resources.map_location_settings_title
import com.falcon.tripingly.feature.map.generated.resources.map_pin_add
import com.falcon.tripingly.feature.map.generated.resources.map_pin_name_label
import com.falcon.tripingly.feature.map.generated.resources.map_pin_name_title
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content_message
import com.falcon.tripingly.feature.map.generated.resources.map_remove_my_content_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The map screen's dialogs, one at a time. */
@Composable
internal fun MapDialogs(dialog: MapDialog, onAction: (MapAction) -> Unit) {
    when (dialog) {
        is MapDialog.ClearDay -> ConfirmDialog(
            title = stringResource(Res.string.map_clear_day_title, dialog.dayNumber),
            text = stringResource(Res.string.map_clear_day_message, dialog.stopCount),
            confirm = Res.string.map_confirm_delete,
            onAction = onAction,
        )
        is MapDialog.DeleteDay -> ConfirmDialog(
            title = stringResource(Res.string.map_delete_day_title, dialog.dayNumber),
            text = stringResource(Res.string.map_delete_day_message, dialog.stopCount),
            confirm = Res.string.map_confirm_delete,
            onAction = onAction,
        )
        MapDialog.RemoveMyContent -> ConfirmDialog(
            title = stringResource(Res.string.map_remove_my_content_title),
            text = stringResource(Res.string.map_remove_my_content_message),
            confirm = Res.string.map_confirm_remove,
            onAction = onAction,
        )
        is MapDialog.DeleteOthersContent -> SimpleDialog(
            title = stringResource(Res.string.map_delete_others_content_title),
            text = dialog.message.asString(),
            confirm = stringResource(Res.string.map_confirm_delete),
            isDestructive = true,
            onConfirm = { onAction(MapAction.OnDeleteAnyway) },
            onDismiss = { onAction(MapAction.OnDismissDeleteAnyway) },
        )
        is MapDialog.Report -> ReportDialog(
            onReport = { reason, details -> onAction(MapAction.OnSendReport(reason, details)) },
            onDismiss = { onAction(MapAction.OnDismissReport) },
            isSending = dialog.isSending,
        )
        MapDialog.NamePin -> NamePinDialog(onAction)
        is MapDialog.Location -> SimpleDialog(
            title = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_explain_title
                    LocationPrompt.OpenSettings -> Res.string.map_location_settings_title
                },
            ),
            text = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_explain_message
                    LocationPrompt.OpenSettings -> Res.string.map_location_settings_message
                },
            ),
            confirm = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_continue
                    LocationPrompt.OpenSettings -> Res.string.map_location_open_settings
                },
            ),
            onConfirm = { onAction(MapAction.OnConfirmLocationPrompt) },
            onDismiss = { onAction(MapAction.OnDismissLocationPrompt) },
        )
        is MapDialog.BestOrder -> BestOrderDialog(dialog.preview, onAction)
    }
}

/** Clearing or deleting a day, or removing my content from a stop. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirm: StringResource, onAction: (MapAction) -> Unit) = SimpleDialog(
    title = title,
    text = text,
    confirm = stringResource(confirm),
    isDestructive = true,
    onConfirm = { onAction(MapAction.OnConfirm) },
    onDismiss = { onAction(MapAction.OnDismissConfirm) },
)

@Composable
private fun SimpleDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isDestructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.map_cancel))
            }
        },
    )
}

/** The best order, for the user to look at before it's saved. */
@Composable
private fun BestOrderDialog(order: BestOrderPreview, onAction: (MapAction) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAction(MapAction.OnDismissBestOrder) },
        title = { Text(stringResource(Res.string.map_best_order_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.stops.forEachIndexed { index, stop ->
                    Text("${index + 1}. ${stop.title.asString()}", style = MaterialTheme.typography.bodyMedium)
                }
                // Free: by distance, with what Tripinly Pro does instead. Pro: the minutes it saves.
                val savedMinutes = order.savedMinutes?.takeIf { it > 0 }
                val note = when {
                    !order.byTravelTime -> stringResource(Res.string.map_best_order_hint)
                    savedMinutes != null -> stringResource(Res.string.map_best_order_saved, savedMinutes)
                    else -> null
                }
                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(MapAction.OnApplyBestOrder) }) {
                Text(stringResource(Res.string.map_best_order_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MapAction.OnDismissBestOrder) }) {
                Text(stringResource(Res.string.map_cancel))
            }
        },
    )
}

@Composable
private fun NamePinDialog(onAction: (MapAction) -> Unit) {
    // What the user typed: the text field's own state, kept across a rotation.
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { onAction(MapAction.OnDismissPinName) },
        title = { Text(stringResource(Res.string.map_pin_name_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_PIN_NAME_LENGTH) },
                label = { Text(stringResource(Res.string.map_pin_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onAction(MapAction.OnConfirmPinName(name)) }, enabled = name.isNotBlank()) {
                Text(stringResource(Res.string.map_pin_add))
            }
        },
        dismissButton = {
            TextButton(onClick = { onAction(MapAction.OnDismissPinName) }) {
                Text(stringResource(Res.string.map_cancel))
            }
        },
    )
}

/** The server's limit for a stop's name. */
private const val MAX_PIN_NAME_LENGTH = 120
