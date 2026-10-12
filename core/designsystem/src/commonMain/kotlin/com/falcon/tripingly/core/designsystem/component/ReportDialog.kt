package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_cancel
import com.falcon.tripingly.core.designsystem.generated.resources.report_details
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_harassment
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_hate
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_nudity
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_other
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_spam
import com.falcon.tripingly.core.designsystem.generated.resources.report_reason_violence
import com.falcon.tripingly.core.designsystem.generated.resources.report_send
import com.falcon.tripingly.core.designsystem.generated.resources.report_title
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.model.social.ReportReason
import org.jetbrains.compose.resources.stringResource

/** The server's longest report details. */
private const val MAX_DETAILS_LENGTH = 1000

/** Asks why something (a person, trip, stop, photo or comment) is being reported. */
@Composable
fun ReportDialog(
    onReport: (reason: ReportReason, details: String) -> Unit,
    onDismiss: () -> Unit,
    isSending: Boolean = false,
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.report_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                ReportReason.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .selectable(selected = reason == option, role = Role.RadioButton, onClick = { reason = option })
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = null)
                        Text(option.label(), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(MAX_DETAILS_LENGTH) },
                    label = { Text(stringResource(Res.string.report_details)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { reason?.let { onReport(it, details) } },
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

@Composable
private fun ReportReason.label(): String = stringResource(
    when (this) {
        ReportReason.Spam -> Res.string.report_reason_spam
        ReportReason.Harassment -> Res.string.report_reason_harassment
        ReportReason.Nudity -> Res.string.report_reason_nudity
        ReportReason.Violence -> Res.string.report_reason_violence
        ReportReason.Hate -> Res.string.report_reason_hate
        ReportReason.Other -> Res.string.report_reason_other
    },
)

@Preview
@Composable
private fun ReportDialogPreview() {
    TripinglyTheme { ReportDialog(onReport = { _, _ -> }, onDismiss = {}) }
}
