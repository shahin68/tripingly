package com.falcon.tripingly.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.falcon.tripingly.core.designsystem.theme.TripinglyTheme
import com.falcon.tripingly.core.designsystem.theme.spacing

/**
 * A pill-shaped control for picking one of a few options, e.g. Public / Private: a quiet track with the
 * picked option lifted out as a white (dark: deepest surface) pill.
 * Put one [SegmentedToggleOption] per option in [content].
 */
@Composable
fun SegmentedToggle(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(SegmentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(SegmentPadding),
        content = content,
    )
}

/** One option of a [SegmentedToggle]; the options share the width equally. */
@Composable
fun RowScope.SegmentedToggleOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceVariant,
    )
    val content by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = modifier
            .weight(1f)
            .heightIn(min = SegmentHeight)
            .clip(CircleShape)
            .background(container)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = MaterialTheme.spacing.small),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(SegmentIconSize))
                Spacer(Modifier.width(MaterialTheme.spacing.extraSmall))
            }
            Text(text = label, color = content, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

private val SegmentPadding = 4.dp
private val SegmentHeight = 40.dp
private val SegmentIconSize = 18.dp

@Composable
private fun VisibilityTogglePreviewContent(publicSelected: Boolean) {
    SegmentedToggle(Modifier.fillMaxWidth().padding(16.dp)) {
        SegmentedToggleOption("Public", publicSelected, {}, icon = Icons.Default.Public)
        SegmentedToggleOption("Private", !publicSelected, {}, icon = Icons.Default.Lock)
    }
}

@Preview
@Composable
private fun SegmentedTogglePreview() {
    TripinglyTheme { VisibilityTogglePreviewContent(publicSelected = true) }
}

@Preview
@Composable
private fun SegmentedToggleDarkPreview() {
    TripinglyTheme(darkTheme = true) {
        Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) { VisibilityTogglePreviewContent(publicSelected = false) }
    }
}
