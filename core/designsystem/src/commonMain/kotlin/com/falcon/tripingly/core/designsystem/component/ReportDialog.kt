package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_cancel
import com.falcon.tripingly.core.designsystem.generated.resources.report_details
import com.falcon.tripingly.core.designsystem.generated.resources.report_send
import com.falcon.tripingly.core.designsystem.generated.resources.report_title
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import com.falcon.tripingly.core.model.config.ConfigItem
import org.jetbrains.compose.resources.stringResource

/** The server's longest report details. */
private const val MAX_DETAILS_LENGTH = 1000

/** Why something can be reported: the server's list, labelled in the app's language, provided once at the app's root. */
val LocalReportReasons = staticCompositionLocalOf { emptyList<ConfigItem>() }

/**
 * Asks why something (a person, trip, stop, photo or comment) is being reported. The optional [details] text is held
 * by the screen's view model, which reads it when [onReport] sends the report and clears it when the dialog closes.
 */
@Composable
fun ReportDialog(
    details: TextFieldState,
    onReport: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    isSending: Boolean = false,
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            // Its own height limit: on iOS a dialog is now and then measured with none, and a scroll would throw.
            Column(Modifier.heightIn(max = ReportListMaxHeight).verticalScroll(rememberScrollState()).selectableGroup()) {
                LocalReportReasons.current.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = reason == option.key, role = Role.RadioButton, onClick = { reason = option.key })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option.key, onClick = null)
                        Text(option.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                AppTextField(
                    state = details,
                    placeholder = stringResource(Res.string.report_details),
                    modifier = Modifier.fillMaxWidth().padding(top = MaterialTheme.spacing.small),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 4),
                    inputTransformation = InputTransformation.maxLength(MAX_DETAILS_LENGTH),
                    style = AppTextFieldStyle.Dialog,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let(onReport) },
                enabled = reason != null && !isSending,
            ) {
                Text(stringResource(Res.string.report_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Preview
@Composable
private fun ReportDialogPreview() {
    TripinglyTheme { CompositionLocalProvider(LocalReportReasons provides PreviewReportReasons) { ReportDialog(details = rememberTextFieldState(), onReport = {}, onDismiss = {}) } }
}

@Preview
@Composable
private fun ReportDialogDarkPreview() {
    TripinglyTheme(darkTheme = true) { CompositionLocalProvider(LocalReportReasons provides PreviewReportReasons) { ReportDialog(details = rememberTextFieldState(), onReport = {}, onDismiss = {}) } }
}

@Preview(widthDp = 800, heightDp = 360)
@Composable
private fun ReportDialogLandscapePreview() {
    TripinglyTheme { CompositionLocalProvider(LocalReportReasons provides PreviewReportReasons) { ReportDialog(details = rememberTextFieldState(), onReport = {}, onDismiss = {}) } }
}

private val ReportListMaxHeight = 480.dp

private val PreviewReportReasons = listOf(
    ConfigItem("spam", "Spam"),
    ConfigItem("harassment", "Harassment or bullying"),
    ConfigItem("other", "Something else"),
)
