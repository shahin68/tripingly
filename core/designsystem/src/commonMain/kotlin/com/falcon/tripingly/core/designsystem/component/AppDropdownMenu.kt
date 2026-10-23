package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme

data class DropdownAction(
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<DropdownAction>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = action.label,
                            color = if (action.isDestructive) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            }
                        )
                    },
                    onClick = {
                        action.onClick()
                        onDismissRequest()
                    }
                )
            }
        }
    }
}

private val previewActions = listOf(
    DropdownAction("Rename trip", onClick = {}),
    DropdownAction("Change dates", onClick = {}),
    DropdownAction("Delete trip", isDestructive = true, onClick = {}),
)

@Preview
@Composable
private fun AppDropdownMenuPreview() {
    TripinglyTheme { AppDropdownMenu(expanded = true, onDismissRequest = {}, actions = previewActions) }
}

@Preview
@Composable
private fun AppDropdownMenuDarkPreview() {
    TripinglyTheme(darkTheme = true) { AppDropdownMenu(expanded = true, onDismissRequest = {}, actions = previewActions) }
}
