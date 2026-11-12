package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.common_close
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * The top of every bottom sheet: its [title] (and [subtitle]) with a round close button, which waits while
 * [closeEnabled] is false (e.g. while saving). [onClose] null leaves the close button out (the sheet still closes with a swipe or back). [actions] sit
 * before the close button on their own; [joinedActions] are joined with it into one group (Delete ✕, Add ✕).
 */
@Composable
fun SheetHeader(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    joinedActions: List<JoinedButtonSlot> = emptyList(),
) {
    val joined = joinedActions.isNotEmpty() && onClose != null
    Row(
        // The close button's touch area makes up the rest of the end, so its circle sits as far in as the title; a
        // joined group has no touch area around it, so it sits that far in itself.
        modifier = modifier.fillMaxWidth().padding(
            start = MaterialTheme.spacing.large,
            end = if (joined) MaterialTheme.spacing.large else MaterialTheme.spacing.medium + MaterialTheme.spacing.extraSmall,
            bottom = MaterialTheme.spacing.small,
        ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
        when {
            onClose == null -> Unit
            joined -> {
                val close = stringResource(Res.string.common_close)
                val closeButton: JoinedButtonSlot = { shape -> JoinedButton(Icons.Default.Close, close, shape, onClick = onClose, enabled = closeEnabled) }
                JoinedButtons(joinedActions + listOf(closeButton))
            }
            else -> FilledTonalIconButton(onClick = onClose, enabled = closeEnabled) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.common_close))
            }
        }
    }
}

@Preview
@Composable
private fun SheetHeaderPreview() {
    TripinglyTheme {
        Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
            SheetHeader(title = "New trip", onClose = {})
            SheetHeader(title = "Comments", subtitle = "Harrachgasse", onClose = {})
            SheetHeader(
                title = "Giant Ferris Wheel",
                subtitle = "Attraction · Stop 2",
                onClose = {},
                joinedActions = listOf<JoinedButtonSlot>({ JoinedButton(Icons.Outlined.Delete, "Delete", it, onClick = {}, colors = JoinedButtonDefaults.destructive()) }),
            )
        }
    }
}

@Preview
@Composable
private fun SheetHeaderDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        SheetHeader(title = "Trip members", subtitle = "4 people", onClose = {}, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}
