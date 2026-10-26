package com.falcon.tripingly.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.generated.resources.Res
import com.falcon.tripingly.core.designsystem.generated.resources.chip_remove
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing
import org.jetbrains.compose.resources.stringResource

/**
 * A pill-shaped chip: a choice to tap (dates), or something picked that [onRemove] takes away again
 * (a person). With an [error] the chip turns red and says why below it. A [selected] chip (a filter
 * that's on) shows in the primary container colors.
 */
@Composable
fun PillChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    error: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val isError = error != null
    val container = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
    Column(modifier = modifier.semantics { this.selected = selected }, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        val chipContent: @Composable () -> Unit = {
            Row(
                // A bounded width keeps the weighted label measurable in a scrolling row, where the width
                // is otherwise unbounded and the label measures to nothing.
                modifier = Modifier
                    .heightIn(min = ChipHeight)
                    .widthIn(max = ChipMaxWidth)
                    .padding(
                        start = if (leading != null) MaterialTheme.spacing.extraSmall else MaterialTheme.spacing.medium,
                        end = if (onRemove != null) MaterialTheme.spacing.none else MaterialTheme.spacing.medium,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            ) {
                leading?.invoke()
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (onRemove != null) {
                    IconButton(onClick = onRemove, enabled = enabled, modifier = Modifier.size(ChipHeight)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.chip_remove, label),
                            modifier = Modifier.size(RemoveIconSize),
                        )
                    }
                }
            }
        }
        if (onClick != null) {
            Surface(
                onClick = onClick,
                enabled = enabled,
                shape = CircleShape,
                color = container,
                contentColor = content,
                border = border,
                content = chipContent,
            )
        } else {
            Surface(shape = CircleShape, color = container, contentColor = content, border = border, content = chipContent)
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
            )
        }
    }
}

private val ChipHeight = 40.dp
private val ChipMaxWidth = 280.dp
private val RemoveIconSize = 18.dp

@Composable
private fun PillChipPreviewContent() {
    Column(
        Modifier.background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillChip(label = "Add dates", onClick = {}, leading = { Icon(Icons.Default.DateRange, null, Modifier.padding(start = 8.dp)) })
        PillChip(label = "@anna", onRemove = {}, leading = { AvatarCircle("Anna", size = AvatarSize.Small) })
        PillChip(label = "@nobody", onRemove = {}, error = "No one has that username.")
        PillChip(label = "This month", onClick = {}, selected = true)
    }
}

@Preview
@Composable
private fun PillChipPreview() {
    TripinglyTheme { PillChipPreviewContent() }
}

@Preview
@Composable
private fun PillChipDarkPreview() {
    TripinglyTheme(darkTheme = true) { PillChipPreviewContent() }
}
