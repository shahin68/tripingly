package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/**
 * The screen's main action ("Create trip"): a high-contrast pill, dark on light and light on dark.
 * While [isLoading] it shows a spinner and can't be tapped.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier.heightIn(min = ButtonHeight),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(SpinnerSize),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
    }
}

private val ButtonHeight = 56.dp
private val SpinnerSize = 22.dp

@Composable
private fun PrimaryButtonPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth())
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth().padding(top = 8.dp), enabled = false)
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth().padding(top = 8.dp), isLoading = true)
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
    TripinglyTheme { PrimaryButtonPreviewContent() }
}

@Preview
@Composable
private fun PrimaryButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { PrimaryButtonPreviewContent() }
}
