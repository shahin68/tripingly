package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * One entry of an [AppDropdownMenu]. An optional [icon] leads it; a [badge] (e.g. how many join requests wait)
 * sits at the end in the accent color, and a [selected] entry (the filter in use) ends with a check mark.
 */
@Immutable
data class DropdownAction(
    val label: String,
    val isDestructive: Boolean = false,
    val icon: ImageVector? = null,
    val badge: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * The app's overflow menu (⋮, long press): solid rounded card, one row per [DropdownAction], destructive ones
 * in red. Popups open in their own window, where glass blur can't reach, so the menu is opaque.
 */
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<DropdownAction>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = GlassDefaults.ShadowElevation,
            modifier = Modifier.widthIn(min = MenuMinWidth),
        ) {
            actions.forEach { action ->
                val color = if (action.isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified
                DropdownMenuItem(
                    text = { Text(text = action.label, color = color, style = MaterialTheme.typography.bodyLarge) },
                    leadingIcon = action.icon?.let { icon ->
                        {
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = if (action.isDestructive) color else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = when {
                        action.badge != null -> ({ MenuBadge(action.badge) })
                        action.selected -> ({ Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) })
                        else -> null
                    },
                    onClick = {
                        action.onClick()
                        onDismissRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun MenuBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall / 2),
    )
}

private val MenuMinWidth = 200.dp

private val previewActions = listOf(
    DropdownAction("Trip members · 4", icon = Icons.Outlined.Group, badge = "2", onClick = {}),
    DropdownAction("Rename trip", icon = Icons.Outlined.Edit, onClick = {}),
    DropdownAction("Change dates", icon = Icons.Outlined.DateRange, selected = true, onClick = {}),
    DropdownAction("Delete trip", isDestructive = true, icon = Icons.Outlined.Delete, onClick = {}),
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
