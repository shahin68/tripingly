package com.falcon.tripingly.feature.trips.presentation.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.component.AppDialog
import com.falcon.tripingly.core.designsystem.component.RoundedTextField
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.feature.trips.generated.resources.*
import com.falcon.tripingly.feature.trips.generated.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
fun RenameTripDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    isSaving: Boolean = false,
    error: String? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }

    AppDialog(
        title = stringResource(Res.string.rename_trip_title),
        confirmLabel = stringResource(Res.string.rename_trip_button_rename),
        onConfirm = { onConfirm(name) },
        onDismiss = onDismiss,
        error = error,
        isBusy = isSaving,
        confirmEnabled = name.isNotBlank() && name.trim() != initialName,
    ) {
        RoundedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(Res.string.create_trip_name_label),
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
        RenameTripDialog(
            initialName = renameTripInitialNamePreviewData,
            onDismiss = {},
            onConfirm = {}
        )
    }
}


@Preview
@Composable
private fun RenameTripDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        RenameTripDialog(initialName = renameTripInitialNamePreviewData, onDismiss = {}, onConfirm = {}, error = "Couldn’t rename the trip. No internet connection.")
    }
}
