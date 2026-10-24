package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

/** One search suggestion, e.g. a place with its address, on whatever surface holds the list. */
@Composable
fun SuggestionItem(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun SuggestionItemPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        SuggestionItem(title = "Eiffel Tower", subtitle = "Avenue Gustave Eiffel, Paris, France", onClick = {})
        SuggestionItem(title = "Paris", subtitle = null, onClick = {})
        SuggestionItem(
            title = "Musée national des Arts asiatiques – Guimet, the long name of a museum",
            subtitle = "6 Place d'Iéna, 75116 Paris, Île-de-France, France",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun SuggestionItemPreview() {
    TripinglyTheme { SuggestionItemPreviewContent() }
}

@Preview
@Composable
private fun SuggestionItemDarkPreview() {
    TripinglyTheme(darkTheme = true) { SuggestionItemPreviewContent() }
}
