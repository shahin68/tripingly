package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.*
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

@Composable
fun FloatingSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(Res.string.common_search_placeholder),
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.medium),
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

@Composable
private fun FloatingSearchBarPreviewContent() {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        FloatingSearchBar(query = "", onQueryChange = {})
        FloatingSearchBar(
            query = "Eiffel",
            onQueryChange = {},
            trailingIcon = { Icon(Icons.Default.Close, contentDescription = null) },
        )
        FloatingSearchBar(query = "A very long search for a museum somewhere in the centre of Paris", onQueryChange = {})
    }
}

@Preview
@Composable
private fun FloatingSearchBarPreview() {
    TripinglyTheme { FloatingSearchBarPreviewContent() }
}

@Preview
@Composable
private fun FloatingSearchBarDarkPreview() {
    TripinglyTheme(darkTheme = true) { FloatingSearchBarPreviewContent() }
}
