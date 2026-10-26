package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/** The small heading above a field or a group of fields in a form or sheet ("Who can see it"). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** A form field with its [label] above it: every field in the app's forms and sheets is laid out like this. */
@Composable
fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        SectionLabel(label, Modifier.padding(start = MaterialTheme.spacing.extraSmall))
        content()
    }
}

@Composable
private fun SectionLabelPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(MaterialTheme.spacing.medium)) {
        SectionLabel("Who can see it")
        FormField("Trip name") { RoundedTextField("Autumn in Vienna", {}, "Give it a name", Modifier.fillMaxWidth()) }
    }
}

@Preview
@Composable
private fun SectionLabelPreview() {
    TripinglyTheme { SectionLabelPreviewContent() }
}

@Preview
@Composable
private fun SectionLabelDarkPreview() {
    TripinglyTheme(darkTheme = true) { SectionLabelPreviewContent() }
}
