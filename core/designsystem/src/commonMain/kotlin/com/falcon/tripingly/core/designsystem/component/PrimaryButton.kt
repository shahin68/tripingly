package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/** How big a button is: [Large] for a screen's main action at the bottom, [Medium] inline (retry, follow). */
enum class ButtonSize(internal val height: Dp, internal val padding: PaddingValues) {
    Large(56.dp, PaddingValues(horizontal = 24.dp)),
    Medium(48.dp, PaddingValues(horizontal = 24.dp)),
}

/**
 * The screen's main action ("Create trip"): an Ivy-filled pill. While [isLoading] it shows a spinner and
 * can't be tapped.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        contentPadding = size.padding,
        modifier = modifier.heightIn(min = size.height),
    ) {
        ButtonContent(text, isLoading, size)
    }
}

/** A quieter action next to or below a [PrimaryButton] ("Not now", "Edit profile"): a neutral filled pill. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    size: ButtonSize = ButtonSize.Large,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = CircleShape,
        contentPadding = size.padding,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = modifier.heightIn(min = size.height),
    ) {
        ButtonContent(text, isLoading, size)
    }
}

@Composable
private fun ButtonContent(text: String, isLoading: Boolean, size: ButtonSize) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(SpinnerSize),
            strokeWidth = 2.dp,
            color = LocalContentColor.current,
        )
    } else {
        Text(
            text = text,
            style = if (size == ButtonSize.Large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

private val SpinnerSize = 22.dp

@Composable
private fun ButtonsPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(MaterialTheme.spacing.medium),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth())
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth(), enabled = false)
        PrimaryButton("Create trip", {}, Modifier.fillMaxWidth(), isLoading = true)
        SecondaryButton("Not now", {}, Modifier.fillMaxWidth())
        PrimaryButton("Try again", {}, size = ButtonSize.Medium)
        SecondaryButton("Edit profile", {}, size = ButtonSize.Medium)
    }
}

@Preview
@Composable
private fun PrimaryButtonPreview() {
    TripinglyTheme { ButtonsPreviewContent() }
}

@Preview
@Composable
private fun PrimaryButtonDarkPreview() {
    TripinglyTheme(darkTheme = true) { ButtonsPreviewContent() }
}
