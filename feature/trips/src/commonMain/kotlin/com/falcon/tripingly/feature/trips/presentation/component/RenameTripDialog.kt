package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.AppTextField
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.*
import com.falcon.tripingly.feature.trips.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

/** Renames a trip; [name] is the view model's field, starting at [currentName]. Done on the keyboard renames. */
@Composable
fun RenameTripDialog(
    name: TextFieldState,
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    isSaving: Boolean = false,
    error: String? = null,
) {
    val canRename = name.text.isNotBlank() && name.text.toString().trim() != currentName
    AppDialog(
        title = stringResource(Res.string.rename_trip_title),
        confirmLabel = stringResource(Res.string.rename_trip_button_rename),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        error = error,
        isBusy = isSaving,
        confirmEnabled = canRename,
    ) {
        AppTextField(
            state = name,
            placeholder = stringResource(Res.string.create_trip_name_label),
            onImeAction = { if (canRename && !isSaving) onConfirm() },
            enabled = !isSaving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Preview
@Composable
private fun RenameTripDialogPreview() {
    TripinglyTheme {
        RenameTripDialog(name = rememberTextFieldState(renameTripInitialNamePreviewData), currentName = renameTripInitialNamePreviewData, onDismiss = {}, onConfirm = {})
    }
}

@Preview
@Composable
private fun RenameTripDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        RenameTripDialog(
            name = rememberTextFieldState("Paris in June"),
            currentName = renameTripInitialNamePreviewData,
            onDismiss = {},
            onConfirm = {},
            error = "Couldn’t rename the trip. No internet connection.",
        )
    }
}
