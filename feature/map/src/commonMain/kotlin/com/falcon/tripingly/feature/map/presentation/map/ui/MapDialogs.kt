package com.falcon.tripingly.feature.map.presentation.map.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.component.ReportDialog
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.feature.map.generated.resources.Res
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_apply
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_hint
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_saved
import com.falcon.tripingly.feature.map.generated.resources.map_best_order_title
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
import com.falcon.tripingly.feature.map.presentation.map.BestOrderPreview
import com.falcon.tripingly.feature.map.presentation.map.LocationPrompt
import com.falcon.tripingly.feature.map.presentation.map.MapAction
import com.falcon.tripingly.feature.map.presentation.map.MapDialog
import com.falcon.tripingly.feature.map.presentation.map.preview.MapDialogPreviewStates
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
        is MapDialog.DeleteOthersContent -> AppDialog(
            title = stringResource(Res.string.map_delete_others_content_title),
            message = dialog.message.asString(),
            confirmLabel = stringResource(Res.string.map_confirm_delete),
            isDestructive = true,
            onConfirm = { onAction(MapAction.Dialog.DeleteAnyway) },
            onDismiss = { onAction(MapAction.Dialog.DismissDeleteAnyway) },
        )
        is MapDialog.Report -> ReportDialog(
            details = dialog.details,
            onReport = { reason -> onAction(MapAction.Dialog.SendReport(reason)) },
            onDismiss = { onAction(MapAction.Dialog.DismissReport) },
            isSending = dialog.isSending,
        )
        is MapDialog.NamePin -> NamePinDialog(dialog.name, onAction)
        is MapDialog.Location -> AppDialog(
            title = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_explain_title
                    LocationPrompt.OpenSettings -> Res.string.map_location_settings_title
                },
            ),
            message = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_explain_message
                    LocationPrompt.OpenSettings -> Res.string.map_location_settings_message
                },
            ),
            confirmLabel = stringResource(
                when (dialog.prompt) {
                    LocationPrompt.Explain -> Res.string.map_location_continue
                    LocationPrompt.OpenSettings -> Res.string.map_location_open_settings
                },
            ),
            onConfirm = { onAction(MapAction.Location.ConfirmPrompt) },
            onDismiss = { onAction(MapAction.Location.DismissPrompt) },
        )
        is MapDialog.BestOrder -> BestOrderDialog(dialog.preview, onAction)
    }
}

/** Clearing or deleting a day, or removing my content from a stop. */
@Composable
private fun ConfirmDialog(title: String, text: String, confirm: StringResource, onAction: (MapAction) -> Unit) = AppDialog(
    title = title,
    message = text,
    confirmLabel = stringResource(confirm),
    isDestructive = true,
    onConfirm = { onAction(MapAction.Dialog.Confirm) },
    onDismiss = { onAction(MapAction.Dialog.DismissConfirm) },
)

/** The best order, for the user to look at before it's saved. */
@Composable
private fun BestOrderDialog(order: BestOrderPreview, onAction: (MapAction) -> Unit) {
    // Free: by distance, with what Tripinly Pro does instead. Pro: the minutes it saves.
    val savedMinutes = order.savedMinutes?.takeIf { it > 0 }
    val note = when {
        !order.byTravelTime -> stringResource(Res.string.map_best_order_hint)
        savedMinutes != null -> stringResource(Res.string.map_best_order_saved, savedMinutes)
        else -> null
    }
    AppDialog(
        title = stringResource(Res.string.map_best_order_title),
        confirmLabel = stringResource(Res.string.map_best_order_apply),
        onConfirm = { onAction(MapAction.Dialog.ApplyBestOrder) },
        onDismiss = { onAction(MapAction.Dialog.DismissBestOrder) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
            order.stops.forEachIndexed { index, stop ->
                Text("${index + 1}. ${stop.title.asString()}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Names a dropped pin; [name] is the view model's field. Done on the keyboard adds the stop. */
@Composable
private fun NamePinDialog(name: TextFieldState, onAction: (MapAction) -> Unit) {
    val canAdd = name.text.isNotBlank()
    AppDialog(
        title = stringResource(Res.string.map_pin_name_title),
        confirmLabel = stringResource(Res.string.map_pin_add),
        confirmEnabled = canAdd,
        onConfirm = { onAction(MapAction.Dialog.ConfirmPinName) },
        onDismiss = { onAction(MapAction.Dialog.DismissPinName) },
    ) {
        AppTextField(
            state = name,
            placeholder = stringResource(Res.string.map_pin_name_label),
            onImeAction = { if (canAdd) onAction(MapAction.Dialog.ConfirmPinName) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            inputTransformation = InputTransformation.maxLength(MAX_PIN_NAME_LENGTH),
        )
    }
}

/** The server's limit for a stop's name. */
private const val MAX_PIN_NAME_LENGTH = 120

@Preview
@Composable
private fun MapDialogsPreview(@PreviewParameter(MapDialogPreviewStates::class) dialog: MapDialog) {
    TripinglyTheme { MapDialogs(dialog = dialog, onAction = {}) }
}

@Preview
@Composable
private fun MapDialogsDarkPreview() {
    TripinglyTheme(darkTheme = true) { MapDialogs(dialog = MapDialog.NamePin(rememberTextFieldState("Our hotel")), onAction = {}) }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun BestOrderDialogLandscapePreview() {
    TripinglyTheme(darkTheme = true) { MapDialogs(dialog = MapDialogPreviewStates().values.elementAt(8), onAction = {}) }
}
